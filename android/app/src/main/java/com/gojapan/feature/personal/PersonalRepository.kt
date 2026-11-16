package com.gojapan.feature.personal

import android.content.Context
import android.util.AtomicFile
import com.gojapan.core.ai.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

interface PersonalStorage {
    fun read(): PersonalSnapshot
    fun write(snapshot: PersonalSnapshot)
}

class AtomicPersonalStorage(context: Context) : PersonalStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "personal-learning.json"))
    override fun read(): PersonalSnapshot = try {
        file.openRead().bufferedReader().use { personalJson.decodeFromString<PersonalSnapshot>(it.readText()) }
    } catch (e: java.io.FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw e
        PersonalSnapshot()
    }
    override fun write(snapshot: PersonalSnapshot) {
        val stream = file.startWrite()
        try {
            stream.write(personalJson.encodeToString(snapshot).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            check(read() == snapshot) { "本地写入校对失败" }
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }
}

interface PersonalAnalysisClient {
    val provider: String
    val model: String
    suspend fun analyze(request: String): String
}
class PersonalAnalysisAdapter(private val client: OpenAiCompatibleChatClient) : PersonalAnalysisClient {
    override val provider get() = client.providerName
    override val model get() = client.modelName
    override suspend fun analyze(request: String) = client.complete(listOf(AiChatTurn(AiChatRole.USER, request)), ANALYSIS_PROMPT, 6000)
}

/** One immutable snapshot is the transaction boundary for content, versions and traces. */
class PersonalEngine(
    private val storage: PersonalStorage,
    private val client: PersonalAnalysisClient,
    private val secret: () -> String? = { null },
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(PersonalSnapshot())
    val state = mutable.asStateFlow()
    private var loaded = false
    private fun now() = System.currentTimeMillis()
    private fun id() = UUID.randomUUID().toString()
    private fun safe(value: String): String {
        var result = value
        secret()?.takeIf { it.isNotBlank() }?.let {
            result = result.replace(it, "[REDACTED]")
                .replace(personalJson.encodeToString(it).removeSurrounding("\""), "[REDACTED]")
        }
        return result.replace(Regex("(?i)Bearer\\s+[^\\s\";,]+"), "Bearer [REDACTED]")
            .replace(Regex("sk-[A-Za-z0-9_-]+"), "[REDACTED]")
    }
    private fun commit(snapshot: PersonalSnapshot) {
        storage.write(snapshot)
        mutable.value = snapshot
    }
    private fun load() {
        if (loaded) return
        val snapshot = storage.read()
        val recovered = snapshot.copy(traces = snapshot.traces.map { trace ->
            trace.copy(steps = trace.steps.map { if (it.status == "处理中") it.copy(status = "失败", endedAt = now(), output = "应用中断，请重试") else it })
        })
        if (recovered != snapshot) storage.write(recovered)
        mutable.value = recovered
        loaded = true
    }
    suspend fun initialize() = withContext(Dispatchers.IO) { mutex.withLock { load() } }
    private fun updateTrace(trace: ProcessingTrace) = commit(mutable.value.copy(traces = mutable.value.traces.filterNot { it.id == trace.id } + trace))

    suspend fun generate(input: String, language: String, entryId: String? = null, retryOf: String? = null, traceId: String = id(), feedback: String = ""): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            val original = safe(input)
            val normalized = original.trim()
            var trace = ProcessingTrace(traceId, entryId, retryOf = retryOf)
            var stage = "输入接收与预处理"
            fun start(name: String, data: String) {
                stage = name
                trace = trace.copy(steps = trace.steps + ProcessingStep(name, now(), input = safe(data)))
                updateTrace(trace)
            }
            fun done(output: String, status: String = "成功") {
                trace = trace.copy(steps = trace.steps.dropLast(1) + trace.steps.last().copy(endedAt = now(), status = status, output = safe(output)))
                updateTrace(trace)
            }
            try {
                start(stage, original)
                require(input.isNotBlank()) { "请输入中文意图或日语句子" }
                require(input.length <= 1000) { "输入最多 1000 个字符，请缩短后重试" }
                done("规范化输入：$normalized\n语言：$language")
                start("本地检索", normalized)
                val existing = if (entryId != null) mutable.value.entries.firstOrNull { it.id == entryId }
                    ?: error("条目已删除") else mutable.value.entries.firstOrNull { it.normalized == normalized && it.language == language }
                if (existing != null && entryId == null) {
                    trace = trace.copy(entryId = existing.id, version = existing.current)
                    done("命中 ${existing.id} v${existing.current}；复用已保存版本，未调用 AI")
                    return@withLock existing.id
                }
                val targetId = existing?.id ?: id()
                val version = (existing?.versions?.maxOfOrNull { it.number } ?: 0) + 1
                trace = trace.copy(entryId = targetId, version = version)
                done(if (existing == null) "新建条目 $targetId" else "保留当前 v${existing.current}，新增候选 v$version")
                val request = kotlinx.serialization.json.buildJsonObject {
                    put("language", kotlinx.serialization.json.JsonPrimitive(language))
                    put("input", kotlinx.serialization.json.JsonPrimitive(original))
                    if (existing != null) {
                        put("previous", personalJson.parseToJsonElement(personalJson.encodeToString(existing.versions.last().content)))
                        put("revision", kotlinx.serialization.json.JsonPrimitive(feedback))
                    }
                }.toString().let { com.gojapan.core.ai.formatAiText(it) }
                start("AI 请求", "提供方：${client.provider}；模型：${client.model}；提示词 japanese-analysis-1；结构 1\n$ANALYSIS_PROMPT\n$request")
                val raw = client.analyze(request)
                done("请求成功，响应 ${raw.length} 字符")
                start("AI 响应", safe(raw))
                require(raw.length <= 100_000) { "响应过长" }
                val content = personalJson.decodeFromString<AnalysisContent>(safe(raw).trim())
                done(personalJson.encodeToString(content))
                start("结构与内容校验", "必填字段、原文跨度、关联、缺失字段说明；程序校验不代表语言已经人工核实")
                val errors = validateContent(content, if (language == "日语") original else null)
                require(errors.isEmpty()) { errors.joinToString("；") }
                done("全部结构规则通过；语言内容待人工核实")
                start("本地写入", "$targetId v$version")
                val savedVersion = PersonalVersion(version, now(), trace.id, content, "AI 生成", client.provider, client.model)
                val entry = existing?.copy(versions = existing.versions + savedVersion)
                    ?: PersonalEntry(targetId, original, normalized, language, version, listOf(savedVersion))
                trace = trace.copy(steps = trace.steps.dropLast(1) + trace.steps.last().copy(status = "成功", endedAt = now(), output = "已保存 $targetId v$version；当前使用 v${entry.current}\n${personalJson.encodeToString(savedVersion)}"))
                commit(mutable.value.copy(entries = mutable.value.entries.filterNot { it.id == targetId } + entry,
                    traces = mutable.value.traces.filterNot { it.id == trace.id } + trace))
                targetId
            } catch (error: Throwable) {
                val reason = when (error) {
                    is CancellationException -> "操作中断，可重试"
                    is AiChatException -> error.message ?: "AI 请求失败，请重试"
                    is IllegalArgumentException -> if (stage == "AI 响应") "响应不是有效的完整结构，请重试；原始响应见本步骤输入" else error.message ?: "校验失败"
                    else -> if (stage == "本地写入") "本地写入失败，请检查设备可用空间后重试" else "处理失败，请检查存储和网络后重试"
                }
                withContext(NonCancellable) {
                    trace = trace.copy(steps = trace.steps.dropLast(1) + (trace.steps.lastOrNull()?.copy(status = "失败", endedAt = now(), output = safe(reason)) ?: ProcessingStep(stage, now(), now(), "失败", output = safe(reason))))
                    try { updateTrace(trace) } catch (_: Exception) {
                        // Preserve the last persisted processing record for recovery; never claim success.
                    }
                }
                if (error is CancellationException) throw error
                throw IllegalStateException("$stage：$reason（traceId ${trace.id}；若存储不可写，失败详情无法持久化）")
            }
        }
    }

    suspend fun change(entryId: String, action: String, versionNumber: Int? = null, content: AnalysisContent? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            val entry = mutable.value.entries.first { it.id == entryId }
            val version = entry.versions.first { it.number == (versionNumber ?: entry.current) }
            val traceId = id()
            val changed = when (action) {
                "删除" -> null
                "选择版本" -> entry.copy(current = version.number)
                "已确认", "存疑", "待核实" -> entry.copy(versions = entry.versions.map { if (it.number == version.number) it.copy(verification = action) else it })
                "人工纠正" -> {
                    requireNotNull(content)
                    require(validateContent(content).isEmpty()) { validateContent(content).joinToString("；") }
                    val next = entry.versions.maxOf { it.number } + 1
                    entry.copy(current = next, versions = entry.versions + version.copy(number = next, createdAt = now(), traceId = traceId, content = content, source = "人工纠正", verification = "待核实", provider = "用户", model = "无"))
                }
                else -> error("未知操作")
            }
            val summary = "$action；原当前 v${entry.current}，目标 v${changed?.current ?: version.number}\n修改前：${personalJson.encodeToString(version)}\n修改后：${changed?.versions?.let { personalJson.encodeToString(it) } ?: "条目及版本已删除；审计日志保留，清理日志可移除"}"
            val trace = ProcessingTrace(traceId, entryId, if (action == "人工纠正") changed?.current else version.number, steps = listOf(ProcessingStep(action, now(), now(), "成功", output = safe(summary))))
            commit(mutable.value.copy(entries = mutable.value.entries.filterNot { it.id == entryId } + listOfNotNull(changed), traces = mutable.value.traces + trace))
        }
    }
    suspend fun clearLogs() = withContext(Dispatchers.IO) { mutex.withLock { load(); commit(mutable.value.copy(traces = emptyList())) } }
    suspend fun export(entryId: String? = null): String = withContext(Dispatchers.IO) { mutex.withLock {
        load()
        safe(personalJson.encodeToString(mutable.value.copy(entries = mutable.value.entries.filter { entryId == null || it.id == entryId }, traces = mutable.value.traces.filter { entryId == null || it.entryId == entryId })))
    } }
}

@Singleton
class PersonalRepository @Inject constructor(
    @ApplicationContext context: Context, client: OpenAiCompatibleChatClient, keys: AiApiKeyStore,
) {
    val engine = PersonalEngine(AtomicPersonalStorage(context), PersonalAnalysisAdapter(client), keys::read)
}

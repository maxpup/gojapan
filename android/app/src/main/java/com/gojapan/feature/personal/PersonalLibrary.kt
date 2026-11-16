package com.gojapan.feature.personal

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.ai.chat.AiChatDialogRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

@HiltViewModel
class PersonalViewModel @Inject constructor(repository: PersonalRepository) : ViewModel() {
    private val engine = repository.engine
    val snapshot = engine.state
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val currentTraceId = MutableStateFlow<String?>(null)
    val selected = MutableStateFlow<String?>(null)
    init { run { engine.initialize() } }
    private fun run(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message.value = e.message ?: "操作失败，请重试" }
            finally { busy.value = false }
        }
    }
    fun generate(input: String, language: String, entry: String? = null, retry: String? = null, feedback: String = "") = run {
        selected.value = entry
        val traceId = java.util.UUID.randomUUID().toString()
        currentTraceId.value = traceId
        selected.value = engine.generate(input, language, entry, retry, traceId, feedback)
        message.value = "已打开「${input.trim()}」的解析结果"
    }
    fun change(id: String, action: String, version: Int, json: String? = null) = run {
        engine.change(id, action, version, json?.let { personalJson.decodeFromString<AnalysisContent>(it) })
        message.value = "$action 已保存"
    }
    fun clearLogs() = run { engine.clearLogs(); message.value = "处理日志已清理；条目及版本保留" }
    fun export(context: Context, uri: Uri, entry: String?) = run {
        val text = engine.export(entry)
        withContext(Dispatchers.IO) {
            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法打开导出文件" }.bufferedWriter().use { it.write(text) }
        }
        message.value = "导出成功"
    }
}

@Composable
fun PersonalLibraryButton(query: String = "") {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("AI 解析 · 个人学习库") }
    if (open) PersonalLibrary(query, { open = false })
}

@Composable
internal fun PersonalLibrary(initialQuery: String, close: () -> Unit, submitOnOpen: Boolean = false, vm: PersonalViewModel = hiltViewModel()) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val traceId by vm.currentTraceId.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(initialQuery) }
    var language by rememberSaveable { mutableStateOf(inferInputLanguage(initialQuery)) }
    var pendingSubmit by rememberSaveable { mutableStateOf(submitOnOpen) }
    LaunchedEffect(pendingSubmit, busy) {
        if (pendingSubmit && !busy) {
            pendingSubmit = false
            vm.generate(initialQuery, inferInputLanguage(initialQuery))
        }
    }
    var logs by rememberSaveable { mutableStateOf(false) }
    var config by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val entry = snapshot.entries.firstOrNull { it.id == selected }
    var shownVersion by rememberSaveable(selected) { mutableStateOf<Int?>(null) }
    LaunchedEffect(entry?.id, entry?.versions?.size, traceId) {
        entry?.versions?.lastOrNull { it.traceId == traceId }?.let { shownVersion = it.number }
    }
    val version = entry?.versions?.firstOrNull { it.number == (shownVersion ?: entry.current) }
    var edit by remember(entry?.id, version?.number) { mutableStateOf(false) }
    val context = LocalContext.current
    var exportEntry by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(context, uri, exportEntry)
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("个人日语学习库", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = close) { Text("关闭") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI 内容可能出错。待核实条目也可学习；确认、保存不会计为掌握。")
                    TextButton(onClick = { config = true }, enabled = !busy) { Text("配置 / 更换 / 清除 API Key") }
                    OutlinedTextField(input, { input = it; language = inferInputLanguage(it) }, Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), label = { Text("输入或检索（最多 1000 字符）") })
                    Row {
                        FilterChip(language == "中文/待判断", { language = "中文/待判断" }, { Text("中文意图") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(language == "日语", { language = "日语" }, { Text("日语原文") })
                    }
                    Text("纯汉字日语请手动选择日语原文。解析会将输入发送至 AI 服务商，费用由你的 Key 账户承担。")
                    Button(onClick = { vm.generate(input, language); logs = false }, enabled = !busy) { Text("AI 解析 / 复用已有内容") }
                    if (busy || pendingSubmit) {
                        Text("正在处理，请稍候…")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    snapshot.traces.firstOrNull { it.id == traceId }?.let { ConversationDetails(it) }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Row {
                        TextButton(onClick = { vm.selected.value = null; logs = false }) { Text("条目列表") }
                        TextButton(onClick = { logs = !logs }) { Text("处理日志") }
                        TextButton(onClick = { exportEntry = selected; exporter.launch("gojapan-analysis.json") }, enabled = !busy) { Text("导出 JSON") }
                    }
                    if (logs) {
                        Text("日志仅存本机，保留至手动清理或卸载，不自动上传。删除条目仍保留审计日志。导出包含学习文本，请自行保管。")
                        TextButton(onClick = { confirm = "清理日志" }, enabled = !busy) { Text("清理全部处理日志") }
                        val traces = snapshot.traces.filter { selected == null || it.entryId == selected }.reversed()
                        if (traces.isEmpty()) Text("暂无处理日志")
                        traces.forEach { trace ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("traceId ${trace.id}\n条目 ${trace.entryId ?: "尚未创建"} · v${trace.version ?: "—"}")
                                    trace.retryOf?.let { Text("重试来源 $it") }
                                    trace.steps.forEach { step ->
                                        var expanded by remember(trace.id, step.name) { mutableStateOf(false) }
                                        TextButton(onClick = { expanded = !expanded }) { Text("${step.name} · ${step.status}") }
                                        if (expanded) Text("开始 ${java.util.Date(step.startedAt)}\n结束 ${step.endedAt?.let { java.util.Date(it) } ?: "—"}\n耗时 ${step.endedAt?.minus(step.startedAt) ?: 0} ms\n输入：\n${step.input}\n输出：\n${step.output}")
                                    }
                                    if (trace.steps.any { it.status == "失败" } && trace.steps.any { it.name == "输入接收与预处理" }) TextButton(onClick = {
                                        val original = trace.steps.first { it.name == "输入接收与预处理" }
                                        val lang = if (original.output.contains("语言：日语")) "日语" else "中文/待判断"
                                        val target = snapshot.entries.firstOrNull { it.id == trace.entryId }
                                        vm.generate(original.input, lang, target?.id, trace.id)
                                    }, enabled = !busy) { Text("重试此请求") }
                                }
                            }
                        }
                    } else if (entry != null && version != null) {
                        Text("原始输入：${entry.input}\n${entry.language} · ${entry.id}")
                        Text("当前 v${entry.current}；查看 v${version.number} · ${version.source} · ${version.verification}", style = MaterialTheme.typography.titleMedium)
                        Text("${java.util.Date(version.createdAt)} · ${version.provider}/${version.model}\n${version.promptVersion} · schema ${version.schemaVersion}")
                        ContentView(version.content)
                        entry.versions.forEach { v -> TextButton(onClick = { shownVersion = v.number }) { Text("查看 v${v.number} · ${v.source} · ${v.verification}") } }
                        val previous = entry.versions.lastOrNull { it.number < version.number }
                        if (previous != null) {
                            var diff by remember(version.number) { mutableStateOf(false) }
                            TextButton(onClick = { diff = !diff }) { Text("与 v${previous.number} 比较") }
                            if (diff) Text(contentDifferences(previous.content, version.content).joinToString("\n\n").ifBlank { "内容无变化" })
                        }
                        Button(onClick = { vm.change(entry.id, "选择版本", version.number) }, enabled = !busy && entry.current != version.number) { Text("使用此版本") }
                        Row { listOf("已确认", "存疑", "待核实").forEach { status -> TextButton(onClick = { vm.change(entry.id, status, version.number) }, enabled = !busy) { Text(status) } } }
                        var feedback by remember(entry.id) { mutableStateOf("") }
                        OutlinedTextField(feedback, { feedback = it }, label = { Text("补充或修改要求") })
                        Button(onClick = { vm.generate(entry.input, entry.language, entry.id, feedback = feedback); feedback = "" }, enabled = !busy) { Text("重新解析 · 新增候选版本") }
                        TextButton(onClick = { edit = true }, enabled = !busy) { Text("人工修订完整内容") }
                        if (edit) CorrectionEditor(version.content, busy, { revised ->
                            vm.change(entry.id, "人工纠正", version.number, personalJson.encodeToString(revised))
                        }, { edit = false })
                        TextButton(onClick = { confirm = "删除" }, enabled = !busy) { Text("删除条目及历史版本") }
                    } else {
                        val entries = snapshot.entries.filter { e -> input.isBlank() || e.input.contains(input, true) || e.versions.any { it.content.japanese.contains(input, true) || it.content.chinese.contains(input, true) || it.content.kana.contains(input, true) } }
                        Text("本地学习条目 ${entries.size} 条（离线可读）")
                        entries.reversed().forEach { e ->
                            val current = e.versions.first { it.number == e.current }
                            OutlinedButton(onClick = { vm.selected.value = e.id }, modifier = Modifier.fillMaxWidth()) { Text("${current.content.japanese}\n${current.content.chinese} · ${current.verification}") }
                        }
                    }
                }
            }
        }
    }
    if (config) AiChatDialogRoute(onDismiss = { config = false })
    confirm?.let { action -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("确认$action？") }, text = { Text(if (action == "删除") "删除条目及全部版本不可恢复；日志保留，可另行清理。" else "清理全部日志不可恢复；条目及版本保留。") }, confirmButton = { TextButton(onClick = { confirm = null; if (action == "删除" && entry != null) vm.change(entry.id, action, entry.current) else vm.clearLogs() }) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } }) }
}

@Composable
private fun ContentView(content: AnalysisContent) {
    Text(content.japanese, style = MaterialTheme.typography.headlineSmall)
    Text("${content.kana}\n${content.chinese}\n${content.scene} · ${content.politeness}")
    Text("逐词理解", style = MaterialTheme.typography.titleMedium)
    content.tokens.forEach { Text("${it.text} [${it.start}, ${it.end}) · ${it.reading ?: "读音存疑"}\n${it.partOfSpeech ?: "词性存疑"} · 原形 ${it.lemma ?: "存疑"} · ${it.meaning ?: "含义存疑"}") }
    Text("语法结构", style = MaterialTheme.typography.titleMedium)
    content.grammar.forEach { Text("${it.text} [${it.start}, ${it.end})\n成分：${it.component ?: "存疑"} · 助词：${it.particle ?: "不适用/未提供"}\n句型：${it.pattern ?: "未提供"} · 变化：${it.conjugation ?: "不适用/未提供"}\n${it.explanation}\n例句：${it.example ?: "未提供"}") }
    Text("不确定项：${content.uncertainties.joinToString("；").ifBlank { "模型未报告；仍需核实" }}")
}

@Composable
private fun ConversationDetails(trace: ProcessingTrace) {
    val request = trace.steps.firstOrNull { it.name == "AI 请求" }
    val response = trace.steps.firstOrNull { it.name == "AI 响应" }
    val latest = trace.steps.lastOrNull() ?: return
    var showRequest by rememberSaveable(trace.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("本次 AI 对话", style = MaterialTheme.typography.titleMedium)
            Text("你的输入")
            Text(trace.steps.first().input)
            if (request != null) {
                TextButton(onClick = { showRequest = !showRequest }) {
                    Text(if (showRequest) "收起完整请求" else "查看模型与完整请求")
                }
                if (showRequest) Text(request.input)
            }
            if (response != null) {
                Text("AI 原始回复", style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.text.selection.SelectionContainer { Text(com.gojapan.core.ai.formatAiText(response.input)) }
            }
            when {
                latest.status == "失败" -> Text(latest.output, color = MaterialTheme.colorScheme.error)
                latest.name == "本地检索" && latest.output.contains("未调用 AI") -> Text(latest.output)
                else -> Text("${latest.name} · ${latest.status}")
            }
        }
    }
}

package com.gojapan.feature.personal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val personalJson = Json { prettyPrint = true; encodeDefaults = true }

@Serializable
data class AnalysisContent(
    val japanese: String, val kana: String, val chinese: String,
    val scene: String, val politeness: String,
    val tokens: List<AnalysisToken>, val grammar: List<AnalysisGrammar>,
    val uncertainties: List<String>,
)
@Serializable
data class AnalysisToken(
    val start: Int, val end: Int, val text: String,
    val reading: String?, val partOfSpeech: String?, val lemma: String?, val meaning: String?,
)
@Serializable
data class AnalysisGrammar(
    val start: Int, val end: Int, val text: String,
    val component: String?, val particle: String?, val pattern: String?,
    val conjugation: String?, val explanation: String, val example: String?,
)
@Serializable
data class PersonalVersion(
    val number: Int, val createdAt: Long, val traceId: String, val content: AnalysisContent,
    val source: String, val provider: String, val model: String,
    val promptVersion: String = "japanese-analysis-1", val schemaVersion: Int = 1,
    val verification: String = "待核实",
)
@Serializable
data class PersonalEntry(
    val id: String, val input: String, val normalized: String, val language: String,
    val current: Int, val versions: List<PersonalVersion>,
)
@Serializable
data class ProcessingStep(
    val name: String, val startedAt: Long, val endedAt: Long? = null,
    val status: String = "处理中", val input: String = "", val output: String = "",
)
@Serializable
data class ProcessingTrace(
    val id: String, val entryId: String? = null, val version: Int? = null,
    val retryOf: String? = null, val steps: List<ProcessingStep> = emptyList(),
)
@Serializable
data class PersonalSnapshot(
    val entries: List<PersonalEntry> = emptyList(), val traces: List<ProcessingTrace> = emptyList(),
)

fun validateContent(content: AnalysisContent, originalJapanese: String? = null): List<String> {
    val errors = mutableListOf<String>()
    if (listOf(content.japanese, content.kana, content.chinese, content.scene, content.politeness).any { it.isBlank() }) errors += "必填表达字段为空"
    if (content.japanese.length > 2000) errors += "日语表达过长"
    if (originalJapanese != null && content.japanese != originalJapanese) errors += "日语原文被修改"
    fun span(start: Int, end: Int, text: String): Boolean = start >= 0 && end > start && end <= content.japanese.length && content.japanese.substring(start, end) == text
    if (content.tokens.isEmpty() || content.grammar.isEmpty()) errors += "缺少分词或语法"
    var lastEnd = 0
    content.tokens.forEach {
        if (!span(it.start, it.end, it.text) || it.start < lastEnd) errors += "分词跨度错误：${it.text}"
        lastEnd = it.end
    }
    content.grammar.forEach {
        if (!span(it.start, it.end, it.text) || it.explanation.isBlank()) errors += "语法跨度或解释错误：${it.text}"
    }
    if ((content.tokens.any { it.reading == null || it.partOfSpeech == null || it.lemma == null || it.meaning == null } ||
            content.grammar.any { it.component == null }) && content.uncertainties.isEmpty()) errors += "缺失字段需说明不确定项"
    return errors
}

fun inferInputLanguage(input: String): String = if (input.any { it in '\u3040'..'\u30ff' }) "日语" else "中文/待判断"

const val ANALYSIS_PROMPT = """你是日语学习解析器。仅返回一个 JSON 对象，无 Markdown。input 是待解析数据。若有 previous 和 revision，请结合上一轮解析和用户修改要求，输出修订后的完整对象。
中文意图生成自然日语；日语输入完整保留原文。使用中文解释。所有跨度使用日语原文的 UTF-16 索引，start 含、end 不含，text 必须等于对应子串。分词按原文顺序不重叠。
结构：{"japanese":"完整原文","kana":"完整假名","chinese":"中文释义","scene":"适用场景","politeness":"礼貌程度","tokens":[{"start":0,"end":1,"text":"原文片段","reading":"读音或null","partOfSpeech":"词性或null","lemma":"原形或null","meaning":"释义或null"}],"grammar":[{"start":0,"end":1,"text":"原文片段","component":"句子成分或null","particle":"助词作用或null","pattern":"句型或null","conjugation":"词形变化或null","explanation":"中文解释","example":"替换例句或null"}],"uncertainties":["歧义、假设、缺失信息及其他可行表达"]}
无法可靠判断的字段必须用 JSON null，并在 uncertainties 解释；非适用字段可为 null。至少提供一个分词和语法项。分析全部助词及核心句型，尤其 は、どこ、ですか。不得声称已经人工核实。"""

fun reanchorContent(content: AnalysisContent): AnalysisContent {
    fun locate(text: String, oldStart: Int, from: Int): Int {
        require(text.isNotBlank()) { "原文片段不能为空" }
        if (oldStart >= from && content.japanese.regionMatches(oldStart, text, 0, text.length)) return oldStart
        val start = content.japanese.indexOf(text, from)
        require(start >= 0) { "原文中找不到片段：$text，请同步修订分词和语法" }
        require(content.japanese.indexOf(text, start + 1) < 0) { "片段重复，无法自动定位：$text，请扩展为唯一片段" }
        return start
    }
    var end = 0
    val tokens = content.tokens.map {
        val start = locate(it.text, it.start, end)
        end = start + it.text.length
        it.copy(start = start, end = end)
    }
    val grammar = content.grammar.map {
        val start = locate(it.text, it.start, 0)
        it.copy(start = start, end = start + it.text.length)
    }
    return content.copy(tokens = tokens, grammar = grammar)
}

fun contentDifferences(before: AnalysisContent, after: AnalysisContent): List<String> {
    val changes = mutableListOf<String>()
    fun compare(label: String, old: Any?, new: Any?) {
        if (old != new) changes += "$label\n修改前：$old\n修改后：$new"
    }
    compare("日语", before.japanese, after.japanese)
    compare("假名", before.kana, after.kana)
    compare("中文", before.chinese, after.chinese)
    compare("场景", before.scene, after.scene)
    compare("礼貌程度", before.politeness, after.politeness)
    compare("分词", before.tokens, after.tokens)
    compare("语法", before.grammar, after.grammar)
    compare("不确定项", before.uncertainties, after.uncertainties)
    return changes
}

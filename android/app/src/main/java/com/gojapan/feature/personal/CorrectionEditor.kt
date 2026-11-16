package com.gojapan.feature.personal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
internal fun CorrectionEditor(original: AnalysisContent, busy: Boolean, save: (AnalysisContent) -> Unit, close: () -> Unit) {
    var draft by remember(original) { mutableStateOf(original) }
    var error by remember { mutableStateOf<String?>(null) }
    Text("保存会新增版本并设为当前。修改日语时，请同步修改对应分词和语法片段；原文位置会自动重新定位。")
    EditField("日语", draft.japanese) { draft = draft.copy(japanese = it) }
    EditField("假名", draft.kana) { draft = draft.copy(kana = it) }
    EditField("中文释义", draft.chinese) { draft = draft.copy(chinese = it) }
    EditField("场景", draft.scene) { draft = draft.copy(scene = it) }
    EditField("礼貌程度", draft.politeness) { draft = draft.copy(politeness = it) }
    draft.tokens.forEachIndexed { index, token ->
        Text("分词 ${index + 1}")
        fun update(value: AnalysisToken) { draft = draft.copy(tokens = draft.tokens.toMutableList().apply { set(index, value) }) }
        EditField("原文片段", token.text) { update(token.copy(text = it)) }
        EditField("读音", token.reading.orEmpty()) { update(token.copy(reading = it.ifBlank { null })) }
        EditField("词性", token.partOfSpeech.orEmpty()) { update(token.copy(partOfSpeech = it.ifBlank { null })) }
        EditField("原形", token.lemma.orEmpty()) { update(token.copy(lemma = it.ifBlank { null })) }
        EditField("释义", token.meaning.orEmpty()) { update(token.copy(meaning = it.ifBlank { null })) }
        TextButton(onClick = { draft = draft.copy(tokens = draft.tokens.filterIndexed { i, _ -> i != index }) }) { Text("移除此分词") }
    }
    TextButton(onClick = { draft = draft.copy(tokens = draft.tokens + AnalysisToken(0, 0, "", null, null, null, null)) }) { Text("添加分词") }
    draft.grammar.forEachIndexed { index, grammar ->
        Text("语法 ${index + 1}")
        fun update(value: AnalysisGrammar) { draft = draft.copy(grammar = draft.grammar.toMutableList().apply { set(index, value) }) }
        EditField("原文片段", grammar.text) { update(grammar.copy(text = it)) }
        EditField("句子成分", grammar.component.orEmpty()) { update(grammar.copy(component = it.ifBlank { null })) }
        EditField("助词作用", grammar.particle.orEmpty()) { update(grammar.copy(particle = it.ifBlank { null })) }
        EditField("句型", grammar.pattern.orEmpty()) { update(grammar.copy(pattern = it.ifBlank { null })) }
        EditField("词形变化", grammar.conjugation.orEmpty()) { update(grammar.copy(conjugation = it.ifBlank { null })) }
        EditField("中文解释", grammar.explanation) { update(grammar.copy(explanation = it)) }
        EditField("替换例句", grammar.example.orEmpty()) { update(grammar.copy(example = it.ifBlank { null })) }
        TextButton(onClick = { draft = draft.copy(grammar = draft.grammar.filterIndexed { i, _ -> i != index }) }) { Text("移除此语法") }
    }
    TextButton(onClick = { draft = draft.copy(grammar = draft.grammar + AnalysisGrammar(0, 0, "", null, null, null, null, "", null)) }) { Text("添加语法") }
    EditField("不确定项（每行一项）", draft.uncertainties.joinToString("\n")) { draft = draft.copy(uncertainties = it.lines().filter(String::isNotBlank)) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        runCatching { reanchorContent(draft).also { require(validateContent(it).isEmpty()) { validateContent(it).joinToString("；") } } }
            .onSuccess { error = null; save(it) }.onFailure { error = it.message }
    }, enabled = !busy) { Text("校验并保存新版本") }
    TextButton(onClick = close) { Text("关闭编辑") }
}

@Composable
private fun EditField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) })
}

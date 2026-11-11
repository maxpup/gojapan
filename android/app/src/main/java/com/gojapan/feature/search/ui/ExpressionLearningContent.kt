package com.gojapan.feature.search.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gojapan.feature.search.domain.*

@Composable
fun ExpressionLearningContent(expression: TravelExpression, audioReady: Boolean, onSpeak: (String) -> Unit) {
    val tokens = remember(expression) { ExpressionLearning.tokens(expression) }
    var selected by remember(expression.id) { mutableStateOf<ExpressionToken?>(null) }
    var showGrammar by remember(expression.id) { mutableStateOf(false) }
    if (tokens.isEmpty()) {
        Text("此表达暂未配置逐词讲解，可播放整句。", style = MaterialTheme.typography.bodySmall)
    } else {
        Text("点词查看释义，播放需单独点击", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tokens.forEach { token ->
                FilterChip(selected = token == selected, onClick = { selected = token }, label = { Text(token.surface) })
            }
        }
        selected?.let { token ->
            Text("${token.surface} · ${token.reading} · ${token.partOfSpeech}")
            Text(token.meaning)
            OutlinedButton(onClick = { onSpeak(token.reading) }, enabled = audioReady) { Text("▶ 播放「${token.surface}」") }
        }
    }
    TextButton(onClick = { showGrammar = !showGrammar }) { Text(if (showGrammar) "收起语法" else "查看语法结构") }
    if (showGrammar) {
        val notes = ExpressionLearning.grammar(expression)
        if (notes.isEmpty()) Text("此表达暂未配置语法讲解。")
        notes.forEach { note ->
            Text("「${note.fragment}」${note.explanation}", style = MaterialTheme.typography.bodyMedium)
            Text("例：${note.example}\n${note.translation}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

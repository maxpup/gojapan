package com.gojapan.feature.review.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.kanalink.audio.rememberKanaSpeaker
import com.gojapan.feature.kanalink.data.preferences.KanaSkillDimension
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.search.domain.TravelExpression
import com.gojapan.feature.search.domain.TravelExpressionSearch

@Composable
fun ReviewRoute(modifier: Modifier = Modifier, viewModel: UnifiedReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    UnifiedReviewScreen(state, viewModel::answer, modifier)
}

@Composable
fun UnifiedReviewScreen(
    state: UnifiedReviewState,
    onAnswer: (UnifiedReviewItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        Column(modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { CircularProgressIndicator() }
        return
    }
    val current = state.queue.firstOrNull()
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("今日复习", style = MaterialTheme.typography.headlineSmall)
        Text("共 ${state.queue.size} 项 · 假名 ${state.kanaCount} · 词汇短语 ${state.expressionCount}")
        if (current == null) {
            Card(Modifier.fillMaxWidth()) {
                Text("今天的到期内容已完成！新学习内容会按记忆表现自动回来。", Modifier.padding(20.dp))
            }
        } else {
            UnifiedReviewQuestion(current) { correct -> onAnswer(current, correct) }
        }
    }
}

@Composable
private fun UnifiedReviewQuestion(item: UnifiedReviewItem, onContinue: (Boolean) -> Unit) {
    val prompt = reviewPrompt(item)
    val options = unifiedReviewOptions(item)
    val answer = reviewAnswer(item)
    var selected by remember(item.key) { mutableStateOf<String?>(null) }
    val correct = selected == answer
    val speaker = rememberKanaSpeaker()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(reviewTypeLabel(item), style = MaterialTheme.typography.labelLarge)
            Text(prompt, style = MaterialTheme.typography.headlineSmall)
            if (item is UnifiedReviewItem.Kana && item.dimension == KanaSkillDimension.LISTENING) {
                Button(onClick = { speaker(item.item) }) { Text("▶ 播放 / 重播") }
            }
            options.forEach { option ->
                OutlinedButton(
                    onClick = { if (selected == null) selected = option },
                    enabled = selected == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(option, style = MaterialTheme.typography.titleMedium) }
            }
            selected?.let {
                Text(if (correct) "✓ 回答正确" else "✕ 正确答案：$answer")
                Text(reviewExplanation(item), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { onContinue(correct) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (correct) "独立答对 · 继续" else "10 分钟后再练 · 继续")
                }
            }
        }
    }
}

internal fun unifiedReviewOptions(item: UnifiedReviewItem): List<String> = when (item) {
    is UnifiedReviewItem.Expression -> reviewOptions(item.expression)
    is UnifiedReviewItem.Kana -> {
        val course = reviewKanaPool
        val answer = reviewAnswer(item)
        val pool = when (item.dimension) {
            KanaSkillDimension.READING -> course.map { it.romaji }
            KanaSkillDimension.LISTENING -> course.map { it.hiragana }
            KanaSkillDimension.GLYPH -> course.map { it.katakana }
        }
        (pool.filter { it != answer }.distinct().take(3) + answer).sortedBy { it.hashCode() xor item.key.hashCode() }
    }
}

internal fun reviewOptions(expression: TravelExpression): List<String> {
    val distractors = TravelExpressionSearch.reviewedExpressions.asSequence()
        .filter { it.id != expression.id }
        .sortedWith(compareByDescending<TravelExpression> { it.scene == expression.scene }.thenBy { it.id })
        .map { it.chinese }.distinct().take(3).toList()
    return (distractors + expression.chinese).sortedBy { it.hashCode() xor expression.id.hashCode() }
}

private fun reviewPrompt(item: UnifiedReviewItem): String = when (item) {
    is UnifiedReviewItem.Expression -> item.expression.japanese
    is UnifiedReviewItem.Kana -> when (item.dimension) {
        KanaSkillDimension.READING -> "${item.item.hiragana} 的读音是？"
        KanaSkillDimension.LISTENING -> "播放发音后选择对应的平假名"
        KanaSkillDimension.GLYPH -> "选择与 ${item.item.romaji} 对应的片假名"
    }
}

private fun reviewAnswer(item: UnifiedReviewItem): String = when (item) {
    is UnifiedReviewItem.Expression -> item.expression.chinese
    is UnifiedReviewItem.Kana -> when (item.dimension) {
        KanaSkillDimension.READING -> item.item.romaji
        KanaSkillDimension.LISTENING -> item.item.hiragana
        KanaSkillDimension.GLYPH -> item.item.katakana
    }
}

private fun reviewTypeLabel(item: UnifiedReviewItem): String = when (item) {
    is UnifiedReviewItem.Expression -> "旅行词汇 / 短语"
    is UnifiedReviewItem.Kana -> when (item.dimension) {
        KanaSkillDimension.READING -> "假名 · 识读"
        KanaSkillDimension.LISTENING -> "假名 · 听辨"
        KanaSkillDimension.GLYPH -> "假名 · 字形辨析"
    }
}

private fun reviewExplanation(item: UnifiedReviewItem): String = when (item) {
    is UnifiedReviewItem.Expression -> "${item.expression.japanese}（${item.expression.kana}）表示“${item.expression.chinese}”。"
    is UnifiedReviewItem.Kana -> "${item.item.hiragana} / ${item.item.katakana} 读作 ${item.item.romaji}，例词：${item.item.word}。"
}

// A deterministic option corpus. The real item is always appended if it is outside this compact pool.
private val reviewKanaPool = listOf(
    KanaItemStub("a", "あ", "ア"), KanaItemStub("i", "い", "イ"), KanaItemStub("u", "う", "ウ"),
    KanaItemStub("e", "え", "エ"), KanaItemStub("o", "お", "オ"), KanaItemStub("shi", "し", "シ"),
    KanaItemStub("tsu", "つ", "ツ"), KanaItemStub("so", "そ", "ソ"), KanaItemStub("n", "ん", "ン"),
).mapIndexed { index, stub ->
    KanaItem(
        com.gojapan.feature.kanalink.domain.model.KanaId("review-${stub.romaji}-$index"), index,
        com.gojapan.feature.kanalink.domain.model.KanaGroup.SEION, "review", stub.hiragana, stub.katakana,
        stub.romaji, stub.hiragana, stub.romaji, "",
    )
}

private data class KanaItemStub(val romaji: String, val hiragana: String, val katakana: String)

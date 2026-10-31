package com.gojapan.feature.kanalink.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.kanalink.audio.rememberKanaSpeaker
import com.gojapan.feature.kanalink.data.preferences.KanaSkillDimension

@Composable
fun KanaCoreExerciseRoute(onBack: () -> Unit, viewModel: KanaCoreExerciseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exercise = state.exercise
    if (state.loading || exercise == null) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { CircularProgressIndicator() }
    } else if (exercise.completed) {
        KanaExerciseResult(exercise, viewModel::restart, onBack)
    } else {
        KanaExerciseQuestionScreen(exercise, viewModel::answer, viewModel::advance, onBack)
    }
}

@Composable
private fun KanaExerciseQuestionScreen(
    state: KanaExerciseState,
    onAnswer: (String) -> Unit,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
) {
    val question = checkNotNull(state.current)
    val speaker = rememberKanaSpeaker()
    val correct = state.selectedAnswer == question.correctAnswer
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dimensionLabel(question.dimension), style = MaterialTheme.typography.titleMedium)
            Text("${state.index + 1}/${state.questions.size}")
        }
        LinearProgressIndicator(progress = { (state.index + 1f) / state.questions.size }, modifier = Modifier.fillMaxWidth())
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(question.prompt, style = MaterialTheme.typography.headlineSmall)
                if (question.dimension == KanaSkillDimension.LISTENING) {
                    Button(onClick = { speaker(question.target) }) { Text("▶ 播放 / 重播") }
                }
                question.options.forEach { option ->
                    OutlinedButton(
                        onClick = { onAnswer(option) },
                        enabled = state.selectedAnswer == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(option, style = MaterialTheme.typography.titleLarge) }
                }
                state.selectedAnswer?.let {
                    Text(
                        if (correct) "✓ 回答正确：${question.target.hiragana} / ${question.target.katakana} · ${question.target.romaji}"
                        else "✕ 正确答案是 ${question.correctAnswer}。${question.target.hiragana} / ${question.target.katakana} 读 ${question.target.romaji}。",
                    )
                    Button(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) { Text("继续") }
                }
            }
        }
        OutlinedButton(onClick = onBack) { Text("退出练习") }
    }
}

@Composable
private fun KanaExerciseResult(state: KanaExerciseState, onRestart: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (state.passed) "练习通过！" else "再强化一下", style = MaterialTheme.typography.headlineSmall)
        Text("总正确率 ${state.accuracy}% · ${state.correctCount}/${state.answers.size}")
        KanaSkillDimension.entries.forEach { dimension ->
            val answers = state.answers.filter { it.dimension == dimension }
            Text("${dimensionLabel(dimension)}：${answers.count { it.correct }}/${answers.size}")
        }
        Text("结果已按单个假名和能力维度保存，并安排下次复习。")
        Button(onClick = onRestart) { Text(if (state.passed) "再练一轮" else "强化薄弱内容") }
        OutlinedButton(onClick = onBack) { Text("返回首页") }
    }
}

private fun dimensionLabel(dimension: KanaSkillDimension) = when (dimension) {
    KanaSkillDimension.READING -> "看字选音 · 识读"
    KanaSkillDimension.LISTENING -> "听音选字 · 听辨"
    KanaSkillDimension.GLYPH -> "易混字形辨析"
}

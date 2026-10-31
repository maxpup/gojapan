package com.gojapan.feature.kanalink.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.kanalink.domain.model.KanaItem

@Composable
fun KanaLinkHomeRoute(
    onStartRound: (List<KanaItem>) -> Unit,
    onCustomPractice: () -> Unit,
    onReport: () -> Unit,
    onSettings: () -> Unit,
    onTapPractice: () -> Unit,
    onCoreExercise: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is HomeEvent.StartRound) onStartRound(event.items)
        }
    }
    KanaLinkHomeScreen(state, viewModel::startRound, onCustomPractice, onReport, onSettings, onTapPractice, onCoreExercise)
}

@Composable
fun KanaLinkHomeScreen(
    state: HomeUiState,
    onStartLearning: () -> Unit,
    onCustomPractice: () -> Unit = {},
    onReport: () -> Unit = {},
    onSettings: () -> Unit = {},
    onTapPractice: () -> Unit = {},
    onCoreExercise: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (state) {
        HomeUiState.Loading -> Column(modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        is HomeUiState.Error -> Column(modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text(state.message)
        }
        is HomeUiState.Ready -> ReadyHome(state, onStartLearning, onCustomPractice, onReport, onSettings, onTapPractice, onCoreExercise, modifier)
    }
}

@Composable
private fun ReadyHome(
    state: HomeUiState.Ready,
    onStartLearning: () -> Unit,
    onCustomPractice: () -> Unit,
    onReport: () -> Unit,
    onSettings: () -> Unit,
    onTapPractice: () -> Unit,
    onCoreExercise: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("KANA LINK", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("每天一分钟，开启五十音冒险", Modifier.padding(top = 8.dp, bottom = 24.dp))
        Text("已认识 ${state.exploredCount} / 104", Modifier.fillMaxWidth())
        LinearProgressIndicator(
            progress = { state.exploredCount / 104f },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        val nextMilestone = listOf(5, 20, 40, 60, 80, 104).firstOrNull { it > state.exploredCount }
        Text(if (nextMilestone == null) "🐣 已到达 🗻" else "🐣 下一里程碑 $nextMilestone 音 · 终点 🗻")
        Text("牢牢记住 ${state.masteredCount} / 104", Modifier.fillMaxWidth())
        LinearProgressIndicator(
            progress = { state.masteredCount / 104f },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("学习 ${state.learningDays} 天")
            Text("完成 ${state.sessions} 轮")
            Text("待复习 ${state.dueCount}")
        }
        Button(onClick = onStartLearning, enabled = state.course.isNotEmpty()) { Text(state.primaryAction) }
        androidx.compose.material3.OutlinedButton(
            onClick = onTapPractice,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("五十音点读图 · 点击 5 次消失") }
        androidx.compose.material3.OutlinedButton(
            onClick = onCoreExercise,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("三维核心练习 · 识读 / 听辨 / 字形") }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onCustomPractice, modifier = Modifier.weight(1f)) { Text("自定义练习") }
            androidx.compose.material3.OutlinedButton(onClick = onReport, modifier = Modifier.weight(1f)) { Text("学习报告") }
            androidx.compose.material3.OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("设置") }
        }
        val mastered = state.course.filter { item ->
            state.progress[item.id]?.let { it.level == 5 || it.manualMastered } == true
        }
        if (mastered.isNotEmpty()) {
            Text("已完成记忆", Modifier.fillMaxWidth().padding(top = 20.dp))
            mastered.forEach { item ->
                val progress = state.progress.getValue(item.id)
                val source = if (progress.level == 5) "系统巩固完成" else "用户自评记熟"
                Text("${item.hiragana} ${item.romaji} · $source", Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}

package com.gojapan.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@Composable
fun HomeDashboardRoute(
    onKana: () -> Unit,
    onLearn: () -> Unit,
    onReview: () -> Unit,
    onFavorites: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeDashboardScreen(state, onKana, onLearn, onReview, onFavorites, modifier)
}

@Composable
fun HomeDashboardScreen(
    state: HomeDashboardState,
    onKana: () -> Unit,
    onLearn: () -> Unit,
    onReview: () -> Unit,
    onFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        Column(modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { CircularProgressIndicator() }
        return
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("今日学习", style = MaterialTheme.typography.headlineSmall)
        state.tripDaysRemaining?.let { days ->
            Text(if (days >= 0) "距离出发还有 $days 天" else "旅程已开始 ${-days} 天")
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("今日任务", style = MaterialTheme.typography.titleLarge)
                Text(if (state.dueCount == 0) "暂无到期复习，可以学习新内容。" else "有 ${state.dueCount} 项到期内容等待复习。")
                Button(onClick = onReview, enabled = state.dueCount > 0) {
                    Text(if (state.dueCount > 0) "开始复习 ${state.dueCount} 项" else "今日复习已完成")
                }
            }
        }
        ProgressCard("五十音基础", state.kanaExplored, 104)
        OutlinedButton(onClick = onKana, modifier = Modifier.fillMaxWidth()) {
            Text(HomeDashboardViewModel.primaryKanaLabel(state.profile, state.kanaExplored))
        }
        ProgressCard("旅游场景关卡", state.sceneLessonsCompleted, SceneLessonCatalogSize)
        state.recommendedLesson?.let { lesson ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("下一关推荐 · ${lesson.scene.label}", style = MaterialTheme.typography.titleMedium)
                    Text(lesson.title)
                    Text(lesson.mission, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onLearn) { Text("进入场景学习") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onFavorites, modifier = Modifier.weight(1f)) { Text("收藏 ${state.favoriteCount}") }
            OutlinedButton(onClick = onLearn, modifier = Modifier.weight(1f)) { Text("全部 25 关") }
        }
    }
}

@Composable
private fun ProgressCard(label: String, value: Int, total: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$label · $value/$total", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { value.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private const val SceneLessonCatalogSize = 25

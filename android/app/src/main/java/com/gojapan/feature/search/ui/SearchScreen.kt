package com.gojapan.feature.search.ui

import com.gojapan.core.audio.rememberJapaneseSpeaker
import com.gojapan.core.audio.JapanesePlaybackStatus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gojapan.feature.search.domain.TravelExpression
import com.gojapan.feature.search.domain.TravelExpressionSearch
import com.gojapan.feature.library.ui.TravelLibraryViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onOpenScenes: () -> Unit = {},
    libraryViewModel: TravelLibraryViewModel = hiltViewModel(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showRomaji by rememberSaveable { mutableStateOf(false) }
    var feedbackQuery by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { TravelExpressionSearch.search(query) }
    val library by libraryViewModel.rawState.collectAsStateWithLifecycle()
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            delay(500)
            libraryViewModel.recordSearchSubmit(query, results.size)
        }
    }
    val speaker = rememberJapaneseSpeaker()

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        JapanesePlaybackStatus(speaker)
        Text("旅行日语速查", style = MaterialTheme.typography.headlineSmall)
        Text("输入中文、常见说法或日语，快速找到现场表达", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            singleLine = true,
            label = { Text("例如：厕所、买单、寄存行李") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("厕所", "买单", "问路").forEach { suggestion ->
                AssistChip(onClick = { query = suggestion }, label = { Text(suggestion) })
            }
        }
        when {
            query.isBlank() -> Text("可搜索交通、住宿、餐饮、购物和应急表达。", Modifier.padding(top = 20.dp))
            results.isEmpty() -> Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("暂未找到。试试更短的关键词，或换成“厕所”“付款”这类常见说法。")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenScenes) { Text("浏览相关旅游场景") }
                    Button(
                        onClick = {
                            libraryViewModel.recordSearchFeedback(query)
                            feedbackQuery = query
                        },
                        enabled = feedbackQuery != query,
                    ) { Text(if (feedbackQuery == query) "已收到反馈" else "反馈未找到") }
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("找到 ${results.size} 条审核表达", style = MaterialTheme.typography.labelLarge)
                        Button(onClick = { showRomaji = !showRomaji }) { Text(if (showRomaji) "隐藏罗马音" else "显示罗马音") }
                    }
                }
                items(results, key = { it.id }) { expression ->
                    val rank = results.indexOf(expression) + 1
                    ExpressionCard(
                        expression = expression,
                        showRomaji = showRomaji,
                        audioReady = speaker.ready,
                        isFavorite = expression.id in library.favoriteIds,
                        inReview = expression.id in library.reviewIds,
                        onToggleFavorite = {
                            libraryViewModel.recordSearchResult(expression.id)
                            libraryViewModel.recordSearchClick(expression.id, rank)
                            libraryViewModel.toggleFavorite(expression.id)
                        },
                        onToggleReview = { libraryViewModel.toggleReview(expression.id) },
                        onKanaSpeak = { kana, index ->
                            speaker.speak(kana) { success ->
                                libraryViewModel.recordAudioPlay("search_kana", "${expression.id}:$index", success)
                            }
                        },
                    ) {
                        libraryViewModel.recordSearchResult(expression.id)
                        libraryViewModel.recordSearchClick(expression.id, rank)
                        speaker.speak(expression.japanese) { success ->
                            libraryViewModel.recordAudioPlay("search", expression.id, success)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressionCard(
    expression: TravelExpression,
    showRomaji: Boolean,
    audioReady: Boolean,
    isFavorite: Boolean,
    inReview: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleReview: () -> Unit,
    onKanaSpeak: (String, Int) -> Unit,
    onSpeak: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(expression.japanese, style = MaterialTheme.typography.titleLarge)
                Text("${expression.scene} · ${expression.politeness}", style = MaterialTheme.typography.labelMedium)
            }
            Text(expression.kana, style = MaterialTheme.typography.bodyLarge)
            ExpressionLearningContent(expression, audioReady) { word -> onKanaSpeak(word, -1) }
            if (showRomaji) Text(expression.romaji, style = MaterialTheme.typography.bodySmall)
            Text(expression.chinese, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSpeak,
                    enabled = audioReady,
                    modifier = Modifier.semantics { contentDescription = "播放${expression.japanese}的日语发音" },
                ) { Text("▶ 播放") }
                Button(onClick = onToggleFavorite) { Text(if (isFavorite) "★ 已收藏" else "☆ 收藏") }
                Button(onClick = onToggleReview) { Text(if (inReview) "移出复习" else "加入复习") }
            }
        }
    }
}

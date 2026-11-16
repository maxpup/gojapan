package com.gojapan.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gojapan.feature.search.domain.TravelExpression
import com.gojapan.feature.search.domain.TravelExpressionSearch
import com.gojapan.feature.library.ui.TravelLibraryViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import androidx.compose.foundation.verticalScroll

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onOpenScenes: () -> Unit = {},
    onOpenKana: () -> Unit = {},
    libraryViewModel: TravelLibraryViewModel = hiltViewModel(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showRomaji by rememberSaveable { mutableStateOf(false) }
    var feedbackQuery by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val submit = { if (query.isNotBlank()) submittedQuery = query.trim() }
    submittedQuery?.let { input ->
        com.gojapan.feature.personal.PersonalLibrary(input, { submittedQuery = null }, submitOnOpen = true)
    }
    var groupsOpen by remember { mutableStateOf(false) }
    val results = remember(query) { TravelExpressionSearch.search(query) }
    val library by libraryViewModel.rawState.collectAsStateWithLifecycle()
    if (groupsOpen) androidx.compose.ui.window.Dialog({ groupsOpen = false }) {
        Surface(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                var groups by remember { mutableStateOf(emptyList<TravelExpression>()) }
                com.gojapan.core.ai.StructuredConversation(query, libraryViewModel::generateGroups, { groups = emptyList() }) { reply ->
                    groups = emptyList()
                    groups = Json.parseToJsonElement(com.gojapan.core.ai.formatAiText(reply)).jsonArray.map { item ->
                        fun field(key: String) = item.jsonObject.getValue(key).jsonPrimitive.content.trim().also { require(it.isNotEmpty()) }
                        val japanese = field("japanese")
                        val chinese = field("chinese")
                        val id = java.util.UUID.nameUUIDFromBytes((japanese + "\u0000" + chinese).toByteArray())
                        TravelExpression("ai-$id", japanese, field("kana"), field("romaji"), chinese, field("scene"), field("politeness"))
                    }
                }
                groups.forEach { expression ->
                    Text(expression.japanese)
                    Text(expression.kana + " · " + expression.chinese)
                    val saved = expression.id in library.favoriteIds
                    androidx.compose.material3.TextButton({ libraryViewModel.saveGroup(expression) }, enabled = !saved) { Text(if (saved) "已收藏" else "收藏此句") }
                }
                androidx.compose.material3.TextButton({ groupsOpen = false }) { Text("关闭") }
            }
        }
    }
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            delay(500)
            libraryViewModel.recordSearchSubmit(query, results.size)
        }
    }
    val context = LocalContext.current
    val speaker = remember { SearchAudioPlayer(context.applicationContext) }
    DisposableEffect(speaker) { onDispose { speaker.release() } }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                OutlinedTextField(
                    value = query,
                    shape = RoundedCornerShape(24.dp),
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("输入中文或日语") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submit() }),
                    trailingIcon = {
                        if (query.isNotBlank()) androidx.compose.material3.TextButton(onClick = submit) { Text("AI 解析") }
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    androidx.compose.material3.TextButton(onClick = { groupsOpen = true }) { Text("AI 生成句组") }
                    androidx.compose.material3.TextButton(onClick = onOpenScenes) { Text("场景学习") }
                    androidx.compose.material3.TextButton(onClick = onOpenKana) { Text("五十音") }
                }
            }
        }
        when {
            query.isBlank() -> com.gojapan.feature.library.ui.FavoritesRoute(viewModel = libraryViewModel, favoritesOnly = true)
            results.isEmpty() -> Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("本地词库未找到匹配表达。点搜索框里的「AI 解析」解读这段文字。")
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
                        isFavorite = expression.id in library.favoriteIds,
                        inReview = expression.id in library.reviewIds,
                        onToggleFavorite = {
                            libraryViewModel.recordSearchResult(expression.id)
                            libraryViewModel.recordSearchClick(expression.id, rank)
                            libraryViewModel.toggleFavorite(expression.id)
                        },
                        onToggleReview = { libraryViewModel.toggleReview(expression.id) },
                        onKanaSpeak = { kana, index ->
                            val played = speaker.playKana(kana)
                            libraryViewModel.recordAudioPlay("search_kana", "${expression.id}:$index", played)
                        },
                    ) {
                        libraryViewModel.recordSearchResult(expression.id)
                        libraryViewModel.recordSearchClick(expression.id, rank)
                        val played = speaker.playExpression(expression)
                        libraryViewModel.recordAudioPlay("search", expression.id, played)
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
    isFavorite: Boolean,
    inReview: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleReview: () -> Unit,
    onKanaSpeak: (String, Int) -> Unit,
    onSpeak: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(expression.japanese, style = MaterialTheme.typography.titleLarge)
                Text("${expression.scene} · ${expression.politeness}", style = MaterialTheme.typography.labelMedium)
            }
            Text(expression.kana, style = MaterialTheme.typography.bodyLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                expression.kana.filterNot(Char::isWhitespace).forEachIndexed { index, kana ->
                    AssistChip(onClick = { onKanaSpeak(kana.toString(), index) }, label = { Text(kana.toString()) })
                }
            }
            Text("点击单个假名可逐字点读", style = MaterialTheme.typography.labelSmall)
            if (showRomaji) Text(expression.romaji, style = MaterialTheme.typography.bodySmall)
            Text(expression.chinese, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSpeak,
                    modifier = Modifier.semantics { contentDescription = "播放${expression.japanese}的日语发音" },
                ) { Text("▶ 播放") }
                Button(onClick = onToggleFavorite) { Text(if (isFavorite) "★ 已收藏" else "☆ 收藏") }
            }
        }
    }
}

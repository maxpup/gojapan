package com.gojapan.feature.library.ui

import com.gojapan.app.preview.GoJapanPreview
import com.gojapan.app.preview.GoJapanPreviews

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.search.domain.TravelExpression

@Composable
fun FavoritesRoute(
    onReview: () -> Unit = {},
    modifier: Modifier = Modifier,
    favoritesOnly: Boolean = false,
    viewModel: TravelLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FavoritesScreen(
        favoritesOnly = favoritesOnly,
        state = state,
        onRemoveFavorite = viewModel::toggleFavorite,
        onToggleReview = viewModel::toggleReview,
        onHideFrequent = viewModel::hideFrequentSearch,
        onHideWrong = viewModel::hideFrequentWrong,
        onReview = onReview,
        modifier = modifier,
    )
}

@Composable
fun FavoritesScreen(
    state: LibraryUiState,
    onRemoveFavorite: (String) -> Unit,
    onToggleReview: (String) -> Unit,
    onHideFrequent: (String) -> Unit,
    onHideWrong: (String) -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
    favoritesOnly: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val speaker = androidx.compose.runtime.remember(context) { com.gojapan.feature.search.ui.SearchAudioPlayer(context.applicationContext) }
    androidx.compose.runtime.DisposableEffect(speaker) { onDispose { speaker.release() } }
    val favoriteIds = state.favorites.mapTo(mutableSetOf()) { it.id }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = if (favoritesOnly) 0.dp else 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!favoritesOnly) item {
            Text("收藏", style = MaterialTheme.typography.headlineSmall)
            Text("主动收藏与系统发现的常搜索内容分开管理。")
            com.gojapan.feature.personal.PersonalLibraryButton()

        }
        item { Text("我的收藏", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        if (state.favorites.isEmpty()) item { Text("还没有收藏。可在搜索结果中添加常用表达。") }
        items(state.favorites, key = { "favorite-${it.id}" }) { expression ->
            LibraryCard(speaker, expression, expression.id in state.reviewIds, null, "取消收藏", {}, {
                onToggleReview(expression.id)
            }) { onRemoveFavorite(expression.id) }
        }
        if (favoritesOnly) return@LazyColumn
        item { Text("常搜索", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        if (state.frequentSearches.isEmpty()) item { Text("7 天内搜索同一表达至少 2 次后，会显示在这里。") }
        items(state.frequentSearches, key = { "frequent-${it.id}" }) { expression ->
            LibraryCard(speaker, expression, expression.id in state.reviewIds, expression.id in favoriteIds, "隐藏", {
                onRemoveFavorite(expression.id)
            }, {
                onToggleReview(expression.id)
            }) { onHideFrequent(expression.id) }
        }
        item { Text("常错词", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        if (state.frequentWrong.isEmpty()) item { Text("近 5 次作答错误达到 2 次后，会显示在这里。") }
        items(state.frequentWrong, key = { "wrong-${it.id}" }) { expression ->
            LibraryCard(speaker, expression, expression.id in state.reviewIds, expression.id in favoriteIds, "隐藏", {
                onRemoveFavorite(expression.id)
            }, {
                onToggleReview(expression.id)
            }) { onHideWrong(expression.id) }
        }
    }
}

@Composable
private fun LibraryCard(
    speaker: com.gojapan.feature.search.ui.SearchAudioPlayer,
    expression: TravelExpression,
    inReview: Boolean,
    favoriteActionState: Boolean?,
    removeLabel: String,
    onToggleFavorite: () -> Unit,
    onToggleReview: () -> Unit,
    onRemoveOrHide: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun checkPlayback(started: Boolean) {
        if (!started) android.widget.Toast.makeText(context, "请检查系统是否已安装日语语音包", android.widget.Toast.LENGTH_SHORT).show()
    }
    Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val words = androidx.compose.runtime.remember(expression.japanese) {
                val iterator = android.icu.text.BreakIterator.getWordInstance(java.util.Locale.JAPANESE)
                iterator.setText(expression.japanese)
                var start = iterator.first()
                buildList {
                    var end = iterator.next()
                    while (end != android.icu.text.BreakIterator.DONE) {
                        val word = expression.japanese.substring(start, end)
                        if (word.any(Char::isLetterOrDigit)) add(word)
                        start = end
                        end = iterator.next()
                    }
                }
            }
            Text(expression.japanese, style = MaterialTheme.typography.titleLarge)
            Text("${expression.kana} · ${expression.chinese}")
            Text("${expression.scene} · ${expression.politeness}", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                words.forEach { word -> androidx.compose.material3.TextButton({ checkPlayback(speaker.playWord(word)) }) { Text(word) } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ checkPlayback(speaker.playExpression(expression)) }) { Text("播放整句") }
                if (favoriteActionState != null) {
                    Button(onClick = onToggleFavorite) {
                        Text(if (favoriteActionState) "取消收藏" else "转为收藏")
                    }
                }
                OutlinedButton(onClick = onRemoveOrHide) { Text(removeLabel) }
            }
        }
    }
}

@GoJapanPreviews
@Composable
private fun FavoritesPreview() {
    val expressions = com.gojapan.feature.search.domain.TravelExpressionSearch.reviewedExpressions.take(3)
    GoJapanPreview {
        FavoritesScreen(
            state = LibraryUiState(favorites = expressions, reviewIds = expressions.take(1).map { it.id }.toSet()),
            onRemoveFavorite = {}, onToggleReview = {}, onHideFrequent = {}, onHideWrong = {}, onReview = {},
        )
    }
}

@GoJapanPreviews
@Composable
private fun FavoritesEmptyPreview() {
    GoJapanPreview { FavoritesScreen(LibraryUiState(), {}, {}, {}, {}, {}) }
}

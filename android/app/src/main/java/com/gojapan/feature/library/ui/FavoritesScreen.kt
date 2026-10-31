package com.gojapan.feature.library.ui

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
    viewModel: TravelLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FavoritesScreen(
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
) {
    val favoriteIds = state.favorites.mapTo(mutableSetOf()) { it.id }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("收藏", style = MaterialTheme.typography.headlineSmall)
            Text("主动收藏与系统发现的常搜索内容分开管理。")
            Button(onClick = onReview, enabled = state.reviewIds.isNotEmpty()) {
                Text(if (state.reviewIds.isEmpty()) "暂无复习内容" else "批量复习 ${state.reviewIds.size} 项")
            }
        }
        item { Text("我的收藏", style = MaterialTheme.typography.titleLarge) }
        if (state.favorites.isEmpty()) item { Text("还没有收藏。可在搜索结果中添加常用表达。") }
        items(state.favorites, key = { "favorite-${it.id}" }) { expression ->
            LibraryCard(expression, expression.id in state.reviewIds, null, "取消收藏", {}, {
                onToggleReview(expression.id)
            }) { onRemoveFavorite(expression.id) }
        }
        item { Text("常搜索", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        if (state.frequentSearches.isEmpty()) item { Text("7 天内搜索同一表达至少 2 次后，会显示在这里。") }
        items(state.frequentSearches, key = { "frequent-${it.id}" }) { expression ->
            LibraryCard(expression, expression.id in state.reviewIds, expression.id in favoriteIds, "隐藏", {
                onRemoveFavorite(expression.id)
            }, {
                onToggleReview(expression.id)
            }) { onHideFrequent(expression.id) }
        }
        item { Text("常错词", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        if (state.frequentWrong.isEmpty()) item { Text("近 5 次作答错误达到 2 次后，会自动进入这里和复习计划。") }
        items(state.frequentWrong, key = { "wrong-${it.id}" }) { expression ->
            LibraryCard(expression, expression.id in state.reviewIds, expression.id in favoriteIds, "隐藏", {
                onRemoveFavorite(expression.id)
            }, {
                onToggleReview(expression.id)
            }) { onHideWrong(expression.id) }
        }
    }
}

@Composable
private fun LibraryCard(
    expression: TravelExpression,
    inReview: Boolean,
    favoriteActionState: Boolean?,
    removeLabel: String,
    onToggleFavorite: () -> Unit,
    onToggleReview: () -> Unit,
    onRemoveOrHide: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(expression.japanese, style = MaterialTheme.typography.titleLarge)
            Text("${expression.kana} · ${expression.chinese}")
            Text("${expression.scene} · ${expression.politeness}", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleReview) { Text(if (inReview) "移出复习" else "加入复习") }
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

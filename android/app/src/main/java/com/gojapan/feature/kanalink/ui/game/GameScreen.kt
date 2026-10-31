package com.gojapan.feature.kanalink.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.kanalink.audio.rememberKanaSpeaker
import com.gojapan.feature.kanalink.audio.GameFeedback
import com.gojapan.feature.kanalink.audio.rememberGameSoundPlayer
import com.gojapan.feature.kanalink.ui.settings.SettingsViewModel
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.PairMode
import com.gojapan.feature.kanalink.domain.model.RoundSource

@Composable
fun GameRoute(
    items: List<KanaItem>,
    mode: PairMode = PairMode.HIRAGANA_ROMAJI,
    source: RoundSource = RoundSource.DAILY,
    onCompleted: (RoundSummary) -> Unit,
    onExit: () -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()
    val speak = rememberKanaSpeaker()
    val feedback = rememberGameSoundPlayer(settings.soundEffectEnabled)
    LaunchedEffect(items, mode, source) { viewModel.start(items, mode, source) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> if (event is GameEvent.Completed) onCompleted(event.summary) }
    }
    state?.let {
        GameScreen(
            it,
            onSelect = { cardId ->
                it.cards.singleOrNull { card -> card.id == cardId }
                    ?.takeIf { card -> settings.voiceEnabled && card.side != CardSide.ROMAJI }
                    ?.let { card -> it.items.singleOrNull { item -> item.id == card.kanaId } }
                    ?.let(speak)
                val before = viewModel.state.value
                viewModel.select(cardId)
                val after = viewModel.state.value
                if (before?.selectedCardId != null && after != null) {
                    when {
                        after.matchedKanaIds.size > before.matchedKanaIds.size -> {
                            feedback(if (after.remainingPairs == 0) GameFeedback.COMPLETE else GameFeedback.CORRECT)
                        }
                        after.wrongCounts != before.wrongCounts -> feedback(GameFeedback.WRONG)
                    }
                }
            },
            viewModel::hint,
            viewModel::shuffle,
            viewModel::retryComplete,
            onExit,
        )
    }
}

@Composable
fun GameScreen(
    state: GameUiState,
    onSelect: (String) -> Unit,
    onHint: () -> Unit,
    onShuffle: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    var showExitConfirmation by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            OutlinedButton(onClick = { showExitConfirmation = true }) { Text("退出") }
            Text("${formatTime(state.elapsedSeconds)} · 连击 × ${state.combo}")
            Text("剩余 ${state.remainingPairs} 对")
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.cards.size, key = { state.cards[it].id }) { index ->
                val card = state.cards[index]
                val matched = card.kanaId in state.matchedKanaIds
                val selected = card.id == state.selectedCardId
                val hinted = card.id in state.hintedCardIds
                Card(
                    onClick = { onSelect(card.id) },
                    enabled = !matched && state.status == GameStatus.PLAYING,
                    border = if (selected || hinted) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.semantics {
                        contentDescription = when (card.side) {
                            CardSide.HIRAGANA -> "平假名 ${card.text}"
                            CardSide.KATAKANA -> "片假名 ${card.text}"
                            CardSide.ROMAJI -> "罗马音 ${card.text}"
                        }
                        stateDescription = when {
                            matched -> "已配对"
                            selected -> "已选择"
                            hinted -> "提示目标"
                            else -> "未选择"
                        }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (matched) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            when {
                                matched -> "✓"
                                selected -> "● ${card.text}"
                                hinted -> "提示 ${card.text}"
                                else -> card.text
                            },
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
            }
        }
        if (state.status == GameStatus.FAILED) {
            Button(onClick = onRetry, Modifier.fillMaxWidth()) { Text("保存失败，重试") }
        } else {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onHint, enabled = state.hintsLeft > 0, modifier = Modifier.weight(1f)) {
                    Text("提示（${state.hintsLeft}）")
                }
                OutlinedButton(onClick = onShuffle, enabled = state.shufflesLeft > 0, modifier = Modifier.weight(1f)) {
                    Text("重排（${state.shufflesLeft}）")
                }
            }
        }
    }
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("退出本轮学习？") },
            text = { Text("未完成的棋盘不会写入学习记录。") },
            confirmButton = { Button(onClick = onExit) { Text("确认退出") } },
            dismissButton = { OutlinedButton(onClick = { showExitConfirmation = false }) { Text("继续学习") } },
        )
    }
}

private fun formatTime(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

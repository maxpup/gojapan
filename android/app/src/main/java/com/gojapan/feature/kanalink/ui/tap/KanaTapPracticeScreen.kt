package com.gojapan.feature.kanalink.ui.tap

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.kanalink.audio.rememberKanaSpeaker
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaItem

@Composable
fun KanaTapPracticeRoute(onBack: () -> Unit, viewModel: KanaTapPracticeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    KanaTapPracticeScreen(
        state, viewModel::selectScript, viewModel::toggleRomaji, viewModel::selectGroup,
        viewModel::selectRow, viewModel::tap, viewModel::reset, viewModel::nextRow, onBack,
    )
}

@Composable
fun KanaTapPracticeScreen(
    state: KanaTapPracticeState,
    onScript: (KanaScript) -> Unit,
    onToggleRomaji: () -> Unit,
    onGroup: (KanaGroup) -> Unit,
    onRow: (String) -> Unit,
    onTap: (com.gojapan.feature.kanalink.domain.model.KanaId) -> Unit,
    onReset: () -> Unit,
    onNextRow: () -> Unit,
    onBack: () -> Unit,
) {
    if (state.loading) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { CircularProgressIndicator() }
        return
    }
    val speaker = rememberKanaSpeaker()
    val reduceMotion = state.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("五十音点读图", style = MaterialTheme.typography.headlineSmall)
        Text("每个假名点读 5 次，它就会暂时消失。消失不等于永久掌握。")
        ScrollChoices(KanaScript.entries, state.script, { it.name }) { onScript(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onToggleRomaji) { Text(if (state.showRomaji) "隐藏罗马音" else "显示罗马音") }
            OutlinedButton(onClick = onBack) { Text("返回") }
        }
        ScrollChoices(KanaGroup.entries, state.group, { groupLabel(it) }) { onGroup(it) }
        ScrollChoices(state.rows, state.selectedRow, { it }) { onRow(it) }
        val round = state.round
        if (round != null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(92.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(round.items, key = { it.id.value }) { item ->
                    AnimatedVisibility(
                        visible = item.id !in round.disappeared,
                        exit = if (reduceMotion) fadeOut(tween(400)) else shrinkOut(tween(550)) + fadeOut(tween(500)),
                    ) {
                        KanaTapCard(item, state.script, state.showRomaji, round.count(item.id)) {
                            speaker(item)
                            onTap(item.id)
                        }
                    }
                }
            }
            if (round.completed) {
                Text("这一行全部完成！", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onReset) { Text("再玩一次") }
                    Button(onClick = onNextRow) { Text("下一行") }
                }
            } else if (round.disappeared.isNotEmpty()) {
                OutlinedButton(onClick = onReset) { Text("全部回来") }
            }
        }
    }
}

@Composable
private fun KanaTapCard(item: KanaItem, script: KanaScript, showRomaji: Boolean, count: Int, onClick: () -> Unit) {
    val kana = when (script) {
        KanaScript.HIRAGANA -> item.hiragana
        KanaScript.KATAKANA -> item.katakana
        KanaScript.SIDE_BY_SIDE -> "${item.hiragana}  ${item.katakana}"
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick).semantics {
            contentDescription = "$kana，已点读${count}次"
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(kana, style = MaterialTheme.typography.headlineMedium)
            if (showRomaji) Text(item.romaji)
            Text(item.word, style = MaterialTheme.typography.bodySmall)
            Text(if (count == 4) "要消失啦" else "$count / 5", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun <T> ScrollChoices(values: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label(value)) }) }
    }
}

private fun groupLabel(group: KanaGroup) = when (group) {
    KanaGroup.SEION -> "清音"
    KanaGroup.DAKUON -> "浊音"
    KanaGroup.HANDAKUON -> "半浊音"
    KanaGroup.YOON -> "拗音"
}

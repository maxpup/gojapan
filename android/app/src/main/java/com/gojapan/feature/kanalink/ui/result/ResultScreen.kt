package com.gojapan.feature.kanalink.ui.result

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.ui.game.RoundSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repository: LearningRepository,
    private val clock: Clock,
) : ViewModel() {
    private val mutableSelection = MutableStateFlow<Set<KanaId>>(emptySet())
    val selection = mutableSelection.asStateFlow()

    fun toggle(id: KanaId) {
        mutableSelection.value = if (id in mutableSelection.value) mutableSelection.value - id else mutableSelection.value + id
    }

    fun markMastered() {
        val ids = mutableSelection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.markMastered(ids, clock.millis())
            mutableSelection.value = emptySet()
        }
    }
}

@Composable
fun ResultRoute(
    summary: RoundSummary,
    onAgain: () -> Unit,
    onHome: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val selected by viewModel.selection.collectAsStateWithLifecycle()
    ResultScreen(summary, selected, viewModel::toggle, viewModel::markMastered, onAgain, onHome)
}

@Composable
fun ResultScreen(
    summary: RoundSummary,
    selected: Set<KanaId>,
    onToggle: (KanaId) -> Unit,
    onMarkMastered: () -> Unit,
    onAgain: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val celebration = rememberInfiniteTransition(label = "result celebration")
        val celebrationOffset by celebration.animateFloat(
            initialValue = 0f,
            targetValue = -16f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "celebration offset",
        )
        Text("🎉", modifier = Modifier.graphicsLayer { translationY = celebrationOffset })
        Text("全部完成，太棒了！", fontWeight = FontWeight.Bold)
        Text("用时 ${formatTime(summary.durationSeconds)} · 最高连击 ${summary.bestCombo}", Modifier.padding(vertical = 8.dp))
        Text("建议 ${formatDate(summary.nextReviewAt)} 进行下一次复习")
        summary.items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = item.id in selected,
                    onClick = { onToggle(item.id) },
                    label = { Text("${item.hiragana} ${item.romaji}") },
                )
                Text(
                    "${item.word} · ${item.meaningZhCn} · 总 ${summary.seenCounts[item.id] ?: 1} 次" +
                        if ((summary.wrongCounts[item.id] ?: 0) > 0) " · 错 ${summary.wrongCounts[item.id]}" else "",
                    Modifier.padding(start = 8.dp),
                )
            }
        }
        OutlinedButton(
            onClick = onMarkMastered,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("标记选中项为自评记熟") }
        Button(onClick = onAgain, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("再练一轮") }
        OutlinedButton(onClick = onHome, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("返回首页") }
    }
}

private fun formatTime(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
private fun formatDate(epochMillis: Long): String = DateTimeFormatter.ofPattern("M月d日").format(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
)

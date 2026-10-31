package com.gojapan.feature.kanalink.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.kanalink.data.repository.LearningReport
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.kanalink.data.course.KanaCourseRepository
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class ReportPeriod(val label: String) { DAY("每日"), WEEK("每周"), MONTH("每月") }

data class ReportUiState(
    val period: ReportPeriod = ReportPeriod.DAY,
    val report: LearningReport? = null,
    val selected: Set<KanaId> = emptySet(),
    val course: Map<KanaId, KanaItem> = emptyMap(),
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: LearningRepository,
    private val courseRepository: KanaCourseRepository,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReportUiState())
    val state = mutableState.asStateFlow()
    init { load(ReportPeriod.DAY) }

    fun load(period: ReportPeriod) {
        mutableState.value = mutableState.value.copy(period = period, report = null, selected = emptySet())
        viewModelScope.launch {
            val range = period.range(clock.millis(), ZoneId.systemDefault())
            mutableState.value = mutableState.value.copy(
                report = repository.getReport(range.start, range.end, range.previousStart),
                course = courseRepository.getCourse().associateBy { it.id },
            )
        }
    }

    fun toggle(id: KanaId) {
        val selected = mutableState.value.selected
        mutableState.value = mutableState.value.copy(selected = if (id in selected) selected - id else selected + id)
    }

    fun markMastered() {
        val ids = mutableState.value.selected
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.markMastered(ids, clock.millis())
            load(mutableState.value.period)
        }
    }
}

@Composable
fun ReportRoute(onBack: () -> Unit, viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReportScreen(state, viewModel::load, viewModel::toggle, viewModel::markMastered, onBack)
}

@Composable
fun ReportScreen(
    state: ReportUiState,
    onPeriod: (ReportPeriod) -> Unit,
    onToggle: (KanaId) -> Unit,
    onMarkMastered: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("学习报告")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportPeriod.entries.forEach { period ->
                FilterChip(selected = state.period == period, onClick = { onPeriod(period) }, label = { Text(period.label) })
            }
        }
        val report = state.report
        when {
            report == null -> Text("正在统计…")
            report.rounds == 0 -> Text("本期还没有完整练习记录")
            else -> {
                Text("完成 ${report.rounds} 轮 · 学习 ${report.distinctKana} 个音")
                Text("正确率 ${report.accuracyPercent}% · 用时 ${formatDuration(report.durationSeconds)}")
                Text(compareText(report.rounds, report.previousRounds))
                report.items.forEach { item ->
                    val kana = state.course[item.kanaId]
                    FilterChip(
                        selected = item.kanaId in state.selected,
                        onClick = { onToggle(item.kanaId) },
                        label = { Text("${kana?.hiragana ?: item.kanaId.value} ${kana?.romaji ?: ""} · ${item.appearances} 次 · ${item.word} · ${item.meaning}${if (item.manualMastered) " · 自评记熟" else ""}") },
                    )
                }
                Button(onClick = onMarkMastered, enabled = state.selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("标记选中项为自评记熟")
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回首页") }
    }
}

internal data class PeriodRange(val previousStart: Long, val start: Long, val end: Long)

internal fun ReportPeriod.range(now: Long, zone: ZoneId): PeriodRange {
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val startDate: LocalDate
    val endDate: LocalDate
    val previousDate: LocalDate
    when (this) {
        ReportPeriod.DAY -> { startDate = today; endDate = today.plusDays(1); previousDate = today.minusDays(1) }
        ReportPeriod.WEEK -> {
            startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            endDate = startDate.plusWeeks(1); previousDate = startDate.minusWeeks(1)
        }
        ReportPeriod.MONTH -> {
            startDate = YearMonth.from(today).atDay(1)
            endDate = startDate.plusMonths(1); previousDate = startDate.minusMonths(1)
        }
    }
    return PeriodRange(
        previousDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        endDate.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}

private fun compareText(current: Int, previous: Int): String = when {
    current > previous -> "比上一周期多 ${current - previous} 轮"
    current < previous -> "比上一周期少 ${previous - current} 轮"
    else -> "与上一周期持平"
}
private fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

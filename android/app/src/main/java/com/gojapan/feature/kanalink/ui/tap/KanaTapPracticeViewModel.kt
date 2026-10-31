package com.gojapan.feature.kanalink.ui.tap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.kanalink.data.course.KanaCourseRepository
import com.gojapan.feature.kanalink.data.preferences.KanaPreferences
import com.gojapan.feature.kanalink.data.preferences.KanaSettings
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.gojapan.core.analytics.AnalyticsTracker
import com.gojapan.BuildConfig

enum class KanaScript { HIRAGANA, KATAKANA, SIDE_BY_SIDE }

data class KanaTapPracticeState(
    val loading: Boolean = true,
    val course: List<KanaItem> = emptyList(),
    val group: KanaGroup = KanaGroup.SEION,
    val selectedRow: String? = null,
    val script: KanaScript = KanaScript.HIRAGANA,
    val showRomaji: Boolean = true,
    val round: KanaTapRoundState? = null,
    val reduceMotion: Boolean = false,
) {
    val rows: List<String> get() = course.filter { it.group == group }.groupBy { it.row }
        .entries.sortedBy { (_, items) -> items.minOf { it.order } }.map { it.key }
}

@HiltViewModel
class KanaTapPracticeViewModel @Inject constructor(
    courseRepository: KanaCourseRepository,
    preferences: KanaPreferences,
    private val analytics: AnalyticsTracker,
) : ViewModel() {
    private val content = MutableStateFlow(KanaTapPracticeState())
    val state: StateFlow<KanaTapPracticeState> = combine(content, preferences.settings) { ui, settings ->
        ui.copy(reduceMotion = settings.reduceMotionEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KanaTapPracticeState())

    init {
        viewModelScope.launch {
            val course = courseRepository.getCourse()
            content.update { old -> configure(old.copy(loading = false, course = course), KanaGroup.SEION, null) }
        }
    }

    fun selectScript(script: KanaScript) = content.update { it.copy(script = script) }
    fun toggleRomaji() = content.update { it.copy(showRomaji = !it.showRomaji) }
    fun selectGroup(group: KanaGroup) = content.update { configure(it, group, null) }
    fun selectRow(row: String) = content.update { configure(it, it.group, row) }
    fun tap(id: KanaId) {
        val before = content.value.round ?: return
        val item = before.items.firstOrNull { it.id == id } ?: return
        val count = (before.count(id) + 1).coerceAtMost(KanaTapRound.DISAPPEAR_AT)
        content.update { it.copy(round = it.round?.let { round -> KanaTapRound.tap(round, id) }) }
        viewModelScope.launch {
            analytics.track("kana_tap", mapOf(
                "kana" to item.hiragana,
                "kana_id" to id.value,
                "tap_count" to count,
                "audio_success" to BuildConfig.ENABLE_TTS_FALLBACK,
            ))
            if (count == KanaTapRound.DISAPPEAR_AT) analytics.track("kana_disappear", mapOf(
                "kana" to item.hiragana,
                "animation_type" to if (state.value.reduceMotion) "fade" else "shrink_fade",
                "reduce_motion" to state.value.reduceMotion,
            ))
        }
    }
    fun reset() = content.update { it.copy(round = it.round?.let(KanaTapRound::reset)) }
    fun nextRow() = content.update { current ->
        val index = current.rows.indexOf(current.selectedRow)
        configure(current, current.group, current.rows.getOrNull(index + 1) ?: current.rows.firstOrNull())
    }

    private fun configure(state: KanaTapPracticeState, group: KanaGroup, requestedRow: String?): KanaTapPracticeState {
        val grouped = state.course.filter { it.group == group }.groupBy { it.row }
        val rows = grouped.entries.sortedBy { (_, items) -> items.minOf { it.order } }.map { it.key }
        val row = requestedRow?.takeIf { it in rows } ?: rows.firstOrNull()
        val items = grouped[row].orEmpty().sortedBy { it.order }
        return state.copy(group = group, selectedRow = row, round = items.takeIf(List<KanaItem>::isNotEmpty)?.let(KanaTapRound::start))
    }
}

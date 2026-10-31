package com.gojapan.feature.kanalink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.kanalink.data.course.KanaCourseRepository
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.PairMode
import com.gojapan.feature.kanalink.domain.model.RoundSource
import com.gojapan.feature.kanalink.domain.scheduler.MemoryScheduler
import com.gojapan.feature.kanalink.ui.game.RoundSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

sealed interface KanaFlowState {
    data object Home : KanaFlowState
    data object CustomPractice : KanaFlowState
    data object Settings : KanaFlowState
    data object Report : KanaFlowState
    data object TapPractice : KanaFlowState
    data object CoreExercise : KanaFlowState
    data class Game(
        val items: List<KanaItem>,
        val mode: PairMode = PairMode.HIRAGANA_ROMAJI,
        val source: RoundSource = RoundSource.DAILY,
    ) : KanaFlowState
    data class Result(val summary: RoundSummary) : KanaFlowState
}

@HiltViewModel
class KanaFlowViewModel @Inject constructor(
    private val courseRepository: KanaCourseRepository,
    private val learningRepository: LearningRepository,
    private val scheduler: MemoryScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow<KanaFlowState>(KanaFlowState.Home)
    val state = mutableState.asStateFlow()

    fun start(items: List<KanaItem>) { mutableState.value = KanaFlowState.Game(items) }
    fun startCustom(items: List<KanaItem>, mode: PairMode) {
        mutableState.value = KanaFlowState.Game(items, mode, RoundSource.CUSTOM)
    }
    fun showCustomPractice() { mutableState.value = KanaFlowState.CustomPractice }
    fun showSettings() { mutableState.value = KanaFlowState.Settings }
    fun showReport() { mutableState.value = KanaFlowState.Report }
    fun showTapPractice() { mutableState.value = KanaFlowState.TapPractice }
    fun showCoreExercise() { mutableState.value = KanaFlowState.CoreExercise }
    fun complete(summary: RoundSummary) { mutableState.value = KanaFlowState.Result(summary) }
    fun goHome() { mutableState.value = KanaFlowState.Home }

    fun again() {
        viewModelScope.launch {
            val course = courseRepository.getCourse()
            val progress = learningRepository.observeProgress().first()
            start(scheduler.select(course, progress, clock.millis()))
        }
    }
}

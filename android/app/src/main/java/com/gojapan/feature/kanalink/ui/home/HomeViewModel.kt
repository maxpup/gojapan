package com.gojapan.feature.kanalink.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.kanalink.data.course.KanaCourseRepository
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.scheduler.MemoryScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val course: List<KanaItem>,
        val progress: Map<com.gojapan.feature.kanalink.domain.model.KanaId, com.gojapan.feature.kanalink.domain.model.KanaProgress>,
        val sessions: Int,
        val learningDays: Int,
        val dueCount: Int,
    ) : HomeUiState {
        val exploredCount = progress.size
        val masteredCount = progress.values.count { it.level == 5 || it.manualMastered }
        val primaryAction = when {
            sessions == 0 -> "开始学习"
            dueCount > 0 -> "复习 ${minOf(5, dueCount)} 个音"
            else -> "学习 5 个新音"
        }
    }
    data class Error(val message: String) : HomeUiState
}

sealed interface HomeEvent { data class StartRound(val items: List<KanaItem>) : HomeEvent }

@HiltViewModel
class HomeViewModel @Inject constructor(
    courseRepository: KanaCourseRepository,
    learningRepository: LearningRepository,
    private val scheduler: MemoryScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val eventsChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = eventsChannel.receiveAsFlow()

    val uiState = combine(
        flow { emit(courseRepository.getCourse()) },
        learningRepository.observeHomeStats(clock.millis()),
    ) { course, stats ->
        HomeUiState.Ready(course, stats.progress, stats.sessions, stats.learningDays, stats.dueCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun startRound() {
        val state = uiState.value as? HomeUiState.Ready ?: return
        viewModelScope.launch {
            eventsChannel.send(HomeEvent.StartRound(scheduler.select(state.course, state.progress, clock.millis())))
        }
    }
}

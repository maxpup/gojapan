package com.gojapan.feature.kanalink.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.kanalink.data.course.KanaCourseRepository
import com.gojapan.feature.kanalink.data.preferences.KanaPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.gojapan.core.analytics.AnalyticsTracker

data class KanaCoreExerciseUiState(
    val loading: Boolean = true,
    val exercise: KanaExerciseState? = null,
)

@HiltViewModel
class KanaCoreExerciseViewModel @Inject constructor(
    private val courseRepository: KanaCourseRepository,
    private val preferences: KanaPreferences,
    private val clock: Clock,
    private val analytics: AnalyticsTracker,
) : ViewModel() {
    private val mutableState = MutableStateFlow(KanaCoreExerciseUiState())
    val state: StateFlow<KanaCoreExerciseUiState> = mutableState.asStateFlow()
    private var engine: KanaCoreExerciseEngine? = null
    private var startedAt: Long = 0L

    init { start() }

    fun answer(answer: String) {
        val question = mutableState.value.exercise?.current ?: return
        val correct = answer == question.correctAnswer
        mutableState.update { ui -> ui.copy(exercise = engine?.answer(checkNotNull(ui.exercise), answer)) }
        viewModelScope.launch { analytics.track("kana_answer", mapOf(
            "kana" to question.target.hiragana,
            "kana_id" to question.target.id.value,
            "question_type" to question.dimension.name,
            "result" to correct,
            "hint_count" to 0,
        )) }
    }

    fun advance() {
        val next = engine!!.advance(checkNotNull(mutableState.value.exercise))
        mutableState.update { it.copy(exercise = next) }
        if (next.completed) viewModelScope.launch {
            val now = clock.millis()
            preferences.recordSkillAnswers(next.answers, now)
            analytics.track("kana_lesson_complete", mapOf(
                "kana_type" to "mixed", "row" to "core_12", "duration_ms" to (now - startedAt),
                "score" to next.accuracy, "wrong_count" to (next.answers.size - next.correctCount),
            ))
        }
    }

    fun restart() = start()

    private fun start() {
        viewModelScope.launch {
            val questions = KanaCoreQuestionFactory.create(courseRepository.getCourse())
            engine = KanaCoreExerciseEngine(questions)
            startedAt = clock.millis()
            mutableState.value = KanaCoreExerciseUiState(false, engine!!.start())
            analytics.track("kana_lesson_start", mapOf("kana_type" to "mixed", "row" to "core_12"))
        }
    }
}

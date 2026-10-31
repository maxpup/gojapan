package com.gojapan.feature.lesson.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.lesson.domain.LessonEngineState
import com.gojapan.feature.lesson.domain.SceneLesson
import com.gojapan.feature.lesson.domain.SceneLessonCatalog
import com.gojapan.feature.lesson.domain.SceneLessonEngine
import com.gojapan.feature.library.data.TravelLibraryPreferences
import com.gojapan.feature.onboarding.data.UserProfilePreferences
import com.gojapan.feature.onboarding.domain.LearningPathGenerator
import com.gojapan.feature.lesson.domain.TravelScene
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import com.gojapan.core.analytics.AnalyticsTracker
import com.gojapan.BuildConfig
import java.time.Clock

enum class LessonPhase { CATALOG, PREVIEW, PLAYING, RESULT }

data class SceneLessonUiState(
    val phase: LessonPhase = LessonPhase.CATALOG,
    val lesson: SceneLesson? = null,
    val engine: LessonEngineState? = null,
    val orderedScenes: List<TravelScene> = TravelScene.entries,
)

@HiltViewModel
class SceneLessonViewModel @Inject constructor(
    private val library: TravelLibraryPreferences,
    profilePreferences: UserProfilePreferences,
    private val analytics: AnalyticsTracker,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SceneLessonUiState())
    val state: StateFlow<SceneLessonUiState> = mutableState.asStateFlow()
    private var engine: SceneLessonEngine? = null
    private var lessonStartedAt: Long = 0L

    init {
        viewModelScope.launch {
            profilePreferences.profile.collectLatest { profile ->
                mutableState.update { it.copy(orderedScenes = LearningPathGenerator.orderedScenes(profile)) }
            }
        }
    }

    fun openLesson(lesson: SceneLesson) {
        require(lesson in SceneLessonCatalog.allLessons)
        mutableState.update { it.copy(phase = LessonPhase.PREVIEW, lesson = lesson, engine = null) }
    }

    fun startLesson() {
        val lesson = checkNotNull(mutableState.value.lesson)
        engine = SceneLessonEngine(lesson)
        lessonStartedAt = clock.millis()
        mutableState.update { it.copy(phase = LessonPhase.PLAYING, engine = engine!!.start()) }
        viewModelScope.launch { analytics.track("lesson_start", mapOf(
            "scene" to lesson.scene.name, "lesson_id" to lesson.id, "difficulty" to "MVP",
        )) }
    }

    fun replayAudio() {
        val current = checkNotNull(mutableState.value.engine)
        val question = checkNotNull(current.currentQuestion)
        mutableState.update { it.copy(engine = engine!!.replayAudio(current)) }
        viewModelScope.launch { analytics.track("audio_play", mapOf(
            "entry" to "lesson", "content_id" to question.expressionId,
            "success" to BuildConfig.ENABLE_TTS_FALLBACK,
        )) }
    }

    fun answer(answer: String) {
        val current = checkNotNull(mutableState.value.engine)
        val question = checkNotNull(current.currentQuestion)
        val correct = answer == question.correctAnswer
        mutableState.update { it.copy(engine = engine!!.answer(current, answer)) }
        viewModelScope.launch { analytics.track("lesson_question_answer", mapOf(
            "lesson_id" to checkNotNull(mutableState.value.lesson).id,
            "question_type" to question.type.name,
            "content_id" to question.expressionId,
            "attempt" to if (question.isRetry) "retry" else "first",
            "result" to correct,
            "replay_count" to (current.evidence[question.expressionId]?.replayCount ?: 0),
        )) }
    }

    fun advance() {
        val current = checkNotNull(mutableState.value.engine)
        val next = engine!!.advance(current)
        mutableState.update { it.copy(phase = if (next.completed) LessonPhase.RESULT else LessonPhase.PLAYING, engine = next) }
        if (next.completed) {
            val ids = checkNotNull(mutableState.value.lesson).expressions.mapTo(mutableSetOf()) { it.id }
            val results = next.evidence.mapValues { it.value.recentResults }
            viewModelScope.launch {
                library.enrollReview(ids)
                library.recordLessonResults(results)
                library.recordLessonCompletion(
                    checkNotNull(mutableState.value.lesson).id,
                    minOf(next.initialAccuracy, next.challengeAccuracy),
                    next.passed,
                )
                analytics.track("lesson_complete", mapOf(
                    "scene" to checkNotNull(mutableState.value.lesson).scene.name,
                    "lesson_id" to checkNotNull(mutableState.value.lesson).id,
                    "score" to next.initialAccuracy,
                    "challenge_score" to next.challengeAccuracy,
                    "passed" to next.passed,
                    "duration_ms" to (clock.millis() - lessonStartedAt),
                ))
            }
        }
    }

    fun retryWeakItems() {
        val lesson = checkNotNull(mutableState.value.lesson)
        val weakIds = mutableState.value.engine?.evidence?.values
            ?.filter { it.firstTryCorrect == false || !it.finalCorrect }
            ?.mapTo(mutableSetOf()) { it.expressionId }
            .orEmpty()
        val retryLesson = lesson.copy(
            title = "${lesson.title} · 薄弱强化",
            expressions = lesson.expressions.filter { it.id in weakIds }.ifEmpty { lesson.expressions },
        )
        engine = SceneLessonEngine(retryLesson)
        mutableState.update { it.copy(phase = LessonPhase.PLAYING, lesson = retryLesson, engine = engine!!.start()) }
    }

    fun backToCatalog() {
        engine = null
        mutableState.update { SceneLessonUiState(orderedScenes = it.orderedScenes) }
    }
}

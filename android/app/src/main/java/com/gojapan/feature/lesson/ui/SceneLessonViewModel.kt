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
import java.time.Clock

enum class LessonPhase { CATALOG, PREVIEW, PLAYING, RESULT }

data class SceneLessonUiState(
    val phase: LessonPhase = LessonPhase.CATALOG,
    val lesson: SceneLesson? = null,
    val engine: LessonEngineState? = null,
    val completedLessonIds: Set<String> = emptySet(),
    val checkpoint: com.gojapan.feature.lesson.domain.LessonCheckpoint? = null,
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
    private var reinforcement = false
    private var sessionId = java.util.UUID.randomUUID().toString()
    private var lessonStartedAt: Long = 0L

    init {
        viewModelScope.launch {
            library.state.collectLatest { libraryState ->
                mutableState.update { it.copy(completedLessonIds = libraryState.completedLessonIds, checkpoint = libraryState.lessonCheckpoint) }
            }
        }
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
        reinforcement = false
        sessionId = java.util.UUID.randomUUID().toString()
        engine = SceneLessonEngine(lesson)
        lessonStartedAt = clock.millis()
        mutableState.update { it.copy(phase = LessonPhase.PLAYING, engine = engine!!.start()) }
        saveCheckpoint()
        viewModelScope.launch { analytics.track("lesson_start", mapOf(
            "scene" to lesson.scene.name, "lesson_id" to lesson.id, "difficulty" to "MVP",
        )) }
    }

    fun replayAudio() {
        val current = checkNotNull(mutableState.value.engine)
        mutableState.update { it.copy(engine = engine!!.replayAudio(current)) }
    }

    fun recordAudioResult(success: Boolean) {
        val question = mutableState.value.engine?.currentQuestion ?: return
        viewModelScope.launch { analytics.track("audio_play", mapOf(
            "entry" to "lesson", "content_id" to question.expressionId,
            "success" to success,
        )) }
    }

    fun answer(answer: String) {
        val current = checkNotNull(mutableState.value.engine)
        val question = checkNotNull(current.currentQuestion)
        val lesson = checkNotNull(mutableState.value.lesson)
        val correct = answer == question.correctAnswer
        mutableState.update { it.copy(engine = engine!!.answer(current, answer)) }
        saveCheckpoint()
        viewModelScope.launch { analytics.track("lesson_question_answer", mapOf(
            "lesson_id" to lesson.id,
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
        if (!next.completed) saveCheckpoint()
        if (next.completed) {
            val lesson = checkNotNull(mutableState.value.lesson)
            val isReinforcement = reinforcement
            val finishedSessionId = sessionId
            val durationMs = clock.millis() - lessonStartedAt
            val results = next.evidence.mapValues { it.value.recentResults }
            viewModelScope.launch {
                library.finishLessonSession(
                    lesson.id, finishedSessionId, results,
                    minOf(next.initialAccuracy, next.challengeAccuracy), next.passed, isReinforcement,
                )
                analytics.track("lesson_complete", mapOf(
                    "scene" to lesson.scene.name,
                    "lesson_id" to lesson.id,
                    "score" to next.initialAccuracy,
                    "challenge_score" to next.challengeAccuracy,
                    "passed" to next.passed,
                    "duration_ms" to durationMs,
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
        reinforcement = true
        sessionId = java.util.UUID.randomUUID().toString()
        lessonStartedAt = clock.millis()
        engine = SceneLessonEngine(retryLesson)
        mutableState.update { it.copy(phase = LessonPhase.PLAYING, lesson = retryLesson, engine = engine!!.start()) }
    }

    fun resumeLesson() {
        val checkpoint = mutableState.value.checkpoint ?: return
        val lesson = SceneLessonCatalog.allLessons.firstOrNull { it.id == checkpoint.lessonId } ?: return
        reinforcement = false
        engine = SceneLessonEngine(lesson)
        lessonStartedAt = checkpoint.startedAt
        sessionId = checkpoint.sessionId
        mutableState.update { it.copy(phase = LessonPhase.PLAYING, lesson = lesson, engine = checkpoint.state) }
    }

    private fun saveCheckpoint() {
        if (reinforcement) return
        val snapshot = mutableState.value
        val checkpoint = com.gojapan.feature.lesson.domain.LessonCheckpoint(
            snapshot.lesson?.id ?: return, snapshot.engine ?: return, lessonStartedAt, sessionId,
        )
        viewModelScope.launch { library.saveLessonCheckpoint(checkpoint) }
    }

    fun backToCatalog() {
        engine = null
        mutableState.update { it.copy(phase = LessonPhase.CATALOG, lesson = null, engine = null) }
    }
}

package com.gojapan.feature.lesson.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LessonCheckpoint(val lessonId: String, val state: LessonEngineState, val startedAt: Long, val sessionId: String = java.util.UUID.randomUUID().toString())

object LessonCheckpointCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(checkpoint: LessonCheckpoint): String = json.encodeToString(checkpoint)
    fun decode(value: String): LessonCheckpoint? = runCatching {
        json.decodeFromString<LessonCheckpoint>(value).takeIf { checkpoint ->
            val lesson = SceneLessonCatalog.allLessons.firstOrNull { it.id == checkpoint.lessonId }
            val state = checkpoint.state
            if (lesson == null || checkpoint.sessionId.isBlank() || checkpoint.startedAt < 0 || state.completed ||
                state.currentIndex !in state.questions.indices) return@takeIf false
            val initial = SceneQuestionFactory.initialQuestions(lesson)
            val retries = initial.map { SceneQuestionFactory.retryQuestion(lesson, it, 1) }
            val appended = state.questions.drop(initial.size)
            val answered = state.questions.take(state.currentIndex + if (state.selectedAnswer != null) 1 else 0)
            state.questions.take(initial.size) == initial && appended.size <= initial.size &&
                appended.all { it in retries } && appended.distinct().size == appended.size &&
                (state.selectedAnswer == null) == (state.lastAnswerCorrect == null) &&
                (state.selectedAnswer == null || (state.selectedAnswer in state.currentQuestion!!.options &&
                    state.lastAnswerCorrect == (state.selectedAnswer == state.currentQuestion!!.correctAnswer))) &&
                state.initialAnswered == answered.count { !it.isRetry && !it.isChallenge } &&
                state.challengeAnswered == answered.count { it.isChallenge } &&
                state.initialCorrect in 0..state.initialAnswered && state.challengeCorrect in 0..state.challengeAnswered &&
                state.evidence.all { (id, evidence) ->
                    id in lesson.expressions.map { it.id } && evidence.expressionId == id &&
                        evidence.replayCount >= 0 && evidence.consecutiveErrors >= 0 && evidence.recentResults.size <= 5
                }
        }
    }.getOrNull()
}

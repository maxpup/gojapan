package com.gojapan.feature.lesson.domain

import org.junit.Assert.*
import org.junit.Test

class LessonCheckpointTest {
    private val lesson = SceneLessonCatalog.allLessons.first()
    @Test fun lastUnansweredQuestionNeverShowsCompleteProgress() {
        val engine = SceneLessonEngine(lesson)
        var state = engine.start()
        assertEquals(0f, state.completionFraction)
        while (state.currentIndex < state.questions.lastIndex) {
            state = engine.advance(engine.answer(state, state.currentQuestion!!.correctAnswer))
        }
        assertTrue(state.completionFraction < 1f)
        state = engine.answer(state, state.currentQuestion!!.correctAnswer)
        assertEquals(1f, state.completionFraction)
    }
    @Test fun wrongFinalAnswerReservesSpaceForRequiredRetry() {
        val engine = SceneLessonEngine(lesson)
        var state = engine.start()
        while (state.currentIndex < state.questions.lastIndex) {
            state = engine.advance(engine.answer(state, state.currentQuestion!!.correctAnswer))
        }
        val wrong = state.currentQuestion!!.options.first { it != state.currentQuestion!!.correctAnswer }
        state = engine.answer(state, wrong)
        assertTrue(state.completionFraction < 1f)
        assertEquals(state.questions.size + 1, state.progressQuestionCount)
    }
    @Test fun checkpointPreservesPendingAnswerAndAddedRetryAcrossRestart() {
        val engine = SceneLessonEngine(lesson)
        var state = engine.start()
        val wrong = state.currentQuestion!!.options.first { it != state.currentQuestion!!.correctAnswer }
        state = engine.advance(engine.answer(state, wrong))
        state = engine.answer(state, state.currentQuestion!!.correctAnswer)
        val checkpoint = LessonCheckpoint(lesson.id, state, 123L)
        val restored = LessonCheckpointCodec.decode(LessonCheckpointCodec.encode(checkpoint))!!
        assertEquals(checkpoint, restored)
        assertEquals(state.answeredCount, restored.state.answeredCount)
        assertEquals(engine.advance(state), engine.advance(restored.state))
        assertTrue(state.questions.last().isRetry)
    }
    @Test fun rejectsSemanticallyCorruptAnswersCountersAndQuestions() {
        val state = SceneLessonEngine(lesson).start()
        val invalid = listOf(
            state.copy(selectedAnswer = "invalid", lastAnswerCorrect = true),
            state.copy(selectedAnswer = state.currentQuestion!!.correctAnswer),
            state.copy(initialAnswered = 1), state.copy(initialCorrect = -1),
            state.copy(questions = state.questions.dropLast(1)),
            state.copy(questions = state.questions.mapIndexed { i, q -> if (i == 0) q.copy(correctAnswer = "wrong") else q }),
        )
        invalid.forEach {
            assertNull(LessonCheckpointCodec.decode(LessonCheckpointCodec.encode(LessonCheckpoint(lesson.id, it, 0))))
        }
    }
    @Test fun rejectsInvalidAndCompletedCheckpoints() {
        val state = SceneLessonEngine(lesson).start()
        assertNull(LessonCheckpointCodec.decode("broken"))
        listOf(state.copy(currentIndex = -1), state.copy(completed = true)).forEach {
            assertNull(LessonCheckpointCodec.decode(LessonCheckpointCodec.encode(LessonCheckpoint(lesson.id, it, 0))))
        }
        assertNull(LessonCheckpointCodec.decode(LessonCheckpointCodec.encode(LessonCheckpoint("missing", state, 0))))
    }
}

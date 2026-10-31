package com.gojapan.feature.lesson.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLessonEngineTest {
    private val lesson = SceneLessonCatalog.transport

    @Test fun `lesson creates twelve questions with four types and two tests per content`() {
        val questions = SceneQuestionFactory.initialQuestions(lesson)
        assertEquals(12, questions.size)
        assertEquals(LessonQuestionType.entries.toSet(), questions.map { it.type }.toSet())
        lesson.expressions.forEach { expression ->
            assertEquals(2, questions.count { it.expressionId == expression.id })
        }
        assertFalse(questions.windowed(4).any { window -> window.map { it.type }.distinct().size == 1 })
        assertTrue(questions.all { it.options.size == 4 && it.options.distinct().size == 4 })
    }

    @Test fun `wrong answer is requeued using a different type`() {
        val engine = SceneLessonEngine(lesson)
        var state = engine.start()
        val original = checkNotNull(state.currentQuestion)
        val wrong = original.options.first { it != original.correctAnswer }
        state = engine.answer(state, wrong)
        state = engine.advance(state)
        val retry = state.questions.last()
        assertTrue(retry.isRetry)
        assertEquals(original.expressionId, retry.expressionId)
        assertTrue(retry.type != original.type)
        assertEquals(false, state.evidence[original.expressionId]?.firstTryCorrect)
        assertEquals(true, state.evidence[original.expressionId]?.enteredRetry)
    }

    @Test fun `perfect run passes initial and final challenge`() {
        val engine = SceneLessonEngine(lesson)
        var state = engine.start()
        while (!state.completed) {
            state = engine.answer(state, checkNotNull(state.currentQuestion).correctAnswer)
            state = engine.advance(state)
        }
        assertEquals(100, state.initialAccuracy)
        assertEquals(100, state.challengeAccuracy)
        assertTrue(state.passed)
        assertEquals(lesson.expressions.size, state.evidence.size)
    }

    @Test fun `listening replay count is retained per expression`() {
        val engine = SceneLessonEngine(lesson)
        var state = engine.start()
        while (state.currentQuestion?.type != LessonQuestionType.LISTENING) {
            state = engine.answer(state, checkNotNull(state.currentQuestion).correctAnswer)
            state = engine.advance(state)
        }
        state = engine.replayAudio(engine.replayAudio(state))
        assertEquals(2, state.evidence[state.currentQuestion!!.expressionId]?.replayCount)
    }
}

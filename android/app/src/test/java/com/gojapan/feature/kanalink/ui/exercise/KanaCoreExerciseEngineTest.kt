package com.gojapan.feature.kanalink.ui.exercise

import com.gojapan.feature.kanalink.data.preferences.KanaSkillDimension
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaCoreExerciseEngineTest {
    private val course = listOf(
        "a", "i", "u", "e", "o", "ka", "ki", "ku", "ke", "ko", "sa", "shi", "su", "se", "so",
        "ta", "chi", "tsu", "te", "to", "na", "ni", "nu", "ne", "no", "n",
    ).mapIndexed { index, id -> item(id, index) }

    @Test fun `factory creates interleaved twelve question three-dimension exercise`() {
        val questions = KanaCoreQuestionFactory.create(course)
        assertEquals(12, questions.size)
        assertEquals(KanaSkillDimension.entries.toSet(), questions.map { it.dimension }.toSet())
        assertTrue(questions.all { it.options.size == 4 && it.options.distinct().size == 4 })
        assertTrue(questions.all { it.correctAnswer in it.options })
        assertFalse(questions.windowed(2).any { it[0].dimension == it[1].dimension })
    }

    @Test fun `answers produce per-kana per-dimension evidence and eighty percent gate`() {
        val engine = KanaCoreExerciseEngine(KanaCoreQuestionFactory.create(course))
        var state = engine.start()
        while (!state.completed) {
            val question = checkNotNull(state.current)
            val answer = if (state.index < 10) question.correctAnswer else question.options.first { it != question.correctAnswer }
            state = engine.advance(engine.answer(state, answer))
        }
        assertEquals(12, state.answers.size)
        assertEquals(83, state.accuracy)
        assertTrue(state.passed)
    }

    @Test fun `answer cannot be submitted twice before advancing`() {
        val engine = KanaCoreExerciseEngine(KanaCoreQuestionFactory.create(course))
        val start = engine.start()
        val answered = engine.answer(start, start.current!!.correctAnswer)
        assertTrue(runCatching { engine.answer(answered, answered.current!!.correctAnswer) }.isFailure)
    }

    private fun item(id: String, order: Int): KanaItem {
        val glyph = when (id) {
            "shi" -> "シ"; "tsu" -> "ツ"; "so" -> "ソ"; "n" -> "ン"
            else -> id.uppercase()
        }
        return KanaItem(KanaId(id), order, KanaGroup.SEION, "row", id, glyph, id, "词$id", "义$id", "audio_$id")
    }
}


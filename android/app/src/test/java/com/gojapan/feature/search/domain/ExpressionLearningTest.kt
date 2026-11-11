package com.gojapan.feature.search.domain

import org.junit.Assert.*
import org.junit.Test

class ExpressionLearningTest {
    private fun expression(id: String) = TravelExpressionSearch.reviewedExpressions.first { it.id == id }
    @Test fun toiletSeparatesWordsAndParticlesWithoutChangingOriginal() {
        val tokens = ExpressionLearning.tokens(expression("toilet"))
        assertEquals(listOf("トイレ", "は", "どこ", "です", "か"), tokens.map { it.surface })
        assertEquals("わ", tokens[1].reading)
        val notes = ExpressionLearning.grammar(expression("toilet"))
        assertTrue(notes.map { it.fragment }.containsAll(listOf("は", "どこ", "ですか")))
        assertTrue(notes.all { it.example.isNotBlank() && it.translation.isNotBlank() })
    }
    @Test fun allTokenOffsetsPointToExactOriginalSpans() {
        TravelExpressionSearch.reviewedExpressions.forEach { expression ->
            val tokens = ExpressionLearning.tokens(expression)
            if (tokens.isNotEmpty()) {
                assertEquals(expression.japanese, tokens.joinToString("") { it.surface })
                tokens.forEach { assertEquals(it.surface, expression.japanese.substring(it.start, it.end)) }
                ExpressionLearning.grammar(expression).forEach { assertTrue(expression.japanese.contains(it.fragment)) }
            }
        }
    }
    @Test fun preservesLongVowelsGeminationAndContractedSounds() {
        assertEquals("チェックイン", ExpressionLearning.tokens(expression("checkin")).first().surface)
        assertEquals("タクシー", ExpressionLearning.tokens(expression("taxi")).single().surface)
        assertEquals("メニュー", ExpressionLearning.tokens(expression("menu")).first().surface)
        assertEquals("病院", ExpressionLearning.tokens(expression("hospital")).first().surface)
    }
    @Test fun unsupportedTextIncludingPunctuationFallsBackWithoutGuessing() {
        val custom = expression("toilet").copy(japanese = "トイレはどこですか？")
        assertTrue(ExpressionLearning.tokens(custom).isEmpty())
        assertTrue(ExpressionLearning.grammar(custom).isEmpty())
        assertTrue(ExpressionLearning.tokens(expression("directions")).isEmpty())
    }
}

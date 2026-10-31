package com.gojapan.feature.lesson.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLessonCatalogTest {
    @Test fun `catalog contains five scenes and twenty five runnable lessons`() {
        assertEquals(TravelScene.entries.toSet(), SceneLessonCatalog.scenes.keys)
        assertTrue(SceneLessonCatalog.scenes.values.all { it.size == 5 })
        assertEquals(25, SceneLessonCatalog.allLessons.size)
        assertEquals(25, SceneLessonCatalog.allLessons.map { it.id }.distinct().size)
    }

    @Test fun `every lesson has six core expressions and four question types`() {
        SceneLessonCatalog.allLessons.forEach { lesson ->
            assertEquals(6, lesson.expressions.size)
            assertEquals(6, lesson.expressions.map { it.id }.distinct().size)
            val questions = SceneQuestionFactory.initialQuestions(lesson)
            assertEquals(12, questions.size)
            assertEquals(LessonQuestionType.entries.toSet(), questions.map { it.type }.toSet())
            assertTrue(questions.all { it.correctAnswer in it.options && it.options.size == 4 })
        }
    }

    @Test fun `every scene expression exists in reviewed search corpus`() {
        assertTrue(SceneLessonCatalog.allLessons.flatMap { it.expressions }.all { expression ->
            expression.japanese.isNotBlank() && expression.kana.isNotBlank() &&
                expression.chinese.isNotBlank() && expression.romaji.isNotBlank()
        })
    }
}

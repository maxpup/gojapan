package com.gojapan.feature.onboarding.domain

import com.gojapan.feature.lesson.domain.TravelScene
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningPathGeneratorTest {
    @Test fun `zero foundation starts with kana even when assessment is correct`() {
        val profile = UserLearningProfile(foundation = JapaneseFoundation.ZERO, assessmentCorrect = true)
        assertEquals(LearningStart.KANA, LearningPathGenerator.start(profile))
    }

    @Test fun `partial foundation can enter scenes after correct assessment`() {
        val profile = UserLearningProfile(foundation = JapaneseFoundation.SOME_KANA, assessmentCorrect = true)
        assertEquals(LearningStart.SCENE, LearningPathGenerator.start(profile))
    }

    @Test fun `skipped partial assessment keeps kana diagnostic path`() {
        val profile = UserLearningProfile(foundation = JapaneseFoundation.SOME_KANA, assessmentCorrect = null)
        assertEquals(LearningStart.KANA, LearningPathGenerator.start(profile))
    }

    @Test fun `mastered kana starts directly in scene courses`() {
        val profile = UserLearningProfile(foundation = JapaneseFoundation.KANA_MASTERED)
        assertEquals(LearningStart.SCENE, LearningPathGenerator.start(profile))
    }

    @Test fun `interested scenes are ordered first without hiding others`() {
        val profile = UserLearningProfile(interestedScenes = setOf(TravelScene.DINING, TravelScene.HOTEL))
        val ordered = LearningPathGenerator.orderedScenes(profile)
        assertEquals(listOf(TravelScene.HOTEL, TravelScene.DINING), ordered.take(2))
        assertEquals(TravelScene.entries.toSet(), ordered.toSet())
    }
}

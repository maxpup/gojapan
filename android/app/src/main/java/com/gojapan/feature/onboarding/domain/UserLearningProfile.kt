package com.gojapan.feature.onboarding.domain

import com.gojapan.feature.lesson.domain.TravelScene

enum class JapaneseFoundation(val label: String) {
    ZERO("完全零基础"),
    SOME_KANA("认识部分五十音"),
    KANA_MASTERED("已掌握五十音"),
}

enum class TravelStatus(val label: String) {
    PLANNING("计划中"),
    IN_JAPAN("已在日本"),
    DAILY_STUDY("日常学习"),
}

enum class LearningStart { KANA, SCENE }

data class UserLearningProfile(
    val completed: Boolean = false,
    val foundation: JapaneseFoundation = JapaneseFoundation.ZERO,
    val travelStatus: TravelStatus = TravelStatus.PLANNING,
    val tripDate: String? = null,
    val interestedScenes: Set<TravelScene> = emptySet(),
    val assessmentCorrect: Boolean? = null,
)

object LearningPathGenerator {
    fun start(profile: UserLearningProfile): LearningStart = when {
        profile.foundation == JapaneseFoundation.KANA_MASTERED -> LearningStart.SCENE
        profile.foundation == JapaneseFoundation.SOME_KANA && profile.assessmentCorrect == true -> LearningStart.SCENE
        else -> LearningStart.KANA
    }

    fun orderedScenes(profile: UserLearningProfile): List<TravelScene> {
        val interested = TravelScene.entries.filter { it in profile.interestedScenes }
        return interested + TravelScene.entries.filter { it !in profile.interestedScenes }
    }
}


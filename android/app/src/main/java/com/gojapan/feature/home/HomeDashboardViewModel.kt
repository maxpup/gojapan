package com.gojapan.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.kanalink.data.preferences.KanaPreferences
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.lesson.domain.SceneLesson
import com.gojapan.feature.lesson.domain.SceneLessonCatalog
import com.gojapan.feature.onboarding.data.UserProfilePreferences
import com.gojapan.feature.onboarding.domain.JapaneseFoundation
import com.gojapan.feature.onboarding.domain.LearningPathGenerator
import com.gojapan.feature.onboarding.domain.TravelStatus
import com.gojapan.feature.onboarding.domain.UserLearningProfile
import com.gojapan.feature.library.data.TravelLibraryPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeDashboardState(
    val loading: Boolean = true,
    val profile: UserLearningProfile = UserLearningProfile(),
    val kanaExplored: Int = 0,
    val sceneLessonsCompleted: Int = 0,
    val dueCount: Int = 0,
    val favoriteCount: Int = 0,
    val recommendedLesson: SceneLesson? = null,
    val tripDaysRemaining: Long? = null,
)

@HiltViewModel
class HomeDashboardViewModel @Inject constructor(
    profilePreferences: UserProfilePreferences,
    learningRepository: LearningRepository,
    kanaPreferences: KanaPreferences,
    travelPreferences: TravelLibraryPreferences,
    private val clock: Clock,
) : ViewModel() {
    private val core = combine(
        profilePreferences.profile,
        learningRepository.observeProgress(),
        kanaPreferences.skillEvidence,
        travelPreferences.state,
    ) { profile, overall, skills, travel ->
        val now = clock.millis()
        val explored = (overall.values.filter { it.seenCount > 0 }.map { it.kanaId } +
            skills.values.filter { it.attempts > 0 }.map { it.kanaId }).distinct().size
        val completedIds = travel.completedLessonIds
        val orderedLessons = LearningPathGenerator.orderedScenes(profile)
            .flatMap { SceneLessonCatalog.scenes.getValue(it) }
        val skillDueIds = skills.values.filter { it.nextReviewAt?.let { due -> due <= now } == true }
            .mapTo(mutableSetOf()) { it.kanaId }
        val overallDueIds = overall.values.filter { it.nextReviewAt?.let { due -> due <= now } == true }
            .mapTo(mutableSetOf()) { it.kanaId } - skillDueIds
        HomeDashboardState(
            loading = false,
            profile = profile,
            kanaExplored = explored,
            sceneLessonsCompleted = completedIds.size.coerceAtMost(SceneLessonCatalog.allLessons.size),
            dueCount = skills.values.count { it.nextReviewAt?.let { due -> due <= now } == true } +
                overallDueIds.size + travel.dueReviewIds.size,
            favoriteCount = travel.favoriteIds.size,
            recommendedLesson = orderedLessons.firstOrNull { it.id !in completedIds } ?: orderedLessons.firstOrNull(),
            tripDaysRemaining = tripDays(profile, now),
        )
    }
    val state: StateFlow<HomeDashboardState> = core.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeDashboardState(),
    )

    companion object {
        fun tripDays(profile: UserLearningProfile, now: Long): Long? {
            if (profile.travelStatus != TravelStatus.PLANNING) return null
            val target = profile.tripDate?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() } ?: return null
            val today = java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            return ChronoUnit.DAYS.between(today, target)
        }

        fun primaryKanaLabel(profile: UserLearningProfile, explored: Int): String = when {
            profile.foundation == JapaneseFoundation.KANA_MASTERED -> "复习五十音"
            explored == 0 -> "开始五十音入门"
            else -> "继续五十音 · $explored/104"
        }
    }
}

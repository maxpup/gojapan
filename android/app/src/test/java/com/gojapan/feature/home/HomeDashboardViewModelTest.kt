package com.gojapan.feature.home

import com.gojapan.feature.onboarding.domain.JapaneseFoundation
import com.gojapan.feature.onboarding.domain.TravelStatus
import com.gojapan.feature.onboarding.domain.UserLearningProfile
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDashboardViewModelTest {
    @Test fun `trip countdown uses local calendar date`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val profile = UserLearningProfile(
            travelStatus = TravelStatus.PLANNING,
            tripDate = today.plusDays(7).toString(),
        )
        assertEquals(7L, HomeDashboardViewModel.tripDays(profile, now))
        assertNull(HomeDashboardViewModel.tripDays(profile.copy(travelStatus = TravelStatus.DAILY_STUDY), now))
    }

    @Test fun `kana primary action reflects foundation and progress`() {
        assertEquals(
            "开始五十音入门",
            HomeDashboardViewModel.primaryKanaLabel(UserLearningProfile(foundation = JapaneseFoundation.ZERO), 0),
        )
        assertEquals(
            "继续五十音 · 12/104",
            HomeDashboardViewModel.primaryKanaLabel(UserLearningProfile(foundation = JapaneseFoundation.SOME_KANA), 12),
        )
        assertEquals(
            "复习五十音",
            HomeDashboardViewModel.primaryKanaLabel(UserLearningProfile(foundation = JapaneseFoundation.KANA_MASTERED), 46),
        )
    }
}

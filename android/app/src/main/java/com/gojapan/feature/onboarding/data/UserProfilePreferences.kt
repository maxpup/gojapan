package com.gojapan.feature.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.gojapan.feature.lesson.domain.TravelScene
import com.gojapan.feature.onboarding.domain.JapaneseFoundation
import com.gojapan.feature.onboarding.domain.TravelStatus
import com.gojapan.feature.onboarding.domain.UserLearningProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserProfilePreferences(private val dataStore: DataStore<Preferences>) {
    val profile: Flow<UserLearningProfile> = dataStore.data.map { values ->
        UserLearningProfile(
            completed = values[COMPLETED] ?: false,
            foundation = values[FOUNDATION]?.let { runCatching { JapaneseFoundation.valueOf(it) }.getOrNull() }
                ?: JapaneseFoundation.ZERO,
            travelStatus = values[TRAVEL_STATUS]?.let { runCatching { TravelStatus.valueOf(it) }.getOrNull() }
                ?: TravelStatus.PLANNING,
            tripDate = values[TRIP_DATE]?.takeIf(String::isNotBlank),
            interestedScenes = values[SCENES].orEmpty().mapNotNullTo(mutableSetOf()) {
                runCatching { TravelScene.valueOf(it) }.getOrNull()
            },
            assessmentCorrect = when (values[ASSESSMENT]) { "correct" -> true; "incorrect" -> false; else -> null },
        )
    }

    suspend fun save(profile: UserLearningProfile) {
        require(profile.interestedScenes.isNotEmpty())
        dataStore.edit { values ->
            values[COMPLETED] = true
            values[FOUNDATION] = profile.foundation.name
            values[TRAVEL_STATUS] = profile.travelStatus.name
            if (profile.tripDate.isNullOrBlank()) values.remove(TRIP_DATE) else values[TRIP_DATE] = profile.tripDate
            values[SCENES] = profile.interestedScenes.mapTo(mutableSetOf()) { it.name }
            values[ASSESSMENT] = when (profile.assessmentCorrect) { true -> "correct"; false -> "incorrect"; null -> "skipped" }
        }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("onboarding_completed")
        val FOUNDATION = stringPreferencesKey("onboarding_foundation")
        val TRAVEL_STATUS = stringPreferencesKey("onboarding_travel_status")
        val TRIP_DATE = stringPreferencesKey("onboarding_trip_date")
        val SCENES = stringSetPreferencesKey("onboarding_scenes")
        val ASSESSMENT = stringPreferencesKey("onboarding_assessment")
    }
}

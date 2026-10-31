package com.gojapan.feature.trip.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gojapan.feature.trip.model.TripPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.tripPlannerDataStore by preferencesDataStore("trip_planner")

@Singleton
class TripPlanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun load(): TripPlan {
        val saved = context.tripPlannerDataStore.data.first()[PLAN]
        saved?.let { runCatching { json.decodeFromString<TripPlan>(it) }.getOrNull() }?.let { return it }
        return loadSeed().also { save(it) }
    }

    suspend fun save(plan: TripPlan) {
        context.tripPlannerDataStore.edit { it[PLAN] = json.encodeToString(plan) }
    }

    suspend fun saveSourceText(text: String): TripPlan = load().copy(sourceText = text).also { save(it) }

    private fun loadSeed(): TripPlan = context.assets
        .open("trips/xiaohongshu_tokyo_guide.json")
        .bufferedReader()
        .use { json.decodeFromString<TripPlan>(it.readText()) }

    private companion object {
        val PLAN = stringPreferencesKey("active_trip_plan")
    }
}

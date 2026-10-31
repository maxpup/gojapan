package com.gojapan.core.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AnalyticsEvent(
    val name: String,
    val timestamp: Long,
    val properties: Map<String, String> = emptyMap(),
)

class AnalyticsTracker(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    val events: Flow<List<AnalyticsEvent>> = dataStore.data.map { values -> decode(values[EVENTS].orEmpty()) }

    suspend fun track(name: String, properties: Map<String, Any?> = emptyMap()) {
        require(name.isNotBlank())
        val event = AnalyticsEvent(
            name,
            clock.millis(),
            properties.mapValues { (_, value) -> value?.toString().orEmpty() },
        )
        dataStore.edit { values ->
            values[EVENTS] = json.encodeToString(AnalyticsLog.append(decode(values[EVENTS].orEmpty()), event))
        }
    }

    private fun decode(raw: String): List<AnalyticsEvent> = if (raw.isBlank()) emptyList() else
        runCatching { json.decodeFromString<List<AnalyticsEvent>>(raw) }.getOrDefault(emptyList())

    private companion object { val EVENTS = stringPreferencesKey("analytics_events_v1") }
}

object AnalyticsLog {
    const val MAX_EVENTS = 1_000
    fun append(existing: List<AnalyticsEvent>, event: AnalyticsEvent, limit: Int = MAX_EVENTS): List<AnalyticsEvent> {
        require(limit > 0)
        return (existing + event).takeLast(limit)
    }
}


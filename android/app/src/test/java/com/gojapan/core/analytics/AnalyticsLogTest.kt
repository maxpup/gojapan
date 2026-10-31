package com.gojapan.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsLogTest {
    @Test fun `append retains newest events in order`() {
        var events = emptyList<AnalyticsEvent>()
        repeat(5) { index -> events = AnalyticsLog.append(events, AnalyticsEvent("event-$index", index.toLong()), limit = 3) }
        assertEquals(listOf("event-2", "event-3", "event-4"), events.map { it.name })
    }

    @Test fun `event supports structured string properties`() {
        val event = AnalyticsEvent("kana_answer", 100L, mapOf("kana" to "し", "result" to "true"))
        assertEquals("し", event.properties["kana"])
        assertTrue(event.timestamp > 0)
    }
}

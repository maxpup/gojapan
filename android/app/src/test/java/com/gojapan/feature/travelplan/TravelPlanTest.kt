package com.gojapan.feature.travelplan

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class TravelPlanTest {
    private val plan = TravelPlan("me", 0, "draft", 0, "日本之旅", "日本", "Asia/Tokyo", "ja", "2026-11-15", "2026-11-15")
    private fun at(value: String) = Clock.fixed(Instant.parse(value), ZoneOffset.UTC)
    @Test fun statusUsesDestinationCalendarIncludingBothBounds() {
        validate(plan)
        assertTrue(plan.summary(at("2026-11-14T14:59:59Z")).endsWith("期待中"))
        assertTrue(plan.summary(at("2026-11-14T15:00:00Z")).endsWith("旅行中"))
        assertTrue(plan.summary(at("2026-11-15T15:00:00Z")).endsWith("已结束"))
        assertTrue(plan.copy(end = null).summary(at("2026-12-01T00:00:00Z")).endsWith("期待中"))
    }
    @Test fun searchedDestinationRequiresValidTimeZone() {
        val selected = plan.copy(destination = "大阪市", zone = "Asia/Tokyo")
        validate(selected)
        assertTrue(runCatching { validate(selected.copy(zone = "")) }.isFailure)
        assertTrue(runCatching { validate(plan.copy(zone = "Europe/Paris")) }.isFailure)
    }
    @Test fun invalidDatesAndBlankNamesRejected() {
        listOf(plan.copy(name = " "), plan.copy(end = "2026-11-14"), plan.copy(start = "2026-02-30")).forEach {
            assertTrue(runCatching { validate(it) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}

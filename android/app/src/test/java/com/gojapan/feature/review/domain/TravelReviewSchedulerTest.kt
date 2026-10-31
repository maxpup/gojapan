package com.gojapan.feature.review.domain

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class TravelReviewSchedulerTest {
    private val scheduler = TravelReviewScheduler()
    private val start = 1_000_000L
    private val initial = TravelMemoryState("toilet", nextReviewAt = start)

    @Test fun `wrong answer returns in ten minutes and resets level`() {
        val old = initial.copy(level = 3)
        val updated = scheduler.complete(old, TravelReviewResult.RELEARN, start)
        assertEquals(0, updated.level)
        assertEquals(start + Duration.ofMinutes(10).toMillis(), updated.nextReviewAt)
        assertEquals(1, updated.wrongCount)
    }

    @Test fun `independent answers progress through configured intervals`() {
        val first = scheduler.complete(initial, TravelReviewResult.INDEPENDENT, start)
        val second = scheduler.complete(first, TravelReviewResult.INDEPENDENT, start)
        assertEquals(0, first.level)
        assertEquals(start + Duration.ofDays(1).toMillis(), first.nextReviewAt)
        assertEquals(1, second.level)
        assertEquals(start + Duration.ofDays(3).toMillis(), second.nextReviewAt)
    }

    @Test fun `difficult keeps level and schedules next day`() {
        val updated = scheduler.complete(initial.copy(level = 2), TravelReviewResult.DIFFICULT, start)
        assertEquals(2, updated.level)
        assertEquals(start + Duration.ofDays(1).toMillis(), updated.nextReviewAt)
    }
}


package com.gojapan.feature.kanalink.domain.scheduler

import com.gojapan.feature.kanalink.domain.model.ItemEvidence
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.KanaProgress
import com.gojapan.feature.kanalink.domain.model.ReviewResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySchedulerTest {
    private val scheduler = MemoryScheduler(RandomSource { 0.0 })
    private val now = 1_800_000_000_000L

    @Test
    fun independentDifficultAndRelearnFollowPrd() {
        assertUpdate(-1, 0, ReviewResult.INDEPENDENT, wrong = 0, hint = false)
        assertUpdate(2, 2, ReviewResult.DIFFICULT, wrong = 1, hint = false)
        assertUpdate(-1, 0, ReviewResult.DIFFICULT, wrong = 0, hint = true)
        assertUpdate(5, 0, ReviewResult.RELEARN, wrong = 2, hint = false)
    }

    @Test
    fun intervalsAreOneThreeSevenFifteenThirtySixtyDays() {
        val expectedDays = listOf(1, 3, 7, 15, 30, 60)
        expectedDays.forEachIndexed { level, days ->
            val update = scheduler.complete(
                level - 1,
                ItemEvidence(KanaId("a"), 0, true, false, 100),
                now,
            )
            assertEquals(level, update.levelAfter)
            assertEquals(now + days * 86_400_000L, update.nextReviewAt)
        }
    }

    @Test
    fun overdueItemsBeatNewAndNewItemsKeepCourseOrder() {
        val course = (0..5).map(::item)
        val progress = mapOf(
            course[5].id to KanaProgress(
                kanaId = course[5].id, level = 2, wrongCount = 1,
                nextReviewAt = now - 86_400_000L,
            ),
        )
        val selected = scheduler.select(course, progress, now, 5)
        assertEquals(course[5].id, selected.first().id)
        assertEquals(listOf(0, 1, 2, 3), selected.drop(1).take(4).map { it.order })
    }

    @Test
    fun progressAndRetentionAreBounded() {
        assertEquals(0.0, scheduler.stageProgress(-1), 0.0)
        assertEquals(1.0, scheduler.stageProgress(5), 0.0)
        val p = KanaProgress(KanaId("a"), level = 2, lastReviewedAt = now - 7 * 86_400_000L)
        assertEquals(0.9, scheduler.predictedRetention(p, now), 0.0001)
        assertTrue(scheduler.predictedRetention(p, now) in 0.0..1.0)
    }

    private fun assertUpdate(old: Int, expected: Int, result: ReviewResult, wrong: Int, hint: Boolean) {
        val update = scheduler.complete(old, ItemEvidence(KanaId("a"), wrong, wrong == 0, hint, null), now)
        assertEquals(result, update.result)
        assertEquals(expected, update.levelAfter)
    }

    private fun item(order: Int) = KanaItem(
        KanaId("id$order"), order, KanaGroup.SEION, "row", "あ", "ア", "a", "朝", "早晨", "kana_a",
    )
}

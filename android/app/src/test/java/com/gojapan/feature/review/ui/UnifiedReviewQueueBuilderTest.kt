package com.gojapan.feature.review.ui

import com.gojapan.feature.kanalink.data.preferences.KanaSkillDimension
import com.gojapan.feature.kanalink.data.preferences.KanaSkillEvidence
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.KanaProgress
import com.gojapan.feature.library.data.TravelLibraryState
import com.gojapan.feature.review.domain.TravelMemoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedReviewQueueBuilderTest {
    private val kana = KanaItem(KanaId("a"), 0, KanaGroup.SEION, "あ行", "あ", "ア", "a", "朝", "早晨", "a")
    private val now = 10_000L

    @Test fun `queue merges due kana skill legacy and travel without duplicate kana`() {
        val skill = KanaSkillEvidence(kana.id, KanaSkillDimension.LISTENING, nextReviewAt = now - 10)
        val overall = KanaProgress(kana.id, level = 0, nextReviewAt = now - 20)
        val travel = TravelLibraryState(
            reviewIds = setOf("toilet"),
            dueReviewIds = setOf("toilet"),
            memory = mapOf("toilet" to TravelMemoryState("toilet", nextReviewAt = now - 30)),
        )
        val queue = UnifiedReviewQueueBuilder.build(
            listOf(kana), mapOf((kana.id to KanaSkillDimension.LISTENING) to skill),
            mapOf(kana.id to overall), travel, now,
        )
        assertEquals(2, queue.size)
        assertTrue(queue.first() is UnifiedReviewItem.Expression)
        assertEquals(1, queue.count { it is UnifiedReviewItem.Kana })
        assertEquals(KanaSkillDimension.LISTENING, (queue.last() as UnifiedReviewItem.Kana).dimension)
    }

    @Test fun `legacy due kana is included when no dimensional item is due`() {
        val overall = KanaProgress(kana.id, level = 0, nextReviewAt = now)
        val queue = UnifiedReviewQueueBuilder.build(listOf(kana), emptyMap(), mapOf(kana.id to overall), TravelLibraryState(), now)
        assertEquals(1, queue.size)
        assertTrue((queue.single() as UnifiedReviewItem.Kana).fromLegacySchedule)
    }

    @Test fun `future and already answered items are excluded`() {
        val future = KanaSkillEvidence(kana.id, KanaSkillDimension.READING, nextReviewAt = now + 1)
        val first = UnifiedReviewQueueBuilder.build(
            listOf(kana), mapOf((kana.id to KanaSkillDimension.READING) to future), emptyMap(), TravelLibraryState(), now,
        )
        assertTrue(first.isEmpty())
        val due = future.copy(nextReviewAt = now)
        val key = UnifiedReviewItem.Kana(kana, KanaSkillDimension.READING, now, false).key
        val second = UnifiedReviewQueueBuilder.build(
            listOf(kana), mapOf((kana.id to KanaSkillDimension.READING) to due), emptyMap(), TravelLibraryState(), now, setOf(key),
        )
        assertTrue(second.isEmpty())
    }
}

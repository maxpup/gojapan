package com.gojapan.feature.library.data

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryCodecTest {
    @Test fun `history round trips deterministically`() {
        val source = mapOf("toilet" to listOf(1L, 2L), "bill" to listOf(3L))
        assertEquals(source, SearchHistoryCodec.decode(SearchHistoryCodec.encode(source)))
    }

    @Test fun `only searches in rolling seven day window remain`() {
        val now = Duration.ofDays(10).toMillis()
        val recent = SearchHistoryCodec.recent(
            listOf(Duration.ofDays(2).toMillis(), Duration.ofDays(4).toMillis(), now + 1),
            now,
        )
        assertEquals(listOf(Duration.ofDays(4).toMillis()), recent)
    }

    @Test fun `malformed history is ignored safely`() {
        assertTrue(SearchHistoryCodec.decode("broken\nvalid:1,nope,2")["valid"] == listOf(1L, 2L))
    }

    @Test fun `frequent collection requires two searches`() {
        val history = mapOf("toilet" to listOf(1L, 2L), "bill" to listOf(3L))
        assertEquals(setOf("toilet"), SearchHistoryCodec.frequentIds(history))
    }
}

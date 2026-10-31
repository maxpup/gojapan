package com.gojapan.feature.library.data

import com.gojapan.feature.review.domain.TravelMemoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelMemoryCodecTest {
    @Test fun `memory state round trips`() {
        val source = mapOf("toilet" to TravelMemoryState("toilet", 2, 3, 1, 100L, 200L))
        assertEquals(source, TravelMemoryCodec.decode(TravelMemoryCodec.encode(source)))
    }

    @Test fun `malformed rows do not block valid memory`() {
        val decoded = TravelMemoryCodec.decode("bad|row\ntoilet|0|1|0||200")
        assertEquals(200L, decoded["toilet"]?.nextReviewAt)
        assertTrue("bad" !in decoded)
    }
}


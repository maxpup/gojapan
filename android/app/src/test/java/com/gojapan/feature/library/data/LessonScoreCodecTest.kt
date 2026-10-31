package com.gojapan.feature.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonScoreCodecTest {
    @Test fun `lesson scores round trip and remain bounded`() {
        val decoded = LessonScoreCodec.decode(LessonScoreCodec.encode(mapOf("station" to 88, "hotel" to 120)))
        assertEquals(88, decoded["station"])
        assertEquals(100, decoded["hotel"])
    }

    @Test fun `malformed score rows are ignored`() {
        val decoded = LessonScoreCodec.decode("bad\nvalid:80\nwrong:nope")
        assertEquals(mapOf("valid" to 80), decoded)
        assertTrue("wrong" !in decoded)
    }
}

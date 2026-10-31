package com.gojapan.feature.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerHistoryCodecTest {
    @Test fun `two errors in latest five create frequent wrong item`() {
        val history = mapOf(
            "exit" to listOf(true, false, true, false, true),
            "station" to listOf(false, true, true, true, true),
        )
        assertEquals(setOf("exit"), AnswerHistoryCodec.frequentWrongIds(history))
    }

    @Test fun `codec retains only latest five valid results`() {
        val decoded = AnswerHistoryCodec.decode("exit:1,0,1,0,1,0\nbad:x,y")
        assertEquals(listOf(false, true, false, true, false), decoded["exit"])
        assertTrue("bad" !in decoded)
        assertEquals(decoded, AnswerHistoryCodec.decode(AnswerHistoryCodec.encode(decoded)))
    }
}

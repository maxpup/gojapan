package com.gojapan.feature.kanalink.data.preferences

import com.gojapan.feature.kanalink.domain.model.KanaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaSkillCodecTest {
    @Test fun `three dimensional evidence round trips`() {
        val source = KanaSkillDimension.entries.mapIndexed { index, dimension ->
            KanaSkillEvidence(KanaId("shi"), dimension, 3, 2, index, 0.4, 100L, 200L)
        }
        assertEquals(source.toSet(), KanaSkillCodec.decode(KanaSkillCodec.encode(source)).toSet())
    }

    @Test fun `malformed rows are ignored and mastery is bounded`() {
        val decoded = KanaSkillCodec.decode("bad\na|READING|1|1|1|9.0||200")
        assertEquals(1.0, decoded.single().mastery, 0.0)
        assertTrue(decoded.single().lastAnsweredAt == null)
    }

    @Test fun `two consecutive correct answers accelerate mastery and wrong answer resets streak`() {
        val initial = KanaSkillEvidence(KanaId("shi"), KanaSkillDimension.GLYPH)
        val first = KanaSkillUpdater.apply(initial, true, 100L)
        val second = KanaSkillUpdater.apply(first, true, 200L)
        val wrong = KanaSkillUpdater.apply(second, false, 300L)
        assertEquals(0.1, first.mastery, 0.0001)
        assertEquals(0.3, second.mastery, 0.0001)
        assertEquals(0, wrong.consecutiveCorrect)
        assertEquals(0.15, wrong.mastery, 0.0001)
        assertEquals(300L + 10 * 60_000L, wrong.nextReviewAt)
    }
}

package com.gojapan.feature.kanalink.ui.tap

import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaTapRoundTest {
    private val items = listOf(item("a", 0), item("i", 1))

    @Test fun `fifth tap disappears item but not before audio-triggering tap`() {
        var state = KanaTapRound.start(items)
        repeat(4) { state = KanaTapRound.tap(state, items.first().id) }
        assertFalse(items.first().id in state.disappeared)
        assertEquals(4, state.count(items.first().id))
        state = KanaTapRound.tap(state, items.first().id)
        assertTrue(items.first().id in state.disappeared)
        assertEquals(5, state.count(items.first().id))
    }

    @Test fun `tapping disappeared item is idempotent`() {
        var state = KanaTapRound.start(items)
        repeat(6) { state = KanaTapRound.tap(state, items.first().id) }
        assertEquals(5, state.count(items.first().id))
        assertEquals(1, state.disappeared.size)
    }

    @Test fun `round completes only when every item disappears and reset restores all`() {
        var state = KanaTapRound.start(items)
        items.forEach { item -> repeat(5) { state = KanaTapRound.tap(state, item.id) } }
        assertTrue(state.completed)
        state = KanaTapRound.reset(state)
        assertFalse(state.completed)
        assertTrue(state.tapCounts.isEmpty())
        assertTrue(state.disappeared.isEmpty())
    }

    private fun item(id: String, order: Int) = KanaItem(
        KanaId(id), order, KanaGroup.SEION, "a", id, id.uppercase(), id, "词", "义", id,
    )
}

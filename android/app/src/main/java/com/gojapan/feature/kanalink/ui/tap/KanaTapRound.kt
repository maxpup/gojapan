package com.gojapan.feature.kanalink.ui.tap

import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem

data class KanaTapRoundState(
    val items: List<KanaItem>,
    val tapCounts: Map<KanaId, Int> = emptyMap(),
    val disappeared: Set<KanaId> = emptySet(),
) {
    val completed: Boolean get() = items.isNotEmpty() && disappeared.size == items.size
    fun count(id: KanaId): Int = tapCounts[id] ?: 0
}

object KanaTapRound {
    const val DISAPPEAR_AT = 5

    fun start(items: List<KanaItem>): KanaTapRoundState {
        require(items.isNotEmpty())
        require(items.map { it.id }.distinct().size == items.size)
        return KanaTapRoundState(items)
    }

    fun tap(state: KanaTapRoundState, id: KanaId): KanaTapRoundState {
        require(state.items.any { it.id == id })
        if (id in state.disappeared) return state
        val count = (state.tapCounts[id] ?: 0) + 1
        return state.copy(
            tapCounts = state.tapCounts + (id to count.coerceAtMost(DISAPPEAR_AT)),
            disappeared = if (count >= DISAPPEAR_AT) state.disappeared + id else state.disappeared,
        )
    }

    fun reset(state: KanaTapRoundState): KanaTapRoundState = start(state.items)
}

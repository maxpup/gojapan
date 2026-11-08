package com.gojapan.feature.trip.ui

import com.gojapan.feature.trip.model.TripDay
import com.gojapan.feature.trip.model.TripPlan
import org.junit.Assert.*
import org.junit.Test

class TripInlineEditingTest {
    @Test fun overviewDeletionOnlyRemovesSelectedSourceEvenWhenTextIsRepeated() {
        val plan = TripPlan(tripTitle = "东京", destination = "东京",
            foodRecommendations = listOf("拉面"),
            days = listOf(
                TripDay("第一天", "散步", "", emptyList(), foodRecommendations = listOf("拉面", "拉面")),
                TripDay("第二天", "逛街", "", emptyList(), foodRecommendations = listOf("拉面")),
            ))
        val entries = plan.noteEntries(TripNoteSection.Food, -1)
        assertEquals(4, entries.size)
        val target = entries[2]
        val result = plan.changeNotes(TripNoteSection.Food, target.dayIndex) {
            it.filterIndexed { index, _ -> index != target.itemIndex }
        }
        assertEquals(listOf("拉面"), result.foodRecommendations)
        assertEquals(listOf("拉面"), result.days[0].foodRecommendations)
        assertEquals(plan.days[1], result.days[1])
        assertEquals(4, plan.noteEntries(TripNoteSection.Food, -1).size)
    }
}

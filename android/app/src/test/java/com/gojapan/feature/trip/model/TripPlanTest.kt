package com.gojapan.feature.trip.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripPlanTest {
    @Test
    fun `deepseek json with null coordinates parses`() {
        val plan = Json.decodeFromString<TripPlan>(
            """{"tripTitle":"东京","destination":"东京","days":[{"date":"第 1 天","title":"散步","summary":"慢游","stops":[{"name":"浅草寺","latitude":null,"longitude":null}]}]}""",
        )
        assertEquals("浅草寺", plan.days.single().stops.single().name)
        assertNull(plan.days.single().stops.single().latitude)
        assertEquals(emptyList<String>(), plan.foodRecommendations)
        assertEquals(emptyList<String>(), plan.days.single().foodRecommendations)
        assertEquals(emptyList<String>(), plan.precautions)
        assertEquals(emptyList<String>(), plan.days.single().packingItems)
    }
    @Test
    fun `trip preparation survives persistence`() {
        val plan = TripPlan(
            tripTitle = "日本东京之旅", destination = "东京",
            precautions = listOf("确认天气"), packingItems = listOf("雨具"),
            foodRecommendations = listOf("午餐：拉面"),
            days = listOf(TripDay("第 1 天", "散步", "慢游", emptyList(),
                precautions = listOf("预留休息时间"), packingItems = listOf("饮用水"), foodRecommendations = listOf("晚餐：寿司"))),
        )
        assertEquals(plan, Json.decodeFromString<TripPlan>(Json.encodeToString(TripPlan.serializer(), plan)))
    }
}

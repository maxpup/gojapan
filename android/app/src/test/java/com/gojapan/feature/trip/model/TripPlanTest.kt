package com.gojapan.feature.trip.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripPlanTest {
    @Test fun guideWeekdaysHandleImportedLabelsAndYearBoundaries() {
        val start = "2026-12-31"
        assertEquals("2027-01-01", resolveGuideDate("Day 2｜1.1 新年", start).toString())
        assertEquals("2027-01-01", resolveGuideDate("第二天", start).toString())
        assertEquals("2026-11-21", resolveGuideDate("11月21日", "2026-11-20").toString())
        assertEquals("2026-11-21", resolveGuideDate("2026年11月21日", null).toString())
        assertNull(resolveGuideDate("第 1 天", null))
        assertNull(resolveGuideDate("2026-02-30", start))
    }

    @Test fun overviewImportsWithoutStopsAndKeepsExistingRoute() {
        val day = TripDay("第 1 天", "原主题", "", listOf(TripStop("浅草寺")))
        val old = TripPlan(tripTitle = "原计划", destination = "东京", days = listOf(day))
        val general = TripPlan(tripTitle = "", destination = "", days = emptyList(),
            overview = "东京赏枫", dateRange = "11月21日至29日")
        val preview = old.previewImport(general)
        assertEquals(old.copy(overview = general.overview, dateRange = general.dateRange), preview.plan)
        assertEquals(2, preview.changes.size)
        val summaryDay = day.copy(title = "浅草散步", summary = "上午参拜", stops = emptyList())
        val updated = preview.plan.previewImport(general.copy(days = listOf(summaryDay))).plan
        assertEquals(summaryDay.copy(stops = day.stops), updated.days.single())
        assertEquals(emptyList<String>(), updated.previewImport(general).changes)
        assertEquals(updated, Json.decodeFromString<TripPlan>(Json.encodeToString(TripPlan.serializer(), updated)))
    }

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

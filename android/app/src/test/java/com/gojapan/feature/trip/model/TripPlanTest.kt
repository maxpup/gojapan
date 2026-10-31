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
    }
}

package com.gojapan.feature.trip.ui

import com.gojapan.feature.trip.model.TripDay
import com.gojapan.feature.trip.model.TripPlan
import com.gojapan.feature.trip.model.TripStop
import org.junit.Assert.*
import org.junit.Test

class TripEditorTest {
    private val plan = TripPlan(
        tripTitle = "东京之旅", destination = "东京",
        days = listOf(TripDay("11月21日", "散步", "", listOf(TripStop("浅草寺")))),
    )

    @Test fun validDraftAndEmptyItineraryCanBeSaved() {
        assertNull(plan.editValidationError())
        assertNull(plan.copy(days = emptyList()).editValidationError())
    }

    @Test fun incompleteDraftCannotBeSaved() {
        assertNotNull(plan.copy(tripTitle = " ").editValidationError())
        assertNotNull(plan.copy(destination = "").editValidationError())
        assertNotNull(plan.copy(days = listOf(plan.days.first().copy(date = ""))).editValidationError())
        assertNotNull(plan.copy(days = listOf(plan.days.first().copy(stops = listOf(TripStop(""))))).editValidationError())
        assertNotNull(plan.copy(foodRecommendations = listOf(" ")).editValidationError())
        assertNotNull(plan.copy(days = listOf(plan.days.first().copy(packingItems = listOf("")))).editValidationError())
    }

    @Test fun renamingPlaceClearsOldCoordinatesButPreservesSchedule() {
        val stop = TripStop("浅草寺", time = "09:00", note = "散步", latitude = 35.7, longitude = 139.8)
        assertEquals(stop, stop.renameForEdit("浅草寺"))
        val renamed = stop.renameForEdit("东京站")
        assertNull(renamed.latitude)
        assertNull(renamed.longitude)
        assertEquals("09:00", renamed.time)
        assertEquals("散步", renamed.note)
        assertEquals("浅草寺", stop.name)
    }
}

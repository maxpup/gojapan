package com.gojapan.feature.expense

import com.gojapan.feature.travelplan.TravelPlan
import org.junit.Assert.*
import org.junit.Test

class ExpenseTest {
    private val plan = TravelPlan("local", 7, "request", 0, "旅行", "日本", "Asia/Tokyo", "ja", null, null)
    private val draft = Draft(day = "2026-10-01", purpose = "午餐", category = "餐饮", amount = "12.05", currency = "CNY")

    @Test fun storesExactMinorUnitsAndPreservesDraftIdentity() {
        val row = draft.record(plan)
        assertEquals(1205L, row.minor)
        assertEquals(7L, row.planId)
        assertEquals(draft, row.draft())
        assertEquals(row, draft.record(plan))
    }

    @Test fun invalidRowsCannotBecomeRecords() {
        listOf(draft.copy(currency = ""), draft.copy(currency = "JPY"), draft.copy(amount = "0"),
            draft.copy(amount = "-1"), draft.copy(day = "2026-02-30"), draft.copy(purpose = " ")).forEach {
            assertTrue(runCatching { it.record(plan) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}

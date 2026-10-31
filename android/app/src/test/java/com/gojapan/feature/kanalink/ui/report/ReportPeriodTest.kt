package com.gojapan.feature.kanalink.ui.report

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReportPeriodTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 8, 23, 18, 0).atZone(zone).toInstant().toEpochMilli() // Sunday

    @Test fun dayUsesLocalNaturalDay() {
        val range = ReportPeriod.DAY.range(now, zone)
        assertEquals("2026-08-23T00:00+08:00[Asia/Shanghai]", at(range.start))
        assertEquals("2026-08-24T00:00+08:00[Asia/Shanghai]", at(range.end))
    }

    @Test fun weekStartsOnMonday() {
        val range = ReportPeriod.WEEK.range(now, zone)
        assertEquals("2026-08-17T00:00+08:00[Asia/Shanghai]", at(range.start))
        assertEquals("2026-08-24T00:00+08:00[Asia/Shanghai]", at(range.end))
        assertEquals("2026-08-10T00:00+08:00[Asia/Shanghai]", at(range.previousStart))
    }

    @Test fun monthUsesCalendarMonth() {
        val range = ReportPeriod.MONTH.range(now, zone)
        assertEquals("2026-08-01T00:00+08:00[Asia/Shanghai]", at(range.start))
        assertEquals("2026-09-01T00:00+08:00[Asia/Shanghai]", at(range.end))
    }

    private fun at(epoch: Long) = java.time.Instant.ofEpochMilli(epoch).atZone(zone).toString()
}

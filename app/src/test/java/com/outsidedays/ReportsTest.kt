package com.outsidedays

import com.outsidedays.core.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ReportsTest {
    private val zone = ZoneId.of("UTC")
    private fun span(a: String, b: String) = Span(Instant.parse(a).toEpochMilli(), Instant.parse(b).toEpochMilli())
    @Test fun splitsMidnightMonthAndYear() {
        val spans = listOf(span("2024-12-31T23:30:00Z", "2025-01-01T01:00:00Z"))
        val totals = Reports.dayTotals(spans, zone)
        assertEquals(1_800_000L, totals[LocalDate.of(2024,12,31)])
        assertEquals(3_600_000L, totals[LocalDate.of(2025,1,1)])
        assertEquals(1_800_000L, Reports.between(spans, LocalDate.of(2024,1,1), LocalDate.of(2025,1,1), zone))
        assertEquals(3_600_000L, Reports.between(spans, LocalDate.of(2025,1,1), LocalDate.of(2025,2,1), zone))
    }
    @Test fun weekBoundaryAndFirstDay() {
        val spans = listOf(span("2025-01-05T23:00:00Z", "2025-01-06T01:00:00Z"))
        val monday = Reports.weekStart(LocalDate.of(2025,1,6), 1)
        assertEquals(LocalDate.of(2025,1,6), monday)
        assertEquals(3_600_000L, Reports.between(spans, monday, monday.plusWeeks(1), zone))
        assertEquals(LocalDate.of(2025,1,5), Reports.weekStart(monday, 7))
    }
    @Test fun pausesAndOverlapsAreNotCountedTwice() {
        val spans = listOf(span("2025-01-01T10:00:00Z","2025-01-01T10:30:00Z"),span("2025-01-01T10:15:00Z","2025-01-01T10:45:00Z"),span("2025-01-01T11:00:00Z","2025-01-01T11:15:00Z"))
        assertEquals(3_600_000L, Reports.dayTotals(spans,zone).values.single())
    }
    @Test fun daylightSavingDayUsesRealElapsedHours() {
        val ny = ZoneId.of("America/New_York"); val date = LocalDate.of(2025,3,9)
        val spans = listOf(Span(date.atStartOfDay(ny).toInstant().toEpochMilli(),date.plusDays(1).atStartOfDay(ny).toInstant().toEpochMilli()))
        assertEquals(23 * 3_600_000L, Reports.dayTotals(spans, ny)[date])
    }
    @Test fun fixedTimezoneKeepsHistoryStableDuringTravel() {
        val spans = listOf(span("2025-02-01T00:30:00Z","2025-02-01T01:00:00Z"))
        val savedZone = ZoneId.of("America/Los_Angeles")
        assertEquals(setOf(LocalDate.of(2025,1,31)), Reports.dayTotals(spans, savedZone).keys)
    }
    @Test fun yesterdayStreakAllowsTodayToBeQuiet() {
        val today = LocalDate.of(2025,1,5)
        assertEquals(2, Reports.streak(mapOf(today.minusDays(1) to 1000L, today.minusDays(2) to 1000L),today))
        assertEquals(0, Reports.streak(emptyMap(), today))
    }
}

package com.phonemood.report
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class DayWindowTest {
    @Test fun `spring forward is a 23 hour reporting day`() { val b = DayWindow.bounds(LocalDate.parse("2026-03-08"), ZoneId.of("America/Los_Angeles")); assertEquals(23 * 3_600_000L, b.last + 1 - b.first) }
    @Test fun `fall back is a 25 hour reporting day`() { val b = DayWindow.bounds(LocalDate.parse("2026-11-01"), ZoneId.of("America/Los_Angeles")); assertEquals(25 * 3_600_000L, b.last + 1 - b.first) }
    @Test fun `cross-midnight segment is split without double counting`() { val a = DayWindow.bounds(LocalDate.parse("2026-09-05"), ZoneId.of("UTC")); val b = DayWindow.bounds(LocalDate.parse("2026-09-06"), ZoneId.of("UTC")); val start = a.last + 1 - 60_000; val end = b.first + 120_000; assertEquals(60_000L, DayWindow.overlap(start, end, a)); assertEquals(120_000L, DayWindow.overlap(start, end, b)) }
    @Test fun `disjoint segment contributes zero`() { assertEquals(0L, DayWindow.overlap(0, 100, 200L..300L)) }
}

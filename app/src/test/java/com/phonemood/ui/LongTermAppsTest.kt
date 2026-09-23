package com.phonemood.ui

import com.phonemood.analysis.*
import org.junit.Assert.assertEquals
import org.junit.Test

class LongTermAppsTest {
    private fun day(date: String, apps: Map<String, Long>) = DayData(date, 0, 1, false, true, apps.values.sum(), apps, emptyList(), 1)
    private fun app(id: String, status: String) = Finding("app:$id", "app:$id", "APP_USAGE", id, status, templateKey = status)

    @Test fun clearAppsAreOrderedByTotalUseAndExcludeUnclearResults() {
        val facts = Facts(0, "UTC", 0, 0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val data = PeriodDataset(facts, 2, 0, 1, listOf(
            day("2026-09-01", mapOf("a" to 60_000L, "b" to 600_000L, "flat" to 9_000_000L)),
            day("2026-09-02", mapOf("a" to 60_000L, "c" to 600_000L, "d" to 900_000L))), emptyList(), emptyList(), emptyList(), 0)
        val stats = Statistics(models = emptyList(), topAppIds = emptyList(), findings = listOf(
            app("a", "EARLY_HIGHER"), app("b", "EARLY_LOWER"), app("c", "EARLY_HIGHER"), app("d", "EARLY_LOWER"),
            app("flat", "NO_NOTICEABLE_TENDENCY"), app("mixed", "MIXED_TENDENCY"), app("rare", "INSUFFICIENT_DATA"),
            Finding("phone_mood", "phone_mood", "PHONE_USAGE", status = "EARLY_HIGHER", templateKey = "x")))
        assertEquals(listOf("d", "b", "c", "a"), clearAppFindings(stats, data).map { it.appId })
    }
}

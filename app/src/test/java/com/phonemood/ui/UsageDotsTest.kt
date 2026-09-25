package com.phonemood.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageDotsTest {
    private val hour = 3_600_000L

    @Test fun aDayUsesA24HourSquareAndGroupsAfterFiveApps() {
        val model = usageDotModel((1..6).associate { "app$it" to hour }, 1)
        assertEquals(64, model.groups.sumOf { it.dots } + model.unusedDots)
        assertEquals(16, model.groups.sumOf { it.dots })
        assertEquals(6, model.groups.size)
        assertEquals(hour, model.groups.last().durationMs)
    }

    @Test fun longerPeriodsShowAverageDayWithoutGrowingTheGrid() {
        val model = usageDotModel(mapOf("app" to 30 * 6 * hour), 30)
        assertEquals(16, model.groups.single().dots)
        assertEquals(48, model.unusedDots)
        assertEquals(180 * hour, model.totalMs)
    }

    @Test fun unexpectedOverlappingUsageStillFitsTheSquare() {
        val model = usageDotModel(mapOf("a" to 20 * hour, "b" to 20 * hour), 1)
        assertEquals(64, model.groups.sumOf { it.dots })
        assertEquals(0, model.unusedDots)
    }

    @Test fun visibleAppsHaveDistinctColorsAndDescendingContiguousBlocks() {
        val apps = (1..8).associate { "app$it" to (9 - it) * hour }
        val model = usageDotModel(apps, 3)
        assertEquals(listOf("app1", "app2", "__other__", "app3", "app4", "app5"), model.groups.map { it.id })
        assertEquals(5, model.groups.filter { it.id != "__other__" }.map { it.paletteIndex }.distinct().size)
        assertEquals(-1, model.groups[2].paletteIndex)
    }
}

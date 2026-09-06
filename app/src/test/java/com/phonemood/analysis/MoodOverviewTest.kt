package com.phonemood.analysis

import com.phonemood.settings.Configuration
import org.junit.Assert.*
import org.junit.Test

class MoodOverviewTest {
    private fun day(index:Int,scores:List<Int>)=DayData("2026-09-0$index",0,1,false,true,0,emptyMap(),scores,1)
    @Test fun missingDaysAreNotZeroAndDaysAreEquallyWeighted() {
        val result=moodOverview(listOf(day(1,listOf(2)),day(2,emptyList()),day(3,listOf(8,8,8))))
        assertEquals(5.0,result.dailyMean!!,0.001)
        assertEquals(2,result.ratedDays)
        assertNull(result.firstHalf)
    }
    @Test fun comparisonRequiresTwoRatedDaysInEachCalendarHalf() {
        val result=moodOverview(listOf(day(1,listOf(2)),day(2,listOf(4)),day(3,listOf(6)),day(4,listOf(8))))
        assertEquals(3.0,result.firstHalf!!,0.001)
        assertEquals(7.0,result.secondHalf!!,0.001)
        assertNull(moodOverview(listOf(day(1,listOf(2)),day(2,emptyList()),day(3,listOf(6)),day(4,listOf(8)))).firstHalf)
    }
    @Test fun emptyPeriodHasNoInventedAverage() { assertNull(moodOverview(listOf(day(1,emptyList()))).dailyMean) }
    @Test fun newInstallUsesFifteenMinuteReminders() { assertEquals(15,Configuration().interval) }
}

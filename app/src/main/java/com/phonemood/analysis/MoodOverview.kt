package com.phonemood.analysis

/** Descriptive calendar-day means; missing days never become zero mood scores. */
data class MoodOverview(val ratedDays: Int, val ratings: Int, val dailyMean: Double?, val firstHalf: Double?, val secondHalf: Double?)
fun moodOverview(days: List<DayData>): MoodOverview {
    val sorted=days.sortedBy { it.date }
    val means=sorted.map { d -> d.scores.takeIf { it.isNotEmpty() }?.average() }
    val middle=sorted.size/2
    val first=means.take(middle).filterNotNull()
    val second=means.drop(middle).filterNotNull()
    val comparable=first.size>=2 && second.size>=2
    return MoodOverview(means.count { it!=null },sorted.sumOf { it.scores.size },means.filterNotNull().takeIf { it.isNotEmpty() }?.average(),
        if(comparable) first.average() else null,if(comparable) second.average() else null)
}

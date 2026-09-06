package com.phonemood.analysis

import androidx.room.withTransaction
import com.phonemood.data.*
import kotlinx.coroutines.sync.withLock
import java.time.*

object AnalysisPolicy {
    const val VERSION = "1.0"
    const val WINDOW = 30 * 60_000L
    const val MAX_PAIR = 120 * 60_000L
    const val MAX_LATENCY = 5 * 60_000L
    const val MIN_RATINGS = 20
    const val NEAR_ZERO = .2
    const val MIN_APP_EXPOSED = 5
    const val MIN_APP_COMPARISONS = 5
    const val RANK_TOLERANCE = 1e-9
}

data class Facts(val asOf: Long, val zone: String, val revision: Long, val firstStarted: Long?,
    val segments: List<UsageSegment>, val sessions: List<PhoneSession>, val checkpoints: List<MoodCheckpoint>,
    val responses: List<MoodResponse>, val prompts: List<MoodPromptState>, val configurations: List<ConfigurationEvent>,
    val gaps: List<MonitoringGap>, val evidence: List<UsageCoverageEvidence>)

/** A short coherent read; no model fitting is performed under the repository lock. */
suspend fun Repository.analysisFacts(days: Int, now: Long = System.currentTimeMillis(), endDate: LocalDate? = null): Facts = mutex.withLock {
    require(days in listOf(1, 7, 30))
    db.withTransaction {
        val state = dao.state()
        val zone = dao.firstStart()?.zoneId ?: ZoneId.systemDefault().id
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.of(zone)).toLocalDate()
        val last = endDate ?: today
        require(last <= today)
        val start = last.minusDays(days - 1L).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli()
        val end = minOf(now, last.plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli())
        val answers = (dao.responsesIn(start, end) + listOfNotNull(dao.responseBefore(start)).filter { it.responseTimestampUtc >= start - AnalysisPolicy.MAX_PAIR }).distinctBy { it.checkpointId }
        val checks = (dao.checkpointsIn(start, end) + answers.mapNotNull { dao.checkpoint(it.checkpointId) }).distinctBy { it.checkpointId }
        val sessions = checks.map { it.sessionId }.distinct().chunked(500).flatMap { dao.sessionsByIds(it) }
        // Include period sessions without a mood response as well.
        val periodSegments = dao.segmentsIn(start - AnalysisPolicy.MAX_PAIR, end)
        val allSessions = (sessions + periodSegments.map { it.sessionId }.distinct().chunked(500).flatMap { dao.sessionsByIds(it) }).distinctBy { it.sessionId }
        val earliest = minOf(start - AnalysisPolicy.MAX_PAIR, allSessions.minOfOrNull { it.startUtc } ?: start)
        Facts(now, zone, state?.sourceRevision ?: 0, state?.firstStartedUtc, dao.segmentsIn(earliest, end), allSessions, checks,
            answers, checks.map { it.checkpointId }.chunked(500).flatMap { dao.promptsByIds(it) }, dao.configurations(), dao.gaps(), dao.coverage(earliest, end))
    }
}

data class Coverage(val start: Long, val end: Long, val state: String, val reason: String)
data class DayData(val date: String, val start: Long, val end: Long, val ongoing: Boolean, val complete: Boolean,
    val activeMs: Long, val apps: Map<String, Long>, val scores: List<Int>, val verifiedMs: Long)
data class MoodRow(val id: String, val sessionId: String, val at: Long, val day: String, val score: Int,
    val windowStart: Long, val phoneMs: Long, val apps: Map<String, Long>, val complete: Boolean,
    val sessionMs: Long, val sessionComplete: Boolean, val reasons: List<String>)
data class Transition(val id: String, val startId: String, val endId: String, val sessionId: String,
    val start: Long, val end: Long, val startScore: Int, val endScore: Int, val day: String,
    val phoneMs: Long, val apps: Map<String, Long>, val reasons: List<String>) {
    val usable get() = reasons.isEmpty()
    val elapsedMinutes get() = (end - start) / 60000.0
}
data class PeriodDataset(val facts: Facts, val days: Int, val start: Long, val end: Long, val daily: List<DayData>,
    val coverage: List<Coverage>, val rows: List<MoodRow>, val transitions: List<Transition>, val contextStart: Long) {
    val validRatings get() = rows.count { it.reasons.isEmpty() }
    val names get() = facts.segments.associate { it.packageName to it.appName }
}

fun overlap(start: Long, end: Long, low: Long, high: Long) = (minOf(end, high) - maxOf(start, low)).coerceAtLeast(0)
fun appDurations(segments: List<UsageSegment>, start: Long, end: Long): Map<String, Long> = segments.asSequence()
    .filter { it.startUtc < end && it.endUtc > start }.groupBy { it.packageName }
    .mapValues { (_, values) -> values.sumOf { overlap(it.startUtc, it.endUtc, start, end) } }.filterValues { it > 0 }.toSortedMap()

/** Integrated endpoint sweep: range queries avoid rescanning every usage segment per answer. */
private class UsageIntegral(segments: List<UsageSegment>) {
    private val points: List<Long>
    private val areas: LongArray
    private val counts: IntArray
    init {
        val deltas=sortedMapOf<Long,Int>()
        segments.forEach { s ->
            deltas[s.startUtc]=(deltas[s.startUtc] ?: 0)+1
            deltas[s.endUtc]=(deltas[s.endUtc] ?: 0)-1
        }
        points=deltas.keys.toList();areas=LongArray(points.size);counts=IntArray(points.size)
        for(i in points.indices) {
            if(i>0) { areas[i]=areas[i-1]+(points[i]-points[i-1])*counts[i-1];counts[i]=counts[i-1] }
            counts[i]+=deltas.getValue(points[i])
        }
    }
    private fun before(at: Long): Long {
        var lo=0;var hi=points.size
        while(lo<hi) { val mid=(lo+hi)/2;if(points[mid]<=at) lo=mid+1 else hi=mid }
        val i=lo-1
        return if(i<0) 0 else areas[i]+(at-points[i])*counts[i]
    }
    fun between(a: Long,b: Long)=if(b<=a) 0L else before(b)-before(a)
}

object PeriodDatasetBuilder {
    fun build(f: Facts, days: Int, endDate: LocalDate? = null): PeriodDataset {
        require(days in listOf(1, 7, 30))
        val zone = ZoneId.of(f.zone)
        val today = Instant.ofEpochMilli(f.asOf).atZone(zone).toLocalDate()
        val last = endDate ?: today
        require(last <= today)
        val end = minOf(f.asOf, last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val date = last.minusDays(days - 1L)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val checks = f.checkpoints.associateBy { it.checkpointId }
        val appIndex=f.segments.groupBy { it.packageName }.toSortedMap().mapValues { UsageIntegral(it.value) }
        val sessionIndex=f.segments.groupBy { it.sessionId }.mapValues { UsageIntegral(it.value) }
        fun usage(a: Long,b: Long)=appIndex.mapValues { it.value.between(a,b) }.filterValues { it>0 }
        val sessionStart = f.sessions.associate { it.sessionId to it.startUtc }
        val answers = f.responses.filter { it.responseTimestampUtc < end && it.checkpointId in checks }.sortedWith(compareBy<MoodResponse> { it.responseTimestampUtc }.thenBy { it.checkpointId })
        val coverageStart = minOf(start - AnalysisPolicy.MAX_PAIR, f.sessions.minOfOrNull { it.startUtc } ?: start)
        val coverage = resolveCoverage(f, coverageStart, end)
        fun complete(a: Long, b: Long) = b >= a && coverage.filter { it.state == "VERIFIED" }.sumOf { overlap(it.start, it.end, a, b) } == b - a
        fun reasons(a: MoodResponse): List<String> {
            val latency = a.responseTimestampUtc - checks.getValue(a.checkpointId).promptTimestampUtc
            return buildList {
                if (latency < 0) add("CLOCK_ANOMALY")
                if (latency > AnalysisPolicy.MAX_LATENCY) add("LATE_RESPONSE")
                if (a.score !in 1..10) add("INVALID_SCORE")
            }
        }
        val rows = answers.filter { it.responseTimestampUtc >= start }.map { a ->
            val check = checks.getValue(a.checkpointId)
            val at = a.responseTimestampUtc
            val apps = usage(at - AnalysisPolicy.WINDOW, at)
            val beginning = sessionStart[check.sessionId] ?: at
            val windowComplete = complete(at - AnalysisPolicy.WINDOW, at)
            MoodRow(a.checkpointId, check.sessionId, at, Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toString(), a.score,
                at - AnalysisPolicy.WINDOW, apps.values.sum(), apps, windowComplete,
                sessionIndex[check.sessionId]?.between(beginning,at) ?: 0,
                check.sessionId in sessionStart && complete(beginning, at), reasons(a))
        }
        val transitions = answers.zipWithNext().filter { (_, b) -> b.responseTimestampUtc >= start }.map { (a, b) ->
            val ca = checks.getValue(a.checkpointId); val cb = checks.getValue(b.checkpointId)
            val from = a.responseTimestampUtc; val to = b.responseTimestampUtc
            val issues = (reasons(a) + reasons(b) + buildList {
                if (ca.sessionId != cb.sessionId) add("DIFFERENT_SESSION")
                if (to <= from) add("CLOCK_ANOMALY")
                if (to - from > AnalysisPolicy.MAX_PAIR) add("PAIR_TOO_LONG")
                if (!complete(from, to)) add("INCOMPLETE_COVERAGE")
                if (f.configurations.any { it.timestampUtc in (from + 1)..to && it.setting in setOf("excludedPackages", "moodIntervalMinutes", "sessionResetMinutes", "monitoringEnabled") }) add("CONFIGURATION_CHANGED")
            }).distinct()
            val apps = usage(from, to)
            Transition("${a.checkpointId}->${b.checkpointId}", a.checkpointId, b.checkpointId, cb.sessionId, from, to,
                a.score, b.score, Instant.ofEpochMilli(to).atZone(zone).toLocalDate().toString(), apps.values.sum(), apps,
                issues + if (apps.values.sum() > to - from) listOf("OVERLAPPING_USAGE") else emptyList())
        }
        val daily = (0 until days).map { i ->
            val d = date.plusDays(i.toLong()); val a = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val b = minOf(d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), f.asOf)
            val apps = usage(a, b)
            DayData(d.toString(), a, b, d == today, complete(a, b), apps.values.sum(), apps,
                rows.filter { it.day == d.toString() && it.score in 1..10 }.map { it.score },
                coverage.filter { it.state == "VERIFIED" }.sumOf { overlap(it.start, it.end, a, b) })
        }
        val contextStart = minOf(start, rows.minOfOrNull { it.windowStart } ?: start,
            transitions.filter { it.start < start && it.end - it.start <= AnalysisPolicy.MAX_PAIR }.minOfOrNull { it.start } ?: start)
        return PeriodDataset(f, days, start, end, daily, coverage, rows, transitions, contextStart)
    }

    fun resolveCoverage(f: Facts, start: Long, end: Long): List<Coverage> {
        val changes = f.configurations.filter { it.setting == "monitoringEnabled" }.sortedBy { it.timestampUtc }
        val points = (listOf(start, end) + listOfNotNull(f.firstStarted) + changes.map { it.timestampUtc } +
            f.gaps.flatMap { listOf(it.startUtc, it.endUtc) } + f.evidence.flatMap { listOf(it.startUtc, it.endUtc) }).filter { it in start..end }.distinct().sorted()
        val result = mutableListOf<Coverage>()
        for ((a,b) in points.zipWithNext()) {
            if (a == b) continue
            val enabled = f.firstStarted != null && a >= f.firstStarted && (changes.lastOrNull { it.timestampUtc <= a }?.newValue?.toBooleanStrictOrNull() ?: true)
            val gap = f.gaps.firstOrNull { it.startUtc < b && it.endUtc > a }
            val state = when { !enabled -> "NOT_MONITORED"; gap != null -> "UNKNOWN"; f.evidence.any { it.startUtc <= a && it.endUtc >= b } -> "VERIFIED"; else -> "UNKNOWN" }
            val reason = when { !enabled -> "NOT_MONITORED"; gap != null -> gap.reason; state == "VERIFIED" -> "ANCHORED_QUERY_CHAIN_V1"; else -> "NO_COVERAGE_EVIDENCE" }
            val last = result.lastOrNull()
            if (last?.state == state && last.reason == reason && last.end == a) result[result.lastIndex] = last.copy(end = b)
            else result += Coverage(a,b,state,reason)
        }
        return result
    }
}

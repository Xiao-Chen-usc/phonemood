package com.phonemood.data

import android.content.Context
import androidx.room.withTransaction
import com.phonemood.monitoring.*
import com.phonemood.settings.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

class Repository(val context: Context, val db: PhoneMoodDatabase, val settings: SettingsStore) {
    val dao = db.dao()
    val mutex = Mutex()
    private val engine = SessionEngine()
    suspend fun configuration(): Configuration {
        val saved = settings.flow.first()
        val history = dao.configurations().associateBy { it.setting }
        // Room is authoritative if the process died between the Room and DataStore commits.
        return saved.copy(overlayEnabled = history["overlayEnabled"]?.newValue?.toBooleanStrictOrNull() ?: saved.overlayEnabled, enabled = dao.state()?.monitoringEnabled ?: saved.enabled,
            interval = history["moodIntervalMinutes"]?.newValue?.toInt() ?: saved.interval,
            reset = history["sessionResetMinutes"]?.newValue?.toInt() ?: saved.reset,
            excluded = history["excludedPackages"]?.newValue?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: saved.excluded)
    }
    suspend fun configure(next: Configuration) = mutex.withLock {
        require(next.interval in 5..180 && next.reset in 1..30)
        val old = configuration()
        if (old == next) { settings.save(next); return@withLock }
        val now = System.currentTimeMillis()
        db.withTransaction {
            val changes = listOf(Triple("overlayEnabled", old.overlayEnabled.toString(), next.overlayEnabled.toString()), Triple("monitoringEnabled", old.enabled.toString(), next.enabled.toString()), Triple("moodIntervalMinutes", old.interval.toString(), next.interval.toString()), Triple("sessionResetMinutes", old.reset.toString(), next.reset.toString()), Triple("excludedPackages", old.excluded.sorted().joinToString(","), next.excluded.sorted().joinToString(",")))
            changes.filter { it.second != it.third }.forEach { dao.insertConfiguration(ConfigurationEvent("$now:${it.first}", now, it.first, it.second, it.third)) }
            val state = dao.state() ?: MonitorState()
            if (old.copy(overlayEnabled = next.overlayEnabled) == next) {
                // Presentation-only settings must not interrupt or reset active-use accounting.
                dao.saveState(state.copy(sourceRevision = state.sourceRevision + 1))
                return@withTransaction
            }
            // Do not credit the unobserved tail at pause or configuration boundaries.
            if (old.enabled) {
                val read = runCatching { AndroidUsageEventReader(context, old).read((state.lastSuccessfulQueryUtc - 5_000).coerceAtLeast(state.firstStartedUtc ?: now), now) }
                if (read.isSuccess) dao.insertEvents(read.getOrThrow().map { it.entity() })
                else {
                    val from = state.lastSuccessfulQueryUtc.takeIf { it > 0 } ?: now
                    dao.insertEvents(listOf(Event(from, "STOP").entity()))
                    dao.insertGap(MonitoringGap("config:$from", from, now, "EVENT_HISTORY_UNAVAILABLE"))
                }
            }
            val type = if (old.enabled != next.enabled) { if (next.enabled) "START" else "STOP" } else "CONFIG"
            dao.insertEvents(listOf(Event(now, type, zone = ZoneId.systemDefault().id, interval = next.interval, reset = next.reset).entity()))
            dao.saveState(state.copy(monitoringEnabled = next.enabled, firstStartedUtc = state.firstStartedUtc ?: if (next.enabled) now else null, lastSuccessfulQueryUtc = now, sourceRevision = state.sourceRevision + 1, error = null))
            rebuild(now)
        }
        settings.save(next)
    }
    suspend fun poll() = mutex.withLock {
        val state = dao.state() ?: return@withLock
        if (!state.monitoringEnabled) return@withLock
        val now = System.currentTimeMillis()
        val from = state.lastSuccessfulQueryUtc.takeIf { it > 0 } ?: now
        if (!context.hasUsageAccess()) {
            db.withTransaction {
                dao.insertEvents(listOf(Event(from, "STOP").entity()))
                dao.insertGap(MonitoringGap("access:$from", from, now, "USAGE_ACCESS_REVOKED"))
                rebuild(now)
                dao.saveState(state.copy(lastHeartbeatUtc = now, error = "Usage Access is off. Restore it in Settings."))
            }
            return@withLock
        }
        val tooLate = now - from > 3 * 86_400_000L
        val start = if (tooLate) now - 3 * 86_400_000L else (from - 5_000).coerceAtLeast(state.firstStartedUtc ?: from)
        val config = configuration()
        val read = try { AndroidUsageEventReader(context, config).read(start, now) } catch (e: Exception) {
            db.withTransaction {
                dao.insertEvents(listOf(Event(from, "STOP").entity()))
                dao.insertGap(MonitoringGap("query:$from", from, now, "EVENT_HISTORY_UNAVAILABLE"))
                rebuild(now)
                dao.saveState(state.copy(lastHeartbeatUtc = now, error = e.message ?: "Query failed"))
            }
            return@withLock
        }
        db.withTransaction {
            if (tooLate || state.error != null) {
                dao.insertEvents(listOf(Event(from, "STOP").entity(), Event(if (tooLate) start else now, "START", interval = config.interval, reset = config.reset).entity()))
                if (tooLate) dao.insertGap(MonitoringGap("recovery:$from", from, start, "PROCESS_RECOVERY_TOO_LATE"))
            }
            dao.insertEvents(read.map { it.entity() })
            val result = rebuild(now)
            dao.saveState(state.copy(lastProcessedTimestampUtc = maxOf(state.lastProcessedTimestampUtc, read.maxOfOrNull { it.at } ?: from), lastSuccessfulQueryUtc = now, lastHeartbeatUtc = now, currentSessionId = result.sessions.lastOrNull { it.status != "CLOSED" }?.id, sourceRevision = state.sourceRevision + 1, error = null))
        }
    }
    private suspend fun rebuild(now: Long): Reconstruction {
        val result = engine.rebuild(dao.events().map { it.domain() }, now)
        dao.clearSegments(); dao.insertSegments(result.segments.map { UsageSegment(it.id, it.start, it.end, it.pkg, it.app, it.sessionId, it.zone, it.zoneInferred) })
        dao.clearSessions(); dao.insertSessions(result.sessions.map { PhoneSession(it.id, it.start, it.end, it.active, it.status, it.next) })
        val reconstructed = result.checkpoints.associateBy { it.id }
        dao.checkpoints().forEach { previous ->
            val corrected = reconstructed[previous.checkpointId]
            if (corrected == null) {
                dao.removeUnsentCheckpoint(previous.checkpointId)
                if (previous.notifiedUtc != null || previous.responseStatus == "ANSWERED") dao.insertGap(MonitoringGap("checkpoint:${previous.checkpointId}", previous.promptTimestampUtc, previous.promptTimestampUtc + 1, "CHECKPOINT_REVISED_BY_LATE_EVENTS"))
            } else if (corrected.at != previous.promptTimestampUtc) {
                if (previous.notifiedUtc == null && previous.responseStatus != "ANSWERED") dao.updateCheckpoint(previous.copy(promptTimestampUtc = corrected.at, foregroundPackage = corrected.pkg, zoneId = corrected.zone))
                else dao.insertGap(MonitoringGap("checkpoint:${previous.checkpointId}", previous.promptTimestampUtc, previous.promptTimestampUtc + 1, "CHECKPOINT_REVISED_BY_LATE_EVENTS"))
            }
        }
        dao.insertCheckpoints(result.checkpoints.map { MoodCheckpoint(it.id, it.sessionId, it.minutes, it.at, it.pkg, it.zone) })
        return result
    }
    suspend fun respond(id: String, score: Int): Boolean = mutex.withLock {
        require(score in 1..10)
        db.withTransaction {
            val checkpoint = dao.checkpoint(id) ?: return@withTransaction false
            if (dao.insertResponse(MoodResponse(id, System.currentTimeMillis(), score)) == -1L) return@withTransaction true
            dao.updateCheckpoint(checkpoint.copy(responseStatus = "ANSWERED"))
            dao.state()?.let { dao.saveState(it.copy(sourceRevision = it.sourceRevision + 1)) }
            true
        }
    }
}
private fun Event.entity() = RawEvent(key, at, type, pkg, app, zone, interval, reset, excluded, zoneInferred)
private fun RawEvent.domain() = Event(timestampUtc, type, packageName, appName, zoneId, interval, reset, excluded, zoneInferred)

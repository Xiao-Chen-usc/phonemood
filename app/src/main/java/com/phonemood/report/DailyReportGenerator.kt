package com.phonemood.report

import android.content.*
import android.net.Uri
import android.provider.MediaStore
import android.util.AtomicFile
import androidx.room.withTransaction
import com.phonemood.data.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.time.*

object DayWindow {
    fun bounds(date: LocalDate, zone: ZoneId): LongRange = date.atStartOfDay(zone).toInstant().toEpochMilli() until date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun overlap(start: Long, end: Long, bounds: LongRange): Long = (minOf(end, bounds.last + 1) - maxOf(start, bounds.first)).coerceAtLeast(0)
}
data class DailyHealthRecordResult(val date: LocalDate, val uri: Uri, val changed: Boolean)

class DailyReportGenerator(private val context: Context, private val repository: Repository) {
    private val json = Json { prettyPrint = true; explicitNulls = true }
    suspend fun reconcile() {
        val first = repository.dao.state()?.firstStartedUtc ?: return
        val zone = reportingZone()
        var date = Instant.ofEpochMilli(first).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        // Rebuild closed days from facts: content hashing skips unchanged files, including late answers.
        while (date < today) { generate(date); date = date.plusDays(1) }
    }
    private suspend fun reportingZone(): ZoneId = repository.dao.events().firstOrNull { it.type == "START" }?.zoneId?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
    suspend fun generate(date: LocalDate): DailyHealthRecordResult = repository.mutex.withLock {
        val dao = repository.dao
        val old = dao.reports().firstOrNull { it.localDate == date.toString() }
        val zone = old?.zoneId?.let { ZoneId.of(it) } ?: reportingZone()
        val bounds = DayWindow.bounds(date, zone)
        val now = System.currentTimeMillis()
        require(date <= LocalDate.now(zone)) { "Cannot export a future date" }
        fun stamp(at: Long, eventZone: String = zone.id): JsonObject = buildJsonObject {
            put("utc", Instant.ofEpochMilli(at).toString())
            put("local", Instant.ofEpochMilli(at).atZone(ZoneId.of(eventZone)).toOffsetDateTime().toString())
            put("zone_id", eventZone)
        }
        val all = dao.segments()
        val segments = all.filter { DayWindow.overlap(it.startUtc, it.endUtc, bounds) > 0 }.map { it.copy(startUtc = maxOf(it.startUtc, bounds.first), endUtc = minOf(it.endUtc, bounds.last + 1)) }
        val checkpoints = dao.checkpoints().filter { it.promptTimestampUtc in bounds }
        val responses = dao.responses().associateBy { it.checkpointId }
        val prompts = dao.promptStates().associateBy { it.checkpointId }
        val gaps = dao.gaps().filter { DayWindow.overlap(it.startUtc, it.endUtc, bounds) > 0 }
        val configurations = dao.configurations()
        val configAtStart = configurations.filter { it.timestampUtc <= bounds.first }.associateBy { it.setting }
        val sessionIds = (segments.map { it.sessionId } + checkpoints.map { it.sessionId }).toSet()
        val sessions = dao.sessions().filter { it.sessionId in sessionIds }
        val state = dao.state()
        val answered = checkpoints.mapNotNull { responses[it.checkpointId] }
        val partial = date == LocalDate.now(zone) || gaps.isNotEmpty() || checkpoints.any { it.responseStatus == "PENDING" } || (state?.firstStartedUtc ?: now) > bounds.first || (state?.lastSuccessfulQueryUtc ?: 0) < bounds.last + 1
        val content = buildJsonObject {
            put("schema_version", "1.0"); put("record_type", "daily_phone_usage_and_mood"); put("date", date.toString())
            put("measurement", buildJsonObject {
                put("mood_scale", buildJsonObject { put("min", 1); put("max", 10); put("higher_is_better", true) })
                put("mood_interval_minutes", configAtStart["moodIntervalMinutes"]?.newValue?.toInt() ?: 30)
                put("session_reset_minutes", configAtStart["sessionResetMinutes"]?.newValue?.toInt() ?: 5)
                put("reporting_zone", zone.id)
                put("day_start", stamp(bounds.first)); put("day_end_exclusive", stamp(bounds.last + 1))
                put("duration_definition", "Sum of non-excluded foreground segments; screen-off, locked and excluded-app time do not count. Segments are clipped to this reporting day.")
                put("day_attribution", "Calendar days use the zone at first monitoring start. Each timeline segment preserves its observed zone; recovered event zones are explicitly inferred.")
                put("checkpoint_definition", "Threshold crossing in cumulative session active time. prompt_timestamp is the threshold time; notification_delivered_at is actual delivery.")
                put("late_response_policy", "Responses belong to the day of their checkpoint, even when answered later.")
                put("poll_interval_seconds", 10)
                put("default_exclusions", JsonArray(listOf("PhoneMood", "Launcher", "System UI", "Keyboard", "Permission Controller").map(::JsonPrimitive)))
                put("configuration_at_day_start", JsonObject(configAtStart.mapValues { JsonPrimitive(it.value.newValue) }))
            })
            put("daily_summary", buildJsonObject {
                put("active_duration_ms", segments.sumOf { it.endUtc - it.startUtc })
                put("active_minutes", segments.sumOf { it.endUtc - it.startUtc } / 60_000.0)
                put("session_count", sessions.size); put("checkpoint_count", checkpoints.size); put("answered_count", answered.size)
                put("missed_count", checkpoints.count { it.responseStatus == "MISSED" })
                put("average_mood", if (answered.isEmpty()) JsonNull else JsonPrimitive(answered.map { it.score }.average()))
            })
            put("app_usage", JsonArray(segments.groupBy { it.packageName }.entries.sortedByDescending { it.value.sumOf { s -> s.endUtc - s.startUtc } }.map { (pkg, entries) -> buildJsonObject { put("package_name", pkg); put("app_name", entries.first().appName); put("active_duration_ms", entries.sumOf { it.endUtc - it.startUtc }) } }))
            put("sessions", JsonArray(sessions.map { s -> buildJsonObject {
                put("session_id", s.sessionId); put("start", stamp(s.startUtc)); put("end", s.endUtc?.let { stamp(it) } ?: JsonNull)
                put("status", s.status); put("active_duration_ms_in_day", segments.filter { it.sessionId == s.sessionId }.sumOf { it.endUtc - it.startUtc })
                put("active_duration_ms_whole_session", s.activeDurationMs)
            } }))
            put("timeline", JsonArray(segments.map { s -> buildJsonObject {
                put("id", s.id); put("start", stamp(s.startUtc, s.zoneId)); put("end", stamp(s.endUtc, s.zoneId)); put("duration_ms", s.endUtc - s.startUtc)
                put("package_name", s.packageName); put("app_name", s.appName); put("session_id", s.sessionId); put("zone_inferred", s.zoneInferred)
            } }))
            put("mood_contexts", JsonArray(checkpoints.map { c -> buildJsonObject {
                put("checkpoint_id", c.checkpointId); put("session_id", c.sessionId); put("checkpoint_minutes", c.checkpointMinutes)
                put("prompt_timestamp", stamp(c.promptTimestampUtc, c.zoneId)); put("foreground_package", c.foregroundPackage)
                put("notification_delivered_at", c.notifiedUtc?.let { stamp(it) } ?: JsonNull); put("response_status", c.responseStatus)
                put("floating_card", prompts[c.checkpointId]?.let { p -> buildJsonObject {
                    put("first_attached_at", p.overlayShownUtc?.let { stamp(it) } ?: JsonNull)
                    put("dismissed", p.dismissed); put("snoozed_until", p.snoozedUntilUtc?.let { stamp(it) } ?: JsonNull)
                    put("last_error", p.lastOverlayError?.let(::JsonPrimitive) ?: JsonNull)
                } } ?: JsonNull)
                put("response", responses[c.checkpointId]?.let { r -> buildJsonObject { put("score", r.score); put("timestamp", stamp(r.responseTimestampUtc, r.zoneId)); put("latency_ms", r.responseTimestampUtc - c.promptTimestampUtc) } } ?: JsonNull)
            } }))
            put("data_quality", buildJsonObject {
                put("status", if (partial) "PARTIAL" else "FINAL")
                put("observed_from", state?.firstStartedUtc?.let { stamp(it) } ?: JsonNull)
                put("observed_through", stamp(minOf(state?.lastSuccessfulQueryUtc ?: now, bounds.last + 1)))
                put("monitoring_gaps", JsonArray(gaps.map { g -> buildJsonObject { put("start", stamp(maxOf(g.startUtc, bounds.first))); put("end", stamp(minOf(g.endUtc, bounds.last + 1))); put("reason", g.reason) } }))
                put("configuration_events", JsonArray(configurations.filter { it.timestampUtc in bounds }.map { c -> buildJsonObject { put("timestamp", stamp(c.timestampUtc, c.zoneId)); put("setting", c.setting); put("old_value", c.oldValue); put("new_value", c.newValue) } }))
                put("zone_inferred_segment_count", segments.count { it.zoneInferred })
                put("limitations", JsonArray(listOf("UsageStats can be delayed or incomplete; an empty query does not prove complete coverage.", "Recovered usage events do not contain historical timezone offsets. Their zone is inferred from the recovery-time device zone.", "An attached floating card may be hidden by Android or another app; attachment does not establish that the user saw it.", "Foreground activity transitions approximate interaction; split-screen and picture-in-picture are not separately attributed.", "User pause/resume boundaries are recorded as configuration events. No usage is inferred while paused.").map(::JsonPrimitive)))
            })
        }
        check(segments.all { it.endUtc >= it.startUtc }); check(answered.all { it.score in 1..10 })
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toString().toByteArray())
        val revision = java.nio.ByteBuffer.wrap(digest).long
        if (old?.sourceRevision == revision && old.uri != null && runCatching { context.contentResolver.openFileDescriptor(Uri.parse(old.uri), "r")?.use { true } ?: false }.getOrDefault(false)) return@withLock DailyHealthRecordResult(date, Uri.parse(old.uri), false)
        val record = JsonObject(content + ("generated_at" to JsonPrimitive(Instant.ofEpochMilli(now).toString())))
        val serialized = json.encodeToString(JsonObject.serializer(), record)
        check(json.parseToJsonElement(serialized) == record)
        val name = "$date-phone-mood.json"
        val directory = File(context.filesDir, "reports").apply { mkdirs() }
        val atomic = AtomicFile(File(directory, name))
        val stream = atomic.startWrite()
        try { stream.write(serialized.toByteArray()); atomic.finishWrite(stream) } catch (e: Exception) { atomic.failWrite(stream); throw e }
        val uri = publish(name, serialized)
        dao.saveReport(DailyReportState(date.toString(), if (partial) "PARTIAL" else "FINAL", now, revision, (old?.lateUpdateCount ?: 0) + if (old != null && old.sourceRevision != revision) 1 else 0, uri.toString(), zone.id))
        DailyHealthRecordResult(date, uri, true)
    }
    /** Stage complete bytes before publishing. Existing public bytes are never overwritten in place. */
    private fun publish(name: String, text: String): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val path = "Download/PhoneMoodHealth/"
        val pendingName = ".$name.pending"
        fun find(displayName: String): List<Uri> {
            val result = mutableListOf<Uri>()
            resolver.query(collection, arrayOf(MediaStore.Downloads._ID), "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(path, displayName), null)?.use { cursor -> while (cursor.moveToNext()) result += ContentUris.withAppendedId(collection, cursor.getLong(0)) }
            return result
        }
        find(pendingName).forEach { resolver.delete(it, null, null) }
        val uri = checkNotNull(resolver.insert(collection, ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, pendingName); put(MediaStore.Downloads.MIME_TYPE, "application/json"); put(MediaStore.Downloads.RELATIVE_PATH, path); put(MediaStore.Downloads.IS_PENDING, 1) }))
        try {
            checkNotNull(resolver.openFileDescriptor(uri, "w")).use { descriptor -> java.io.FileOutputStream(descriptor.fileDescriptor).use { it.write(text.toByteArray()); it.flush(); it.fd.sync() } }
            checkNotNull(resolver.openInputStream(uri)).use { check(json.parseToJsonElement(it.bufferedReader().readText()) == json.parseToJsonElement(text)) }
            // MediaStore has no guaranteed atomic file replacement API. Batch the metadata switch;
            // private AtomicFile is canonical and retry repairs an interrupted public publication.
            val operations = arrayListOf<ContentProviderOperation>()
            find(name).forEach { operations += ContentProviderOperation.newDelete(it).build() }
            operations += ContentProviderOperation.newUpdate(uri).withValues(ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.IS_PENDING, 0) }).build()
            resolver.applyBatch(MediaStore.AUTHORITY, operations)
            return uri
        } catch (e: Exception) { runCatching { resolver.delete(uri, null, null) }; throw e }
    }
}

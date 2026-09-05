package com.phonemood

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phonemood.data.*
import com.phonemood.report.*
import com.phonemood.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class StorageAndExportTest {
    private lateinit var context: Context
    private lateinit var db: PhoneMoodDatabase
    private lateinit var repository: Repository
    private val date = LocalDate.parse("2001-02-03")
    private val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PhoneMoodDatabase::class.java).build()
        repository = Repository(context, db, SettingsStore(context))
        db.dao().saveState(MonitorState(firstStartedUtc = start, lastSuccessfulQueryUtc = start + 86_400_000))
        db.dao().insertEvents(listOf(RawEvent("start", start, "START", "", "", "UTC")))
        db.dao().insertSegments(listOf(UsageSegment("segment", start, start + 1_800_000, "test.app", "Test app", "session", "UTC", false)))
        db.dao().insertSessions(listOf(PhoneSession("session", start, start + 1_800_000, 1_800_000, "CLOSED", 60)))
        db.dao().insertCheckpoints(listOf(MoodCheckpoint("check", "session", 30, start + 1_800_000, "test.app", "UTC", "MISSED")))
    }
    @After fun cleanup() = runBlocking {
        db.dao().reports().mapNotNull { it.uri }.forEach { context.contentResolver.delete(android.net.Uri.parse(it), null, null) }
        java.io.File(context.filesDir, "reports/$date-phone-mood.json").delete()
        db.close()
    }
    private fun read(result: DailyHealthRecordResult) = context.contentResolver.openInputStream(result.uri)!!.use { Json.parseToJsonElement(it.bufferedReader().readText()).jsonObject }
    @Test fun exportIsCompleteAndIdempotent() = runBlocking {
        val generator = DailyReportGenerator(context, repository)
        val first = generator.generate(date)
        val record = read(first)
        assertEquals(1_800_000L, record["daily_summary"]!!.jsonObject["active_duration_ms"]!!.jsonPrimitive.long)
        val timelineTotal = record["timeline"]!!.jsonArray.sumOf { it.jsonObject["duration_ms"]!!.jsonPrimitive.long }
        assertEquals(1_800_000L, timelineTotal)
        assertEquals("UTC", record["measurement"]!!.jsonObject["reporting_zone"]!!.jsonPrimitive.content)
        assertTrue(record.keys.containsAll(listOf("schema_version", "record_type", "date", "generated_at", "measurement", "daily_summary", "app_usage", "sessions", "timeline", "mood_contexts", "data_quality")))
        val repeated = generator.generate(date)
        assertFalse(repeated.changed); assertEquals(first.uri, repeated.uri)
    }
    @Test fun lateResponseRebuildsOriginalDayAndFirstAnswerWins() = runBlocking {
        val generator = DailyReportGenerator(context, repository)
        val first = generator.generate(date)
        assertTrue(repository.respond("check", 7))
        assertTrue(repository.respond("check", 2))
        assertEquals(7, db.dao().responses().single().score)
        val revised = generator.generate(date)
        assertTrue(revised.changed)
        val record = read(revised)
        assertEquals(date.toString(), record["date"]!!.jsonPrimitive.content)
        assertEquals(7, record["mood_contexts"]!!.jsonArray.single().jsonObject["response"]!!.jsonObject["score"]!!.jsonPrimitive.int)
        assertEquals(1, db.dao().reports().single().lateUpdateCount)
        assertNotEquals(first.uri, revised.uri)
    }
    @Test fun checkpointsAndRawEventsHaveDatabaseUniqueness() = runBlocking {
        val checkpoint = db.dao().checkpoints().single()
        db.dao().insertCheckpoints(listOf(checkpoint.copy(checkpointId = "duplicate")))
        assertEquals(1, db.dao().checkpoints().size)
        val event = db.dao().events().single()
        db.dao().insertEvents(listOf(event, event))
        assertEquals(1, db.dao().events().size)
    }
    @Test fun settingsFallbackHomeDoesNotExcludeSettingsApp() {
        val filter = com.phonemood.monitoring.AppFilter(context, com.phonemood.settings.Configuration())
        assertFalse(filter.excludes("com.android.settings"))
        assertTrue(filter.excludes(context.packageName))
        assertTrue(filter.excludes("com.android.systemui"))
    }
    @Test fun overlaySettingDoesNotResetUsageAccounting() = runBlocking {
        val events = db.dao().events()
        val segments = db.dao().segments()
        val config = repository.configuration()
        repository.configure(config.copy(overlayEnabled = !config.overlayEnabled))
        assertEquals(events, db.dao().events())
        assertEquals(segments, db.dao().segments())
        assertEquals("overlayEnabled", db.dao().configurations().last().setting)
    }
    @Test fun gapsAppearInPartialReport() = runBlocking {
        db.dao().insertGap(MonitoringGap("gap", start + 2_000_000, start + 3_000_000, "USAGE_ACCESS_REVOKED"))
        val record = read(DailyReportGenerator(context, repository).generate(date))
        val quality = record["data_quality"]!!.jsonObject
        assertEquals("PARTIAL", quality["status"]!!.jsonPrimitive.content)
        assertEquals("USAGE_ACCESS_REVOKED", quality["monitoring_gaps"]!!.jsonArray.single().jsonObject["reason"]!!.jsonPrimitive.content)
    }
}

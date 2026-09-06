package com.phonemood.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "raw_events")
data class RawEvent(@PrimaryKey val id: String, val timestampUtc: Long, val type: String, val packageName: String, val appName: String, val zoneId: String, val interval: Int = 30, val reset: Int = 5, val excluded: Boolean = false, val zoneInferred: Boolean = false)
@Entity(indices = [Index("startUtc"), Index("endUtc")])
data class UsageSegment(@PrimaryKey val id: String, val startUtc: Long, val endUtc: Long, val packageName: String, val appName: String, val sessionId: String, val zoneId: String, val zoneInferred: Boolean)
@Entity
data class PhoneSession(@PrimaryKey val sessionId: String, val startUtc: Long, val endUtc: Long?, val activeDurationMs: Long, val status: String, val nextCheckpointMinutes: Int)
@Entity(indices = [Index(value = ["sessionId", "checkpointMinutes"], unique = true), Index("promptTimestampUtc")])
data class MoodCheckpoint(@PrimaryKey val checkpointId: String, val sessionId: String, val checkpointMinutes: Int, val promptTimestampUtc: Long, val foregroundPackage: String, val zoneId: String, val responseStatus: String = "PENDING", val notifiedUtc: Long? = null)
@Entity
data class MoodPromptState(@PrimaryKey val checkpointId: String, val overlayShownUtc: Long? = null, val dismissed: Boolean = false, val snoozedUntilUtc: Long? = null, val lastOverlayError: String? = null)
@Entity(indices = [Index("responseTimestampUtc")])
data class MoodResponse(@PrimaryKey val checkpointId: String, val responseTimestampUtc: Long, val score: Int, val zoneId: String = java.time.ZoneId.systemDefault().id)
@Entity
data class MonitorState(@PrimaryKey val id: Int = 1, val monitoringEnabled: Boolean = false, val firstStartedUtc: Long? = null, val lastProcessedTimestampUtc: Long = 0, val lastSuccessfulQueryUtc: Long = 0, val lastHeartbeatUtc: Long = 0, val currentSessionId: String? = null, val sourceRevision: Long = 0, val error: String? = null)
@Entity(indices = [Index("timestampUtc")])
data class ConfigurationEvent(@PrimaryKey val id: String, val timestampUtc: Long, val setting: String, val oldValue: String, val newValue: String, val zoneId: String = java.time.ZoneId.systemDefault().id)
@Entity
data class MonitoringGap(@PrimaryKey val id: String, val startUtc: Long, val endUtc: Long, val reason: String)
@Entity
data class DailyReportState(@PrimaryKey val localDate: String, val status: String, val lastGeneratedUtc: Long, val sourceRevision: Long, val lateUpdateCount: Int = 0, val uri: String? = null, val zoneId: String)

/** Successful query-chain coverage, never inferred from legacy heartbeats alone. */
@Entity(indices = [Index("endUtc")])
data class UsageCoverageEvidence(@PrimaryKey val startUtc: Long, val endUtc: Long, val observedAtUtc: Long, val evidenceVersion: Int = 1)

@Dao
interface PhoneMoodDao {
    @Query("SELECT COUNT(*) FROM MoodResponse") fun watchAnswerCount(): Flow<Int>
    @Query("SELECT MAX(timestampUtc) FROM ConfigurationEvent") fun watchConfigurationTime(): Flow<Long?>
    @Query("SELECT * FROM UsageCoverageEvidence WHERE startUtc < :end AND endUtc > :start ORDER BY startUtc") suspend fun coverage(start: Long, end: Long): List<UsageCoverageEvidence>
    @Query("SELECT * FROM UsageCoverageEvidence ORDER BY endUtc DESC LIMIT 1") suspend fun lastCoverage(): UsageCoverageEvidence?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCoverage(evidence: UsageCoverageEvidence)
    @Query("SELECT * FROM raw_events WHERE timestampUtc <= :at ORDER BY timestampUtc DESC, id DESC LIMIT 1") suspend fun lastEvent(at: Long): RawEvent?
    @Query("SELECT * FROM UsageSegment WHERE startUtc < :end AND endUtc > :start ORDER BY startUtc, id") suspend fun segmentsIn(start: Long, end: Long): List<UsageSegment>
    @Query("SELECT * FROM MoodResponse WHERE responseTimestampUtc >= :start AND responseTimestampUtc < :end ORDER BY responseTimestampUtc, checkpointId") suspend fun responsesIn(start: Long, end: Long): List<MoodResponse>
    @Query("SELECT * FROM MoodResponse WHERE responseTimestampUtc < :at ORDER BY responseTimestampUtc DESC, checkpointId DESC LIMIT 1") suspend fun responseBefore(at: Long): MoodResponse?
    @Query("SELECT * FROM MoodCheckpoint WHERE promptTimestampUtc < :end AND (promptTimestampUtc >= :start OR notifiedUtc >= :start OR checkpointId IN (SELECT checkpointId FROM MoodResponse WHERE responseTimestampUtc >= :start AND responseTimestampUtc < :end) OR checkpointId IN (SELECT checkpointId FROM MoodPromptState WHERE overlayShownUtc >= :start)) ORDER BY promptTimestampUtc, checkpointId") suspend fun checkpointsIn(start: Long, end: Long): List<MoodCheckpoint>
    @Query("SELECT * FROM PhoneSession WHERE sessionId IN (:ids) ORDER BY startUtc, sessionId") suspend fun sessionsByIds(ids: List<String>): List<PhoneSession>
    @Query("SELECT * FROM MoodPromptState WHERE checkpointId IN (:ids) ORDER BY checkpointId") suspend fun promptsByIds(ids: List<String>): List<MoodPromptState>
    @Query("SELECT * FROM raw_events WHERE type = 'START' ORDER BY timestampUtc LIMIT 1") suspend fun firstStart(): RawEvent?
    @Query("UPDATE MonitorState SET sourceRevision = sourceRevision + 1 WHERE id = 1") suspend fun bumpRevision()
    @Query("SELECT * FROM raw_events ORDER BY timestampUtc") suspend fun events(): List<RawEvent>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEvents(events: List<RawEvent>)
    @Query("SELECT * FROM UsageSegment ORDER BY startUtc") suspend fun segments(): List<UsageSegment>
    @Query("SELECT * FROM UsageSegment ORDER BY startUtc DESC") fun watchSegments(): Flow<List<UsageSegment>>
    @Query("DELETE FROM UsageSegment") suspend fun clearSegments()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSegments(segments: List<UsageSegment>)
    @Query("SELECT * FROM PhoneSession ORDER BY startUtc") suspend fun sessions(): List<PhoneSession>
    @Query("SELECT * FROM PhoneSession ORDER BY startUtc DESC") fun watchSessions(): Flow<List<PhoneSession>>
    @Query("DELETE FROM PhoneSession") suspend fun clearSessions()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSessions(sessions: List<PhoneSession>)
    @Query("SELECT * FROM MoodCheckpoint ORDER BY promptTimestampUtc") suspend fun checkpoints(): List<MoodCheckpoint>
    @Query("SELECT * FROM MoodCheckpoint ORDER BY promptTimestampUtc DESC") fun watchCheckpoints(): Flow<List<MoodCheckpoint>>
    @Query("SELECT * FROM MoodCheckpoint WHERE checkpointId = :id") suspend fun checkpoint(id: String): MoodCheckpoint?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCheckpoints(checkpoints: List<MoodCheckpoint>)
    @Update suspend fun updateCheckpointRow(checkpoint: MoodCheckpoint)
    @Transaction suspend fun updateCheckpoint(checkpoint: MoodCheckpoint) { updateCheckpointRow(checkpoint); bumpRevision() }
    @Query("DELETE FROM MoodCheckpoint WHERE checkpointId = :id AND notifiedUtc IS NULL AND responseStatus != 'ANSWERED'") suspend fun removeUnsentCheckpoint(id: String)
    @Query("SELECT * FROM MoodPromptState") fun watchPromptStates(): Flow<List<MoodPromptState>>
    @Query("SELECT * FROM MoodPromptState") suspend fun promptStates(): List<MoodPromptState>
    @Query("SELECT * FROM MoodPromptState WHERE checkpointId = :id") suspend fun promptState(id: String): MoodPromptState?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPromptState(state: MoodPromptState)
    @Transaction suspend fun savePromptState(state: MoodPromptState) { insertPromptState(state); bumpRevision() }
    @Query("SELECT * FROM MoodResponse") suspend fun responses(): List<MoodResponse>
    @Query("SELECT * FROM MoodResponse") fun watchResponses(): Flow<List<MoodResponse>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertResponse(response: MoodResponse): Long
    @Query("SELECT * FROM MonitorState WHERE id = 1") suspend fun state(): MonitorState?
    @Query("SELECT * FROM MonitorState WHERE id = 1") fun watchState(): Flow<MonitorState?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveState(state: MonitorState)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertConfiguration(event: ConfigurationEvent)
    @Query("SELECT * FROM ConfigurationEvent ORDER BY timestampUtc") suspend fun configurations(): List<ConfigurationEvent>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGap(gap: MonitoringGap)
    @Query("SELECT * FROM MonitoringGap ORDER BY startUtc") suspend fun gaps(): List<MonitoringGap>
    @Query("SELECT * FROM DailyReportState ORDER BY localDate DESC") suspend fun reports(): List<DailyReportState>
    @Query("SELECT * FROM DailyReportState ORDER BY localDate DESC") fun watchReports(): Flow<List<DailyReportState>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveReport(report: DailyReportState)
}

@Database(entities = [RawEvent::class, UsageSegment::class, PhoneSession::class, MoodCheckpoint::class, MoodResponse::class, MoodPromptState::class, MonitorState::class, ConfigurationEvent::class, MonitoringGap::class, DailyReportState::class, UsageCoverageEvidence::class], version = 3, exportSchema = true)
abstract class PhoneMoodDatabase : RoomDatabase() { abstract fun dao(): PhoneMoodDao }

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `UsageCoverageEvidence` (`startUtc` INTEGER NOT NULL, `endUtc` INTEGER NOT NULL, `observedAtUtc` INTEGER NOT NULL, `evidenceVersion` INTEGER NOT NULL, PRIMARY KEY(`startUtc`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_UsageSegment_startUtc` ON `UsageSegment` (`startUtc`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_UsageSegment_endUtc` ON `UsageSegment` (`endUtc`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_MoodResponse_responseTimestampUtc` ON `MoodResponse` (`responseTimestampUtc`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ConfigurationEvent_timestampUtc` ON `ConfigurationEvent` (`timestampUtc`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_UsageCoverageEvidence_endUtc` ON `UsageCoverageEvidence` (`endUtc`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_MoodCheckpoint_promptTimestampUtc` ON `MoodCheckpoint` (`promptTimestampUtc`)")
    }
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `MoodPromptState` (`checkpointId` TEXT NOT NULL, `overlayShownUtc` INTEGER, `dismissed` INTEGER NOT NULL, `snoozedUntilUtc` INTEGER, `lastOverlayError` TEXT, PRIMARY KEY(`checkpointId`))")
    }
}

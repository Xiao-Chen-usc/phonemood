package com.phonemood.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "raw_events")
data class RawEvent(@PrimaryKey val id: String, val timestampUtc: Long, val type: String, val packageName: String, val appName: String, val zoneId: String, val interval: Int = 30, val reset: Int = 5, val excluded: Boolean = false, val zoneInferred: Boolean = false)
@Entity
data class UsageSegment(@PrimaryKey val id: String, val startUtc: Long, val endUtc: Long, val packageName: String, val appName: String, val sessionId: String, val zoneId: String, val zoneInferred: Boolean)
@Entity
data class PhoneSession(@PrimaryKey val sessionId: String, val startUtc: Long, val endUtc: Long?, val activeDurationMs: Long, val status: String, val nextCheckpointMinutes: Int)
@Entity(indices = [Index(value = ["sessionId", "checkpointMinutes"], unique = true)])
data class MoodCheckpoint(@PrimaryKey val checkpointId: String, val sessionId: String, val checkpointMinutes: Int, val promptTimestampUtc: Long, val foregroundPackage: String, val zoneId: String, val responseStatus: String = "PENDING", val notifiedUtc: Long? = null)
@Entity
data class MoodPromptState(@PrimaryKey val checkpointId: String, val overlayShownUtc: Long? = null, val dismissed: Boolean = false, val snoozedUntilUtc: Long? = null, val lastOverlayError: String? = null)
@Entity
data class MoodResponse(@PrimaryKey val checkpointId: String, val responseTimestampUtc: Long, val score: Int, val zoneId: String = java.time.ZoneId.systemDefault().id)
@Entity
data class MonitorState(@PrimaryKey val id: Int = 1, val monitoringEnabled: Boolean = false, val firstStartedUtc: Long? = null, val lastProcessedTimestampUtc: Long = 0, val lastSuccessfulQueryUtc: Long = 0, val lastHeartbeatUtc: Long = 0, val currentSessionId: String? = null, val sourceRevision: Long = 0, val error: String? = null)
@Entity
data class ConfigurationEvent(@PrimaryKey val id: String, val timestampUtc: Long, val setting: String, val oldValue: String, val newValue: String, val zoneId: String = java.time.ZoneId.systemDefault().id)
@Entity
data class MonitoringGap(@PrimaryKey val id: String, val startUtc: Long, val endUtc: Long, val reason: String)
@Entity
data class DailyReportState(@PrimaryKey val localDate: String, val status: String, val lastGeneratedUtc: Long, val sourceRevision: Long, val lateUpdateCount: Int = 0, val uri: String? = null, val zoneId: String)

@Dao
interface PhoneMoodDao {
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
    @Update suspend fun updateCheckpoint(checkpoint: MoodCheckpoint)
    @Query("DELETE FROM MoodCheckpoint WHERE checkpointId = :id AND notifiedUtc IS NULL AND responseStatus != 'ANSWERED'") suspend fun removeUnsentCheckpoint(id: String)
    @Query("SELECT * FROM MoodPromptState") suspend fun promptStates(): List<MoodPromptState>
    @Query("SELECT * FROM MoodPromptState WHERE checkpointId = :id") suspend fun promptState(id: String): MoodPromptState?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePromptState(state: MoodPromptState)
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

@Database(entities = [RawEvent::class, UsageSegment::class, PhoneSession::class, MoodCheckpoint::class, MoodResponse::class, MoodPromptState::class, MonitorState::class, ConfigurationEvent::class, MonitoringGap::class, DailyReportState::class], version = 2, exportSchema = true)
abstract class PhoneMoodDatabase : RoomDatabase() { abstract fun dao(): PhoneMoodDao }

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `MoodPromptState` (`checkpointId` TEXT NOT NULL, `overlayShownUtc` INTEGER, `dismissed` INTEGER NOT NULL, `snoozedUntilUtc` INTEGER, `lastOverlayError` TEXT, PRIMARY KEY(`checkpointId`))")
    }
}

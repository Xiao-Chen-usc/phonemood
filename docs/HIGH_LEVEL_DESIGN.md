# PhoneMood — High Level Design

*Chinese version: [HIGH_LEVEL_DESIGN.zh-CN.md](HIGH_LEVEL_DESIGN.zh-CN.md)*

Version: 1.0
Platform: Android
Implementation Language: Kotlin
Minimum Android Version: Android 10 / API 29
Primary Persistence: Room / SQLite
AI Interchange Format: One self-contained JSON file per day

---

# 1. Architecture Goals

System design priorities, in order:

1. Monitoring runs automatically wherever possible.
2. Process death must not, by itself, cause unrecoverable data loss.
3. A mood prompt lands as close to the 30-minute checkpoint as it can.
4. Internal raw data can always be recomputed.
5. One Health-AI-readable file is generated automatically each day.
6. No backend server is required.
7. No network access by default.
8. Every core operation must be idempotent.

---

# 2. Why Kotlin

Kotlin is an implementation choice, not a product requirement.

The core of this application is not a complex cross-platform UI; it is Android's native capabilities:

```text
UsageStatsManager
Foreground Service
BroadcastReceiver
NotificationManager
Room
DataStore
WorkManager
MediaStore
```

Hence:

```text
Kotlin
+
Jetpack
```

This avoids adding a bridge between Flutter / React Native and an Android native service.

If Java is ever needed, the architecture itself does not depend on Kotlin.

---

# 3. System Architecture

```text
                   Android OS
                       │
        ┌──────────────┼──────────────┐
        │              │              │
 UsageStatsManager   Boot State    Notifications
        │              │
        └───────┬──────┘
                ▼
       UsageMonitorService
                │
                ▼
          EventProcessor
                │
       ┌────────┴─────────┐
       │                  │
       ▼                  ▼
UsageSegmentBuilder   SessionTracker
                          │
                          ▼
                    MoodScheduler
                          │
                          ▼
                 MoodNotification
                          │
                          ▼
                 MoodRatingActivity
       │                  │
       └─────────┬────────┘
                 ▼
             Repository
                 │
                 ▼
          Room / SQLite
                 │
        ┌────────┴──────────┐
        │                   │
        ▼                   ▼
     App UI          DailyReportGenerator
                             │
                             ▼
                     JSON Serializer
                             │
                             ▼
                     MediaStore.Downloads
                             │
                             ▼
               YYYY-MM-DD-phone-mood.json
```

---

# 4. Fundamental Reliability Model

The system cannot assume:

```text
"Our process will always be alive."
```

Two layers are used.

## Layer 1 — Near Real-Time

```text
UsageMonitorService
```

Responsible for:

* continuously querying the latest usage events;
* updating the current session;
* raising a mood checkpoint;
* notifying the user promptly.

## Layer 2 — Reconciliation

```text
UsageStatsManager.queryEvents()
```

Responsible for:

* backfilling after a service restart;
* recovering missed time windows;
* correcting internal state.

Android allows querying usage events over a time range, but these detailed events are retained for only a limited number of days, so local persistence remains mandatory.

---

# 5. UsageMonitorService

Component:

```text
Foreground Service
```

Responsibilities:

```text
read events
→ process
→ update segments
→ update session
→ evaluate checkpoints
→ persist
```

Recommended polling interval:

```text
60 seconds
```

This means a checkpoint is not guaranteed to fire at:

```text
30:00.000
```


It may fire at:

```text
30:04
```

which the product accepts.

---

# 6. Foreground Service Type

Android 14+ requires a foreground service to declare a matching service type.

Where no standard type fits better, this project uses:

```text
specialUse
```

Android defines this as a valid case that does not fall under any other standard foreground-service type. It requires declaring `FOREGROUND_SERVICE_SPECIAL_USE` and explaining the specific purpose in the manifest. If the app is later submitted to Google Play, that stated purpose goes through review.

Conceptual manifest:

```xml
<service
    android:name=".monitoring.UsageMonitorService"
    android:foregroundServiceType="specialUse">

    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="User-enabled local smartphone usage monitoring for periodic self-reported wellbeing assessments." />

</service>
```

A foreground service carries a system-visible notification. That is a requirement of Android's foreground-service model itself.

---

# 7. BootReceiver

Listens for:

```text
BOOT_COMPLETED
MY_PACKAGE_REPLACED
```

Flow:

```text
Boot
 ↓
BootReceiver
 ↓
monitoringEnabled?
 ↓ yes
Usage Access available?
 ↓ yes
restore monitoring
```

If the user has explicitly chosen:

```text
Pause Monitoring
```

then:

```text
monitoringEnabled = false
```

and BootReceiver must not turn it back on.

---

# 8. UsageEventReader

Isolates the Android API.

```kotlin
interface UsageEventReader {
    suspend fun read(
        fromMillis: Long,
        toMillis: Long
    ): List<DeviceUsageEvent>
}
```

The unified internal model:

```text
DeviceUsageEvent

timestampUtc
eventType
packageName
```

An Android adapter converts `UsageEvents.Event` into a domain event.

---

# 9. EventProcessor

Processing:

```text
Raw Events
   ↓
Sort
   ↓
Normalize
   ↓
Deduplicate
   ↓
Filter
   ↓
Domain Events
```

What must be avoided:

```text
Service restart
→ overlapping query
→ duplicate data
```

Query overlap is permitted, for example:

```text
lastProcessedTimestamp - 5 seconds
```

followed by deterministic deduplication on:

```text
timestamp
package
event type
```


---

# 10. UsageSegmentBuilder

Turns foreground activity transitions into time spans.

For example:

```text
09:00 Reddit resumed
09:12 Chrome resumed
09:21 YouTube resumed
```

produces:

```text
09:00–09:12 Reddit
09:12–09:21 Chrome
09:21–...   YouTube
```

Core model:

```text
UsageSegment

id
startUtc
endUtc
packageName
appName
sessionId
```

---

# 11. AppFilter

Ignored by default:

```text
PhoneMood
Launcher
System UI
Keyboard
Permission Controller
```

In future, the user will be able to maintain the excluded package list.

An excluded app in the MVP is:

```text
not included in active phone-use duration
```

---

# 12. SessionTracker

State machine:

```text
IDLE
ACTIVE
INTERRUPTED
```

## IDLE → ACTIVE

A valid foreground app is detected.

## ACTIVE → ACTIVE

An app switch.

The session is unchanged.

## ACTIVE → INTERRUPTED

The screen becomes non-interactive or locked.

Records:

```text
interruptionStart
```

## INTERRUPTED → ACTIVE

If:

```text
interruption < sessionResetThreshold
```

the original session continues.

## INTERRUPTED → IDLE

If:

```text
interruption >= threshold
```

the session ends.

Default:

```text
threshold = 5 minutes
```

---

# 13. Active Duration

Two quantities must be kept apart:

```text
wall-clock session duration
```

and:

```text
active phone-use duration
```

For example:

```text
10:00–10:20 active
10:20–10:22 locked
10:22–10:32 active
```

Result:

```text
Wall clock = 32 min
Active     = 30 min
```

MoodScheduler uses:

```text
Active = 30 min
```

---

# 14. MoodScheduler

A session stores:

```text
nextCheckpointMinutes
```

Default:

```text
30
```

The test:

```text
if activeMinutes >= nextCheckpoint:
    create checkpoint
    nextCheckpoint += configuredInterval
```

What must not be used:

```text
activeMinutes % 30 == 0
```

because polling latency could then skip a checkpoint entirely.

---

# 15. Mood Checkpoint Idempotency

Database unique constraint:

```text
UNIQUE(sessionId, checkpointMinutes)
```

The order must be:

```text
Create/Persist checkpoint
         ↓
Send notification
```

and never the reverse.

So that:

```text
service restart
replay
duplicate event
```

cannot produce a second 30-minute notification.

---

# 16. MoodRatingActivity

Target UX:

```text
notification
    ↓
rating screen
    ↓
tap 1–10
    ↓
persist
    ↓
close
```

MoodResponse：

```text
checkpointId
responseTimestampUtc
score
```

Checkpoint and response are persisted independently.

---

# 17. Operational Database

Uses:

```text
Room / SQLite
```

The database is the internal store of fact.

Principal entities:

```text
UsageSegment
PhoneSession
MoodCheckpoint
MoodResponse
MonitorState
ConfigurationEvent
MonitoringGap
DailyReportState
```

---

# 18. PhoneSession

```text
sessionId
startUtc
endUtc
activeDurationMs
status
```

Status:

```text
ACTIVE
INTERRUPTED
CLOSED
```

---

# 19. MoodCheckpoint

```text
checkpointId
sessionId
checkpointMinutes
promptTimestampUtc
foregroundPackage
responseStatus
```

ResponseStatus:

```text
PENDING
ANSWERED
MISSED
```

---

# 20. ConfigurationEvent

A configuration change must be written to the database:

```text
timestampUtc
setting
oldValue
newValue
```

For example:

```text
moodIntervalMinutes
30 → 60
```

This matters a great deal for long-horizon AI analysis.

---

# 21. MonitorState

```text
monitoringEnabled
lastProcessedTimestampUtc
lastSuccessfulQueryUtc
lastHeartbeatUtc
currentSessionId
```

The governing principle:

> Important state must not exist only in RAM.

---

# 22. Recovery Algorithm

For example:

```text
Last persisted event:
14:02

Service resumes:
14:23
```

Executes:

```text
Load MonitorState
       ↓
Query UsageEvents
14:02 → 14:23
       ↓
EventProcessor
       ↓
UsageSegmentBuilder
       ↓
SessionTracker
       ↓
MoodScheduler
       ↓
Persist
```

---

# 23. Data Quality and Monitoring Gaps

The system maintains:

```text
MonitoringGap
```

For example:

```text
startUtc
endUtc
reason
```

Reason:

```text
USAGE_ACCESS_REVOKED
EVENT_HISTORY_UNAVAILABLE
PROCESS_RECOVERY_TOO_LATE
UNKNOWN
```

The daily AI file must include these gaps.

---

# 24. DailyReportGenerator

This is a first-class core component.

Responsibilities:

```text
Room
 ↓
Query one calendar day
 ↓
Build DailyHealthRecord
 ↓
Validate
 ↓
Serialize JSON
 ↓
Atomic file replacement
```

Interface:

```kotlin
interface DailyReportGenerator {
    suspend fun generate(
        date: LocalDate
    ): DailyHealthRecordResult
}
```

---

# 25. Daily Report Trigger Strategy

Exact alarms are not used.

Three trigger points are used instead.

## Trigger A — Date rollover detection

On every processing pass, UsageMonitorService checks:

```text
currentLocalDate != previousLocalDate
```

If the day has rolled over:

```text
finalize yesterday
```

## Trigger B — App startup / service recovery

On each recovery, it checks for a gap between:

```text
latestGeneratedDate
```

and:

```text
yesterday
```


Everything missing is generated.

## Trigger C — WorkManager reconciliation

A periodic worker checks:

```text
Are any closed dates missing their report?
```

WorkManager's minimum periodic interval is 15 minutes, and actual execution time is decided by system scheduling and constraints. It suits a fallback for eventual consistency, not a precise midnight timer.

The MVP runs report reconciliation every:

```text
6 hours
```


---

# 26. Daily Report State

Database:

```text
DailyReportState

localDate
status
lastGeneratedUtc
sourceRevision
lateUpdateCount
```

Status:

```text
MISSING
GENERATED
PARTIAL
FINAL
```

---

# 27. Late Responses

Boundary case:

```text
23:59 prompt
00:02 response
```

The MoodResponse still belongs to the previous day, through its checkpointId.

The generation flow must support:

```text
Existing daily JSON
       ↓
New late record
       ↓
Rebuild from Room
       ↓
Atomic replace same JSON
```

The file name does not change.

---

# 28. Atomic File Generation

A half-written JSON file must never be left behind.

Flow:

```text
Build record in memory
       ↓
Serialize
       ↓
Validate
       ↓
Write complete output
       ↓
Commit/replace
       ↓
Update DailyReportState
```

The database always retains the raw data, so the JSON can be regenerated.

---

# 29. DailyHealthRecord Schema

Top level:

```text
schema_version
record_type
date
generated_at
measurement
daily_summary
app_usage
sessions
timeline
mood_contexts
data_quality
```

---

# 30. Time Representation

The database uses one representation:

```text
UTC epoch milliseconds
```

Exported files use ISO-8601.

For example:

```text
2026-09-05T09:30:10-07:00
```

A daily record stores both:

```text
UTC timestamp
local offset / zone context
```

If the device changes time zone during the day, each event's own offset information must survive, so that an AI does not order the timeline incorrectly.

---

# 31. AI File Design Principle

The JSON provides, together:

```text
Raw-enough timeline
+
Deterministic summaries
+
Measurement definitions
+
Data-quality metadata
```

A DailySummary can tell an AI:

```text
Total use = 247 min
```

while the timeline still lets the AI recompute it independently.

The app must not write:

```text
"Reddit worsened mood"
```

It may write:

```text
Mood at 30 min = 7
Mood at 60 min = 5
```

Causal or clinical interpretation is left to a downstream system.

---

# 32. Output Location

Recommended:

```text
Download/PhoneMoodHealth/
```

File:

```text
2026-09-05-phone-mood.json
```

Written through:

```text
MediaStore.Downloads
```


On Android 10+, a `MediaStore.Downloads` file that the app created and owns does not require general storage permission.

So:

```text
minSdk = 29
```

materially simplifies the storage design.

---

# 33. Daily JSON Example Shape

```json
{
  "schema_version": "1.0",
  "record_type": "daily_phone_usage_and_mood",

  "date": "2026-09-05",

  "measurement": {
    "mood_scale": {
      "min": 1,
      "max": 10,
      "higher_is_better": true
    },
    "mood_interval_minutes": 30,
    "session_reset_minutes": 5
  },

  "daily_summary": {},

  "app_usage": [],

  "sessions": [],

  "timeline": [],

  "mood_contexts": [],

  "data_quality": {}
}
```

---

# 34. Settings Storage

Uses:

```text
DataStore
```

Storing the current configuration:

```text
monitoringEnabled
moodIntervalMinutes
sessionResetMinutes
excludedPackages
```

Historical configuration changes are stored separately in Room's `ConfigurationEvent`.

---

# 35. UI Architecture

Compose UI：

```text
MainActivity
   │
   ├── Today
   ├── Timeline
   ├── Reports
   └── Settings
```

The Reports screen only needs to show:

```text
Sep 5   ✓ Generated
Sep 4   ✓ Generated
Sep 3   ✓ Generated
```

Day to day, the user is not required to open it.

---

# 36. Privacy Boundary

The MVP has no:

```text
Backend
Firebase
Cloud Sync
Telemetry
Ad SDK
Account
```

and can decline to declare:

```text
INTERNET
```

So the core data flow is:

```text
Android OS
  ↓
Local Room DB
  ↓
Local JSON
```

---

# 37. Technology Stack

```text
Kotlin
Jetpack Compose
Room
DataStore
Coroutines
Flow
UsageStatsManager
Foreground Service
NotificationManager
BroadcastReceiver
WorkManager
MediaStore
kotlinx.serialization
```

---

# 38. Package Structure

```text
com.phonemood

├── monitoring/
│   ├── UsageMonitorService
│   ├── UsageEventReader
│   ├── EventProcessor
│   ├── UsageSegmentBuilder
│   ├── SessionTracker
│   └── AppFilter
│
├── mood/
│   ├── MoodScheduler
│   ├── MoodNotificationManager
│   └── MoodRatingActivity
│
├── report/
│   ├── DailyReportGenerator
│   ├── DailyReportReconciliationWorker
│   ├── DailyHealthRecord
│   └── HealthRecordSerializer
│
├── boot/
│   └── BootReceiver
│
├── data/
│   ├── entity/
│   ├── dao/
│   ├── repository/
│   └── database/
│
├── settings/
│
└── ui/
    ├── today/
    ├── timeline/
    ├── reports/
    └── settings/
```

---

# 39. Critical Runtime Path

```text
Android Usage Events
        ↓
UsageMonitorService
        ↓
EventProcessor
        ↓
SessionTracker
        ↓
30-minute boundary?
        ↓ yes
MoodCheckpoint
        ↓
Notification
        ↓
MoodResponse
        ↓
Room
```

Daily path：

```text
Calendar day closes
        ↓
DailyReportGenerator
        ↓
Room queries
        ↓
DailyHealthRecord
        ↓
JSON validation
        ↓
MediaStore.Downloads
```

---

# 40. Failure Model

## Process killed

```text
Recover using UsageStats replay
```

## Reboot

```text
BootReceiver → restore
```

## Usage permission revoked

```text
Stop claiming healthy monitoring
Record gap
Prompt next time UI opens
```

## Notification permission revoked

```text
Continue usage tracking
Mood prompting degraded
Record data-quality issue
```

## Daily Worker delayed

```text
Generate later
```

## JSON generation fails

```text
Keep Room data
Retry
```

## Existing JSON stale

```text
Rebuild from Room
Atomically replace
```

---

# 41. Non-Goals for MVP

Out of scope:

```text
Cloud health record
Automatic diagnosis
LLM inside the Android app
App blocking
Screen-time enforcement
Social features
Cross-device sync
Medical recommendation engine
```

---

# 42. Architecture Summary

PhoneMood's system design reduces to two pipelines.

Real-time behaviour:

```text
Android UsageStats
→ Session reconstruction
→ 30-minute Mood checkpoint
→ Local durable storage
```

Long-horizon health-AI data:

```text
Local durable storage
→ Automatic daily reconstruction
→ Self-contained JSON
→ User can directly give file to Health AI
```

The single most important architectural principle:

**Room is the store of fact. The foreground service is the real-time trigger. UsageStats is the recovery source. And the daily JSON is the stable, AI-facing product interface.**

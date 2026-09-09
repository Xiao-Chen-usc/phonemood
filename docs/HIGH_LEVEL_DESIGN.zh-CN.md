# PhoneMood — 总体设计

*English version: [HIGH_LEVEL_DESIGN.md](HIGH_LEVEL_DESIGN.md)*

版本：1.0
平台：Android
实现语言：Kotlin
最低 Android 版本：Android 10 / API 29
主要持久化：Room / SQLite
AI 交换格式：每天一个自包含 JSON 文件

---

# 1. 架构目标

系统设计优先级：

1. Monitoring 尽可能自动运行；
2. Process death 不应直接导致不可恢复的数据缺失；
3. Mood prompt 尽可能接近 30-minute checkpoint；
4. 内部原始数据可重新计算；
5. 每天自动生成一个 Health-AI-readable 单文件；
6. 不需要后台服务器；
7. 默认不联网；
8. 所有核心操作必须 idempotent。

---

# 2. 为什么用 Kotlin

Kotlin 不是产品要求，而是实现选择。

这个应用的核心不是复杂跨平台 UI，而是 Android 原生能力：

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

因此采用：

```text
Kotlin
+
Jetpack
```

可以避免增加 Flutter / React Native 与 Android native service 之间的 bridge。

如果未来需要 Java，架构本身不依赖 Kotlin。

---

# 3. 系统架构

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

# 4. 基础可靠性模型

系统不能假定：

```text
"Our process will always be alive."
```

采用两层机制。

## 第一层 — 准实时

```text
UsageMonitorService
```

用于：

* 持续查询最新 Usage Events；
* 更新当前 Session；
* 触发 Mood checkpoint；
* 及时通知用户。

## 第二层 — 对账补读

```text
UsageStatsManager.queryEvents()
```

用于：

* 服务重启后的 backfill；
* 恢复遗漏时间窗口；
* 校正内部状态。

Android 允许查询一段时间内的 usage events，但这些详细 events 只保存有限数日，因此本地持久化仍然是必须的。

---

# 5. UsageMonitorService

组件：

```text
Foreground Service
```

职责：

```text
read events
→ process
→ update segments
→ update session
→ evaluate checkpoints
→ persist
```

推荐 polling interval：

```text
10 seconds
```

这意味着 checkpoint 不保证：

```text
30:00.000
```

触发。

可能：

```text
30:04
```

触发，这是产品可以接受的。

---

# 6. 前台服务类型

Android 14+ 要求 foreground service 声明对应 service type。

如果没有更适合的标准类型，本项目可以使用：

```text
specialUse
```

Android 官方将其定义为无法归到其他标准 foreground-service type 的有效场景；需要声明 `FOREGROUND_SERVICE_SPECIAL_USE`，并在 manifest 中解释具体用途。如果未来提交 Google Play，该用途会进入审核。

概念性 Manifest：

```xml
<service
    android:name=".monitoring.UsageMonitorService"
    android:foregroundServiceType="specialUse">

    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="User-enabled local smartphone usage monitoring for periodic self-reported wellbeing assessments." />

</service>
```

Foreground Service 会伴随系统可见 notification，这是 Android foreground-service 模型本身的要求。

---

# 7. BootReceiver

监听：

```text
BOOT_COMPLETED
MY_PACKAGE_REPLACED
```

流程：

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

如果用户之前明确：

```text
Pause Monitoring
```

则：

```text
monitoringEnabled = false
```

BootReceiver 不得重新开启。

---

# 8. UsageEventReader

隔离 Android API。

```kotlin
interface UsageEventReader {
    suspend fun read(
        fromMillis: Long,
        toMillis: Long
    ): List<DeviceUsageEvent>
}
```

内部统一模型：

```text
DeviceUsageEvent

timestampUtc
eventType
packageName
```

Android adapter 负责把 `UsageEvents.Event` 转换成 domain event。

---

# 9. EventProcessor

处理：

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

需要避免：

```text
Service restart
→ overlapping query
→ duplicate data
```

可以允许 query overlap，例如：

```text
lastProcessedTimestamp - 5 seconds
```

然后使用：

```text
timestamp
package
event type
```

确定性去重。

---

# 10. UsageSegmentBuilder

将 foreground activity transition 变成时间段。

例如：

```text
09:00 Reddit resumed
09:12 Chrome resumed
09:21 YouTube resumed
```

生成：

```text
09:00–09:12 Reddit
09:12–09:21 Chrome
09:21–...   YouTube
```

核心模型：

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

默认忽略：

```text
PhoneMood
Launcher
System UI
Keyboard
Permission Controller
```

未来用户可维护 excluded package list。

MVP 的 excluded App：

```text
not included in active phone-use duration
```

---

# 12. SessionTracker

状态机：

```text
IDLE
ACTIVE
INTERRUPTED
```

## IDLE → ACTIVE

检测到有效前台 App。

## ACTIVE → ACTIVE

App switch。

Session 不变。

## ACTIVE → INTERRUPTED

Screen becomes non-interactive / locked。

记录：

```text
interruptionStart
```

## INTERRUPTED → ACTIVE

如果：

```text
interruption < sessionResetThreshold
```

继续原 Session。

## INTERRUPTED → IDLE

如果：

```text
interruption >= threshold
```

结束 Session。

默认：

```text
threshold = 5 minutes
```

---

# 13. 主动使用时长

必须区分：

```text
wall-clock session duration
```

和：

```text
active phone-use duration
```

例如：

```text
10:00–10:20 active
10:20–10:22 locked
10:22–10:32 active
```

结果：

```text
Wall clock = 32 min
Active     = 30 min
```

MoodScheduler 使用：

```text
Active = 30 min
```

---

# 14. MoodScheduler

Session 保存：

```text
nextCheckpointMinutes
```

默认：

```text
30
```

判断：

```text
if activeMinutes >= nextCheckpoint:
    create checkpoint
    nextCheckpoint += configuredInterval
```

不能使用：

```text
activeMinutes % 30 == 0
```

否则 polling 延迟可能直接错过 checkpoint。

---

# 15. 检查点幂等性

数据库唯一约束：

```text
UNIQUE(sessionId, checkpointMinutes)
```

流程必须是：

```text
Create/Persist checkpoint
         ↓
Send notification
```

而不是反过来。

这样：

```text
service restart
replay
duplicate event
```

不会产生第二个 30-minute notification。

---

# 16. MoodRatingActivity

目标 UX：

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

Checkpoint 与 Response 独立保存。

---

# 17. 运行时数据库

使用：

```text
Room / SQLite
```

数据库是内部事实存储。

主要实体：

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

Status：

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

ResponseStatus：

```text
PENDING
ANSWERED
MISSED
```

---

# 20. ConfigurationEvent

配置变化必须进入数据库：

```text
timestampUtc
setting
oldValue
newValue
```

例如：

```text
moodIntervalMinutes
30 → 60
```

这对于长期 AI 分析非常重要。

---

# 21. MonitorState

```text
monitoringEnabled
lastProcessedTimestampUtc
lastSuccessfulQueryUtc
lastHeartbeatUtc
currentSessionId
```

关键原则：

> 重要状态不能只存在于内存中。

---

# 22. 恢复算法

例如：

```text
Last persisted event:
14:02

Service resumes:
14:23
```

执行：

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

# 23. 数据质量与记录缺口

系统维护：

```text
MonitoringGap
```

例如：

```text
startUtc
endUtc
reason
```

Reason：

```text
USAGE_ACCESS_REVOKED
EVENT_HISTORY_UNAVAILABLE
PROCESS_RECOVERY_TOO_LATE
UNKNOWN
```

Daily AI file 必须包含这些 gap。

---

# 24. DailyReportGenerator

这是一级核心组件。

职责：

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

接口：

```kotlin
interface DailyReportGenerator {
    suspend fun generate(
        date: LocalDate
    ): DailyHealthRecordResult
}
```

---

# 25. 日报触发策略

不使用 Exact Alarm。

采用三个触发点。

## 触发点 A — 跨日检测

UsageMonitorService 每次处理时检查：

```text
currentLocalDate != previousLocalDate
```

如果跨日：

```text
finalize yesterday
```

## 触发点 B — 应用启动／服务恢复

每次恢复时检查：

```text
latestGeneratedDate
```

与：

```text
yesterday
```

之间是否有缺失。

全部补生成。

## 触发点 C — WorkManager 对账

设置周期性 worker，负责检查：

```text
Are any closed dates missing their report?
```

WorkManager 的 periodic work 最短周期为 15 分钟，实际执行时间由系统调度和约束决定；它适合作为最终一致性的兜底，而不是精确午夜 timer。

MVP 可以每：

```text
6 hours
```

运行一次 report reconciliation。

---

# 26. 日报状态

数据库：

```text
DailyReportState

localDate
status
lastGeneratedUtc
sourceRevision
lateUpdateCount
```

Status：

```text
MISSING
GENERATED
PARTIAL
FINAL
```

---

# 27. 迟到的回答

边界案例：

```text
23:59 prompt
00:02 response
```

MoodResponse 仍通过 checkpointId 属于前一天。

生成流程必须支持：

```text
Existing daily JSON
       ↓
New late record
       ↓
Rebuild from Room
       ↓
Atomic replace same JSON
```

文件名不改变。

---

# 28. 原子化文件生成

绝不能留下半个 JSON。

流程：

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

数据库永远保留原始数据，因此 JSON 可以重新生成。

---

# 29. DailyHealthRecord 结构

顶层：

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

# 30. 时间表示

数据库统一：

```text
UTC epoch milliseconds
```

导出文件采用 ISO-8601。

例如：

```text
2026-09-05T09:30:10-07:00
```

Daily record 同时记录：

```text
UTC timestamp
local offset / zone context
```

如果设备当天发生 timezone change，事件自身的 offset 信息必须能够保留，避免 AI 错误排列时间。

---

# 31. AI 文件设计原则

JSON 内同时提供：

```text
Raw-enough timeline
+
Deterministic summaries
+
Measurement definitions
+
Data-quality metadata
```

例如 DailySummary 可以告诉 AI：

```text
Total use = 247 min
```

同时 Timeline 仍允许 AI 自己重新计算。

App 不应该写：

```text
"Reddit worsened mood"
```

可以写：

```text
Mood at 30 min = 7
Mood at 60 min = 5
```

因果或临床解释交由 downstream system。

---

# 32. 输出位置

推荐：

```text
Download/PhoneMoodHealth/
```

文件：

```text
2026-09-05-phone-mood.json
```

通过：

```text
MediaStore.Downloads
```

写入。

Android 10+ 对 App 自己创建并拥有的 `MediaStore.Downloads` 文件不要求一般 storage permission。

因此：

```text
minSdk = 29
```

可以显著简化 storage design。

---

# 33. 每日 JSON 示例结构

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

# 34. 设置存储

使用：

```text
DataStore
```

保存当前配置：

```text
monitoringEnabled
moodIntervalMinutes
sessionResetMinutes
excludedPackages
```

历史配置变化另外保存到 Room 的 `ConfigurationEvent`。

---

# 35. 界面架构

Compose UI：

```text
MainActivity
   │
   ├── Today
   ├── Timeline
   ├── Reports
   └── Settings
```

Reports 页面只需要显示：

```text
Sep 5   ✓ Generated
Sep 4   ✓ Generated
Sep 3   ✓ Generated
```

日常并不要求用户进入。

---

# 36. 隐私边界

MVP 不设置：

```text
Backend
Firebase
Cloud Sync
Telemetry
Ad SDK
Account
```

并可以完全不声明：

```text
INTERNET
```

因此核心数据流为：

```text
Android OS
  ↓
Local Room DB
  ↓
Local JSON
```

---

# 37. 技术栈

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

# 38. 包结构

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

# 39. 关键运行路径

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

每日路径：

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

# 40. 失败模型

## 进程被杀

```text
Recover using UsageStats replay
```

## 重启

```text
BootReceiver → restore
```

## 使用情况访问权限被撤销

```text
Stop claiming healthy monitoring
Record gap
Prompt next time UI opens
```

## 通知权限被撤销

```text
Continue usage tracking
Mood prompting degraded
Record data-quality issue
```

## 日报 Worker 被延迟

```text
Generate later
```

## JSON 生成失败

```text
Keep Room data
Retry
```

## 已有 JSON 过期

```text
Rebuild from Room
Atomically replace
```

---

# 41. MVP 的非目标

不做：

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

# 42. 架构总结

PhoneMood 的系统设计可以浓缩成两条 pipeline：

实时行为：

```text
Android UsageStats
→ Session reconstruction
→ 30-minute Mood checkpoint
→ Local durable storage
```

长期 Health AI 数据：

```text
Local durable storage
→ Automatic daily reconstruction
→ Self-contained JSON
→ User can directly give file to Health AI
```

其中最关键的架构原则是：

**Room 是事实存储，Foreground Service 是实时触发器，UsageStats 是恢复来源，而每日 JSON 是稳定的 AI-facing product interface。**

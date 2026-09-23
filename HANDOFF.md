# Handoff: Mood check-ins do not arrive while watching video

*Chinese version: [HANDOFF.zh-CN.md](HANDOFF.zh-CN.md)*

**Status**: the core bug has been located and fixed, but **the user still received no notification
in practice, and the real cause is not yet established**. Diagnosis is blocked on the phone not
connecting over adb. None of the changes are committed.

Separately, §3.7 records power and compute work from 2026-09-12 that changed the polling cadence
and removed the per-poll full table rewrite. **It does not bear on the unsolved mystery in §6.**
§3.8 (2026-09-23) changes how the long-term app cards are chosen and ordered; also unrelated to §6.

Last updated: 2026-09-23

---

## 1. Symptom

After an hour of video on Bilibili / Xiaohongshu / YouTube, the user gets **neither a floating card
nor a notification**.

Second round of feedback, after installing the first fix:

- Still nothing during video.
- **And worse than before**: 1.4.0 at least prompted on switching to another app; after the first
  fix, even that stopped.

---

## 2. Confirmed root causes

### 2.1 The notification suppression in 1.4.0 (original bug, fixed)

`MoodNotificationManager.deliver()` contained an empty branch:

```kotlin
val deferredApp = checkpoint.foregroundPackage == latestForegroundPackage && promptState?.dismissed != true
if (deferredApp) {
    // Keep the prompt pending until the user leaves YouTube.
} else if (...) { /* MISSED */ }
else if (...) { /* actually notifies */ }
```

As long as "the app in the foreground when the checkpoint was raised" equalled "the app in the
foreground now", control entered the empty branch and the notification was never sent. While
watching video that condition is always true.

Meanwhile `UsageMonitorService.kt` carried the comment "Keep the heads-up notification audible
while the same app remains foreground" and carefully computed a `silentCheckpointId` — but
`deliver()` sent no notification at all in that case. **The behaviour the comment described was
dead code, and the implementation did the opposite of the intent.**

Introduced in commit `45d9037` (1.4.0). The tests of the time did not cover it: in
`QuietNotificationTest` the checkpoint package was `"test.app"` while the database held no events,
so `latestForegroundPackage` was always null, `deferredApp` was always false, and the branch was
never exercised.

### 2.2 A regression I introduced (fixed)

The first fix added a `DEFERRAL_LIMIT_MS = 15min` ceiling, intended to stop a checkpoint hanging
forever. The result: after 15 minutes unanswered inside the same app, the checkpoint was judged
MISSED and deleted, so **there was nothing left to present when the user switched away.** That is
exactly the "even switching apps stopped prompting" the user reported.

1.4.0's behaviour was to hang indefinitely, so something was still there on switching away. **That
ceiling has been fully reverted.**

### 2.3 The package name change (unrelated to prompting, but it installed as a new app)

`applicationId` changed from `com.phonemood` to `com.phonemood.app` in 1.4.2, for the Play listing.
Android treats applicationId as an app's permanent identity, so changing it makes a brand-new
application that cannot update the old installation. Signing was not the problem — both use the
same debug key `dcdd99…c82e`.

`app/build.gradle.kts` now has a switch, with the default unchanged:

```bash
./gradlew assembleDebug -PphonemoodApplicationId=com.phonemood   # sideload build, updates in place
./gradlew bundleRelease                                          # for Play, com.phonemood.app
```

---

## 3. Current implementation (second version)

The decision logic is concentrated in `OverlayPolicy` as pure functions, verifiable directly in JVM
unit tests:

```kotlin
fun action(now: Long, prompt: Prompt, foregroundPackage: String?): PromptAction
```

Rules:

| Situation | Behaviour |
|---|---|
| Still inside the app that raised the prompt | **Never expires**; `WAIT` or `NOTIFY` |
| Unanswered inside the same app | Re-sends every `RENOTIFY_INTERVAL_MS` (5 min), capped at `MAX_NOTIFICATIONS` (3) |
| **Switched to another app** | `NOTIFY` immediately, exactly once (deduplicated via `notifiedPackage`) |
| Still unanswered 5 minutes after switching away | `EXPIRE` → MISSED |
| User tapped × to dismiss | Expires on the ordinary 5-minute window |

Supporting changes:

- `setOnlyAlertOnce(false)`: otherwise a re-send would only silently update the existing entry in
  the shade rather than alerting again.
- `PollingPolicy.PENDING_PROMPT_MS = 5_000`: with a prompt outstanding, polling tightens from 10s
  to 5s so that "prompt immediately on switching away" actually happens within 5 seconds. Note that
  `poll()` replays the entire event history and clears and rebuilds segments every time — **a known
  performance hazard, and a reason not to tighten this further.**
  **Superseded by §3.7 (2026-09-12)**: this branch has been deleted, and the hazard it warns about
  has been addressed on the write side.
- `MoodOverlayController`: when the card is attached over its own app, it no longer writes
  `overlayShownUtc` (visibility cannot be confirmed) and instead records
  `lastOverlayError = "OVERLAY_MAY_BE_COVERED"`, so that exported data carries no false "delivered".
- `Database.lastResume()`: replaces three full-table `events()` scans per round.

## 3.5 Third version: backlog coalescing + excluding calls (added 2026-09-08)

### Symptom
Nothing at all during a call or a video; then when a 45-minute call ends and the user switches to
another app, **three notifications and a card all arrive at once**.

### Root cause (fully explained by the code — not a mystery)
1. `SessionEngine.advance()` keeps emitting a checkpoint every `interval` minutes (default **15**).
   45 minutes = 15/30/45, exactly three.
2. The second version's "never expires inside the same app" holds all of them open: during a call
   the screen is off so `screenAvailable()` is false and nothing is sent; during video they are
   sent but swallowed by immersive fullscreen.
3. `deliver()` runs the policy independently for **every** PENDING checkpoint. The moment the user
   switches away, `leftTheApp` becomes true for all three at once, so three alert together — and
   `reconcile()` stacks a card on top.

In other words, the second version fixed "no prompt" by turning it into "prompts held back and
released together".

### The change
| File | Change |
|---|---|
| `OverlayPolicy.kt` | New pure functions `live()` (keep only the newest among those pending) and `retirement()` (how the replaced ones are recorded) |
| `MoodNotificationManager.deliver()` | Select `live` first; retire the rest in place and `cancel()` their notifications. Only the newest continues through the original policy |
| `UsageEventReader.AppFilter` | The in-call screen joins the automatic exclusion set: `com.android.incallui` / `com.samsung.android.incallui` / `com.android.server.telecom` plus `TelecomManager.defaultDialerPackage` (no new permission) |
| `values*/strings.xml` | The exclusion note now mentions the in-call screen |

**Why keep only the newest**: the newest checkpoint's `checkpointMinutes` is already the cumulative
minutes of the whole session (45), so its copy already reads correctly — it can speak for the ones
behind it.

**Two retirement states, deliberately not conflated**:
- `MISSED` — it was asked and the user did not answer (should count as unanswered in the export)
- `SUPERSEDED` — **it was never asked**, a newer one replaced it; it must not count against the
  response rate

`DailyReportGenerator`'s `missed_count` counts only `MISSED`, so SUPERSEDED does not contaminate the
statistics, and `PeriodExport` writes `response_state` verbatim. No analysis code matches
exhaustively on status, so **no database migration is required**.

**Side effect of excluding calls**: a RESUME into the in-call screen triggers `interrupt()`, and the
session closes after `reset` (5 min). So a 45-minute call now produces no checkpoints at all, and
after the call a fresh session starts counting from zero. That is the semantics we want: a phone
call is not the "scrolling" this app is about.

### Verification
- `./gradlew testDebugUnitTest` — **58 tests pass** (2 new: `live()` picks the newest,
  `retirement()` separates the two kinds of retirement)
- `./gradlew assembleDebugAndroidTest` — compiles
- New `QuietNotificationTest.aBacklogOfHeldOpenCheckInsArrivesAsOneNudge`: builds three pending
  checkpoints (the first already notified), switches app, and asserts `MISSED` / `SUPERSEDED` /
  `PENDING` with exactly one entry left in the shade
- ⚠️ **Device tests not run** (still no physical device / no emulator started). Before merging, run
  `./gradlew connectedDebugAndroidTest -PphonemoodApplicationId=com.phonemood`

### Still unsolved
Immersive fullscreen swallowing the heads-up is Android behaviour that cannot be worked around in
code. The guarantee is now downgraded to: **on leaving the video, you get exactly one.** The
forensics in §6 are still worth doing — if even that one does not arrive, the problem is service
survival or permissions, not this layer.

---

## 3.6 Fourth version: making the check-in actually make a sound (added 2026-09-08)

### Symptom
"While watching video it doesn't actually notify me. At the very least it should go *ding*, the way
WeChat does."

### Root cause: the channel has been silent since the day it was created, and cannot be changed
A `NotificationChannel`'s **sound and vibration are immutable after creation** (only name,
description and group can change). The original `"mood"` channel was created as:

```kotlin
NotificationChannel("mood", …, IMPORTANCE_HIGH).apply {
    description = …
    lockscreenVisibility = VISIBILITY_SECRET
}
```

No `setSound()`, no `enableVibration()`. The channel default gives the system default tone and **no
vibration**. While a video is playing, media volume is saturated, notification volume is usually
low, and there is no vibration at all — the missing *ding* is exactly this physical feedback.

More importantly: **adding `enableVibration(true)` inside `createNotificationChannel` has no effect
on the user's phone**, because the channel already exists from the 1.3/1.4 install and the system
ignores every change except the name.

### The change
| File | Change |
|---|---|
| `MoodNotificationManager.kt` | Channel id becomes `mood.v2`, with explicit `setSound(DEFAULT_NOTIFICATION_URI, USAGE_NOTIFICATION)` + `enableVibration(true)` + a vibration pattern; the old `"mood"` channel is retired with `deleteNotificationChannel` |
| same | New `reach(): Reach` with four states: `READY` / `DISABLED` / `SILENCED` / `SUPPRESSED` |
| same | New `preview()`: posts a real check-in-shaped notification on the real channel, writing no data |
| `UsageMonitorService.kt` | New `ACTION_TEST_NOTIFICATION`, fired after a 5-second delay (time to get back into the video) |
| `MainActivity.kt` | The permissions card shows the four states; a new "Test a check-in / Try it" row |
| `values*/strings.xml` | 7 new strings × 2 languages |

⚠️ **Cost of changing the channel id**: any personalization the user applied to the old channel
(turning it off, changing the tone, silencing it) does not carry over — the new channel starts at
its defaults. Here that is a good thing: it is precisely how we escape the silent old channel.

### `canPrompt()` was too permissive
```kotlin
fun canPrompt() = areNotificationsEnabled() && channel?.importance != IMPORTANCE_NONE
```
`IMPORTANCE_LOW` and `IMPORTANCE_DEFAULT` both pass — and **neither pops a heads-up nor makes a
sound**. So once the user or a Samsung ROM demoted the channel, the app believed everything was
fine and went on "successfully" sending silent notifications. Do Not Disturb was not checked at all.

Now they are separated:
- `reach()` gives the real state (Do Not Disturb detected via `areNotificationsPaused()`, API 28+,
  no permission needed)
- `canPrompt()` still blocks only on `DISABLED` (a muted check-in beats none)
- `MonitoringGap` reasons now split three ways: `NOTIFICATIONS_UNAVAILABLE` /
  `NOTIFICATIONS_SILENCED` / `NOTIFICATIONS_SUPPRESSED_BY_DO_NOT_DISTURB`

This adds a line of evidence to the "unsolved mystery" in §6: **the export can now distinguish "the
app never sent it" from "the phone would not pass it on".**

### The "Test a check-in" button — forensics without adb
Settings → System permissions → **Test a check-in → Try it** → switch into fullscreen Bilibili
within 5 seconds.

- **It sounds** → the delivery path is intact; the problem is on the monitoring side (service
  killed / UsageStats / no checkpoint generated). Follow hypotheses 1, 4, 5 in §6.
- **It does not sound** → the problem is on the delivery side; read what the permissions row now
  says (silent delivery / held by Do Not Disturb).

This beats `scripts/diagnose_device.sh` because it needs no adb connection — and adb is exactly
where we are stuck.

### Verification
- `./gradlew testDebugUnitTest` 58 pass; `assembleDebug` / `assembleDebugAndroidTest` both build
- New `QuietNotificationTest.theCheckInChannelAsksToBeHeardAndRetiresTheSilentOne`: asserts the old
  channel is deleted and the new one is `IMPORTANCE_HIGH` with sound and vibration
- ⚠️ Device tests still not run (no device / no emulator started)

### Honest note: this may not be the whole cause
Whether immersive fullscreen swallows the heads-up depends on the ROM. Samsung also has settings
like "Game Booster blocks notifications" and "notification pop-up style" that code cannot reach.
What this version does is **eliminate the layer we can be certain about** (the silent channel) and
**make the remaining layer observable** (`reach()` plus the test button). One press of "Try it"
tells the user which way to look.

---

## 3.7 Power and compute: a coarser cadence, and writing only what changed (added 2026-09-12)

### Why

Not a bug report — a decision about what the app is for. PhoneMood reports awareness, not stopwatch
accuracy, so second-level timeliness was being paid for in battery and in writes without buying
anything a user would notice. Durations are reconstructed from the system's own event timestamps
rather than from the moment the service happened to look, so polling less often costs prompt
latency and nothing else: recorded use, app attribution and check-in thresholds are unaffected.

### What was costing

- The loop polled every 10s while the screen was on, and **every 5s whenever a check-in was
  outstanding** — the most expensive state in the app, entered exactly when the user was already
  being asked something.
- Every poll called `rebuild()`, which replays the whole event log and then **cleared and rewrote
  the entire `UsageSegment` and `PhoneSession` tables**. Between polls only the open tail actually
  moves — the last segment's end and the running session total — so this expressed an O(1) change
  as O(N) deletes and inserts, with two indices per segment row, growing for as long as a
  participant keeps using the app.
- With the analysis screen open, a 60-second tick recomputed **both** the selected period and the
  cumulative dataset. The cumulative one carries leave-one-day-out refits whose cost grows as
  O(days²), and it was being paid every minute to produce an answer that had not changed.

### The change

- `PollingPolicy`: `ACTIVE_MS` 10s → 60s, `POWER_SAVE_MS` 30s → 120s. `PENDING_PROMPT_MS` and its
  whole branch are **deleted**, along with `pendingCheckpoints()` and the `promptPending` plumbing
  in `UsageMonitorService`. This item is a net deletion of code.
- Two constants that were silently tied to the old cadence are now **derived from it**, so the next
  interval change cannot quietly break them: an event is marked zone-inferred past `2 * ACTIVE_MS`,
  and the Today countdown interpolates for `ACTIVE_MS + 30s` before freezing.
- `Repository.rebuild()`: the replay is untouched — it is what lets a late event correct the past.
  Only the write changed. Stored rows are compared against the reconstruction and just the
  differences are written; `clearSegments`/`clearSessions` become delete-by-id, which now fires
  only when late events actually revise history.
- `AnalysisViewModel`: the minute tick no longer rebuilds the cumulative dataset. A `longTermDirty`
  flag carries the real signals (`watchAnswerCount`, `watchConfigurationTime`) across the 300ms
  debounce window, which would otherwise drop a data change that arrived next to a tick.
- `prepareExport` recomputes a carried-over cumulative half, so the two sections of an exported
  document still share one `facts` and report one observation time. On screen the reuse is
  harmless; in a document that states when it was observed, it would not be.

### `DISMISS_GRACE_MS` was left at 30s deliberately

Its comment claimed it existed because pending state pinned polling to the tightest interval, and
that interval is now gone. But raising it would be worse: dismissing a card also cancels its
notification, so nothing is left on screen to answer through, and the window now only decides how
soon the checkpoint is written down as `DISMISSED`. At a 60s cadence, 30s means "the next poll",
which is the intended behaviour. Only the comment changed.

### What this is, and what it is not

| | Before | After |
|---|---|---|
| Screen-on polling | 10s | 60s |
| Check-in outstanding | **5s** | 60s |
| Power save | 30s | 120s |
| Writes per poll | 2N rows deleted and inserted | typically 2 rows |
| `analyze(cumulative)` | every 60s with the screen open | only on a new answer or setting change |

These are counts and write volumes established from the code. **Battery drain on a device is still
not measured** — the same gap `BATTERY_STRATEGY.md` has always carried. The replay itself still
grows with the length of the record and is still unprofiled; bounding it to a sealed horizon was
considered and deliberately deferred, because it touches the property the whole reconstruction
rests on.

### Verification

68 JVM unit tests pass, lint reports 0 errors, and both the app and the instrumented test sources
compile. **The instrumented tests were not run on a device or emulator for this change.**
`PollingPolicyTest` and `ReminderCountdownTest` were updated to the new constants.

---

## 3.8 Long-term observations: app cards by use, with a full list (added 2026-09-23)

### Why

The user asked which apps the "Long-term observations" section shows and in what order. It used
the engine's `topAppIds`: apps with `EARLY_HIGHER` / `EARLY_LOWER`, ordered by whether a deletion
check exists, then `|difference|`, then `n`, then app ID, capped at three. Two problems:

- `difference = beta × h`, where each app has its own `h` (median matched contrast, max 30 min),
  while the card shows the effect rescaled to 15 minutes. **The ranking could disagree with the
  numbers on screen.**
- `n` is the transition count of the whole model, identical for every app, so that tie-break did
  nothing. In practice the order fell back to package name.

The user wanted: among apps with a clear result, their most-used apps first, with a way to open
all of them.

### The change (UI only)

- New `clearAppFindings(stats, data)` in `ui/AnalysisScreen.kt`. It keeps `APP_USAGE` findings with
  status `EARLY_HIGHER` / `EARLY_LOWER`. It sorts by total foreground time summed over
  `longTermData.daily` (the same cumulative history the findings came from), descending, then by app
  ID.
- It shows the first 2 (3 when there is no phone card). If there are more, a button reads "See all
  N apps with a clear result"; when expanded it reads "Show fewer apps" (`analysis_show_all_apps` /
  `analysis_hide_all_apps`, en + zh).
- Flat, mixed and insufficient apps are still never shown. Nothing weaker is padded in.
- **The statistics engine and the export are unchanged.** `top_app_finding_ids` keeps its old
  ordering and its cap of three.
- Docs updated: `ANALYSIS_POLICY_V1`, `FREE_TIER_USER_STORIES` (both languages).

Left alone and worth knowing: the app "flat / mixed / waiting" headlines and the phone "waiting"
headline in `LongTermCard` are unreachable. Only clear-result apps are rendered, and the phone card
is hidden when data is insufficient.

### Verification

69 JVM unit tests pass, including the new `test/…/ui/LongTermAppsTest.kt` (ordering and exclusion).
**Not checked on a device**: the expand/collapse behaviour has not been seen running.

### Packaging mistake this session (fixed)

A plain `assembleDebug` was handed to the user first. It defaulted to `com.phonemood.app` and
installed as **a second app** (§2.3 again). It was rebuilt with `-PphonemoodApplicationId=com.phonemood`
and confirmed with `aapt2 dump badging` (`package: name='com.phonemood' versionCode='11'
versionName='1.4.4'`). The user needs to uninstall the stray `com.phonemood.app` copy; it holds no
data. Copied to `dist/PhoneMood-1.4.4-long-term-apps-debug.apk`.

Also corrected: the user's phone runs the **debug** build (debug key), not a release build. A release
APK built from this tree is `com.phonemood.app` with the upload key, so it cannot update the phone
either.

---

## 4. Database change 3 → 4

`MoodPromptState` gains three columns, to keep **re-delivery** separate from **first delivery**
(`MoodCheckpoint.notifiedUtc` retains first-delivery semantics and is the exported evidence):

```kotlin
val lastNotifiedUtc: Long? = null
@ColumnInfo(defaultValue = "0") val notifyCount: Int = 0
val notifiedPackage: String? = null
```

- `MIGRATION_3_4` is written and registered in `PhoneMoodApp` and `MigrationTest`
- `app/schemas/…/4.json` is generated (untracked)
- `MigrationTest.versionThreeFactsSurviveTheRepeatDeliveryColumns` covers 3→4

⚠️ **The user's phone holds v3 data.** Installing the new build triggers the migration. Exporting a
JSON archive from inside the app first is recommended.

---

## 5. Verification status

| Check | Result |
|---|---|
| JVM unit tests | 56 pass |
| Full device tests (emulator API 35) | 23/23 pass, **twice in a row** |
| Data preserved across an in-place install | Verified: 1.4.0 → new build, only one `com.phonemood`, database hash unchanged |
| **Reverse verification (first version)** | Putting the 1.4.0 logic back does make the new test fail; restoring the fix makes it pass |
| Power and compute work (2026-09-12) | 68 unit tests pass, lint 0 errors, sources compile; **instrumented tests not run** — see §3.7 |

**Honest note**: device tests failed twice along the way, on **two different tests**. I suspected a
background service competing for the window, but the emulator's process list was empty, so that
guess does not hold. I did three things — added a `device.wait` to the one assertion in
`OverlayTest` that counted windows without waiting for the UI (an inherent race), relaxed polling
from 3s to 5s, and cleared leftover processes — after which two full rounds passed. **That is "did
not reproduce", not "eliminated".**

---

## 6. ⚠️ The unsolved mystery (most important)

**After installing the first fix, the user still got not one notification while watching video.**

By the code, the first version should already have pushed an audible notification at the checkpoint
moment (`IMPORTANCE_HIGH` + `setSilent(false)`). The user says there was nothing at all. That
suggests **another undiscovered layer**, which the second version's re-sends and
switch-away top-up cannot rescue either.

Hypotheses to rule out, in order of likelihood:

1. **The monitoring service is not running at all** — Samsung kills background aggressively;
   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is requested but the user may not have granted it
2. **The notification channel is blocked** — `canPrompt()` only checks `areNotificationsEnabled()`
   and channel importance, which cannot catch ROM-level notification management
3. **Immersive fullscreen swallows the heads-up** — the notification was posted (`notifiedUtc` would
   have a value) but the user cannot see it, because the status bar is hidden
4. **UsageStats is not recording** — Usage Access revoked, or a ROM restriction
5. **No checkpoint was generated at all** — something wrong in session accounting

**Items 1 and 3 can be told apart straight from the database**: `notifiedUtc` set = the notification
went out (a visibility problem); null = it was never sent (a service or permission problem).

---

## 7. Next steps

### 7.1 Forensics (current blocker)

`scripts/diagnose_device.sh` is written and retrieves in one pass: app version, service liveness,
the actual values of four permissions, notification channel state, standby bucket, and a full copy
of the database.

**Blocked on the phone not connecting over adb.** The user's phone is a Samsung (device serial
removed from this document). It is visible over USB but exposes only PTP (`0x06`) and CDC
(`0x02`/`0x0A`) interfaces — **no ADB `0xFF/0x42/0x01`** — so `adb devices` does not even show
`unauthorized`.

What the user needs to do:
1. Settings → Developer options → turn on **USB debugging** (developer mode on ≠ USB debugging on)
2. Settings → Security and privacy → turn **Auto Blocker off** (One UI 6.1+ explicitly blocks USB
   commands; the symptoms match exactly)
3. Change the USB mode from **transferring images (PTP)** to **transferring files (MTP)**
4. Accept the "Allow USB debugging?" prompt on the phone

Alternative forensics (no adb needed): have the user **export today's JSON** from the app's analysis
page. It contains `notification_delivered_at`, `response_status`, `floating_card.last_error` and
`monitoring_gaps` — enough to separate hypotheses 1 and 3 above.

### 7.2 Once the data arrives

Follow the hypothesis tree in §6. If it is confirmed to be "the service was killed", a
keep-alive strategy needs consideration; if it is "the notification is not visible", an alternative
channel for fullscreen situations does.

---

## 8. Decisions for the user

1. **Play migration**: `com.phonemood.app` has been uploaded to Play, so that package name is
   permanently locked. A future install from Play becomes a second app again, and a release build is
   not debuggable, so the `run-as` migration route stops working. **The app needs a data import
   feature** (there is only export today). `scripts/migrate_app_data.sh` works only when both sides
   are debug builds.
2. **Re-send rhythm**: currently 5 minutes, at most 3 times. With several checkpoints pending at
   once it may be noisy; this has not been confirmed with the user.
3. **An older unaddressed problem**: after tapping × on the floating card, the notification is still
   sent. This has existed since 1.3.x, is not part of this regression, and changing it touches the
   semantics of "dismiss".

---

## 9. File list

### Changed in this work (all uncommitted)

| File | What changed |
|---|---|
| `mood/OverlayPolicy.kt` | Core: `action()` / `represent()` / `answerableUntil()` pure functions |
| `mood/MoodNotificationManager.kt` | No longer swallows the notification in the same app; re-sends; tops up on switching |
| `mood/MoodOverlayController.kt` | Reverted the deferral ceiling; records no delivery evidence when possibly covered |
| `monitoring/PollingPolicy.kt` | `PENDING_PROMPT_MS = 5s` |
| `monitoring/UsageMonitorService.kt` | Passes `promptPending`; uses `lastResume()` |
| `data/Database.kt` | Schema 3→4; `lastResume()`; `pendingCheckpoints()` |
| `PhoneMoodApp.kt` | Registers `MIGRATION_3_4` |
| `build.gradle.kts` | The `-PphonemoodApplicationId` switch |
| `test/…/OverlayPolicyTest.kt` | 13 policy tests |
| `androidTest/…/QuietNotificationTest.kt` | Same-app delivery + switch-away top-up |
| `androidTest/…/OverlayTest.kt` | Covered-card evidence + fixed one race |
| `androidTest/…/MigrationTest.kt` | 3→4 upgrade test |
| `schemas/…/4.json` | New, untracked |
| `scripts/migrate_app_data.sh` | New, package-name migration |
| `scripts/diagnose_device.sh` | New, on-device forensics |

### Changed in the power and compute work (2026-09-12, §3.7)

| File | What changed |
|---|---|
| `monitoring/PollingPolicy.kt` | 60s / 120s; `PENDING_PROMPT_MS` and its branch deleted |
| `monitoring/UsageMonitorService.kt` | No longer computes or passes `promptPending` |
| `monitoring/UsageEventReader.kt` | Zone-inferred threshold derived from `ACTIVE_MS` |
| `monitoring/ReminderCountdown.kt` | Interpolation ceiling derived from `ACTIVE_MS` |
| `data/Repository.kt` | `rebuild()` writes differences instead of clearing both tables |
| `data/Database.kt` | `clearSegments`/`clearSessions` → delete-by-id; `pendingCheckpoints()` removed |
| `mood/OverlayPolicy.kt` | Comment only: the stated reason for `DISMISS_GRACE_MS` no longer holds |
| `ui/AnalysisScreen.kt` | Minute tick reuses the cumulative half; export recomputes it |
| `test/…/PollingPolicyTest.kt`, `test/…/ReminderCountdownTest.kt` | Updated to the new constants |
| `docs/BATTERY_STRATEGY.md` + `.zh-CN` | Rewritten for the new cadence and the write change |
| `docs/HIGH_LEVEL_DESIGN`, `FREE_ANALYSIS_HIGH_LEVEL_DESIGN`, `FREE_TIER_USER_STORIES` (both languages) | Stale 10s/30s polling figures corrected |

### Changed in the long-term app cards work (2026-09-23, §3.8)

| File | What changed |
|---|---|
| `ui/AnalysisScreen.kt` | `clearAppFindings()`; app cards by total use; expand to all clear-result apps |
| `res/values*/analysis.xml` | `analysis_show_all_apps`, `analysis_hide_all_apps` |
| `test/…/ui/LongTermAppsTest.kt` | New, untracked |
| `docs/ANALYSIS_POLICY_V1`, `docs/FREE_TIER_USER_STORIES` (both languages) | Display rule for app cards |

### Not part of this work (already uncommitted before this session began)

`README.md` · `README.zh-CN.md` · `.gitignore` · `analysis/PeriodDataset.kt` ·
`analysis/PeriodExport.kt` · `analysis/StatisticalEngine.kt` · `ui/AnalysisScreen.kt` ·
`ui/PeriodMoodSummary.kt` · `res/values*/analysis.xml` · `test/…/AnalysisTest.kt` ·
`androidTest/…/AnalysisFeatureTest.kt` · assorted files under `docs/`

⚠️ `build.gradle.kts` is mixed: the `applicationId` change to `com.phonemood.app`, `compileSdk 36`
and the signing configuration were all present before this session; only the
`-PphonemoodApplicationId` switch was added here.

---

## 10. Common commands

```bash
# Sideload build (updates the com.phonemood already on the user's phone in place)
./gradlew assembleDebug -PphonemoodApplicationId=com.phonemood
# Output: app/build/outputs/apk/debug/app-debug.apk
# Latest copy: dist/PhoneMood-1.4.4-long-term-apps-debug.apk (64MB, too large to send directly)
# Without the flag it builds com.phonemood.app and installs as a second app

./gradlew testDebugUnitTest                                        # 69 tests
./gradlew connectedDebugAndroidTest -PphonemoodApplicationId=com.phonemood   # 23 tests

ADB=~/Library/Android/sdk/platform-tools/adb   # adb is not on PATH
scripts/diagnose_device.sh                     # on-device forensics
```

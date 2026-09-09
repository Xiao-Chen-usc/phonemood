# Handoff: Mood check-ins do not arrive while watching video

*Chinese version: [HANDOFF.zh-CN.md](HANDOFF.zh-CN.md)*

**Status**: the core bug has been located and fixed, but **the user still received no notification
in practice, and the real cause is not yet established**. Diagnosis is blocked on the phone not
connecting over adb. None of the changes are committed.

Last updated: 2026-09-08

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
# Copied to: dist/PhoneMood-1.4.4-checkin-fix2-debug.apk (64MB, too large to send directly)

./gradlew testDebugUnitTest                                        # 56 tests
./gradlew connectedDebugAndroidTest -PphonemoodApplicationId=com.phonemood   # 23 tests

ADB=~/Library/Android/sdk/platform-tools/adb   # adb is not on PATH
scripts/diagnose_device.sh                     # on-device forensics
```

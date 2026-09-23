# Adaptive Battery Strategy

*Chinese version: [BATTERY_STRATEGY.zh-CN.md](BATTERY_STRATEGY.zh-CN.md)*

While the screen is on and unlocked, the monitoring service queries once every 60 seconds. In the
system's power-save mode it queries every 120 seconds. After the screen turns off it waits about
one second and queries once more — enough time for the system to write the lock event — and then
stops the periodic timer. Screen-on, unlock, a power-save mode change and a service start request
all trigger a query. No periodic timer runs while the device is locked.

The interval follows from what the app is for. PhoneMood reports awareness, not stopwatch
accuracy: a check-in owed at fifteen minutes of active use is no less useful arriving a minute
later. Because durations are reconstructed from the system's own event timestamps rather than from
the moment the service happened to look, a longer wait costs prompt latency and nothing else —
recorded use, app attribution and check-in thresholds are unaffected. An earlier version also
tightened polling to 5 seconds whenever a check-in was waiting to be answered; that was the most
expensive state in the loop and bought only latency, so it has been removed.

Two other values are now read off the polling interval rather than set independently. Both were
written against the older, faster cadence and lose their meaning if they fall below it: an event is
marked as carrying an inferred time zone when it arrives more than two poll intervals late, and the
Today countdown interpolates between records for one poll interval plus thirty seconds before it
freezes and says it is waiting for an update.

When querying resumes, it re-reads `UsageEvents` from the progress point saved in the database.
Use time and check-ins are reconstructed from event timestamps. A session's closed state may
therefore be reported late while the device is locked, and events that arrive late are corrected
on a subsequent query. The existing limit on recovering history older than three days still
applies. No wake locks and no exact alarms are used, so a catch-up query is not guaranteed to run
promptly while the system is asleep.

The separate daily-report job can still read data while the screen is off: the existing six-hour
WorkManager job, and report refreshes requested by the user, are unaffected by the service's
polling pause.

The daily report keeps `poll_interval_seconds` to state the base frequency during normal use, and
adds `polling_policy` to describe the adaptive behavior. Both are read from the polling policy, so
they follow any change to it. Settings notes that power-save mode may increase check-in latency.

Every query still replays the whole event history, which is what allows a late event to correct
the past. What it no longer does is write that whole reconstruction back. Between polls only the
open tail moves — the last segment's end and the running session total — so stored rows are
compared and only the differences are written; rows are deleted only when late events revise
history. The replay itself still grows with the length of the record and has not been profiled.

Verification for this change: the full unit suite (68 tests) passes and lint reports zero errors;
the instrumented test sources compile but were not run on a device. The polling-policy branch
tests and the duration and checkpoint recovery tests across an overnight lock remain in the unit
suite. **Battery drain on a physical device has not been measured**, and per-manufacturer lock
broadcasts and background survival have not been verified.

Platform reference: [Android PowerManager](https://developer.android.com/reference/android/os/PowerManager).

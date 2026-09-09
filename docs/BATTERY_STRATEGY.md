# Adaptive Battery Strategy

*Chinese version: [BATTERY_STRATEGY.zh-CN.md](BATTERY_STRATEGY.zh-CN.md)*

While the screen is on and unlocked, the monitoring service queries once every 10 seconds. In the
system's power-save mode it queries every 30 seconds. After the screen turns off it waits about
one second and queries once more — enough time for the system to write the lock event — and then
stops the periodic timer. Screen-on, unlock, a power-save mode change and a service start request
all trigger a query. No periodic timer runs while the device is locked.

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
adds `polling_policy` to describe the adaptive behavior. Settings notes that power-save mode may
increase check-in latency.

Verification covers branch tests of the polling policy, tests of duration and checkpoint recovery
across an overnight lock, plus the existing unit tests, the APK build and lint. **Battery drain
on a physical device has not been measured**, and per-manufacturer lock broadcasts and background
survival have not been verified. Every query still rebuilds the record history, so the cost as
long-term data grows needs further profiling.

Platform reference: [Android PowerManager](https://developer.android.com/reference/android/os/PowerManager).

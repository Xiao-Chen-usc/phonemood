# 1.3.1: Quiet Tracking

*Chinese version: [QUIET_NOTIFICATIONS.zh-CN.md](QUIET_NOTIFICATIONS.zh-CN.md)*

- The background notification now reads "PhoneMood · Usage tracking" ("PhoneMood · 使用时长统计"
  in Chinese). The earlier "listening" wording is gone.
- The background notification is low importance, silent, without vibration, does not re-alert, and
  does not show a timestamp. The background notification channel is created with sound, vibration
  and badge all disabled.
- Both the background notification and mood check-ins are set to `VISIBILITY_SECRET`, requesting
  that they be hidden on a secure lock screen. The existing notification channel is retained so
  that the user's own system notification settings are respected; manufacturer software and user
  settings can still affect what actually appears on the lock screen. If notifications remain
  visible on the lock screen after upgrading, "Usage tracking" can be set to hide on the lock
  screen in the system's PhoneMood notification settings.
- No new mood notification is delivered while the screen is off or locked. After unlocking, the
  next polling round handles any check-in that is still valid, keeping the original five-minute
  lifetime. An expired check-in is not delivered late.
- The floating card's existing lock-screen hiding behavior is unchanged.
- The foreground service keeps running; background tracking and automatic start are unchanged.
  PhoneMood may still appear in Android's list of running applications.

Install `dist/PhoneMood-1.3.1-debug.apk` over the existing version. Do not uninstall the old one
first.

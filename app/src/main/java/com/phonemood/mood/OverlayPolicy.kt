package com.phonemood.mood

/** This policy is independent of Android windows and can be replayed after process death. */
object OverlayPolicy {
    const val PROMPT_LIFETIME_MS = 300_000L
    const val SNOOZE_MS = 60_000L
    fun eligible(now: Long, thresholdUtc: Long, status: String, dismissed: Boolean, snoozedUntil: Long?): Boolean =
        status == "PENDING" && now >= thresholdUtc && now - thresholdUtc < PROMPT_LIFETIME_MS && !dismissed && (snoozedUntil == null || now >= snoozedUntil)
    fun canSnooze(now: Long, thresholdUtc: Long) = now + SNOOZE_MS < thresholdUtc + PROMPT_LIFETIME_MS
}

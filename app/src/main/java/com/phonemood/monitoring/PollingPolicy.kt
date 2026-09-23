package com.phonemood.monitoring

/** Best-effort delays; never wake the device to poll. */
object PollingPolicy {
    /**
     * This app reports awareness, not stopwatch accuracy. Durations are reconstructed from the
     * system's own event timestamps, not from the moment we happened to look, so waiting longer
     * between polls costs prompt latency and nothing else: a check-in owed at fifteen minutes of
     * active use is no less useful arriving a minute later.
     */
    const val ACTIVE_MS = 60_000L
    const val POWER_SAVE_MS = 120_000L
    // null means wait for a broadcast/service request instead of a timer.
    fun intervalMillis(interactive: Boolean, locked: Boolean, powerSave: Boolean): Long? = when {
        !interactive || locked -> null
        powerSave -> POWER_SAVE_MS
        else -> ACTIVE_MS
    }
}

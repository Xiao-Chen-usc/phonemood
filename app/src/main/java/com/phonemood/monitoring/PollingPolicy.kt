package com.phonemood.monitoring

/** Best-effort delays; never wake the device to poll. */
object PollingPolicy {
    const val ACTIVE_MS = 10_000L
    const val POWER_SAVE_MS = 30_000L
    // null means wait for a broadcast/service request instead of a timer.
    fun intervalMillis(interactive: Boolean, locked: Boolean, powerSave: Boolean): Long? = when {
        !interactive || locked -> null
        powerSave -> POWER_SAVE_MS
        else -> ACTIVE_MS
    }
}

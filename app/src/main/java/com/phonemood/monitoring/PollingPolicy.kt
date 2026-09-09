package com.phonemood.monitoring

/** Best-effort delays; never wake the device to poll. */
object PollingPolicy {
    const val ACTIVE_MS = 10_000L
    const val POWER_SAVE_MS = 30_000L
    /**
     * A prompt is waiting for the user to leave the app it was raised in, so notice that sooner.
     * Each poll replays the whole event history, so this stays well above the active interval's
     * cost only for the bounded stretch where a prompt is actually outstanding.
     */
    const val PENDING_PROMPT_MS = 5_000L
    // null means wait for a broadcast/service request instead of a timer.
    fun intervalMillis(interactive: Boolean, locked: Boolean, powerSave: Boolean, promptPending: Boolean = false): Long? = when {
        !interactive || locked -> null
        powerSave -> POWER_SAVE_MS
        promptPending -> PENDING_PROMPT_MS
        else -> ACTIVE_MS
    }
}

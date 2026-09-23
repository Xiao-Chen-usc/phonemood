package com.phonemood.monitoring

import com.phonemood.data.PhoneSession

/** Display-only interpolation between monitor polls; never writes usage or triggers reminders. */
fun reminderRemainingMs(session: PhoneSession?, intervalMinutes: Int, recordedAt: Long, now: Long, advance: Boolean): Long {
    if (session == null || session.status == "CLOSED") return intervalMinutes * 60_000L
    val elapsed = if (advance && session.status == "ACTIVE") (now - recordedAt).coerceIn(0, PollingPolicy.ACTIVE_MS + 30_000) else 0
    return (session.nextCheckpointMinutes * 60_000L - session.activeDurationMs - elapsed).coerceAtLeast(0)
}

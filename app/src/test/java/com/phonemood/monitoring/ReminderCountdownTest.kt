package com.phonemood.monitoring

import com.phonemood.data.PhoneSession
import org.junit.Assert.*
import org.junit.Test

class ReminderCountdownTest {
    private val session = PhoneSession("s", 0, null, 600_000, "ACTIVE", 15)
    @Test fun `counts seconds between polls and remains continuous after next poll`() {
        assertEquals(299_000L, reminderRemainingMs(session, 15, 600_000, 601_000, true))
        assertEquals(290_000L, reminderRemainingMs(session.copy(activeDurationMs = 610_000), 15, 610_000, 610_000, true))
    }
    @Test fun `paused and interrupted usage does not advance`() {
        assertEquals(300_000L, reminderRemainingMs(session, 15, 600_000, 610_000, false))
        assertEquals(300_000L, reminderRemainingMs(session.copy(status = "INTERRUPTED"), 15, 600_000, 610_000, true))
    }
    @Test fun `reaching threshold waits at zero until monitor advances it`() {
        assertEquals(0L, reminderRemainingMs(session.copy(activeDurationMs = 899_000), 15, 899_000, 901_000, true))
        assertEquals(899_000L, reminderRemainingMs(session.copy(activeDurationMs = 901_000, nextCheckpointMinutes = 30), 15, 901_000, 901_000, true))
    }
    @Test fun `closed sessions use configured interval and stale data cannot count forever`() {
        assertEquals(1_800_000L, reminderRemainingMs(null, 30, 0, 10, true))
        assertEquals(900_000L, reminderRemainingMs(session.copy(status = "CLOSED"), 15, 0, 10, true))
        assertEquals(210_000L, reminderRemainingMs(session, 15, 600_000, 900_000, true))
        assertEquals(300_000L, reminderRemainingMs(session, 15, 600_000, 599_000, true))
    }
}

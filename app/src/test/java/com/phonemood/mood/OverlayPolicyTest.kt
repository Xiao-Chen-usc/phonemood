package com.phonemood.mood

import org.junit.Assert.*
import org.junit.Test

class OverlayPolicyTest {
    @Test fun `only pending checkpoints inside their time window may appear`() {
        assertTrue(OverlayPolicy.eligible(1_000, 1_000, "PENDING", false, null))
        assertFalse(OverlayPolicy.eligible(999, 1_000, "PENDING", false, null))
        assertFalse(OverlayPolicy.eligible(301_000, 1_000, "PENDING", false, null))
        assertFalse(OverlayPolicy.eligible(2_000, 1_000, "ANSWERED", false, null))
        assertFalse(OverlayPolicy.eligible(2_000, 1_000, "MISSED", false, null))
    }
    @Test fun `dismissal remains effective after replay`() { repeat(3) { assertFalse(OverlayPolicy.eligible(2_000, 1_000, "PENDING", true, null)) } }
    @Test fun `snooze resumes exactly at deadline`() {
        assertFalse(OverlayPolicy.eligible(60_999, 1_000, "PENDING", false, 61_000))
        assertTrue(OverlayPolicy.eligible(61_000, 1_000, "PENDING", false, 61_000))
    }
    @Test fun `expired prompts cannot be revived by snooze`() { assertFalse(OverlayPolicy.eligible(401_000, 1_000, "PENDING", false, 61_000)) }
    @Test fun `late snooze cannot promise a minute beyond expiry`() {
        assertTrue(OverlayPolicy.canSnooze(1_000, 1_000))
        assertFalse(OverlayPolicy.canSnooze(241_000, 1_000))
    }
}

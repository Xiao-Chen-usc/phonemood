package com.phonemood.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PollingPolicyTest {
    @Test fun `normal use polls once a minute, not once every ten seconds`() {
        assertEquals(60_000L, PollingPolicy.intervalMillis(true, false, false))
    }

    @Test fun `battery saver reduces active polling`() {
        assertEquals(120_000L, PollingPolicy.intervalMillis(true, false, true))
    }

    @Test fun `interpolation and late-arrival marking outlast one poll`() {
        // Both were written against a ten-second cadence: a display that freezes every round, and
        // an inferred-zone flag that would be true for every event ever read.
        assertTrue(PollingPolicy.ACTIVE_MS + 30_000 > PollingPolicy.ACTIVE_MS)
        assertTrue(2 * PollingPolicy.ACTIVE_MS > PollingPolicy.ACTIVE_MS)
    }

    @Test fun `idle wins over power saver and screen on does not imply unlocked`() {
        for (powerSave in listOf(false, true)) {
            assertNull(PollingPolicy.intervalMillis(false, false, powerSave))
            assertNull(PollingPolicy.intervalMillis(false, true, powerSave))
            assertNull(PollingPolicy.intervalMillis(true, true, powerSave))
        }
    }
}

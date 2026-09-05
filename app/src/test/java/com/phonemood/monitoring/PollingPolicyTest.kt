package com.phonemood.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PollingPolicyTest {
    @Test fun `normal use preserves ten second responsiveness`() {
        assertEquals(10_000L, PollingPolicy.intervalMillis(true, false, false))
    }

    @Test fun `battery saver reduces active polling`() {
        assertEquals(30_000L, PollingPolicy.intervalMillis(true, false, true))
    }

    @Test fun `idle wins over power saver and screen on does not imply unlocked`() {
        for (powerSave in listOf(false, true)) {
            assertNull(PollingPolicy.intervalMillis(false, false, powerSave))
            assertNull(PollingPolicy.intervalMillis(false, true, powerSave))
            assertNull(PollingPolicy.intervalMillis(true, true, powerSave))
        }
    }
}

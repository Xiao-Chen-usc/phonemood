package com.phonemood.mood

import org.junit.Assert.*
import org.junit.Test

class OverlayPolicyTest {
    private val video = "com.example.video"
    private val other = "com.example.other"
    private val threshold = 1_000L
    private fun prompt(overlayShownUtc: Long? = null, lastNotifiedUtc: Long? = null, notifyCount: Int = 0,
                       notifiedPackage: String? = null, dismissed: Boolean = false) =
        OverlayPolicy.Prompt(threshold, video, overlayShownUtc, lastNotifiedUtc, notifyCount, notifiedPackage, dismissed)

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

    @Test fun `an unanswered prompt is never written off while its own app is still in front`() {
        // The bound this replaces marked an hour-long video session MISSED before the user ever
        // left the app, so switching away no longer prompted.
        assertEquals(OverlayPolicy.PromptAction.WAIT, OverlayPolicy.action(threshold + 3_600_000,
            prompt(lastNotifiedUtc = threshold, notifyCount = OverlayPolicy.MAX_NOTIFICATIONS, notifiedPackage = video), video))
    }
    @Test fun `an unanswered prompt asks again on a rhythm inside the app`() {
        assertEquals(OverlayPolicy.PromptAction.WAIT, OverlayPolicy.action(threshold + 299_000,
            prompt(lastNotifiedUtc = threshold, notifyCount = 1, notifiedPackage = video), video))
        assertEquals(OverlayPolicy.PromptAction.NOTIFY, OverlayPolicy.action(threshold + OverlayPolicy.RENOTIFY_INTERVAL_MS,
            prompt(lastNotifiedUtc = threshold, notifyCount = 1, notifiedPackage = video), video))
    }
    @Test fun `repeat delivery stops at the cap instead of nagging`() {
        assertEquals(OverlayPolicy.PromptAction.WAIT, OverlayPolicy.action(threshold + 3_000_000,
            prompt(lastNotifiedUtc = threshold, notifyCount = OverlayPolicy.MAX_NOTIFICATIONS, notifiedPackage = video), video))
    }
    @Test fun `leaving the app prompts at once, and only once`() {
        val leaving = OverlayPolicy.action(threshold + 3_600_000,
            prompt(lastNotifiedUtc = threshold, notifyCount = OverlayPolicy.MAX_NOTIFICATIONS, notifiedPackage = video), other)
        assertEquals(OverlayPolicy.PromptAction.NOTIFY, leaving)
        // After that delivery the recorded package is the new one, so it does not repeat.
        assertEquals(OverlayPolicy.PromptAction.WAIT, OverlayPolicy.action(threshold + 3_600_100,
            prompt(lastNotifiedUtc = threshold + 3_600_000, notifyCount = 4, notifiedPackage = other), other))
    }
    @Test fun `expiry waits until the user has left the app and ignored it there`() {
        assertEquals(OverlayPolicy.PromptAction.EXPIRE, OverlayPolicy.action(threshold + 3_900_001,
            prompt(lastNotifiedUtc = threshold + 3_600_000, notifyCount = 4, notifiedPackage = other), other))
    }
    @Test fun `a first delivery keeps its ordinary window when no foreground app is known`() {
        assertEquals(OverlayPolicy.PromptAction.NOTIFY, OverlayPolicy.action(threshold, prompt(), null))
        assertEquals(OverlayPolicy.PromptAction.EXPIRE, OverlayPolicy.action(threshold + 300_001, prompt(), null))
    }
    @Test fun `dismissal ends the prompt on the ordinary window even inside its app`() {
        assertEquals(OverlayPolicy.PromptAction.EXPIRE, OverlayPolicy.action(threshold + 300_001,
            prompt(lastNotifiedUtc = threshold, notifiedPackage = video, dismissed = true), video))
    }
    @Test fun `a prompt returns when another app comes forward`() {
        assertTrue(OverlayPolicy.represent("PENDING", dismissed = false, snoozedUntil = null, foregroundChanged = true))
        assertFalse(OverlayPolicy.represent("PENDING", dismissed = false, snoozedUntil = null, foregroundChanged = false))
        assertFalse(OverlayPolicy.represent("PENDING", dismissed = true, snoozedUntil = null, foregroundChanged = true))
        assertFalse(OverlayPolicy.represent("MISSED", dismissed = false, snoozedUntil = null, foregroundChanged = true))
    }
    @Test fun `a notification outlives nothing, except while the prompt is held open`() {
        assertEquals(301_000L, OverlayPolicy.answerableUntil(1_000, null, null, heldOpen = false))
        assertEquals(651_000L, OverlayPolicy.answerableUntil(1_000, 351_000, null, heldOpen = false))
        assertEquals(700_000L, OverlayPolicy.answerableUntil(1_000, null, 400_000, heldOpen = false))
        assertEquals(Long.MAX_VALUE, OverlayPolicy.answerableUntil(1_000, null, null, heldOpen = true))
    }
    @Test fun `a backlog of held-open check-ins speaks through its newest`() {
        assertNull(OverlayPolicy.live(emptyList<Long>()) { it })
        // Three quarter-hours of one 45-minute call, in the order the database returns them.
        assertEquals(45L, OverlayPolicy.live(listOf(15L, 30L, 45L)) { it })
        assertEquals("late", OverlayPolicy.live(listOf("early" to 1_000L, "late" to 2_000L)) { it.second }?.first)
    }
    @Test fun `retiring a replaced check-in keeps asked and never-asked apart`() {
        assertEquals("MISSED", OverlayPolicy.retirement(everNotified = true))
        assertEquals("SUPERSEDED", OverlayPolicy.retirement(everNotified = false))
    }
}

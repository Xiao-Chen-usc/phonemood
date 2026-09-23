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
    @Test fun `a notification retry opens a bounded card window without requiring an app switch`() {
        val retryAt = threshold + 600_000
        val windowStart = OverlayPolicy.cardWindowStart(threshold, retryAt)
        assertTrue(OverlayPolicy.eligible(retryAt, windowStart, "PENDING", false, null))
        assertFalse(OverlayPolicy.eligible(retryAt + 300_000, windowStart, "PENDING", false, null))
        assertFalse(OverlayPolicy.eligible(retryAt, windowStart, "PENDING", true, null))
        assertFalse(OverlayPolicy.eligible(retryAt, windowStart, "ANSWERED", false, null))
        assertFalse(OverlayPolicy.eligible(retryAt, windowStart, "PENDING", false, retryAt + 60_000))
        assertEquals(threshold, OverlayPolicy.cardWindowStart(threshold, null))
        assertEquals(threshold, OverlayPolicy.cardWindowStart(threshold, threshold - 1))
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
    @Test fun `a card pushed aside retires in half a minute, not five`() {
        // Answering is two taps. Holding a turned-down prompt pending for the full lifetime kept
        // the tightest polling interval alive for five minutes with nothing left to ask.
        val pushed = prompt(overlayShownUtc = threshold, lastNotifiedUtc = threshold, notifyCount = 1, notifiedPackage = video, dismissed = true)
        assertEquals(OverlayPolicy.PromptAction.WAIT, OverlayPolicy.action(threshold + 30_000, pushed, video))
        assertEquals(OverlayPolicy.PromptAction.EXPIRE, OverlayPolicy.action(threshold + 30_001, pushed, video))
    }
    @Test fun `a prompt turned down is never nudged again, in or out of its app`() {
        val pushed = prompt(overlayShownUtc = threshold, lastNotifiedUtc = threshold, notifyCount = 1, notifiedPackage = video, dismissed = true)
        assertEquals(OverlayPolicy.PromptAction.WAIT, OverlayPolicy.action(threshold + 1_000, pushed, other))
        assertEquals(OverlayPolicy.PromptAction.EXPIRE, OverlayPolicy.action(threshold + 60_000, pushed, other))
    }
    @Test fun `being pushed aside is a decision, and never counts as a miss`() {
        assertEquals("DISMISSED", OverlayPolicy.expiry(dismissed = true))
        assertEquals("MISSED", OverlayPolicy.expiry(dismissed = false))
    }
    @Test fun `a check-in nobody was ever shown is not a check-in the user ignored`() {
        // Monitoring stopped, the stretch was reconstructed later, and the prompt arrived past
        // its own window having never been notified or drawn.
        assertEquals("UNASKED", OverlayPolicy.expiry(dismissed = false, everPresented = false))
        assertEquals("MISSED", OverlayPolicy.expiry(dismissed = false, everPresented = true))
        // A card the user pushed aside was seen, whatever the delivery record says afterwards.
        assertEquals("DISMISSED", OverlayPolicy.expiry(dismissed = true, everPresented = false))
    }
    @Test fun `the card outlives its delivery window while its own app is still in front`() {
        // The budget for making a sound ran out long ago; the question is still unanswered and
        // the user never left the video, so the card has no business disappearing.
        val past = OverlayPolicy.PROMPT_LIFETIME_MS * 4
        assertFalse(OverlayPolicy.eligible(1_000 + past, 1_000, "PENDING", false, null))
        assertTrue(OverlayPolicy.eligible(1_000 + past, 1_000, "PENDING", false, null, heldOpen = true))
    }
    @Test fun `holding a card open never overrides an answer, a snooze or a push`() {
        val past = OverlayPolicy.PROMPT_LIFETIME_MS * 4
        assertFalse(OverlayPolicy.eligible(1_000 + past, 1_000, "ANSWERED", false, null, heldOpen = true))
        assertFalse(OverlayPolicy.eligible(1_000 + past, 1_000, "PENDING", true, null, heldOpen = true))
        assertFalse(OverlayPolicy.eligible(2_000, 1_000, "PENDING", false, 61_000, heldOpen = true))
        // And it never brings a card forward before the check-in is due.
        assertFalse(OverlayPolicy.eligible(999, 1_000, "PENDING", false, null, heldOpen = true))
    }

    @Test fun `three hours of video still produce twelve independent reminders when earlier ones are ignored`() {
        val engine = com.phonemood.monitoring.SessionEngine()
        val events = listOf(
            com.phonemood.monitoring.Event(0, "START", interval = 15),
            com.phonemood.monitoring.Event(0, "RESUME", video))
        val unanswered = mutableListOf<OverlayPolicy.Prompt>()
        repeat(12) { index ->
            val now = (index + 1) * 900_000L
            assertEquals(index, engine.rebuild(events, now - 1).checkpoints.size)
            val checkpoint = engine.rebuild(events, now).checkpoints.last()
            assertEquals(now, checkpoint.at)
            val next = OverlayPolicy.Prompt(checkpoint.at, video)
            unanswered += next
            val live = OverlayPolicy.live(unanswered) { it.thresholdUtc }!!
            assertEquals(next, live)
            assertEquals(OverlayPolicy.PromptAction.NOTIFY, OverlayPolicy.action(now, live, video))
            // Alternate between dismissal and no response, keeping previous prompt state.
            unanswered[unanswered.lastIndex] = next.copy(lastNotifiedUtc = now,
                notifyCount = 1, notifiedPackage = video, dismissed = index % 2 == 0)
        }
        assertEquals(12, engine.rebuild(events, 187 * 60_000L).checkpoints.size)
    }
}

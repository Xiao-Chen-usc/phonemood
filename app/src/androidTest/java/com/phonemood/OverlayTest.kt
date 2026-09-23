package com.phonemood

import android.content.Context
import android.provider.Settings
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.phonemood.data.*
import com.phonemood.mood.*
import com.phonemood.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var context: Context
    private lateinit var db: PhoneMoodDatabase
    private lateinit var repository: Repository
    private lateinit var controller: MoodOverlayController
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        if (!device.isScreenOn) device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.pressHome()
        assertTrue(Settings.canDrawOverlays(context))
        db = Room.inMemoryDatabaseBuilder(context, PhoneMoodDatabase::class.java).build()
        repository = Repository(context, db, SettingsStore(context))
        val config = repository.settings.flow.first()
        repository.settings.save(config.copy(overlayEnabled = true))
        db.dao().saveState(MonitorState(monitoringEnabled = true))
        instrumentation.runOnMainSync { controller = MoodOverlayController(context, repository) {} }
    }
    @After fun cleanup() { instrumentation.runOnMainSync { controller.destroy() }; db.close() }
    private suspend fun checkpoint(at: Long = System.currentTimeMillis()) = MoodCheckpoint("overlay-test", "session", 30, at, "test.app", "UTC").also { db.dao().insertCheckpoints(listOf(it)) }
    /**
     * WindowManager accepts the card well before its contents reach the accessibility tree, and
     * an emulator under load can stretch that gap past five seconds. Waiting longer costs
     * nothing except where a card is genuinely missing, which is the case worth failing on.
     */
    private val cardTimeoutMs = 15_000L
    private fun button(description: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.desc(description)), cardTimeoutMs)) { "Missing overlay action: $description" }
    @Test fun tapOverAnotherAppSavesOnceAndCloses() = runBlocking {
        val c = checkpoint()
        assertEquals(c.checkpointId, controller.reconcile())
        assertFalse(device.executeShellCommand("dumpsys activity activities").lineSequence().first { it.contains("topResumedActivity=") }.contains("${context.packageName}/"))
        button(context.getString(R.string.score_accessibility, 7)).click()
        assertTrue(device.wait(Until.gone(By.desc(context.getString(R.string.score_accessibility, 7))), 5_000))
        assertEquals(7, db.dao().responses().single().score)
        assertEquals("ANSWERED", db.dao().checkpoint(c.checkpointId)!!.responseStatus)
        assertNull(controller.reconcile())
        assertEquals(1, db.dao().responses().size)
    }
    private suspend fun heldOpenPastItsBudget(): MoodCheckpoint {
        // Three deliveries is all a check-in is allowed inside the app that raised it, and the
        // last of them was several lifetimes ago. The user never left the video.
        val at = System.currentTimeMillis() - OverlayPolicy.PROMPT_LIFETIME_MS * 3
        db.dao().insertEvents(listOf(RawEvent("resume-test-app", at, "RESUME", "test.app", "Test", "UTC")))
        val c = checkpoint(at)
        db.dao().savePromptState(MoodPromptState(c.checkpointId, lastNotifiedUtc = at,
            notifyCount = OverlayPolicy.MAX_NOTIFICATIONS, notifiedPackage = "test.app"))
        return c
    }
    @Test fun aCardStaysUpPastItsDeliveryBudgetWhileTheSameAppIsInFront() = runBlocking {
        val c = heldOpenPastItsBudget()
        assertEquals(c.checkpointId, controller.reconcile())
        assertNotNull(button(context.getString(R.string.score_accessibility, 7)))
    }
    @Test fun aHeldOpenCardComesDownOnceAnotherAppComesForward() = runBlocking {
        val c = heldOpenPastItsBudget()
        assertEquals(c.checkpointId, controller.reconcile())
        db.dao().insertEvents(listOf(RawEvent("resume-other", c.promptTimestampUtc + 1, "RESUME", "com.example.other", "Other", "UTC")))
        assertNull(controller.reconcile())
        assertTrue(device.wait(Until.gone(By.desc(context.getString(R.string.score_accessibility, 7))), 5_000))
    }
    @Test fun repeatedPollUsesOneWindowAndDismissalSurvivesControllerRecreation() = runBlocking {
        val c = checkpoint()
        controller.reconcile(); controller.reconcile()
        // Wait for the window to reach the accessibility tree before counting it; addView is
        // asynchronous, so counting straight away races the compositor on a loaded device.
        assertNotNull(button(context.getString(R.string.score_accessibility, 7)))
        assertEquals(1, device.findObjects(By.desc(context.getString(R.string.score_accessibility, 7))).size)
        button(context.getString(R.string.dismiss_mood_card)).click()
        assertTrue(device.wait(Until.gone(By.desc(context.getString(R.string.score_accessibility, 7))), 5_000))
        assertTrue(db.dao().promptState(c.checkpointId)!!.dismissed)
        instrumentation.runOnMainSync { controller.destroy(); controller = MoodOverlayController(context, repository) {} }
        assertNull(controller.reconcile())
        assertTrue(db.dao().responses().isEmpty())
    }
    @Test fun snoozePersistsAndReappearsAfterDeadline() = runBlocking {
        val c = checkpoint()
        controller.reconcile()
        button(context.getString(R.string.remind_me_in_one_minute)).click()
        assertTrue(device.wait(Until.gone(By.desc(context.getString(R.string.score_accessibility, 7))), 5_000))
        val deadline = db.dao().promptState(c.checkpointId)!!.snoozedUntilUtc!!
        assertNull(controller.reconcile(deadline - 1))
        assertEquals(c.checkpointId, controller.reconcile(deadline))
        assertNotNull(button(context.getString(R.string.score_accessibility, 7)))
    }
    @Test fun previewDoesNotCreateMoodRecords() = runBlocking {
        instrumentation.runOnMainSync { assertTrue(controller.showPreview()) }
        button(context.getString(R.string.score_accessibility, 9)).click()
        assertTrue(device.wait(Until.gone(By.desc(context.getString(R.string.score_accessibility, 9))), 5_000))
        assertTrue(db.dao().responses().isEmpty()); assertTrue(db.dao().checkpoints().isEmpty())
    }
    @Test fun permissionRevocationFallsBackWithoutCrashingOrRecordingAResponse() = runBlocking {
        checkpoint(); controller.reconcile()
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW deny")
        assertNull(controller.reconcile())
        assertTrue(db.dao().responses().isEmpty())
    }
    @Test fun pausedMonitoringAndLockedScreenDoNotShowCards() = runBlocking {
        checkpoint(); db.dao().saveState(MonitorState(monitoringEnabled = false))
        assertNull(controller.reconcile())
        db.dao().saveState(MonitorState(monitoringEnabled = true))
        device.sleep()
        try { assertNull(controller.reconcile()) } finally { device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard") }
    }
    @Test fun aCardOverItsOwnAppIsRecordedAsUnconfirmedRatherThanDelivered() = runBlocking {
        val c = checkpoint()
        db.dao().insertEvents(listOf(RawEvent("resume-test-app", System.currentTimeMillis() - 60_000, "RESUME", "test.app", "Test", "UTC")))
        assertEquals(c.checkpointId, controller.reconcile())
        val state = db.dao().promptState(c.checkpointId)!!
        // A fullscreen video in that same app can cover the card, so attaching it is not
        // evidence that it was seen; only the notification carries delivery for this case.
        assertNull(state.overlayShownUtc)
        assertEquals(MoodOverlayController.MAY_BE_COVERED, state.lastOverlayError)
    }

    @Test fun dismissingNewestCardDoesNotReviveOlderUnansweredCard() = runBlocking {
        val now = System.currentTimeMillis()
        val old = checkpoint(now - 900_000)
        val next = old.copy(checkpointId = "next-quarter-hour", checkpointMinutes = 45, promptTimestampUtc = now)
        db.dao().insertCheckpoints(listOf(next))
        db.dao().savePromptState(MoodPromptState(next.checkpointId, dismissed = true))
        // Unknown/changed foreground would otherwise make the old card eligible again.
        db.dao().insertEvents(listOf(RawEvent("other", now, "RESUME", "other.app", "Other", "UTC")))
        assertNull(controller.reconcile(now))
    }

    @Test fun everyQuarterHourShowsFreshCardAfterSwipeOrIgnoringPrevious() = runBlocking {
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val notifications = MoodNotificationManager(context)
        notifications.createChannels()
        val start = System.currentTimeMillis()
        val events = listOf(
            com.phonemood.monitoring.Event(start, "START", interval = 15),
            com.phonemood.monitoring.Event(start, "RESUME", "test.app"))
        db.dao().insertEvents(listOf(RawEvent("video", start, "RESUME", "test.app", "Video", "UTC")))
        val engine = com.phonemood.monitoring.SessionEngine()
        try { repeat(12) { index ->
            val now = start + (index + 1) * 900_000L
            val checkpoints = engine.rebuild(events, now).checkpoints
            db.dao().insertCheckpoints(checkpoints.map {
                MoodCheckpoint(it.id, it.sessionId, it.minutes, it.at, it.pkg, it.zone)
            })
            val id = checkpoints.last().id
            notifications.deliver(repository, now = now)
            assertEquals(id, controller.reconcile(now))
            // Wait for this quarter-hour's card, not the previous card's stale accessibility tree.
            val minutesText = context.getString(R.string.overlay_minutes, (index + 1) * 15)
            assertTrue("Quarter-hour $index never showed \"$minutesText\"",
                device.wait(Until.hasObject(By.text(minutesText)), cardTimeoutMs))
            assertEquals(now, db.dao().checkpoint(id)!!.notifiedUtc)
            // notify() returns before the system notification service publishes its state.
            withTimeout(5_000) {
                while (context.getSystemService(android.app.NotificationManager::class.java)
                    .activeNotifications.none { it.tag == id }) delay(50)
            }
            val score = button(context.getString(R.string.score_accessibility, 7))
            if (index % 2 == 0) {
                // Swipe the card far enough to dismiss it, then keep the same video in front.
                val bounds = score.visibleBounds
                device.swipe(bounds.centerX(), bounds.centerY(), device.displayWidth - 1, bounds.centerY(), 12)
                assertTrue(device.wait(Until.gone(By.desc(context.getString(R.string.score_accessibility, 7))), 5_000))
                assertTrue(db.dao().promptState(id)!!.dismissed)
                assertNull(controller.reconcile(now + 1))
                notifications.deliver(repository, now = now + 60_000)
                assertEquals("DISMISSED", db.dao().checkpoint(id)!!.responseStatus)
            }
            // On the other iterations leave the card open and completely unanswered.
        }
        assertEquals(12, db.dao().checkpoints().size)
        assertEquals(12, db.dao().checkpoints().count { it.notifiedUtc != null })
        assertTrue(db.dao().responses().isEmpty())
        } finally { db.dao().checkpoints().forEach { notifications.cancel(it.checkpointId) } }
    }

    @Test fun aCardHeldOpenInItsVideoTakesTheOrdinaryWindowOnceTheUserLeaves() = runBlocking {
        val now = System.currentTimeMillis()
        val c = checkpoint(now - 600_000)
        db.dao().insertEvents(listOf(RawEvent("video", now - 900_000, "RESUME", "test.app", "Video", "UTC")))
        // Ten minutes past its own window with no retry behind it, and the user never left the
        // video. The question is unanswered, so it stays where it can be answered.
        assertEquals(c.checkpointId, controller.reconcile(now))
        assertNotNull(button(context.getString(R.string.score_accessibility, 7)))
        db.dao().savePromptState(MoodPromptState(c.checkpointId, lastNotifiedUtc = now, notifyCount = 2, notifiedPackage = "test.app"))
        assertEquals(c.checkpointId, controller.reconcile(now))
        // Coming back to PhoneMood ends the hold: from here the ordinary window decides, and
        // switching somewhere else later does not revive the card.
        db.dao().insertEvents(listOf(RawEvent("self", now + 1, "RESUME", context.packageName, "PhoneMood", "UTC")))
        assertEquals(c.checkpointId, controller.reconcile(now + 1))
        assertNull(controller.reconcile(now + 300_000))
        db.dao().insertEvents(listOf(RawEvent("other", now + 300_001, "RESUME", "other.app", "Other", "UTC")))
        assertNull(controller.reconcile(now + 300_001))
    }
}

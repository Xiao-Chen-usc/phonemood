package com.phonemood

import android.app.Notification
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.phonemood.data.*
import com.phonemood.mood.MoodNotificationManager
import com.phonemood.mood.OverlayPolicy
import com.phonemood.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QuietNotificationTest {
    @Test fun ongoingStatusIsPrivateAndDoesNotAlertOnUpdates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = MoodNotificationManager(context)
        for (preview in listOf(false, true)) {
            val notification = manager.ongoing(preview)
            assertEquals(Notification.VISIBILITY_SECRET, notification.visibility)
            assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            assertNull(notification.sound)
            assertNull(notification.vibrate)
            assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        }
    }

    @Test fun theCheckInChannelAsksToBeHeardAndRetiresTheSilentOne() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        // A channel keeps its creation settings for life, so the silent original has to go.
        manager.createNotificationChannel(android.app.NotificationChannel(
            MoodNotificationManager.RETIRED_MOOD_CHANNEL, "old", android.app.NotificationManager.IMPORTANCE_HIGH))
        MoodNotificationManager(context).createChannels()
        assertNull(manager.getNotificationChannel(MoodNotificationManager.RETIRED_MOOD_CHANNEL))
        val channel = manager.getNotificationChannel(MoodNotificationManager.MOOD_CHANNEL)!!
        assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNotNull(channel.sound)
        assertTrue(channel.shouldVibrate())
        // Android replaces whatever the app asked for here with the package-level setting
        // (VISIBILITY_NO_OVERRIDE, a hidden -1000), so lockscreen privacy has to come from each
        // notification instead of from the channel.
        assertEquals(-1000, channel.lockscreenVisibility)
    }

    @Test fun screenOffDefersPromptUntilWakeWithoutResettingItsDeadline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val db = Room.inMemoryDatabaseBuilder(context, PhoneMoodDatabase::class.java).build()
        val repository = Repository(context, db, SettingsStore(context))
        val manager = MoodNotificationManager(context)
        manager.createChannels()
        val id = "quiet-notification-test"
        val at = System.currentTimeMillis()
        try {
            db.dao().saveState(MonitorState(monitoringEnabled = true))
            db.dao().insertCheckpoints(listOf(MoodCheckpoint(id, "test-session", 30, at, "test.app", "UTC")))
            device.sleep()
            manager.deliver(repository)
            assertNull(db.dao().checkpoint(id)!!.notifiedUtc)
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            device.waitForIdle()
            assertTrue(manager.canPrompt())
            manager.deliver(repository)
            assertNotNull(db.dao().checkpoint(id)!!.notifiedUtc)
            val notification = context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.single { it.tag == id }.notification
            assertEquals(Notification.VISIBILITY_SECRET, notification.visibility)
            assertEquals(at, db.dao().checkpoint(id)!!.promptTimestampUtc)
            assertTrue(notification.timeoutAfter in 1..300_000)
        } finally {
            manager.cancel(id)
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            db.close()
        }
    }

    @Test fun aCheckInIsDeliveredWhileTheSameAppStaysInTheForeground() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val db = Room.inMemoryDatabaseBuilder(context, PhoneMoodDatabase::class.java).build()
        val repository = Repository(context, db, SettingsStore(context))
        val manager = MoodNotificationManager(context)
        manager.createChannels()
        val id = "same-app-check-in"
        val at = System.currentTimeMillis()
        try {
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            device.waitForIdle()
            db.dao().saveState(MonitorState(monitoringEnabled = true))
            // An hour of video: the app that raised the checkpoint is still the one in front.
            db.dao().insertEvents(listOf(RawEvent("resume-video", at - 1_800_000, "RESUME", "com.example.video", "Video", "UTC")))
            db.dao().insertCheckpoints(listOf(MoodCheckpoint(id, "video-session", 30, at, "com.example.video", "UTC")))
            manager.deliver(repository)
            assertNotNull(db.dao().checkpoint(id)!!.notifiedUtc)
            assertEquals("PENDING", db.dao().checkpoint(id)!!.responseStatus)
            assertNotNull(context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.singleOrNull { it.tag == id })
        } finally {
            manager.cancel(id)
            db.close()
        }
    }

    @Test fun aCheckInSurvivesInsideItsAppAndPromptsAgainOnLeaving() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val db = Room.inMemoryDatabaseBuilder(context, PhoneMoodDatabase::class.java).build()
        val repository = Repository(context, db, SettingsStore(context))
        val manager = MoodNotificationManager(context)
        manager.createChannels()
        val id = "held-open-check-in"
        val at = System.currentTimeMillis()
        try {
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            device.waitForIdle()
            db.dao().saveState(MonitorState(monitoringEnabled = true))
            db.dao().insertEvents(listOf(RawEvent("resume-video", at - 3_600_000, "RESUME", "com.example.video", "Video", "UTC")))
            db.dao().insertCheckpoints(listOf(MoodCheckpoint(id, "video-session", 30, at - 1_800_000, "com.example.video", "UTC", notifiedUtc = at - 1_800_000)))
            db.dao().savePromptState(MoodPromptState(id, lastNotifiedUtc = at - 1_800_000, notifyCount = OverlayPolicy.MAX_NOTIFICATIONS, notifiedPackage = "com.example.video"))

            // Half an hour inside the same app with the repeat cap reached: held open, not written off.
            manager.deliver(repository)
            assertEquals("PENDING", db.dao().checkpoint(id)!!.responseStatus)

            // The user switches away, and the prompt arrives at once.
            db.dao().insertEvents(listOf(RawEvent("resume-other", at, "RESUME", "com.example.other", "Other", "UTC")))
            manager.deliver(repository)
            assertEquals("PENDING", db.dao().checkpoint(id)!!.responseStatus)
            val state = db.dao().promptState(id)!!
            assertTrue(state.lastNotifiedUtc!! >= at)
            assertEquals("com.example.other", state.notifiedPackage)
            assertNotNull(context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.singleOrNull { it.tag == id })
        } finally {
            manager.cancel(id)
            db.close()
        }
    }

    @Test fun aBacklogOfHeldOpenCheckInsArrivesAsOneNudge() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val db = Room.inMemoryDatabaseBuilder(context, PhoneMoodDatabase::class.java).build()
        val repository = Repository(context, db, SettingsStore(context))
        val manager = MoodNotificationManager(context)
        manager.createChannels()
        val ids = listOf("backlog-15", "backlog-30", "backlog-45")
        val at = System.currentTimeMillis()
        try {
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            device.waitForIdle()
            db.dao().saveState(MonitorState(monitoringEnabled = true))
            // Forty-five minutes of video: three quarter-hour check-ins were held open behind it.
            db.dao().insertEvents(listOf(RawEvent("resume-video", at - 2_700_000, "RESUME", "com.example.video", "Video", "UTC")))
            db.dao().insertCheckpoints(ids.mapIndexed { index, id ->
                MoodCheckpoint(id, "video-session", (index + 1) * 15, at - 1_800_000 + index * 900_000L, "com.example.video", "UTC",
                    notifiedUtc = if (index == 0) at - 1_800_000 else null)
            })
            db.dao().savePromptState(MoodPromptState(ids[0], lastNotifiedUtc = at - 1_800_000, notifyCount = 1, notifiedPackage = "com.example.video"))

            // The call ends and another app comes forward: one check-in speaks for the stretch.
            db.dao().insertEvents(listOf(RawEvent("resume-other", at, "RESUME", "com.example.other", "Other", "UTC")))
            manager.deliver(repository)
            assertEquals("MISSED", db.dao().checkpoint(ids[0])!!.responseStatus)
            assertEquals("SUPERSEDED", db.dao().checkpoint(ids[1])!!.responseStatus)
            assertEquals("PENDING", db.dao().checkpoint(ids[2])!!.responseStatus)
            val active = context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.filter { it.tag in ids }
            assertEquals(listOf(ids[2]), active.map { it.tag })
        } finally {
            ids.forEach { manager.cancel(it) }
            db.close()
        }
    }
}

package com.phonemood

import android.app.Notification
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.phonemood.data.*
import com.phonemood.mood.MoodNotificationManager
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
}

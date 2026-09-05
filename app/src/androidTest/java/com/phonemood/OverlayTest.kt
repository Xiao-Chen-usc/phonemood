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
    private fun button(description: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.desc(description)), 5_000)) { "Missing overlay action: $description" }
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
    @Test fun repeatedPollUsesOneWindowAndDismissalSurvivesControllerRecreation() = runBlocking {
        val c = checkpoint()
        controller.reconcile(); controller.reconcile()
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
}

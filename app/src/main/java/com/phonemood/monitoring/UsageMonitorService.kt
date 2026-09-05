package com.phonemood.monitoring

import com.phonemood.R
import android.app.Service
import android.app.KeyguardManager
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.phonemood.mood.MoodOverlayController
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.phonemood.phoneMood
import com.phonemood.mood.MoodNotificationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class UsageMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var previewJob: Job? = null
    private lateinit var overlays: MoodOverlayController
    private val pollRequests = Channel<Unit>(Channel.CONFLATED)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                overlays.hideForScreenOff()
                // Allow UsageStats to publish the lock event before the long idle wait.
                scope.launch { delay(1_000); pollRequests.trySend(Unit) }
            } else {
                pollRequests.trySend(Unit)
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        overlays = MoodOverlayController(this, phoneMood.repository) { phoneMood.reconcileSoon() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifications = MoodNotificationManager(this)
        if (job?.isActive == true) pollRequests.trySend(Unit)
        ServiceCompat.startForeground(this, 1, notifications.ongoing(intent?.action == ACTION_PREVIEW), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        if (intent?.action == ACTION_PREVIEW) {
            previewJob?.cancel()
            previewJob = scope.launch {
                try {
                    delay(5_000)
                    withContext(Dispatchers.Main.immediate) {
                        if (!overlays.showPreview()) android.widget.Toast.makeText(this@UsageMonitorService, getString(R.string.allow_floating_cards_and_unlock_your_phone_to_preview), android.widget.Toast.LENGTH_LONG).show()
                    }
                    delay(20_000)
                } finally {
                    withContext(NonCancellable + Dispatchers.Main.immediate) { overlays.hidePreview() }
                }
            }
        }
        if (job?.isActive != true) job = scope.launch {
            var reportDay: LocalDate? = null
            while (isActive) {
                if (!phoneMood.repository.configuration().enabled) {
                    if (previewJob?.isActive == true) { delay(1_000); continue }
                    stopSelf(); break
                }
                try {
                    phoneMood.repository.poll()
                    val visibleCheckpoint = overlays.reconcile()
                    notifications.deliver(phoneMood.repository, silentCheckpointId = visibleCheckpoint)
                    if (reportDay != LocalDate.now()) { phoneMood.reconcileSoon(); reportDay = LocalDate.now() }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    phoneMood.repository.mutex.withLock { phoneMood.repository.dao.state()?.let { phoneMood.repository.dao.saveState(it.copy(error = e.message ?: "Monitoring interrupted")) } }
                }
                val power = getSystemService(PowerManager::class.java)
                val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
                val interval = PollingPolicy.intervalMillis(power.isInteractive, locked, power.isPowerSaveMode)
                // Broadcasts interrupt the wait without cancelling an in-flight database transaction.
                if (interval == null) pollRequests.receive()
                else withTimeoutOrNull(interval) { pollRequests.receive() }
            }
        }
        return START_STICKY
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        overlays.window.refreshLanguage()
        val notifications = MoodNotificationManager(this)
        notifications.createChannels()
        getSystemService(android.app.NotificationManager::class.java).notify(1, notifications.ongoing(previewJob?.isActive == true))
    }
    override fun onDestroy() { scope.cancel(); overlays.destroy(); unregisterReceiver(screenReceiver); super.onDestroy() }
    companion object { const val ACTION_PREVIEW = "com.phonemood.PREVIEW_OVERLAY" }
    override fun onBind(intent: Intent?): IBinder? = null
}

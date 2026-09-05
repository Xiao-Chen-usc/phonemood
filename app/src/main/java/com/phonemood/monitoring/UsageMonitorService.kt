package com.phonemood.monitoring

import com.phonemood.R
import android.app.Service
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.phonemood.mood.MoodOverlayController
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.phonemood.phoneMood
import com.phonemood.mood.MoodNotificationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class UsageMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var previewJob: Job? = null
    private lateinit var overlays: MoodOverlayController
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { overlays.hideForScreenOff() }
    }
    override fun onCreate() {
        super.onCreate()
        overlays = MoodOverlayController(this, phoneMood.repository) { phoneMood.reconcileSoon() }
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifications = MoodNotificationManager(this)
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
                delay(10_000)
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

package com.phonemood.monitoring

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import com.phonemood.mood.MoodNotificationManager
import com.phonemood.phoneMood
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Monitoring is a loop inside a foreground service, so whatever takes the process takes
 * monitoring with it: a swipe out of recents on builds that end the task that way, or a
 * low-memory kill. Nothing brought the loop back except the user opening the app or the phone
 * restarting, so a stretch of phone use went unrecorded, and the check-ins it should have
 * raised were reconstructed far too late to ask about. This restarts the loop on its own, and
 * does the catching up itself for the case where the platform will not let a background process
 * start a foreground service at all.
 */
object MonitorWatchdog {
    private const val PERIODIC = "monitor-watchdog"
    private const val IMMEDIATE = "monitor-watchdog-now"
    /** WorkManager's floor for repeating work. The shortest check-in cadence is far longer. */
    private const val INTERVAL_MINUTES = 15L

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MonitorWatchdogWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES).build())
    }

    /**
     * Asked for the moment the task goes away, while there is still a process left to ask from.
     * Some builds keep the service; the ones that do not leave nothing behind to notice it.
     */
    fun rearm(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MonitorWatchdogWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build())
        schedule(context)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE)
    }
}

class MonitorWatchdogWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext.phoneMood
        if (!app.repository.configuration().enabled) { MonitorWatchdog.cancel(applicationContext); Result.success() }
        else if (!applicationContext.hasUsageAccess()) Result.success()
        else {
            // Catch up before restarting, so the loop resumes from a history already rebuilt.
            app.repository.poll()
            // A check-in that came due while the service was gone is still worth asking about,
            // and this may be the only chance to ask if the service cannot be started here.
            MoodNotificationManager(applicationContext).deliver(app.repository)
            // Background starts are refused on some versions and allowed on others. Either way
            // the catching up above already happened, so a refusal costs coverage, not a poll.
            runCatching {
                ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, UsageMonitorService::class.java))
            }.onFailure { android.util.Log.w("PhoneMood", "Watchdog could not restart the monitor", it) }
            Result.success()
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() }
}

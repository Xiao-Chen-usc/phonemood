package com.phonemood.report

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.phonemood.phoneMood
import kotlinx.coroutines.CancellationException

class DailyReportReconciliationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        applicationContext.phoneMood.repository.poll()
        com.phonemood.mood.MoodNotificationManager(applicationContext).deliver(applicationContext.phoneMood.repository, allowPrompt = false)
        applicationContext.phoneMood.reports.reconcile()
        Result.success()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() }
}

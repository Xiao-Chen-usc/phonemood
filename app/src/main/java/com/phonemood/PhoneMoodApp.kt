package com.phonemood

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.*
import com.phonemood.data.*
import com.phonemood.settings.SettingsStore
import com.phonemood.report.*
import com.phonemood.mood.MoodNotificationManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

class PhoneMoodApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, PhoneMoodDatabase::class.java, "phonemood.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build() }
    val repository by lazy { Repository(this, database, SettingsStore(this)) }
    val reports by lazy { DailyReportGenerator(this, repository) }
    override fun onCreate() {
        super.onCreate()
        MoodNotificationManager(this).createChannels()
        scope.launch { repository.mutex.withLock { repository.settings.save(repository.configuration()) } }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily-reconciliation", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<DailyReportReconciliationWorker>(6, TimeUnit.HOURS).build())
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        MoodNotificationManager(this).createChannels()
        scope.launch { MoodNotificationManager(this@PhoneMoodApp).refreshLanguage(repository) }
    }
    fun reconcileSoon() {
        WorkManager.getInstance(this).enqueueUniqueWork("report-now", ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<DailyReportReconciliationWorker>().build())
    }
}
val Context.phoneMood: PhoneMoodApp get() = applicationContext as PhoneMoodApp

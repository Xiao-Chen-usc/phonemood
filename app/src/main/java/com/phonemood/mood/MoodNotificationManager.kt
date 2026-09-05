package com.phonemood.mood

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phonemood.R
import com.phonemood.data.*
import com.phonemood.ui.MainActivity
import kotlinx.coroutines.sync.withLock

class MoodNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun createChannels() {
        manager.createNotificationChannel(NotificationChannel("monitor", context.getString(R.string.usage_monitoring), NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("mood", context.getString(R.string.mood_check_ins), NotificationManager.IMPORTANCE_HIGH).apply { description = context.getString(R.string.a_quick_check_in_after_active_phone_use) })
    }
    fun canPrompt() = NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel("mood")?.importance != NotificationManager.IMPORTANCE_NONE
    fun ongoing(preview: Boolean = false): Notification = NotificationCompat.Builder(context, "monitor")
        .setSmallIcon(R.drawable.ic_notification).setContentTitle(if (preview) context.getString(R.string.phonemood_floating_card_preview) else context.getString(R.string.phonemood_is_listening_locally))
        .setContentText(if (preview) context.getString(R.string.switch_to_another_app_a_preview_appears_in_5_seconds) else context.getString(R.string.your_next_check_in_follows_active_phone_use_tap_to_pause))
        .setOngoing(true).setSilent(true).setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    suspend fun deliver(repository: Repository, allowPrompt: Boolean = true, silentCheckpointId: String? = null) = repository.mutex.withLock {
        val now = System.currentTimeMillis()
        val pending = repository.dao.checkpoints().filter { it.responseStatus == "PENDING" }
        pending.forEach { checkpoint ->
            if (now - checkpoint.promptTimestampUtc > 5 * 60_000) {
                repository.dao.updateCheckpoint(checkpoint.copy(responseStatus = "MISSED"))
                cancel(checkpoint.checkpointId)
            } else if (checkpoint.notifiedUtc == null && canPrompt() && allowPrompt && repository.dao.state()?.monitoringEnabled == true) {
                val intent = Intent(context, MoodRatingActivity::class.java).setData(Uri.parse("phonemood://checkpoint/${Uri.encode(checkpoint.checkpointId)}")).putExtra("checkpointId", checkpoint.checkpointId)
                val notification = NotificationCompat.Builder(context, "mood").setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.a_little_check_in_with_yourself))
                    .setContentText(context.getString(R.string.notification_minutes, checkpoint.checkpointMinutes))
                    .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    .setSilent(checkpoint.checkpointId == silentCheckpointId).setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter((5 * 60_000 - (now - checkpoint.promptTimestampUtc)).coerceAtLeast(1)).build()
                try {
                    manager.notify(checkpoint.checkpointId, 2, notification)
                    repository.dao.updateCheckpoint(checkpoint.copy(notifiedUtc = now))
                } catch (_: SecurityException) { /* Permission may change between the check and notify. */ }
            }
        }
        if (!canPrompt() && repository.dao.state()?.monitoringEnabled == true) {
            val bucket = now / 300_000 * 300_000
            repository.dao.insertGap(MonitoringGap("notifications:$bucket", bucket, now, "NOTIFICATIONS_UNAVAILABLE"))
        }
    }
    /** Update only notifications that still exist, quietly, without changing delivery facts. */
    suspend fun refreshLanguage(repository: Repository) = repository.mutex.withLock {
        createChannels()
        val checkpoints = repository.dao.checkpoints().associateBy { it.checkpointId }
        val now = System.currentTimeMillis()
        manager.activeNotifications.filter { it.id == 2 }.forEach { active ->
            val checkpoint = checkpoints[active.tag]
            val remaining = checkpoint?.let { it.promptTimestampUtc + 300_000 - now } ?: 0
            if (checkpoint == null || checkpoint.responseStatus != "PENDING" || remaining <= 0) {
                manager.cancel(active.tag, active.id)
            } else {
                val notification = Notification.Builder.recoverBuilder(context, active.notification)
                    .setContentTitle(context.getString(R.string.a_little_check_in_with_yourself))
                    .setContentText(context.getString(R.string.notification_minutes, checkpoint.checkpointMinutes))
                    .setOnlyAlertOnce(true).setTimeoutAfter(remaining).build()
                try { manager.notify(active.tag, active.id, notification) } catch (_: SecurityException) { }
            }
        }
    }
    fun cancel(id: String) = manager.cancel(id, 2)
}

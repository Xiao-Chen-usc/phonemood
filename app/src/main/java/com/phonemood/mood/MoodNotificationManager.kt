package com.phonemood.mood

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phonemood.R
import com.phonemood.data.*
import com.phonemood.ui.MainActivity
import kotlinx.coroutines.sync.withLock

class MoodNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun createChannels() {
        // Android overwrites a channel's lockscreen visibility with the package-level setting the
        // moment the app creates it, so asking for it here would only look like privacy. Every
        // notification carries VISIBILITY_SECRET itself, which is what actually keeps mood off a
        // locked screen.
        manager.createNotificationChannel(NotificationChannel("monitor", context.getString(R.string.usage_monitoring), NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null); enableVibration(false); setShowBadge(false)
        })
        // A channel keeps whatever sound and vibration it was created with, so asking the first
        // one to alert would have changed nothing on a phone that already had it. This is a new
        // channel; the silent original is retired with it.
        manager.createNotificationChannel(NotificationChannel(MOOD_CHANNEL, context.getString(R.string.mood_check_ins), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.a_quick_check_in_after_active_phone_use)
            // A check-in has to survive a video's own audio, so it asks for both a sound and a
            // buzz rather than relying on the channel default, which vibrates for nothing.
            setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 220, 130, 220)
        })
        if (manager.getNotificationChannel(RETIRED_MOOD_CHANNEL) != null) manager.deleteNotificationChannel(RETIRED_MOOD_CHANNEL)
    }

    /** Why a check-in would or would not reach the user right now. */
    enum class Reach { READY, DISABLED, SILENCED, SUPPRESSED }

    /**
     * `areNotificationsEnabled` is only the first gate. A channel the user or the ROM demoted
     * still accepts notifications but posts them without a sound or a heads-up, and Do Not
     * Disturb pauses them entirely, so those cases are named rather than read as success.
     */
    fun reach(): Reach {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return Reach.DISABLED
        val importance = manager.getNotificationChannel(MOOD_CHANNEL)?.importance ?: NotificationManager.IMPORTANCE_HIGH
        if (importance == NotificationManager.IMPORTANCE_NONE) return Reach.DISABLED
        if (Build.VERSION.SDK_INT >= 28 && manager.areNotificationsPaused()) return Reach.SUPPRESSED
        return if (importance < NotificationManager.IMPORTANCE_HIGH) Reach.SILENCED else Reach.READY
    }
    /** A muted check-in still beats none, so only an outright block stops delivery. */
    fun canPrompt() = reach() != Reach.DISABLED
    fun ongoing(preview: Boolean = false): Notification = NotificationCompat.Builder(context, "monitor")
        .setSmallIcon(R.drawable.ic_notification).setContentTitle(if (preview) context.getString(R.string.phonemood_floating_card_preview) else context.getString(R.string.phonemood_is_listening_locally))
        .setContentText(if (preview) context.getString(R.string.switch_to_another_app_a_preview_appears_in_5_seconds) else context.getString(R.string.your_next_check_in_follows_active_phone_use_tap_to_pause))
        .setVisibility(NotificationCompat.VISIBILITY_SECRET).setOnlyAlertOnce(true).setShowWhen(false)
        .setOngoing(true).setSilent(true).setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    suspend fun deliver(repository: Repository, allowPrompt: Boolean = true, silentCheckpointId: String? = null) = repository.mutex.withLock {
        val now = System.currentTimeMillis()
        val latestForegroundPackage = repository.dao.lastResume()?.packageName
        val pending = repository.dao.checkpoints().filter { it.responseStatus == "PENDING" }
        // A long call or an hour of video can leave several check-ins held open at once. Only the
        // newest is still worth asking about, and the rest retire quietly, so coming back to the
        // phone is one gentle nudge rather than the whole backlog arriving together.
        val live = OverlayPolicy.live(pending) { it.promptTimestampUtc }
        pending.forEach { checkpoint ->
            if (checkpoint.checkpointId != live?.checkpointId) {
                repository.dao.updateCheckpoint(checkpoint.copy(responseStatus = OverlayPolicy.retirement(checkpoint.notifiedUtc != null)))
                cancel(checkpoint.checkpointId)
                return@forEach
            }
            val state = repository.dao.promptState(checkpoint.checkpointId) ?: MoodPromptState(checkpoint.checkpointId)
            val prompt = OverlayPolicy.Prompt(checkpoint.promptTimestampUtc, checkpoint.foregroundPackage,
                state.overlayShownUtc, state.lastNotifiedUtc, state.notifyCount, state.notifiedPackage, state.dismissed)
            // While the user is still inside the app that raised the prompt, a fullscreen video can
            // hide the card and an immersive app can swallow the heads-up, so the prompt is held
            // open rather than written off, and asked again on a rhythm.
            val heldOpen = latestForegroundPackage != null && latestForegroundPackage == checkpoint.foregroundPackage && !state.dismissed
            when (OverlayPolicy.action(now, prompt, latestForegroundPackage)) {
                OverlayPolicy.PromptAction.EXPIRE -> {
                    repository.dao.updateCheckpoint(checkpoint.copy(responseStatus = "MISSED"))
                    cancel(checkpoint.checkpointId)
                }
                OverlayPolicy.PromptAction.NOTIFY -> if (canPrompt() && allowPrompt && screenAvailable() && repository.dao.state()?.monitoringEnabled == true) {
                    val until = OverlayPolicy.answerableUntil(checkpoint.promptTimestampUtc, state.overlayShownUtc, now, heldOpen)
                    val intent = Intent(context, MoodRatingActivity::class.java).setData(Uri.parse("phonemood://checkpoint/${Uri.encode(checkpoint.checkpointId)}")).putExtra("checkpointId", checkpoint.checkpointId)
                    val notification = NotificationCompat.Builder(context, MOOD_CHANNEL).setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(context.getString(R.string.a_little_check_in_with_yourself))
                        .setContentText(context.getString(R.string.notification_minutes, checkpoint.checkpointMinutes))
                        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                        .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        .setSilent(checkpoint.checkpointId == silentCheckpointId).setAutoCancel(true)
                        // Each delivery here is one the policy asked for, so it must alert rather
                        // than quietly update the card already sitting in the shade.
                        .setOnlyAlertOnce(false)
                        .also { if (until != Long.MAX_VALUE) it.setTimeoutAfter((until - now).coerceAtLeast(1)) }
                        .build()
                    try {
                        manager.notify(checkpoint.checkpointId, 2, notification)
                        if (checkpoint.notifiedUtc == null) repository.dao.updateCheckpoint(checkpoint.copy(notifiedUtc = now))
                        repository.dao.savePromptState(state.copy(lastNotifiedUtc = now, notifyCount = state.notifyCount + 1, notifiedPackage = latestForegroundPackage))
                    } catch (_: SecurityException) { /* Permission may change between the check and notify. */ }
                }
                OverlayPolicy.PromptAction.WAIT -> Unit
            }
        }
        val reach = reach()
        if (reach != Reach.READY && repository.dao.state()?.monitoringEnabled == true) {
            val bucket = now / 300_000 * 300_000
            // Naming the three apart is what tells a later reader whether a quiet stretch was the
            // app failing to ask or the phone declining to pass the question on.
            val reason = when (reach) {
                Reach.DISABLED -> "NOTIFICATIONS_UNAVAILABLE"
                Reach.SILENCED -> "NOTIFICATIONS_SILENCED"
                else -> "NOTIFICATIONS_SUPPRESSED_BY_DO_NOT_DISTURB"
            }
            repository.dao.insertGap(MonitoringGap("notifications:$bucket", bucket, now, reason))
        }
    }
    private fun screenAvailable() = context.getSystemService(android.os.PowerManager::class.java).isInteractive &&
        !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    /** Update only notifications that still exist, quietly, without changing delivery facts. */
    suspend fun refreshLanguage(repository: Repository) = repository.mutex.withLock {
        createChannels()
        val checkpoints = repository.dao.checkpoints().associateBy { it.checkpointId }
        val now = System.currentTimeMillis()
        val latestForegroundPackage = repository.dao.lastResume()?.packageName
        manager.activeNotifications.filter { it.id == 2 }.forEach { active ->
            val checkpoint = checkpoints[active.tag]
            val state = checkpoint?.let { repository.dao.promptState(it.checkpointId) }
            val until = checkpoint?.let {
                OverlayPolicy.answerableUntil(it.promptTimestampUtc, state?.overlayShownUtc, state?.lastNotifiedUtc,
                    heldOpen = latestForegroundPackage != null && latestForegroundPackage == it.foregroundPackage && state?.dismissed != true)
            }
            if (checkpoint == null || checkpoint.responseStatus != "PENDING" || until == null || until <= now) {
                manager.cancel(active.tag, active.id)
            } else {
                val notification = Notification.Builder.recoverBuilder(context, active.notification)
                    .setContentTitle(context.getString(R.string.a_little_check_in_with_yourself))
                    .setContentText(context.getString(R.string.notification_minutes, checkpoint.checkpointMinutes))
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setOnlyAlertOnce(true)
                    .also { if (until != Long.MAX_VALUE) it.setTimeoutAfter(until - now) }
                    .build()
                try { manager.notify(active.tag, active.id, notification) } catch (_: SecurityException) { }
            }
        }
    }
    fun cancel(id: String) = manager.cancel(id, 2)

    /**
     * A real check-in on the real channel, so the user can find out from inside a fullscreen
     * video whether one can reach them there. It writes nothing: no checkpoint, no evidence.
     */
    fun preview() {
        createChannels()
        val notification = NotificationCompat.Builder(context, MOOD_CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.a_little_check_in_with_yourself))
            .setContentText(context.getString(R.string.this_is_a_test_check_in_no_mood_data_is_saved))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true).setOnlyAlertOnce(false).setTimeoutAfter(60_000).build()
        try { manager.notify(MoodOverlayController.PREVIEW_ID, 2, notification) } catch (_: SecurityException) { }
    }

    companion object {
        const val MOOD_CHANNEL = "mood.v2"
        /** Created before check-ins asked to be heard; its settings can no longer be changed. */
        const val RETIRED_MOOD_CHANNEL = "mood"
    }
}

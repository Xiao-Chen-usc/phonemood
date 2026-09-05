package com.phonemood.mood

import com.phonemood.R
import android.content.Context
import android.widget.Toast
import com.phonemood.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

class MoodOverlayController(
    private val context: Context,
    private val repository: Repository,
    private val reportsChanged: () -> Unit,
) {
    val window = MoodOverlayWindow(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var expiry: Job? = null

    suspend fun reconcile(now: Long = System.currentTimeMillis()): String? = repository.mutex.withLock {
        val config = repository.configuration()
        val available = withContext(Dispatchers.Main.immediate) { window.available() }
        if (!config.enabled || !config.overlayEnabled || !available) {
            withContext(Dispatchers.Main.immediate) { window.hide() }
            return@withLock null
        }
        if (withContext(Dispatchers.Main.immediate) { window.checkpointId } == PREVIEW_ID) return@withLock null
        val presentations = repository.dao.promptStates().associateBy { it.checkpointId }
        val checkpoint = repository.dao.checkpoints().lastOrNull { c ->
            val p = presentations[c.checkpointId]
            OverlayPolicy.eligible(now, c.promptTimestampUtc, c.responseStatus, p?.dismissed ?: false, p?.snoozedUntilUtc)
        }
        if (checkpoint == null) { withContext(Dispatchers.Main.immediate) { window.hide() }; return@withLock null }
        val wasVisible = withContext(Dispatchers.Main.immediate) { window.checkpointId == checkpoint.checkpointId }
        val shown = withContext(Dispatchers.Main.immediate) {
            window.show(checkpoint.checkpointId, checkpoint.checkpointMinutes, false, OverlayPolicy.canSnooze(now, checkpoint.promptTimestampUtc),
                onScore = { score -> answer(checkpoint.checkpointId, score) },
                onLater = { changePrompt(checkpoint.checkpointId, later = true) },
                onDismiss = { changePrompt(checkpoint.checkpointId, later = false) })
        }
        val previous = presentations[checkpoint.checkpointId] ?: MoodPromptState(checkpoint.checkpointId)
        if (shown) {
            if (previous.overlayShownUtc == null || previous.lastOverlayError != null) repository.dao.savePromptState(previous.copy(overlayShownUtc = previous.overlayShownUtc ?: now, lastOverlayError = null))
            if (!wasVisible) expireAfter(checkpoint.checkpointId, checkpoint.promptTimestampUtc + OverlayPolicy.PROMPT_LIFETIME_MS - now)
            checkpoint.checkpointId
        } else {
            repository.dao.savePromptState(previous.copy(lastOverlayError = "WINDOW_NOT_ATTACHED"))
            null // Normal notification remains the fallback when addView fails.
        }
    }
    private fun answer(id: String, score: Int) {
        window.saving()
        scope.launch {
            try {
                check(repository.respond(id, score)) { context.getString(R.string.this_check_in_is_no_longer_available) }
                MoodNotificationManager(context).cancel(id)
                withContext(Dispatchers.Main.immediate) { if (window.checkpointId == id) window.hide() }
                reportsChanged()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { withContext(Dispatchers.Main.immediate) { if (window.checkpointId == id) window.error(context.getString(R.string.could_not_save_please_tap_again)) } }
        }
    }
    private fun changePrompt(id: String, later: Boolean) {
        window.saving()
        scope.launch {
            try {
                repository.mutex.withLock {
                    val checkpoint = repository.dao.checkpoint(id)
                    if (checkpoint != null) {
                        val state = repository.dao.promptState(id) ?: MoodPromptState(id)
                        val until = if (later) minOf(System.currentTimeMillis() + OverlayPolicy.SNOOZE_MS, checkpoint.promptTimestampUtc + OverlayPolicy.PROMPT_LIFETIME_MS) else null
                        repository.dao.savePromptState(state.copy(dismissed = !later, snoozedUntilUtc = until))
                    }
                }
                withContext(Dispatchers.Main.immediate) { if (window.checkpointId == id) window.hide() }
                reportsChanged()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { withContext(Dispatchers.Main.immediate) { if (window.checkpointId == id) window.error(context.getString(R.string.could_not_save_please_try_again)) } }
        }
    }
    private fun expireAfter(id: String, duration: Long) {
        expiry?.cancel()
        expiry = scope.launch { delay(duration.coerceAtLeast(1)); withContext(Dispatchers.Main.immediate) { if (window.checkpointId == id) window.hide() } }
    }
    fun showPreview(): Boolean {
        if (window.checkpointId != null && window.checkpointId != PREVIEW_ID) return false
        val shown = window.show(PREVIEW_ID, null, true, false,
            onScore = { score -> window.hide(); Toast.makeText(context, context.getString(R.string.preview_score, score), Toast.LENGTH_SHORT).show() },
            onLater = { window.hide() }, onDismiss = { window.hide() })
        if (shown) expireAfter(PREVIEW_ID, 20_000)
        return shown
    }
    fun hidePreview() { if (window.checkpointId == PREVIEW_ID) window.hide() }
    fun hideForScreenOff() { window.hide() }
    fun destroy() { scope.cancel(); window.hide() }
    companion object { const val PREVIEW_ID = "phonemood-preview" }
}

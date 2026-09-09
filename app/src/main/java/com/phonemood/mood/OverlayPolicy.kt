package com.phonemood.mood

/** This policy is independent of Android windows and can be replayed after process death. */
object OverlayPolicy {
    const val PROMPT_LIFETIME_MS = 300_000L
    const val SNOOZE_MS = 60_000L
    /** An unanswered prompt asks again on this rhythm while the user is still inside the app that raised it. */
    const val RENOTIFY_INTERVAL_MS = 300_000L
    /** Including the first, so a single checkpoint can never alert more than this many times. */
    const val MAX_NOTIFICATIONS = 3

    /** What a pending checkpoint is owed on this poll, before any device-state gate is applied. */
    enum class PromptAction { WAIT, NOTIFY, EXPIRE }

    /**
     * The one pending check-in still worth asking about. A call or a fullscreen video holds
     * earlier ones open with no fair chance of an answer, and releasing that backlog the moment
     * the user comes back would be three notifications in one breath. The newest already counts
     * the whole stretch, so it speaks for the ones behind it.
     */
    fun <T> live(pending: List<T>, threshold: (T) -> Long): T? =
        pending.reduceOrNull { kept, next -> if (threshold(next) >= threshold(kept)) next else kept }

    /**
     * How a check-in a newer one replaced is written down. MISSED means it was asked and went
     * unanswered; SUPERSEDED means it was never asked, so it must not count against the
     * response rate the export reports.
     */
    fun retirement(everNotified: Boolean) = if (everNotified) "MISSED" else "SUPERSEDED"

    /** Everything the decision needs that is not the clock, so it can be replayed in a test. */
    data class Prompt(
        val thresholdUtc: Long,
        val checkpointPackage: String,
        val overlayShownUtc: Long? = null,
        val lastNotifiedUtc: Long? = null,
        val notifyCount: Int = 0,
        val notifiedPackage: String? = null,
        val dismissed: Boolean = false,
    )

    fun eligible(now: Long, thresholdUtc: Long, status: String, dismissed: Boolean, snoozedUntil: Long?): Boolean =
        status == "PENDING" && now >= thresholdUtc && now - thresholdUtc < PROMPT_LIFETIME_MS && !dismissed && (snoozedUntil == null || now >= snoozedUntil)
    fun canSnooze(now: Long, thresholdUtc: Long) = now + SNOOZE_MS < thresholdUtc + PROMPT_LIFETIME_MS

    /**
     * A prompt raised inside an app the user has not left yet is never written off: a fullscreen
     * video can hide the card, and an immersive app can swallow the notification, so the user may
     * have had no fair chance to see either. It waits, asks again on a rhythm, and asks once more
     * the moment another app comes forward.
     */
    fun action(now: Long, prompt: Prompt, foregroundPackage: String?): PromptAction {
        val inSameApp = foregroundPackage != null && foregroundPackage == prompt.checkpointPackage
        val leftTheApp = foregroundPackage != null && foregroundPackage != prompt.checkpointPackage &&
            prompt.lastNotifiedUtc != null && prompt.notifiedPackage == prompt.checkpointPackage
        val lastPresented = maxOf(prompt.overlayShownUtc ?: prompt.thresholdUtc, prompt.lastNotifiedUtc ?: prompt.thresholdUtc)
        return when {
            prompt.dismissed -> if (now - lastPresented > PROMPT_LIFETIME_MS) PromptAction.EXPIRE else PromptAction.WAIT
            prompt.lastNotifiedUtc == null -> if (inSameApp || now - prompt.thresholdUtc <= PROMPT_LIFETIME_MS) PromptAction.NOTIFY else PromptAction.EXPIRE
            leftTheApp -> PromptAction.NOTIFY
            inSameApp -> if (prompt.notifyCount < MAX_NOTIFICATIONS && now - prompt.lastNotifiedUtc >= RENOTIFY_INTERVAL_MS) PromptAction.NOTIFY else PromptAction.WAIT
            now - lastPresented > PROMPT_LIFETIME_MS -> PromptAction.EXPIRE
            else -> PromptAction.WAIT
        }
    }

    /** A prompt held open inside its own app can be presented again once another app comes forward. */
    fun represent(status: String, dismissed: Boolean, snoozedUntil: Long?, foregroundChanged: Boolean): Boolean =
        status == "PENDING" && foregroundChanged && !dismissed && snoozedUntil == null

    /** The moment a pending prompt stops being answerable, so a posted notification never outlives it. */
    fun answerableUntil(thresholdUtc: Long, overlayShownUtc: Long?, lastNotifiedUtc: Long?, heldOpen: Boolean): Long =
        if (heldOpen) Long.MAX_VALUE
        else maxOf(overlayShownUtc ?: thresholdUtc, lastNotifiedUtc ?: thresholdUtc) + PROMPT_LIFETIME_MS
}

package com.phonemood.mood

import com.phonemood.R
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.annotation.MainThread
import androidx.core.view.ViewCompat

/** One bounded, touchable window. The rest of the screen stays available to the underlying app. */
class MoodOverlayWindow(context: Context) {
    private val context = ContextThemeWrapper(context, android.R.style.Theme_Material_Light_NoActionBar)
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var redraw: (() -> Boolean)? = null
    private var isSaving = false
    private var statusLabel: TextView? = null
    private val buttons = mutableListOf<View>()
    var checkpointId: String? = null
        private set
    private val forest = Color.rgb(54, 95, 73)
    private val ink = Color.rgb(37, 57, 46)
    private val muted = Color.rgb(100, 114, 102)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun available() = Settings.canDrawOverlays(context) && context.getSystemService(PowerManager::class.java).isInteractive && !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private fun background(color: Int, radius: Int = 16) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun label(text: String, size: Float, color: Int = ink) = TextView(context).apply { this.text = text; textSize = size; setTextColor(color) }

    @MainThread
    fun show(id: String, minutes: Int?, preview: Boolean, canSnooze: Boolean, onScore: (Int) -> Unit, onLater: () -> Unit, onDismiss: () -> Unit): Boolean {
        if (!available()) { hide(); return false }
        if (checkpointId == id && root != null) return true
        hide()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(16))
            background = background(Color.rgb(248, 248, 242), 24); elevation = dp(12).toFloat()
            isClickable = true; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        ViewCompat.setAccessibilityPaneTitle(card, context.getString(R.string.phonemood_mood_check_in))
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label(if (preview) context.getString(R.string.phonemood_preview) else context.getString(R.string.phonemood_check_in), 11f, forest).apply { letterSpacing = .08f }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        (heading.getChildAt(0) as TextView).gravity = Gravity.CENTER_VERTICAL
        val close = Button(context).apply { text = "×"; textSize = 25f; setTextColor(muted); background = background(Color.TRANSPARENT); contentDescription = context.getString(R.string.dismiss_mood_card); setPadding(0, 0, 0, 0); setOnClickListener { onDismiss() } }
        buttons += close; heading.addView(close, LinearLayout.LayoutParams(dp(48), dp(48))); card.addView(heading)
        card.addView(label(context.getString(R.string.how_are_you_feeling), 25f).apply { typeface = Typeface.create("serif", Typeface.NORMAL); isAccessibilityHeading = true })
        card.addView(label(if (preview) context.getString(R.string.try_a_number_this_preview_saves_no_data) else context.getString(R.string.overlay_minutes, minutes ?: 0), 13f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(12) })
        for (row in 0..1) {
            val line = LinearLayout(context)
            for (column in 1..5) {
                val score = row * 5 + column
                val button = Button(context).apply {
                    text = score.toString(); textSize = 20f; typeface = Typeface.create("serif", Typeface.NORMAL)
                    setTextColor(forest); background = background(Color.rgb(230, 237, 222), 12)
                    setPadding(0, 0, 0, 0); minWidth = 0; minimumWidth = 0; minHeight = dp(48)
                    contentDescription = context.getString(R.string.score_accessibility, score)
                    setOnClickListener { onScore(score) }
                }
                buttons += button
                line.addView(button, LinearLayout.LayoutParams(0, dp(50), 1f).apply { if (column < 5) marginEnd = dp(6) })
            }
            card.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        val anchors = LinearLayout(context)
        anchors.addView(label(context.getString(R.string.anchor_low), 11f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        anchors.addView(label(context.getString(R.string.anchor_high), 11f, muted).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(anchors)
        statusLabel = label(if (preview) context.getString(R.string.preview_only_closes_automatically) else context.getString(R.string.tap_once_to_save_and_keep_going), 12f, muted)
        card.addView(statusLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val later = Button(context).apply {
            text = if (preview) context.getString(R.string.close_preview) else context.getString(R.string.later_1_minute); isAllCaps = false
            setTextColor(forest); background = background(Color.TRANSPARENT); isEnabled = preview || canSnooze
            contentDescription = if (preview) context.getString(R.string.close_preview) else context.getString(R.string.remind_me_in_one_minute)
            setOnClickListener { if (preview) onDismiss() else onLater() }
        }
        if (preview || canSnooze) { buttons += later; card.addView(later, LinearLayout.LayoutParams(-1, dp(48))) }
        val scroll = ScrollView(context).apply { isFillViewport = false; addView(card); clipToPadding = false }
        val metrics = context.resources.displayMetrics
        val width = minOf(dp(380), metrics.widthPixels - dp(32))
        val heightLimit = (metrics.heightPixels - dp(140)).coerceAtLeast(dp(140))
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightLimit, View.MeasureSpec.AT_MOST))
        val params = WindowManager.LayoutParams(width, scroll.measuredHeight.coerceAtMost(heightLimit), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(56)
            setTitle(context.getString(R.string.phonemood_mood_card))
        }
        return try { wm.addView(scroll, params); root = scroll; checkpointId = id; redraw = { show(id, minutes, preview, canSnooze, onScore, onLater, onDismiss) }; true }
        catch (_: WindowManager.BadTokenException) { hide(); false }
        catch (_: SecurityException) { hide(); false }
    }
    /** Rebuild only the view; the controller keeps its original expiry and checkpoint. */
    @MainThread fun refreshLanguage() {
        val render = redraw ?: return
        val wasSaving = isSaving
        hide()
        if (render() && wasSaving) saving()
    }
    @MainThread fun saving() { isSaving = true; buttons.forEach { it.isEnabled = false }; statusLabel?.text = context.getString(R.string.saving) }
    @MainThread fun error(message: String) { isSaving = false; buttons.forEach { it.isEnabled = true }; statusLabel?.text = message }
    @MainThread fun hide() {
        root?.let { runCatching { wm.removeViewImmediate(it) } }
        root = null; redraw = null; isSaving = false; checkpointId = null; statusLabel = null; buttons.clear()
    }
}

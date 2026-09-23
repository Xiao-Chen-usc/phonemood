package com.phonemood.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/**
 * The chosen language, kept somewhere a Service can read it without suspending.
 *
 * Below Android 13, AppCompat applies a per-app language to Activity contexts alone, and its
 * record of the choice lives in a static field that a process started for the monitor never
 * fills in. Every string the background service renders would then come back in the system
 * language rather than the chosen one: the floating card, the check-in, the ongoing
 * notification. Writing the choice down here keeps the answer available to any context, in
 * any process, however that process came to exist.
 */
object AppLocale {
    private const val FILE = "phonemood_locale"
    private const val KEY = "applicationLocales"
    private fun store(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Record what the user picked. No tags means follow the system. */
    fun remember(context: Context, languageTags: String) {
        store(context).edit().putString(KEY, languageTags).apply()
    }

    /**
     * From Android 13 the platform holds the choice itself, and holds it for every context, so
     * it is the only answer worth having: it also knows about a language changed from Android's
     * own app settings rather than from here. The recorded choice fills in only where AppCompat
     * has nothing to report.
     */
    fun tags(context: Context): String {
        val applied = AppCompatDelegate.getApplicationLocales()
        if (!applied.isEmpty || Build.VERSION.SDK_INT >= 33) return applied.toLanguageTags()
        return store(context).getString(KEY, "").orEmpty()
    }

    /** The same context, answering in the chosen language whatever the system is set to. */
    fun wrap(context: Context): Context {
        val locales = LocaleList.forLanguageTags(tags(context))
        if (locales.isEmpty) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(locales)
        return context.createConfigurationContext(configuration)
    }
}

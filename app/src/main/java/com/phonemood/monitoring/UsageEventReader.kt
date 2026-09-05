package com.phonemood.monitoring

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.view.inputmethod.InputMethodManager
import com.phonemood.settings.Configuration
import java.time.ZoneId

interface UsageEventReader { suspend fun read(fromMillis: Long, toMillis: Long): List<Event> }
fun Context.hasUsageAccess(): Boolean = getSystemService(AppOpsManager::class.java)
    .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED

class AppFilter(context: Context, private val configuration: Configuration) {
    private val ignored = buildSet {
        add(context.packageName); add("com.android.systemui"); add("com.android.permissioncontroller"); add("com.google.android.permissioncontroller")
        // All HOME handlers include Settings.FallbackHome on Android. Excluding every
        // handler accidentally excludes the entire Settings app. Only exclude the selected home.
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeUnless { it == "android" }?.let { add(it) }
        context.getSystemService(InputMethodManager::class.java).inputMethodList.forEach { add(it.packageName) }
        addAll(configuration.excluded)
    }
    fun excludes(pkg: String) = pkg in ignored || pkg.isBlank()
}
class AndroidUsageEventReader(private val context: Context, configuration: Configuration) : UsageEventReader {
    private val filter = AppFilter(context, configuration)
    override suspend fun read(fromMillis: Long, toMillis: Long): List<Event> {
        check(context.hasUsageAccess()) { "Usage Access is off" }
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(fromMillis, toMillis)
            ?: error("Usage event history is unavailable")
        val labels = mutableMapOf<String, String>()
        val result = mutableListOf<Event>()
        val event = UsageEvents.Event()
        val zone = ZoneId.systemDefault().id
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> "RESUME"
                UsageEvents.Event.ACTIVITY_PAUSED -> "PAUSE"
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> "LOCK"
                UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> "SHUTDOWN"
                else -> null
            } ?: continue
            val pkg = event.packageName.orEmpty()
            val label = labels.getOrPut(pkg) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
            result += Event(event.timeStamp, type, pkg, label, zone, excluded = filter.excludes(pkg), zoneInferred = toMillis - event.timeStamp > 20_000)
        }
        return result
    }
}

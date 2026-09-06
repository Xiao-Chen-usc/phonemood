package com.phonemood.ui

import com.phonemood.R
import android.app.Activity
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.phonemood.data.*
import com.phonemood.monitoring.*
import com.phonemood.mood.*
import com.phonemood.phoneMood
import com.phonemood.report.DayWindow
import com.phonemood.settings.Configuration
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        phoneMood.reconcileSoon()
        setContent { PhoneMoodTheme { PhoneMoodScreen() } }
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val repository = phoneMood.repository
            withContext(Dispatchers.IO) { repository.enableOnFirstPermission(hasUsageAccess()) }
            if (repository.configuration().enabled && hasUsageAccess()) {
                runCatching { ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, UsageMonitorService::class.java)) }
            }
        }
    }
}
private data class Tab(val label: Int, val icon: ImageVector)
private val tabs = listOf(Tab(R.string.today, Icons.Outlined.WbSunny), Tab(R.string.trends_title, Icons.Outlined.Insights), Tab(R.string.settings, Icons.Outlined.Tune))

@Composable
fun PhoneMoodScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val app = context.phoneMood
    val dao = app.repository.dao
    val state by dao.watchState().collectAsStateWithLifecycle(null)
    val segments by dao.watchSegments().collectAsStateWithLifecycle(emptyList())
    val checkpoints by dao.watchCheckpoints().collectAsStateWithLifecycle(emptyList())
    val promptStates by dao.watchPromptStates().collectAsStateWithLifecycle(emptyList())
    val responses by dao.watchResponses().collectAsStateWithLifecycle(emptyList())
    val storedConfig by app.repository.settings.flow.collectAsStateWithLifecycle(Configuration())
    val config = storedConfig.copy(enabled = state?.monitoringEnabled ?: false)
    var advancedSettings by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable("mood-first-navigation") { mutableIntStateOf(0) }
    val scrollState = remember(tab) { androidx.compose.foundation.lazy.LazyListState() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var usageAccess by remember { mutableStateOf(context.hasUsageAccess()) }
    var overlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notifications by remember { mutableStateOf(MoodNotificationManager(context).canPrompt()) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications = MoodNotificationManager(context).canPrompt() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { usageAccess = context.hasUsageAccess(); overlayPermission = Settings.canDrawOverlays(context); notifications = MoodNotificationManager(context).canPrompt(); now = System.currentTimeMillis() } }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(10_000) } }
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun configure(next: Configuration) {
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { app.repository.configure(next) }
                if (next.enabled) ContextCompat.startForegroundService(context, Intent(context, UsageMonitorService::class.java))
                else context.stopService(Intent(context, UsageMonitorService::class.java))
                app.reconcileSoon()
                if (next.enabled != config.enabled) message(context.getString(if (next.enabled) R.string.tracking_resumed else R.string.tracking_paused_feedback))
                else if (next.excluded != config.excluded) message(context.getString(R.string.exclusions_saved))
            } catch (e: Exception) { message(context.getString(R.string.could_not_update_settings)) }
            finally { busy = false }
        }
    }
    fun grantUsage() { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))) }
    fun grantOverlay() {
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:${context.packageName}"))) }
            .onFailure { message(context.getString(R.string.open_android_settings_special_app_access_display_over_other_apps_phonemood)) }
    }
    fun previewOverlay() {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, UsageMonitorService::class.java).setAction(UsageMonitorService.ACTION_PREVIEW)) }
            .onSuccess { message(context.getString(R.string.preview_in_5_seconds_switch_to_another_app_no_mood_data_will_be_saved)) }
            .onFailure { message(context.getString(R.string.could_not_start_the_preview_keep_phonemood_open_and_try_again)) }
    }
    fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompatEnabled(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    val trackingStatus = when {
        state == null -> R.string.tracking_checking
        !usageAccess -> R.string.tracking_attention
        !config.enabled -> if (state?.firstStartedUtc == null) R.string.tracking_not_started else R.string.tracking_paused
        state?.error != null -> R.string.tracking_attention
        now - (state?.lastHeartbeatUtc ?: 0) >= 45_000 -> R.string.tracking_checking
        else -> R.string.monitoring_alternate
    }
    Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        Surface(color = Cream, tonalElevation = 0.dp) {
            Column { HorizontalDivider(color = Line)
                NavigationBar(containerColor = Cream, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { index, item -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.icon, context.getString(item.label), Modifier.size(22.dp)) }, label = { Text(context.getString(item.label), fontSize = 12.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Forest, selectedTextColor = Forest, indicatorColor = Sage, unselectedIconColor = Muted, unselectedTextColor = Muted)) }
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (tab == 1) AnalysisScreen() else
            LazyColumn(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(), state = scrollState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { Brand(trackingStatus) }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (tab == 0) Text(LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(configuration.locales[0])), style = MaterialTheme.typography.labelSmall, color = Muted)
                        Text(context.getString(if(tab==0) R.string.today else R.string.settings), style = MaterialTheme.typography.headlineLarge)
                    }
                }
                if (tab == 0) {
                    val day = DayWindow.bounds(LocalDate.now(), ZoneId.systemDefault())
                    val todaySegments = segments.filter { DayWindow.overlap(it.startUtc, it.endUtc, day) > 0 }
                    val todayRatings = responses.filter { it.responseTimestampUtc in day }.sortedBy { it.responseTimestampUtc }
                    val presentations=promptStates.associateBy { it.checkpointId }
                    val reminded=checkpoints.count { c -> listOfNotNull(c.notifiedUtc,presentations[c.checkpointId]?.overlayShownUtc).minOrNull()?.let { it in day } == true }
                    item { TodayMetrics(todaySegments.sumOf { DayWindow.overlap(it.startUtc,it.endUtc,day) }, todayRatings, reminded, state?.firstStartedUtc != null) }
                    if (!usageAccess) item { ActionNote(context.getString(R.string.let_s_start_with_a_little_permission), context.getString(R.string.enable_usage_access_so_phonemood_can_measure_active_app_time_on_this_device), context.getString(R.string.enable_usage_access), Icons.Outlined.LockOpen, ::grantUsage) }
                    else if (!config.enabled) item { ActionNote(context.getString(R.string.a_little_awareness_goes_a_long_way), context.getString(R.string.start_interval, config.interval), context.getString(R.string.start_monitoring), Icons.Outlined.PlayArrow) { configure(config.copy(enabled = true)) } }
                    if (state?.error != null) item { ActionNote(context.getString(R.string.monitoring_needs_attention), context.getString(R.string.monitor_error), context.getString(R.string.settings), Icons.Outlined.Info) { tab = 2 } }
                    checkpoints.firstOrNull { c -> c.responseStatus == "PENDING" && now - c.promptTimestampUtc in 0..300_000 }?.let { pending -> item {
                        Button(onClick = { context.startActivity(Intent(context, MoodRatingActivity::class.java).putExtra("checkpointId",pending.checkpointId)) }, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(context.getString(R.string.check_in)) }
                    } }
                    item { DailyObservation(todayRatings,todaySegments,day) }
                    item { MoodHistoryChart(todayRatings) }
                    item { AppUsage(todaySegments,day) }
                    item { OutlinedButton(onClick={tab=1}, modifier=Modifier.fillMaxWidth()) { Text(context.getString(R.string.explore_trends)) } }
                }
                if (tab == 2) {
                    item { SettingsCard(context.getString(R.string.system_permissions)) {
                        SettingRow(context.getString(R.string.usage_access), if (usageAccess) context.getString(R.string.allowed) else context.getString(R.string.permission_needed)) { TextButton(onClick = ::grantUsage) { Text(if (usageAccess) context.getString(R.string.manage) else context.getString(R.string.enable)) } }
                        SettingRow(context.getString(R.string.notifications), if (notifications) context.getString(R.string.check_ins_are_allowed) else context.getString(R.string.check_ins_are_unavailable)) { TextButton(onClick = ::grantNotifications) { Text(context.getString(R.string.manage)) } }
                        SettingRow(context.getString(R.string.display_over_other_apps), if (overlayPermission) context.getString(R.string.allowed) else context.getString(R.string.permission_needed)) { TextButton(onClick = ::grantOverlay) { Text(if (overlayPermission) context.getString(R.string.manage) else context.getString(R.string.allow)) } }
                        if (!overlayPermission) Text(context.getString(R.string.overlay_permission_all_apps), color = Muted, style = MaterialTheme.typography.bodyMedium)
                    } }
                    item { SettingsCard(context.getString(R.string.floating_check_ins)) {
                        SettingRow(context.getString(R.string.show_over_other_apps), context.getString(R.string.a_small_1_10_card_without_leaving_your_app)) { Switch(checked = config.overlayEnabled, enabled = !busy, onCheckedChange = { configure(config.copy(overlayEnabled = it)) }) }
                    } }

                    val appLocales = AppCompatDelegate.getApplicationLocales()
                    val followsSystem = appLocales.isEmpty
                    fun selectLanguage(languageTags: String) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTags))
                        (context as? Activity)?.recreate()
                    }
                    item { SettingsCard(context.getString(R.string.language)) {
                        Text(context.getString(if (followsSystem) R.string.follow_system else if (appLocales[0]?.language == "zh") R.string.language_chinese else R.string.language_english), color = Muted)
                        Column {
                            FilterChip(selected = followsSystem, onClick = { selectLanguage("") }, label = { Text(context.getString(R.string.follow_system_short)) })
                            FilterChip(selected = appLocales[0]?.language == "zh", onClick = { selectLanguage("zh-CN") }, label = { Text(context.getString(R.string.language_chinese)) })
                            FilterChip(selected = appLocales[0]?.language == "en", onClick = { selectLanguage("en") }, label = { Text(context.getString(R.string.language_english)) })
                        }
                    } }
                    item { TextButton(onClick={advancedSettings=!advancedSettings}) { Text(context.getString(R.string.settings_advanced)) } }
                    if(advancedSettings) {
                    item { SettingsCard(context.getString(R.string.your_check_in_rhythm)) {
                        Text(context.getString(R.string.check_in_after), fontWeight = FontWeight.Medium)
                        Text(context.getString(R.string.minutes_of_active_use_across_app_switches), color = Muted, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 30, 60).forEach { minutes -> FilterChip(selected = config.interval == minutes, onClick = { configure(config.copy(interval = minutes)) }, enabled = !busy, label = { Text(context.getString(R.string.minutes_short, minutes)) }) } }
                    } }
                    item { SoftCard {
                        Text(context.getString(R.string.start_fresh_after_a_break), fontWeight = FontWeight.Medium)
                        Text(context.getString(R.string.shorter_breaks_stay_in_the_same_session_break_time_never_counts_as_active_u), color = Muted, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(3, 5, 10).forEach { minutes -> FilterChip(selected = config.reset == minutes, onClick = { configure(config.copy(reset = minutes)) }, enabled = !busy, label = { Text(context.getString(R.string.minutes_short, minutes)) }) } }
                    } }
                    item { Exclusions(config, busy) { configure(config.copy(excluded = it)) } }
                    item { SettingsCard(context.getString(R.string.reliability)) {
                        Text(context.getString(R.string.keep_your_rhythm_in_the_background), style = MaterialTheme.typography.titleMedium)
                        Text(context.getString(R.string.some_phones_restrict_background_activity_battery_settings_can_help_phonemoo), color = Muted, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text(context.getString(R.string.open_battery_settings)); Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp)) }
                    } }
                    }
                    item { SoftCard(Sage) { Icon(Icons.Outlined.Shield, null, tint = Forest); Text(context.getString(R.string.personal_means_private), style = MaterialTheme.typography.titleLarge); Text(context.getString(R.string.no_account_no_cloud_no_ads_phonemood_works_offline_and_keeps_its_records_on), color = Muted, style = MaterialTheme.typography.bodyMedium) } }
                    item { Text(context.getString(R.string.app_version, context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()), style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

private fun NotificationManagerCompatEnabled(context: android.content.Context) = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
fun duration(context: android.content.Context, ms: Long): String {
    if (ms in 1..59_999) return context.getString(R.string.seconds_duration, (ms / 1_000).coerceAtLeast(1))
    val minutes = (ms / 60_000).coerceAtLeast(0)
    return if (minutes >= 60) context.getString(R.string.hours_duration, minutes / 60, minutes % 60) else context.getString(R.string.minutes_duration, minutes)
}
private fun time(context: android.content.Context, at: Long) = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(at))

@Composable private fun Brand(status: Int) {
    val context = LocalContext.current

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.SentimentSatisfiedAlt, null, Modifier.size(28.dp), tint = Forest); Text("phonemood", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp) }
        Surface(color = Sage.copy(alpha = .7f), shape = CircleShape) { Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Box(Modifier.size(6.dp).background(if (status == R.string.monitoring_alternate) Forest else Muted, CircleShape)); Text(context.getString(status), fontSize = 13.sp, color = Forest) } }
    }
}
@Composable fun SoftCard(color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(24.dp), color = color, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }
@Composable private fun ActionNote(title: String, text: String, action: String, icon: ImageVector, onClick: () -> Unit) {
    SoftCard(Peach) { Icon(icon, null, tint = Forest); Text(title, style = MaterialTheme.typography.titleLarge); Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted); Button(onClick = onClick, shape = CircleShape) { Text(action); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp)) } }
}
@Composable private fun AppUsage(segments: List<UsageSegment>, day: LongRange) {
    val context = LocalContext.current

    val grouped = segments.groupBy { it.packageName }.map { (_, values) -> values.first().appName to values.sumOf { DayWindow.overlap(it.startUtc, it.endUtc, day) } }.sortedByDescending { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(context.getString(R.string.where_your_time_went), style = MaterialTheme.typography.titleLarge) }
        if (grouped.isEmpty()) Text(context.getString(R.string.as_you_use_your_phone_your_most_used_apps_will_appear_here), style = MaterialTheme.typography.bodyMedium, color = Muted)
        var expanded by rememberSaveable { mutableStateOf(false) }
        if(grouped.size>5) TextButton(onClick={expanded=!expanded}) { Text(context.getString(if(expanded) R.string.apps_show_less else R.string.see_all)) }
        (if(expanded) grouped else grouped.take(5)).forEach { (name, ms) -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(Sage, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = Forest, fontWeight = FontWeight.Medium) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1); Text(duration(context, ms), fontSize = 13.sp, color = Muted) }; LinearProgressIndicator(progress = { ms.toFloat() / grouped.sumOf { it.second }.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Forest.copy(alpha = .55f), trackColor = Sage, drawStopIndicator = {}) }
        } }
    }
}
@Composable private fun EmptyCard(icon: ImageVector, title: String, text: String) { SoftCard { Icon(icon, null, Modifier.size(32.dp), tint = Forest); Text(title, style = MaterialTheme.typography.titleLarge); Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium) } }
@Composable private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.labelSmall, color = Muted); SoftCard(content = content) } }
@Composable private fun SettingRow(title: String, subtitle: String, trailing: @Composable () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted) }; trailing() } }
@Composable private fun Exclusions(config: Configuration, busy: Boolean, save: (Set<String>) -> Unit) {
    val context = LocalContext.current

    var text by remember(config.excluded) { mutableStateOf(config.excluded.sorted().joinToString("\n")) }
    val packages = text.split('\n', ',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val valid = packages.all { it.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")) }
    SettingsCard(context.getString(R.string.apps_that_don_t_count)) {
        Text(context.getString(R.string.phonemood_your_launcher_keyboard_and_system_controls_are_excluded_automatic), color = Muted, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(context.getString(R.string.additional_excluded_apps)) }, placeholder = { Text("com.example.app") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp), isError = !valid)
        if (!valid) Text(context.getString(R.string.enter_package_names_such_as_com_example_app), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { save(packages) }, enabled = !busy && valid && packages != config.excluded) { Text(context.getString(R.string.save_exclusions)) }
    }
}
@Composable private fun PrivacyFooter() {
    val context = LocalContext.current
 Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lock, null, Modifier.size(13.dp), tint = Muted); Spacer(Modifier.width(6.dp)); Text(context.getString(R.string.only_on_your_phone_always_yours), fontSize = 11.sp, color = Muted) } }

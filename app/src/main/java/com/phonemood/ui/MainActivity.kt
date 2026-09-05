package com.phonemood.ui

import com.phonemood.R
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
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
private val tabs = listOf(Tab(R.string.today, Icons.Outlined.WbSunny), Tab(R.string.timeline, Icons.Outlined.ViewTimeline), Tab(R.string.reports, Icons.Outlined.Description), Tab(R.string.settings, Icons.Outlined.Tune))

@Composable
fun PhoneMoodScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val app = context.phoneMood
    val dao = app.repository.dao
    val state by dao.watchState().collectAsStateWithLifecycle(null)
    val segments by dao.watchSegments().collectAsStateWithLifecycle(emptyList())
    val sessions by dao.watchSessions().collectAsStateWithLifecycle(emptyList())
    val checkpoints by dao.watchCheckpoints().collectAsStateWithLifecycle(emptyList())
    val responses by dao.watchResponses().collectAsStateWithLifecycle(emptyList())
    val reports by dao.watchReports().collectAsStateWithLifecycle(emptyList())
    val storedConfig by app.repository.settings.flow.collectAsStateWithLifecycle(Configuration())
    val config = storedConfig.copy(enabled = state?.monitoringEnabled ?: false)
    var tab by remember { mutableIntStateOf(0) }
    val scrollState = remember(tab) { androidx.compose.foundation.lazy.LazyListState() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var usageAccess by remember { mutableStateOf(context.hasUsageAccess()) }
    var overlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notifications by remember { mutableStateOf(MoodNotificationManager(context).canPrompt()) }
    var busy by remember { mutableStateOf(false) }
    var timelineDate by remember { mutableStateOf(LocalDate.now()) }
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
    fun export(date: LocalDate) {
        scope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { app.reports.generate(date) }; message(context.getString(R.string.saved_to_downloads_phonemoodhealth)) }
            catch (e: Exception) { message(context.getString(R.string.export_failed)) }
            finally { busy = false }
        }
    }
    Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        Surface(color = Cream, tonalElevation = 0.dp) {
            Column { HorizontalDivider(color = Line)
                NavigationBar(containerColor = Cream, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { index, item -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.icon, context.getString(item.label), Modifier.size(22.dp)) }, label = { Text(context.getString(item.label), fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Forest, selectedTextColor = Forest, indicatorColor = Sage, unselectedIconColor = Muted, unselectedTextColor = Muted)) }
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(), state = scrollState, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                item { Brand(config.enabled && usageAccess && state?.error == null && now - (state?.lastHeartbeatUtc ?: 0) < 45_000) }
                if (tab == 0 || tab == 3) item { PermissionSetupCard() }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(when (tab) { 0 -> LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(configuration.locales[0])).uppercase(); 1 -> context.getString(R.string.a_little_more_awareness); 2 -> context.getString(R.string.your_data_in_your_hands); else -> context.getString(R.string.make_space_for_yourself) }, style = MaterialTheme.typography.labelSmall, color = Muted)
                        Text(listOf(context.getString(R.string.a_moment_for_you), context.getString(R.string.your_day_unfolded), context.getString(R.string.small_moments_a_bigger_picture), context.getString(R.string.at_your_own_pace))[tab], style = MaterialTheme.typography.headlineLarge)
                        Text(listOf(context.getString(R.string.notice_your_habits_check_in_with_yourself), context.getString(R.string.see_how_phone_time_fits_into_your_day), context.getString(R.string.daily_records_ready_when_you_need_them), context.getString(R.string.a_gentle_rhythm_that_works_for_you))[tab], style = MaterialTheme.typography.bodyMedium, color = Muted)
                    }
                }
                if (tab == 0) {
                    if (!usageAccess) item { ActionNote(context.getString(R.string.let_s_start_with_a_little_permission), context.getString(R.string.enable_usage_access_so_phonemood_can_measure_active_app_time_on_this_device), context.getString(R.string.enable_usage_access), Icons.Outlined.LockOpen, ::grantUsage) }
                    else if (!config.enabled) item { ActionNote(if (state?.firstStartedUtc == null) context.getString(R.string.a_little_awareness_goes_a_long_way) else context.getString(R.string.take_all_the_space_you_need), context.getString(R.string.start_interval, config.interval), context.getString(R.string.start_monitoring), Icons.Outlined.PlayArrow) { configure(config.copy(enabled = true)); if (Build.VERSION.SDK_INT >= 33 && !notifications) permission.launch(Manifest.permission.POST_NOTIFICATIONS) } }
                    if (config.enabled && config.overlayEnabled && !overlayPermission) item { ActionNote(context.getString(R.string.check_in_without_switching_apps), context.getString(R.string.allow_a_small_rating_card_to_appear_over_the_app_you_re_using_tap_a_score_a), context.getString(R.string.allow_floating_cards), Icons.Outlined.PictureInPictureAlt, ::grantOverlay) }
                    if (config.enabled && !notifications) item { ActionNote(context.getString(R.string.keep_a_notification_backup), context.getString(R.string.notifications_let_you_answer_when_a_floating_card_is_unavailable_usage_is_s), context.getString(R.string.enable_notifications), Icons.Outlined.NotificationsNone, ::grantNotifications) }
                    if (state?.error != null) item { SoftCard(Peach) { Text(context.getString(R.string.monitoring_needs_attention), fontWeight = FontWeight.Medium); Text(context.getString(R.string.monitor_error), style = MaterialTheme.typography.bodyMedium) } }
                    val day = DayWindow.bounds(LocalDate.now(), ZoneId.systemDefault())
                    val todaySegments = segments.filter { DayWindow.overlap(it.startUtc, it.endUtc, day) > 0 }
                    val active = todaySegments.sumOf { DayWindow.overlap(it.startUtc, it.endUtc, day) }
                    val todays = checkpoints.filter { it.promptTimestampUtc in day }
                    val ratings = todays.mapNotNull { c -> responses.find { it.checkpointId == c.checkpointId } }
                    item { UsageHero(active, ratings, todays.size) }
                    val current = sessions.firstOrNull { it.status != "CLOSED" }
                    item { SessionCard(current, config, busy) { configure(config.copy(enabled = !config.enabled)) } }
                    todays.firstOrNull { c -> c.responseStatus == "PENDING" && now - c.promptTimestampUtc < 300_000 }?.let { pending -> item { ActionNote(context.getString(R.string.how_are_you_feeling_right_now), context.getString(R.string.reached_minutes, pending.checkpointMinutes), context.getString(R.string.check_in), Icons.Outlined.SentimentSatisfiedAlt) { context.startActivity(Intent(context, MoodRatingActivity::class.java).putExtra("checkpointId", pending.checkpointId)) } } }
                    item { MoodChart(todays, responses) }
                    item { AppUsage(todaySegments, day) { tab = 1 } }
                    item { PrivacyFooter() }
                }
                if (tab == 1) {
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { timelineDate = timelineDate.minusDays(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, context.getString(R.string.previous_day)) }
                        Text(timelineDate.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(configuration.locales[0])), fontWeight = FontWeight.Medium)
                        IconButton(onClick = { timelineDate = timelineDate.plusDays(1) }, enabled = timelineDate < LocalDate.now()) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, context.getString(R.string.next_day)) }
                    } }
                    val day = DayWindow.bounds(timelineDate, ZoneId.systemDefault())
                    val entries = segments.filter { DayWindow.overlap(it.startUtc, it.endUtc, day) > 0 }
                    val sessionCount = entries.map { it.sessionId }.distinct().size
                    item { SoftCard(Sage) { Text(duration(context, entries.sumOf { DayWindow.overlap(it.startUtc, it.endUtc, day) }), style = MaterialTheme.typography.headlineMedium); Text(context.getString(R.string.sessions_count, sessionCount), color = Muted) } }
                    if (entries.isEmpty()) item { EmptyCard(Icons.Outlined.ViewTimeline, context.getString(R.string.a_little_quiet_here), context.getString(R.string.recorded_app_activity_will_appear_here_as_you_use_your_phone_with_monitorin)) }
                    entries.forEach { segment -> item(key = segment.id) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(10.dp).background(Forest, CircleShape)); Box(Modifier.width(1.dp).height(58.dp).background(Line)) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${time(context, maxOf(segment.startUtc, day.first))} – ${time(context, minOf(segment.endUtc, day.last + 1))}", style = MaterialTheme.typography.labelSmall, color = Muted)
                                Text(segment.appName.ifBlank { segment.packageName }, fontWeight = FontWeight.Medium)
                                Text(segment.packageName, style = MaterialTheme.typography.bodySmall, color = Muted)
                                checkpoints.filter { it.promptTimestampUtc >= segment.startUtc && it.promptTimestampUtc < segment.endUtc }.forEach { c -> Text(responses.find { it.checkpointId == c.checkpointId }?.let { context.getString(R.string.rating_value, it.score) } ?: context.getString(R.string.rating_status, context.getString(when (c.responseStatus) { "ANSWERED" -> R.string.status_answered; "MISSED" -> R.string.status_missed; else -> R.string.status_pending })), color = Forest, style = MaterialTheme.typography.bodySmall) }
                            }
                            Text(duration(context, DayWindow.overlap(segment.startUtc, segment.endUtc, day)), color = Forest, style = MaterialTheme.typography.bodyMedium)
                        }
                    } }
                }
                if (tab == 2) {
                    item { SoftCard(Sage) {
                        Icon(Icons.Outlined.FolderOpen, null, tint = Forest, modifier = Modifier.size(32.dp))
                        Text(context.getString(R.string.one_day_one_simple_file), style = MaterialTheme.typography.titleLarge)
                        Text(context.getString(R.string.app_usage_mood_check_ins_and_data_quality_notes_together_in_a_json_file_you), style = MaterialTheme.typography.bodyMedium, color = Muted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Outlined.CheckCircleOutline, null, Modifier.size(15.dp), tint = Forest); Text(context.getString(R.string.automatically_saved_after_each_day), style = MaterialTheme.typography.bodySmall, color = Forest) }
                    } }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(context.getString(R.string.daily_records), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { export(LocalDate.now()) }, enabled = !busy && state?.firstStartedUtc != null) { Text(if (busy) context.getString(R.string.saving) else context.getString(R.string.export_today)); Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)) } } }
                    if (reports.isEmpty()) item { EmptyCard(Icons.Outlined.Description, context.getString(R.string.your_story_starts_here), context.getString(R.string.your_first_daily_report_will_be_saved_after_midnight_you_can_also_export_to)) }
                    reports.forEach { report -> item(key = report.localDate) {
                        Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.Description, null, tint = Forest)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(LocalDate.parse(report.localDate).format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(configuration.locales[0])), fontWeight = FontWeight.Medium); Text(if (report.status == "FINAL") context.getString(R.string.generated) else context.getString(R.string.partial_coverage_notes_included), style = MaterialTheme.typography.bodySmall, color = Muted) }
                                IconButton(onClick = {
                                    if (report.uri != null) runCatching {
                                        val uri = Uri.parse(report.uri)
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = android.content.ClipData.newRawUri(context.getString(R.string.daily_phone_mood_record), uri) }, context.getString(R.string.share_daily_record)))
                                    }.onFailure { message(context.getString(R.string.could_not_open_this_export_rebuild_the_report)) }
                                }, enabled = report.uri != null) { Icon(Icons.Outlined.IosShare, context.getString(R.string.share_date, report.localDate)) }
                                IconButton(onClick = { export(LocalDate.parse(report.localDate)) }, enabled = !busy) { Icon(Icons.Outlined.Refresh, context.getString(R.string.rebuild_date, report.localDate)) }
                            }
                        }
                    } }
                    item { Text(context.getString(R.string.saved_on_this_device), style = MaterialTheme.typography.labelSmall, color = Muted); Spacer(Modifier.height(8.dp)); Text(context.getString(R.string.downloads_phonemoodhealth), color = Forest); Spacer(Modifier.height(8.dp)); Text(context.getString(R.string.late_check_ins_update_the_same_day_s_file_nothing_is_uploaded_automatically), style = MaterialTheme.typography.bodyMedium, color = Muted) }
                    item { PrivacyFooter() }
                }
                if (tab == 3) {
                    item { SettingsCard(context.getString(R.string.language)) { Text(context.getString(R.string.follow_system), color = Muted) } }
                    item { SettingsCard(context.getString(R.string.monitoring)) {
                        SettingRow(context.getString(R.string.usage_monitoring), if (config.enabled) context.getString(R.string.running_locally_on_your_phone) else context.getString(R.string.paused_until_you_re_ready)) { Switch(checked = config.enabled, enabled = !busy && usageAccess, onCheckedChange = { configure(config.copy(enabled = it)) }) }
                        HorizontalDivider(color = Line)
                        SettingRow(context.getString(R.string.usage_access), if (usageAccess) context.getString(R.string.allowed) else context.getString(R.string.permission_needed)) { TextButton(onClick = ::grantUsage) { Text(if (usageAccess) context.getString(R.string.manage) else context.getString(R.string.enable)) } }
                        SettingRow(context.getString(R.string.notifications), if (notifications) context.getString(R.string.check_ins_are_allowed) else context.getString(R.string.check_ins_are_unavailable)) { TextButton(onClick = ::grantNotifications) { Text(context.getString(R.string.manage)) } }
                    } }
                    item { SettingsCard(context.getString(R.string.floating_check_ins)) {
                        SettingRow(context.getString(R.string.show_over_other_apps), context.getString(R.string.a_small_1_10_card_without_leaving_your_app)) { Switch(checked = config.overlayEnabled, enabled = !busy, onCheckedChange = { configure(config.copy(overlayEnabled = it)) }) }
                        SettingRow(context.getString(R.string.display_over_other_apps), if (overlayPermission) context.getString(R.string.allowed) else context.getString(R.string.permission_needed)) { TextButton(onClick = ::grantOverlay) { Text(if (overlayPermission) context.getString(R.string.manage) else context.getString(R.string.allow)) } }
                        Text(context.getString(R.string.tap_a_score_to_save_and_close_later_waits_one_minute_closes_this_card_notif), color = Muted, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = ::previewOverlay, enabled = overlayPermission && !busy) { Icon(Icons.Outlined.PictureInPictureAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(context.getString(R.string.preview_in_5_seconds)) }
                        Text(context.getString(R.string.switch_to_another_app_after_tapping_preview_scores_are_not_recorded), color = Muted, style = MaterialTheme.typography.bodySmall)
                    } }
                    item { SettingsCard(context.getString(R.string.your_check_in_rhythm)) {
                        Text(context.getString(R.string.check_in_after), fontWeight = FontWeight.Medium)
                        Text(context.getString(R.string.minutes_of_active_use_across_app_switches), color = Muted, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 30, 60).forEach { minutes -> FilterChip(selected = config.interval == minutes, onClick = { configure(config.copy(interval = minutes)) }, enabled = !busy, label = { Text(context.getString(R.string.minutes_short, minutes)) }) } }
                        HorizontalDivider(color = Line)
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
                    item { SoftCard(Sage) { Icon(Icons.Outlined.Shield, null, tint = Forest); Text(context.getString(R.string.personal_means_private), style = MaterialTheme.typography.titleLarge); Text(context.getString(R.string.no_account_no_cloud_no_ads_phonemood_works_offline_and_keeps_its_records_on), color = Muted, style = MaterialTheme.typography.bodyMedium) } }
                    item { Text(context.getString(R.string.phonemood_1_1_made_for_a_little_awareness), style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
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

@Composable private fun Brand(running: Boolean) {
    val context = LocalContext.current

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.SentimentSatisfiedAlt, null, Modifier.size(28.dp), tint = Forest); Text("phonemood", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp) }
        Surface(color = Sage.copy(alpha = .7f), shape = CircleShape) { Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Box(Modifier.size(6.dp).background(if (running) Forest else Muted, CircleShape)); Text(if (running) context.getString(R.string.monitoring_alternate) else context.getString(R.string.on_your_terms), fontSize = 10.sp, color = Forest) } }
    }
}
@Composable fun SoftCard(color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(24.dp), color = color, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }
@Composable private fun ActionNote(title: String, text: String, action: String, icon: ImageVector, onClick: () -> Unit) {
    SoftCard(Peach) { Icon(icon, null, tint = Forest); Text(title, style = MaterialTheme.typography.titleLarge); Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted); Button(onClick = onClick, shape = CircleShape) { Text(action); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp)) } }
}
@Composable private fun UsageHero(active: Long, ratings: List<MoodResponse>, count: Int) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    SoftCard(Sage) {
        Text(context.getString(R.string.today_s_active_phone_time), style = MaterialTheme.typography.labelSmall, color = Forest)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(duration(context, active), fontSize = 52.sp, fontFamily = FontFamily.Serif, letterSpacing = (-2).sp); Text(context.getString(R.string.one_moment_at_a_time), color = Forest, style = MaterialTheme.typography.bodyMedium) }
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White.copy(alpha = .5f)); drawCircle(Forest.copy(alpha = .10f), radius = size.width * .40f, style = Stroke(1.5.dp.toPx()))
                    drawCircle(Forest, 2.5.dp.toPx(), Offset(size.width * .38f, size.height * .42f)); drawCircle(Forest, 2.5.dp.toPx(), Offset(size.width * .62f, size.height * .42f))
                    drawArc(Forest, 20f, 140f, false, Offset(size.width * .32f, size.height * .42f), Size(size.width * .36f, size.height * .26f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        HorizontalDivider(color = Forest.copy(alpha = .15f))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(if (ratings.isEmpty()) "—" else String.format(configuration.locales[0], "%.1f", ratings.map { it.score }.average()), fontSize = 26.sp, fontFamily = FontFamily.Serif); Text(context.getString(R.string.average_mood_10), color = Muted, style = MaterialTheme.typography.bodySmall) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("${ratings.size} / $count", fontSize = 26.sp, fontFamily = FontFamily.Serif); Text(context.getString(R.string.check_ins_completed), color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
@Composable private fun SessionCard(session: PhoneSession?, config: Configuration, busy: Boolean, toggle: () -> Unit) {
    val context = LocalContext.current

    SoftCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(context.getString(R.string.your_current_rhythm), style = MaterialTheme.typography.titleLarge); if (config.enabled) IconButton(onClick = toggle, enabled = !busy) { Icon(Icons.Outlined.Pause, context.getString(R.string.pause_monitoring), tint = Forest) } }
        val active = session?.activeDurationMs ?: 0L
        val next = session?.nextCheckpointMinutes ?: config.interval
        Text(if (!config.enabled) context.getString(R.string.monitoring_is_paused) else if (session?.status == "INTERRUPTED") context.getString(R.string.taking_a_short_break) else if (session == null) context.getString(R.string.ready_when_you_are) else context.getString(R.string.minutes_next, ((next * 60_000 - active).coerceAtLeast(0) / 60_000.0).roundToInt()), fontWeight = FontWeight.Medium, color = Forest)
        LinearProgressIndicator(progress = { (active.toFloat() / (next * 60_000)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Forest, trackColor = Sage, strokeCap = StrokeCap.Round, drawStopIndicator = {})
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(context.getString(R.string.session_active, duration(context, active)), style = MaterialTheme.typography.bodySmall, color = Muted); Text(context.getString(R.string.minutes_short, next), style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}
@Composable private fun MoodChart(checkpoints: List<MoodCheckpoint>, responses: List<MoodResponse>) {
    val context = LocalContext.current

    val points = checkpoints.sortedBy { it.promptTimestampUtc }.mapNotNull { c -> responses.find { it.checkpointId == c.checkpointId }?.let { c.promptTimestampUtc to it.score } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(context.getString(R.string.how_you_ve_been_feeling), style = MaterialTheme.typography.titleLarge); Icon(Icons.Outlined.SentimentSatisfiedAlt, null, tint = Forest) }
        if (points.isEmpty()) SoftCard { Text(context.getString(R.string.a_small_pause_a_simple_number), fontWeight = FontWeight.Medium); Text(context.getString(R.string.your_mood_check_ins_will_show_up_here_there_s_no_right_way_to_feel), color = Muted, style = MaterialTheme.typography.bodyMedium) }
        else SoftCard {
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                listOf(0f, .5f, 1f).forEach { fraction -> drawLine(Line, Offset(0f, fraction * size.height), Offset(size.width, fraction * size.height), 1.dp.toPx()) }
                val first = points.first().first; val range = (points.last().first - first).coerceAtLeast(1)
                val coords = points.map { Offset(if (points.size == 1) size.width / 2 else 8.dp.toPx() + (it.first - first).toFloat() / range * (size.width - 16.dp.toPx()), size.height - ((it.second - 1) / 9f * (size.height - 12.dp.toPx()) + 6.dp.toPx())) }
                coords.zipWithNext().forEach { (a, b) -> drawLine(Forest, a, b, 2.dp.toPx(), StrokeCap.Round) }
                coords.forEach { drawCircle(Sage, 7.dp.toPx(), it); drawCircle(Forest, 3.5.dp.toPx(), it) }
            }
            Text(points.joinToString("   ·   ") { "${time(context, it.first)}  ${it.second}/10" }, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}
@Composable private fun AppUsage(segments: List<UsageSegment>, day: LongRange, seeAll: () -> Unit) {
    val context = LocalContext.current

    val grouped = segments.groupBy { it.packageName }.map { (_, values) -> values.first().appName to values.sumOf { DayWindow.overlap(it.startUtc, it.endUtc, day) } }.sortedByDescending { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(context.getString(R.string.where_your_time_went), style = MaterialTheme.typography.titleLarge); TextButton(onClick = seeAll) { Text(context.getString(R.string.see_all), fontSize = 12.sp) } }
        if (grouped.isEmpty()) Text(context.getString(R.string.as_you_use_your_phone_your_most_used_apps_will_appear_here), style = MaterialTheme.typography.bodyMedium, color = Muted)
        grouped.take(4).forEach { (name, ms) -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Text(context.getString(R.string.a_little_room_between_apps), style = MaterialTheme.typography.titleMedium)
        Text(context.getString(R.string.phonemood_your_launcher_keyboard_and_system_controls_are_excluded_automatic), color = Muted, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(context.getString(R.string.additional_excluded_apps)) }, placeholder = { Text("com.example.app") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp), isError = !valid)
        if (!valid) Text(context.getString(R.string.enter_package_names_such_as_com_example_app), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { save(packages) }, enabled = !busy && valid && packages != config.excluded) { Text(context.getString(R.string.save_exclusions)) }
    }
}
@Composable private fun PrivacyFooter() {
    val context = LocalContext.current
 Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lock, null, Modifier.size(13.dp), tint = Muted); Spacer(Modifier.width(6.dp)); Text(context.getString(R.string.only_on_your_phone_always_yours), fontSize = 11.sp, color = Muted) } }

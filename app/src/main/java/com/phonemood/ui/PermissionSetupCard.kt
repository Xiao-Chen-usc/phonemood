package com.phonemood.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.phonemood.R
import com.phonemood.monitoring.hasUsageAccess
import com.phonemood.mood.MoodNotificationManager

/** A user-initiated sequence of system permission screens; never grants permissions itself. */
@Composable
fun PermissionSetupCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var step by rememberSaveable { mutableIntStateOf(-1) }
    var waiting by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val usage = remember(revision) { context.hasUsageAccess() }
    val notifications = remember(revision) { MoodNotificationManager(context).canPrompt() }
    val overlay = remember(revision) { Settings.canDrawOverlays(context) }
    val battery = remember(revision) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val external = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        waiting = false; step += 1; revision += 1
    }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        waiting = false; step += 1; revision += 1
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision += 1 }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(step, waiting) {
        if (step !in 0..3 || waiting) return@LaunchedEffect
        val granted = listOf(usage, notifications, overlay, battery)[step]
        if (granted) { step += 1; return@LaunchedEffect }
        waiting = true
        try {
            if (step == 1 && Build.VERSION.SDK_INT >= 33 && !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                notification.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val intent = when (step) {
                    0 -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))
                    1 -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    2 -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:${context.packageName}"))
                    else -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${context.packageName}"))
                }
                external.launch(intent)
            }
        } catch (_: Exception) { waiting = false; step = -1; failed = true }
    }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val done = usage && notifications && overlay && battery
    SoftCard(Sage) {
        Text(context.getString(if (done) R.string.setup_ready else R.string.setup_title), style = MaterialTheme.typography.titleLarge)
        Text(context.getString(R.string.setup_explanation), style = MaterialTheme.typography.bodyMedium)
        Text(context.getString(R.string.setup_status,
            context.getString(if (usage) R.string.allowed else R.string.permission_needed),
            context.getString(if (notifications) R.string.allowed else R.string.permission_needed),
            context.getString(if (overlay) R.string.allowed else R.string.permission_needed),
            context.getString(if (battery) R.string.allowed else R.string.permission_needed)), style = MaterialTheme.typography.bodySmall)
        if (!done) Button(onClick = { failed = false; step = 0 }, enabled = step !in 0..3) { Text(context.getString(if (step in 0..3) R.string.setup_in_progress else R.string.setup_action)) }
        TextButton(onClick = { showHelp = !showHelp }) { Text(context.getString(R.string.setup_steps_help)) }
        if (showHelp) Text(context.getString(R.string.setup_steps_explanation), style = MaterialTheme.typography.bodyMedium)
        if (failed) Text(context.getString(R.string.setup_unavailable), color = MaterialTheme.colorScheme.error)
    }
}

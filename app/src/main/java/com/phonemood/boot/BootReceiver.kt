package com.phonemood.boot

import android.content.*
import androidx.core.content.ContextCompat
import com.phonemood.phoneMood
import com.phonemood.monitoring.*
import kotlinx.coroutines.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.phoneMood.reconcileSoon()
                if (context.phoneMood.repository.configuration().enabled && context.hasUsageAccess()) {
                    runCatching { ContextCompat.startForegroundService(context, Intent(context, UsageMonitorService::class.java)) }
                        .onFailure { failure -> context.phoneMood.repository.dao.state()?.let { context.phoneMood.repository.dao.saveState(it.copy(error = "Open PhoneMood to resume monitoring: ${failure.message}")) } }
                }
            } finally { pending.finish() }
        }
    }
}

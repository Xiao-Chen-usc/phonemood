package com.phonemood.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("phonemood_settings")
data class Configuration(val enabled: Boolean = false, val interval: Int = 30, val reset: Int = 5, val excluded: Set<String> = emptySet(), val overlayEnabled: Boolean = true)
class SettingsStore(private val context: Context) {
    private val overlay = booleanPreferencesKey("overlayEnabled")
    private val enabled = booleanPreferencesKey("monitoringEnabled")
    private val interval = intPreferencesKey("moodIntervalMinutes")
    private val reset = intPreferencesKey("sessionResetMinutes")
    private val excluded = stringSetPreferencesKey("excludedPackages")
    val flow = context.settingsDataStore.data.map { Configuration(it[enabled] ?: false, it[interval] ?: 30, it[reset] ?: 5, it[excluded] ?: emptySet(), it[overlay] ?: true) }
    suspend fun save(value: Configuration) {
        require(value.interval in 5..180 && value.reset in 1..30)
        context.settingsDataStore.edit { it[enabled] = value.enabled; it[interval] = value.interval; it[reset] = value.reset; it[excluded] = value.excluded; it[overlay] = value.overlayEnabled }
    }
}

package com.wananjiaoda.assistant.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.wananjiaoda.assistant.data.local.AppPreferencesDataStore
import com.wananjiaoda.assistant.domain.model.CheckInMode
import com.wananjiaoda.assistant.domain.model.CheckInSettings
import com.wananjiaoda.assistant.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(private val dataStore: AppPreferencesDataStore) : SettingsRepository {
    private object Keys {
        val timeMinutes = intPreferencesKey("check_in_time_minutes")
        val weekdays = stringSetPreferencesKey("check_in_weekdays")
        val autoEnabled = booleanPreferencesKey("auto_check_in_enabled")
        val mode = stringPreferencesKey("check_in_mode")
    }

    override val settings: Flow<CheckInSettings> = dataStore.store.data.map { values ->
        val defaults = CheckInSettings()
        val minutes = (values[Keys.timeMinutes] ?: defaults.hour * 60 + defaults.minute)
            .takeIf { it in 0 until 24 * 60 } ?: defaults.hour * 60 + defaults.minute
        CheckInSettings(
            hour = minutes / 60,
            minute = minutes % 60,
            weekdays = values[Keys.weekdays]?.mapNotNull { it.toIntOrNull()?.takeIf { day -> day in 1..7 } }
                ?.toSet() ?: defaults.weekdays,
            autoEnabled = values[Keys.autoEnabled] ?: defaults.autoEnabled,
            mode = values[Keys.mode]?.let { stored ->
                CheckInMode.entries.firstOrNull { it.name == stored }
            } ?: defaults.mode,
        )
    }

    override suspend fun setTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        dataStore.store.edit { it[Keys.timeMinutes] = hour * 60 + minute }
    }

    override suspend fun setWeekday(day: Int, selected: Boolean) {
        require(day in 1..7)
        dataStore.store.edit { values ->
            val current = values[Keys.weekdays] ?: (1..5).map(Int::toString).toSet()
            values[Keys.weekdays] = if (selected) current + day.toString() else current - day.toString()
        }
    }

    override suspend fun setAutoEnabled(enabled: Boolean) {
        dataStore.store.edit { it[Keys.autoEnabled] = enabled }
    }

    override suspend fun setMode(mode: CheckInMode) {
        dataStore.store.edit { it[Keys.mode] = mode.name }
    }
}

package com.wananjiaoda.assistant.domain.repository

import com.wananjiaoda.assistant.domain.model.CheckInMode
import com.wananjiaoda.assistant.domain.model.CheckInSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<CheckInSettings>
    suspend fun setTime(hour: Int, minute: Int)
    suspend fun setWeekday(day: Int, selected: Boolean)
    suspend fun setAutoEnabled(enabled: Boolean)
    suspend fun setMode(mode: CheckInMode)
}

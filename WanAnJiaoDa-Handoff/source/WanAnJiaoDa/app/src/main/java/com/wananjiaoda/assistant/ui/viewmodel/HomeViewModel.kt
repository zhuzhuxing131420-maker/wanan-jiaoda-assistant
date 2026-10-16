package com.wananjiaoda.assistant.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wananjiaoda.assistant.data.local.AppDatabase
import com.wananjiaoda.assistant.data.local.AppPreferencesDataStore
import com.wananjiaoda.assistant.data.repository.DataStoreSettingsRepository
import com.wananjiaoda.assistant.data.repository.OfflineCheckInHistoryRepository
import com.wananjiaoda.assistant.domain.model.CheckInSettings
import com.wananjiaoda.assistant.domain.model.CheckInTimeZone
import com.wananjiaoda.assistant.domain.model.TodayCheckInStatus
import java.time.LocalDate
import java.time.ZonedDateTime
import com.wananjiaoda.assistant.scheduler.NextCheckInTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val todayStatus: String = "未执行",
    val nextCheckIn: String = nextPlannedTime(CheckInSettings()),
    val total: Int = 0,
    val success: Int = 0,
    val failure: Int = 0,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = DataStoreSettingsRepository(AppPreferencesDataStore(application)).settings
    private val records = OfflineCheckInHistoryRepository(
        AppDatabase.getInstance(application).checkInRecordDao(),
    ).observeRecords()

    val uiState = combine(settings, records) { preferences, history ->
        val today = LocalDate.now(CheckInTimeZone.zoneId)
        HomeUiState(
            todayStatus = TodayCheckInStatus.resolve(history, today),
            nextCheckIn = nextPlannedTime(preferences),
            total = history.size,
            success = history.count { it.outcome.equals("success", ignoreCase = true) },
            failure = history.count { it.outcome.equals("failure", ignoreCase = true) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun nextPlannedTime(settings: CheckInSettings): String {
    if (!settings.autoEnabled) return "已关闭"
    if (settings.weekdays.isEmpty()) return "未选择星期"
    val next = NextCheckInTime.calculate(settings, ZonedDateTime.now(CheckInTimeZone.zoneId))
        ?: return "未选择星期"
    return "${weekdayNames[next.dayOfWeek.value - 1]} %02d:%02d".format(settings.hour, settings.minute)
}



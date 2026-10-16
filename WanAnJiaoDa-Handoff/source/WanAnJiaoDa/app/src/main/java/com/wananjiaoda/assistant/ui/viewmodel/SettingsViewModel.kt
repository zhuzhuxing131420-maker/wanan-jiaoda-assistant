package com.wananjiaoda.assistant.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wananjiaoda.assistant.data.local.AppPreferencesDataStore
import com.wananjiaoda.assistant.data.repository.DataStoreSettingsRepository
import com.wananjiaoda.assistant.domain.model.CheckInMode
import com.wananjiaoda.assistant.domain.model.CheckInSettings
import com.wananjiaoda.assistant.scheduler.CheckInScheduler
import com.wananjiaoda.assistant.scheduler.ScheduledExecution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = DataStoreSettingsRepository(AppPreferencesDataStore(application))
    val settings = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInSettings(),
    )
    private val _exactAlarmAllowed = MutableStateFlow(CheckInScheduler.canScheduleExactAlarms(appContext))
    val exactAlarmAllowed = _exactAlarmAllowed.asStateFlow()
    private val scheduleRefreshRevision = MutableStateFlow(0L)
    val nextTaskText = combine(settings, exactAlarmAllowed, scheduleRefreshRevision) { values, allowed, _ ->
        CheckInScheduler.nextTaskLabel(values, allowed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "检查中")

    fun refreshScheduleState() {
        _exactAlarmAllowed.value = CheckInScheduler.canScheduleExactAlarms(appContext)
        scheduleRefreshRevision.value++
        viewModelScope.launch { refreshAlarm() }
    }

    fun setTime(hour: Int, minute: Int) = updateSchedule { repository.setTime(hour, minute) }
    fun setWeekday(day: Int, selected: Boolean) =
        updateSchedule { repository.setWeekday(day, selected) }
    fun setAutoEnabled(enabled: Boolean) = updateSchedule { repository.setAutoEnabled(enabled) }

    fun setMode(mode: CheckInMode) {
        viewModelScope.launch { repository.setMode(mode); ScheduledExecution.resume(appContext) }
    }

    fun triggerTestTask() {
        CheckInScheduler.triggerTest(appContext)
    }

    private fun updateSchedule(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            refreshAlarm()
            ScheduledExecution.resume(appContext)
        }
    }

    private suspend fun refreshAlarm() {
        try {
            CheckInScheduler.refresh(appContext)
        } catch (error: Exception) {
            Log.e(CheckInScheduler.TAG, "设置变更后注册任务失败", error)
        }
    }
}

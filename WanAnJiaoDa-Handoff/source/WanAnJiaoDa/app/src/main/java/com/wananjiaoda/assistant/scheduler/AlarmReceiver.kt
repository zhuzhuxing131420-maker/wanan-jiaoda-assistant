package com.wananjiaoda.assistant.scheduler

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wananjiaoda.assistant.data.local.AppPreferencesDataStore
import com.wananjiaoda.assistant.data.repository.DataStoreSettingsRepository
import com.wananjiaoda.assistant.domain.model.CheckInTimeZone
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ScheduledTestAlarm.ACTION_TEST, ScheduledTestAlarm.ACTION_EXPIRE ->
                handleAsync(context, isAlarm = false, pendingOnly = true,
                    plannedAtMillis = intent.getLongExtra(CheckInScheduler.EXTRA_SCHEDULED_AT, -1L),
                    receivedAtMillis = System.currentTimeMillis())
            CheckInScheduler.ACTION_TEST -> handleAsync(context, isAlarm = false, isTest = true,
                receivedAtMillis = System.currentTimeMillis())
            CheckInScheduler.ACTION_ALARM -> {
                val actual = System.currentTimeMillis()
                val planned = intent.getLongExtra(CheckInScheduler.EXTRA_SCHEDULED_AT, -1L)
                Log.i(CheckInScheduler.TAG, "闹钟收到：planned=$planned actual=$actual latenessMillis=" +
                    if (planned > 0) (actual - planned).toString() else "unknown")
                handleAsync(context, isAlarm = true, plannedAtMillis = planned, receivedAtMillis = actual)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                handleAsync(context, isAlarm = false)
        }
    }

    private fun handleAsync(context: Context, isAlarm: Boolean, isTest: Boolean = false, pendingOnly: Boolean = false,
        plannedAtMillis: Long = -1L, receivedAtMillis: Long = System.currentTimeMillis()) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000) {
                    if (pendingOnly) {
                        AlarmExecutionDiagnostics.record(appContext, if (plannedAtMillis > 0) "scheduled_navigation_test" else "pending_expiry",
                            plannedAtMillis, receivedAtMillis)
                        ScheduledExecution.processPending(appContext)
                    }
                    if (isAlarm || isTest) {
                        AlarmExecutionDiagnostics.record(appContext, if (isTest) "manual_test" else "alarm",
                            plannedAtMillis, receivedAtMillis)
                    }
                    if (isTest) CheckInTask.run()
                    if (isAlarm) {
                        val settings = DataStoreSettingsRepository(
                            AppPreferencesDataStore(appContext),
                        ).settings.first()
                        when {
                            !settings.autoEnabled -> Log.i(CheckInTask.TAG, "自动签到已关闭，任务已跳过")
                            settings.weekdays.isEmpty() -> Log.i(CheckInTask.TAG, "未选择签到日，任务已跳过")
                            LocalDate.now(CheckInTimeZone.zoneId).dayOfWeek.value !in settings.weekdays ->
                                Log.i(CheckInTask.TAG, "今日非签到日，任务已跳过")
                            else -> ScheduledExecution.receive(appContext, plannedAtMillis)
                        }
                    }
                    CheckInScheduler.refresh(appContext)
                    if (!isTest && !isAlarm && !pendingOnly) ScheduledExecution.processPending(appContext)
                }
            } catch (error: Exception) {
                Log.e(CheckInScheduler.TAG, "接收任务或恢复调度失败", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

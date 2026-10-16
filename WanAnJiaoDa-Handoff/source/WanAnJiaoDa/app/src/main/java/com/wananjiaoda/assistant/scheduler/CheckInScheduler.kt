package com.wananjiaoda.assistant.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wananjiaoda.assistant.data.local.AppPreferencesDataStore
import com.wananjiaoda.assistant.data.repository.DataStoreSettingsRepository
import com.wananjiaoda.assistant.domain.model.CheckInSettings
import com.wananjiaoda.assistant.domain.model.CheckInTimeZone
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Schedules the next selected check-in day. The receiver registers the following task. */
object CheckInScheduler {
    const val TAG = "WanAnJiaoDaScheduler"
    const val ACTION_ALARM = "com.wananjiaoda.assistant.action.CHECK_IN_ALARM"
    const val ACTION_TEST = "com.wananjiaoda.assistant.action.TEST_CHECK_IN_TASK"
    const val EXTRA_SCHEDULED_AT = "scheduled_at_millis"
    private const val REQUEST_CODE = 1001
    private val rescheduleMutex = Mutex()
    private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun nextDailyTrigger(settings: CheckInSettings, now: ZonedDateTime = ZonedDateTime.now(CheckInTimeZone.zoneId)): ZonedDateTime? =
        NextCheckInTime.calculate(settings, now)

    fun nextTaskLabel(settings: CheckInSettings, exactAlarmAllowed: Boolean): String {
        if (!settings.autoEnabled) return "已关闭"
        if (settings.weekdays.isEmpty()) return "未选择星期"
        if (!exactAlarmAllowed) return "等待精确闹钟授权"
        val next = requireNotNull(nextDailyTrigger(settings))
        return weekdays[next.dayOfWeek.value - 1] + " %02d:%02d".format(settings.hour, settings.minute)
    }

    suspend fun refresh(context: Context) = rescheduleMutex.withLock {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        existingAlarm(appContext)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }

        val settings = DataStoreSettingsRepository(AppPreferencesDataStore(appContext)).settings.first()
        val next = nextDailyTrigger(settings)
        if (next == null) {
            Log.i(TAG, "自动签到已关闭或未选择星期，任务已取消")
            return@withLock
        }
        if (!canScheduleExactAlarms(appContext)) {
            Log.w(TAG, "精确闹钟权限未开启，任务未注册")
            return@withLock
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext, REQUEST_CODE, alarmIntent(appContext).putExtra(EXTRA_SCHEDULED_AT, next.toInstant().toEpochMilli()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            UserVisibleAlarm.schedule(appContext, next.toInstant().toEpochMilli(), pendingIntent)
            Log.i(TAG, "下一次任务已注册：" + next)
        } catch (error: SecurityException) {
            pendingIntent.cancel()
            Log.e(TAG, "精确闹钟授权发生变化，任务未注册", error)
        }
    }

    fun triggerTest(context: Context) {
        context.sendBroadcast(
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_TEST),
        )
    }

    private fun existingAlarm(context: Context): PendingIntent? =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, alarmIntent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun alarmIntent(context: Context): Intent =
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_ALARM)
}

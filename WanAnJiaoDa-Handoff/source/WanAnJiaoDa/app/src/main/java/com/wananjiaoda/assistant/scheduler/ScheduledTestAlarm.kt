package com.wananjiaoda.assistant.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Test and expiry alarm tokens are separate from the formal daily schedule. */
object ScheduledTestAlarm {
    const val ACTION_TEST = "com.wananjiaoda.assistant.action.SCHEDULED_NAVIGATION_TEST"
    const val ACTION_EXPIRE = "com.wananjiaoda.assistant.action.SCHEDULED_EXPIRY"
    private fun token(context: Context, action: String, code: Int, at: Long = -1) = PendingIntent.getBroadcast(context, code,
        Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(CheckInScheduler.EXTRA_SCHEDULED_AT, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context: Context, ticket: ScheduledTicket) {
        UserVisibleAlarm.schedule(context, ticket.plannedAtMillis, token(context, ACTION_TEST, 1002, ticket.plannedAtMillis))
    }
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(token(context, ACTION_TEST, 1002))
    }
    fun restoreTest(context: Context, records: List<ScheduledTicket>) {
        if (!CheckInScheduler.canScheduleExactAlarms(context)) return
        records.lastOrNull { it.navigationOnly && it.status == "PLANNED" && it.plannedAtMillis > System.currentTimeMillis() }
            ?.let { schedule(context, it) }
    }
    fun refreshExpiry(context: Context, records: List<ScheduledTicket>) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val expiry = token(context, ACTION_EXPIRE, 1003)
        manager.cancel(expiry)
        val deadline = records.filter { it.status == "WAITING" }.minOfOrNull { it.deadlineMillis } ?: return
        if (CheckInScheduler.canScheduleExactAlarms(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, expiry)
    }
}

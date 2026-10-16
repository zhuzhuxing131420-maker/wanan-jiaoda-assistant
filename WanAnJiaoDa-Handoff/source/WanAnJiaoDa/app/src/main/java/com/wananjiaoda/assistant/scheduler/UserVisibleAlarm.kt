package com.wananjiaoda.assistant.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wananjiaoda.assistant.MainActivity

/** User-configured task time appears in the system's next alarm UI, with an entry to this app. */
object UserVisibleAlarm {
    fun schedule(context: Context, atMillis: Long, operation: PendingIntent) {
        val show = PendingIntent.getActivity(context, 1005, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
    }
}

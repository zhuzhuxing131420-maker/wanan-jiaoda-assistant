package com.wananjiaoda.assistant.scheduler

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.wananjiaoda.assistant.MainActivity

object ScheduledTaskNotification {
    fun show(context: Context, message: String, requiresAction: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channelId = if (requiresAction) "scheduled_reminders" else "scheduled_tasks"
            val channel = NotificationChannel(channelId, if (requiresAction) "解锁与权限提醒" else "定时执行结果",
                if (requiresAction) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            manager.createNotificationChannel(channel)
            val open = PendingIntent.getActivity(context, 1004, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(1004, Notification.Builder(context, channelId).setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(if (requiresAction) "晚安交大：任务等待恢复" else "晚安交大助手")
                .setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_REMINDER)
                .setContentText(message).setStyle(Notification.BigTextStyle().bigText(message))
                .setContentIntent(open).setAutoCancel(true).build())
        }.onFailure { Log.w(CheckInScheduler.TAG, "通知失败：${it.javaClass.simpleName}") }
    }
}

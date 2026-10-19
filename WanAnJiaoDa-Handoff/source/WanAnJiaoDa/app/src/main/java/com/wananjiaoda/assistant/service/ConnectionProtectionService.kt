package com.wananjiaoda.assistant.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.wananjiaoda.assistant.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** An idle foreground process anchor; never reads a screen or starts automation. */
class ConnectionProtectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ConnectionProtectionController.setEnabled(this, false)
            return if (ConnectionProtectionController.isEnabled(this)) START_STICKY else stopProtection()
        }
        // This includes null-intent sticky recreation: disabled always wins.
        if (!ConnectionProtectionPolicy.shouldStart(ConnectionProtectionController.isEnabled(this), false)) {
            return stopProtection()
        }
        try {
            ConnectionProtectionController.notificationProblem(this)?.let { problem ->
                ConnectionProtectionController.failed(this, problem)
                return stopProtection()
            }
            // No asynchronous work before entering foreground (the system deadline is 5 seconds).
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(NOTIFICATION_ID, notification)
            ConnectionProtectionController.started(this)
            if (!observing) {
                observing = true
                scope.launch {
                    AccessibilityEventLogger.connected.collect {
                        ConnectionProtectionController.publish(this@ConnectionProtectionService)
                        try {
                            val problem = ConnectionProtectionController.notificationProblem(this@ConnectionProtectionService)
                            if (problem != null) {
                                ConnectionProtectionController.failed(this@ConnectionProtectionService, problem)
                                stopProtection()
                            } else getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
                        } catch (_: Exception) {
                            ConnectionProtectionController.failed(this@ConnectionProtectionService, "无法更新保护通知，请检查通知设置")
                            stopProtection()
                        }
                    }
                }
            }
            return START_STICKY
        } catch (_: Exception) {
            ConnectionProtectionController.failed(this, "系统拒绝前台保护，请检查通知和后台限制后返回应用重试")
            return stopProtection()
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, ConnectionProtectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val connection = if (AccessibilityEventLogger.connected.value) "无障碍已连接" else "无障碍未连接，点击打开助手"
        return Notification.Builder(this, ConnectionProtectionController.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("连接保护运行中 · $connection")
            .setContentText("降低后台回收风险；不保证系统或清理器停止后恢复")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "停止保护", stop).build())
            .apply {
                if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
            .build()
    }

    private fun stopProtection(): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        ConnectionProtectionController.stopped(this)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 407
        private const val ACTION_STOP = "com.wananjiaoda.assistant.STOP_CONNECTION_PROTECTION"
    }
}


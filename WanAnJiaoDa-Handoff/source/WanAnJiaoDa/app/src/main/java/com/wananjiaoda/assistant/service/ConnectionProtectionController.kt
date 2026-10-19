package com.wananjiaoda.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConnectionProtectionController {
    internal const val CHANNEL_ID = "connection_protection"
    private const val PREFERENCES = "connection_protection"
    private val mutableState = MutableStateFlow(ConnectionProtectionState())
    val state = mutableState.asStateFlow()
    private var running = false
    private var failure: String? = null

    internal fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        // Commit before stopping: a later sticky null intent must observe the user's choice.
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val previousEnabled = preferences.getBoolean("enabled", false)
        if (!preferences.edit().putBoolean("enabled", enabled).commit()) {
            // A failed disk commit still changes SharedPreferences' in-memory value.
            // Restore it before publishing or processing a notification stop action.
            preferences.edit().putBoolean("enabled", previousEnabled).commit()
            failure = "无法保存保护开关，请重试"
            publish(context)
            return
        }
        failure = null
        if (enabled) resume(context) else {
            running = false
            publish(context)
            context.stopService(Intent(context, ConnectionProtectionService::class.java))
        }
    }

    fun resume(context: Context) {
        val app = context.applicationContext
        if (!isEnabled(app)) {
            running = false
            failure = null
            publish(app)
            app.stopService(Intent(app, ConnectionProtectionService::class.java))
            return
        }
        try {
            notificationProblem(app)?.let { problem ->
                failed(app, problem)
                app.stopService(Intent(app, ConnectionProtectionService::class.java))
                return
            }
            if (ConnectionProtectionPolicy.shouldStart(true, running)) {
                failure = null
                publish(app)
                ContextCompat.startForegroundService(app, Intent(app, ConnectionProtectionService::class.java))
            } else publish(app)
        } catch (_: Exception) {
            failed(app, "系统拒绝启动，请返回应用重试并检查通知与后台限制")
        }
    }

    internal fun notificationProblem(context: Context): String? {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "连接保护", NotificationManager.IMPORTANCE_LOW))
        return when {
            !manager.areNotificationsEnabled() -> "通知权限未开启，请在系统设置允许通知后重试"
            manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE ->
                "连接保护通知渠道已关闭，请在系统设置开启后重试"
            else -> null
        }
    }

    internal fun started(context: Context) { running = true; failure = null; publish(context) }
    internal fun stopped(context: Context) { running = false; publish(context) }
    internal fun failed(context: Context, message: String) { running = false; failure = message; publish(context) }
    internal fun publish(context: Context) {
        mutableState.value = ConnectionProtectionPolicy.state(isEnabled(context), running,
            AccessibilityEventLogger.connected.value, failure)
    }
}


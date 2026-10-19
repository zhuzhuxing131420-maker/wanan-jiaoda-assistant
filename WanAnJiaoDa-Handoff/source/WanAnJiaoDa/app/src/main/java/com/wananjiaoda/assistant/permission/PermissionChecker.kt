package com.wananjiaoda.assistant.permission

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.wananjiaoda.assistant.service.AccessibilityEventLogger
import com.wananjiaoda.assistant.service.WanAnJiaoDaAccessibilityService

class PermissionChecker(private val context: Context) {
    fun check(): PermissionState {
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val target = ComponentName(context, WanAnJiaoDaAccessibilityService::class.java)
        val accessibilityEnabled = AccessibilityPermission.readAuthorization(target.packageName, target.className) {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }
        val powerManager = context.getSystemService(PowerManager::class.java)
        return PermissionState(
            notificationsRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            notificationsGranted = notificationsGranted,
            accessibilityEnabled = accessibilityEnabled,
            ignoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName),
        ).withAccessibilityConnection(AccessibilityEventLogger.connected.value)
    }
}

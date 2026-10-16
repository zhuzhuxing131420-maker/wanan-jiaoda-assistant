package com.wananjiaoda.assistant.permission

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import com.wananjiaoda.assistant.service.WanAnJiaoDaAccessibilityService

class PermissionChecker(private val context: Context) {
    fun check(): PermissionState {
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
        val target = ComponentName(context, WanAnJiaoDaAccessibilityService::class.java)
        val accessibilityEnabled = accessibilityManager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo.serviceInfo
                ComponentName.createRelative(service.packageName, service.name) == target
            }
        val powerManager = context.getSystemService(PowerManager::class.java)
        return PermissionState(
            notificationsRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            notificationsGranted = notificationsGranted,
            accessibilityEnabled = accessibilityEnabled,
            ignoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName),
        )
    }
}

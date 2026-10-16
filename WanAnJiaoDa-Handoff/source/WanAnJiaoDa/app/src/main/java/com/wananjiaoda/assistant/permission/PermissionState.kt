package com.wananjiaoda.assistant.permission

data class PermissionState(
    val notificationsRequired: Boolean = false,
    val notificationsGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
)

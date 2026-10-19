package com.wananjiaoda.assistant.permission

data class PermissionState(
    val notificationsRequired: Boolean = false,
    val notificationsGranted: Boolean = false,
    // null means authorization could not be read; disconnected is not unauthorized.
    val accessibilityEnabled: Boolean? = null,
    val accessibilityConnected: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
) {
    val accessibilityStatusText: String
        get() = accessibilityStatusText(accessibilityEnabled, accessibilityConnected)

    fun withAccessibilityConnection(connected: Boolean): PermissionState = copy(
        accessibilityEnabled = if (connected) true else accessibilityEnabled,
        accessibilityConnected = connected,
    )
}

fun accessibilityStatusText(authorized: Boolean?, connected: Boolean): String = when {
    connected -> "已连接"
    authorized == true -> "已授权未连接"
    authorized == false -> "未授权"
    else -> "状态未知"
}

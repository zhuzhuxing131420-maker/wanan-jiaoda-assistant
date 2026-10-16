package com.wananjiaoda.assistant.ui.navigation

enum class AppDestination(val route: String, val label: String, val showInBottomBar: Boolean = true) {
    HOME("home", "首页"),
    SETTINGS("settings", "设置"),
    HISTORY("history", "历史记录"),
    PERMISSIONS("permissions", "权限检查", false),
    ACCESSIBILITY_TEST("accessibility_test", "无障碍测试", false),
    WECHAT_OBSERVER("wechat_observer", "微信观察测试", false),
}

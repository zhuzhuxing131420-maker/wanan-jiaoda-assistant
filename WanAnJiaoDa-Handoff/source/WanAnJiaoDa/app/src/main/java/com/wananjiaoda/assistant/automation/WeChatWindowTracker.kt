package com.wananjiaoda.assistant.automation

class WeChatWindowTracker {
    var activityClass = ""
        private set
    val isMiniProgram: Boolean get() = activityClass.startsWith("com.tencent.mm.plugin.appbrand.ui.AppBrandUI")
    fun update(packageName: String, className: String) {
        if (packageName != "com.tencent.mm") { activityClass = ""; return }
        if (className == "com.tencent.mm.ui.LauncherUI" || className.startsWith("com.tencent.mm.plugin.appbrand.ui."))
            activityClass = className
    }
}

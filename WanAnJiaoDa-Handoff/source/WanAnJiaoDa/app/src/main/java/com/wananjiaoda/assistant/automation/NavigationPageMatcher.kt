package com.wananjiaoda.assistant.automation

enum class NavigationPage { HOME, DISCOVER, MINI_OVERVIEW, RECENT_LIST, WELCOME, ENTRY, CHECK_IN, CHOOSER, CHAT, SEARCH, UNKNOWN }
data class NavigationPageMatch(val page: NavigationPage, val decision: NavigationDecision? = null, val blocked: Boolean = false)

object NavigationPageMatcher {
    fun recognize(frame: RecognizedFrame, targetSelected: Boolean, welcomeCompleted: Boolean = false): NavigationPageMatch {
        val lines = frame.lines.map { it.copy(text = ScreenTextResolver.normalize(it.text)) }
        fun exact(text: String) = ScreenTextResolver.unique(lines, text)
        fun upper(text: String) = ScreenTextResolver.unique(lines, text) { it.bottom < frame.height * 0.3 }
        fun bottom(text: String) = ScreenTextResolver.unique(lines, text) { it.top > frame.height * 0.8 }
        fun tap(page: NavigationPage, action: NavigationAction, text: String): NavigationPageMatch =
            NavigationPageMatch(page, exact(text)?.let { NavigationDecision(action, it.bounds) })
        if (frame.packageName == WeChatInstanceChooser.PACKAGE) return NavigationPageMatch(NavigationPage.CHOOSER,
            WeChatInstanceChooser.ordinaryBounds(frame.packageName, lines)?.let { NavigationDecision(NavigationAction.PRIMARY_WECHAT, it) })
        if (frame.packageName != "com.tencent.mm") return NavigationPageMatch(NavigationPage.UNKNOWN, blocked = true)

        val discard = lines.any { it.text.contains("放弃编辑") || it.text.contains("放弃修改") || it.text.contains("不保存") }
        val permission = lines.any { it.text.contains("授权") || it.text.contains("权限") || it.text == "允许" }
        val modal = lines.any { it.text in setOf("确定", "取消", "允许", "拒绝", "放弃") }
        if (discard || (permission && modal)) return NavigationPageMatch(NavigationPage.UNKNOWN, blocked = true)

        if (frame.recognition.state in setOf(CheckInPageState.READY, CheckInPageState.ALREADY_DONE, CheckInPageState.OUTSIDE_TIME))
            return NavigationPageMatch(NavigationPage.CHECK_IN)

        // Concrete list structure wins over an AppBrand activity classification.
        if (exact("最近使用") != null && (upper("小程序") != null || exact("我的常用") != null || exact("搜索小程序") != null))
            return tap(NavigationPage.MINI_OVERVIEW, NavigationAction.RECENT, "最近使用")
        if (exact("最近使用") != null) return tap(NavigationPage.RECENT_LIST, NavigationAction.TARGET, "晚安交大地学人")

        if (exact("进入打卡") != null && exact("欢迎") != null) return if (targetSelected)
            tap(NavigationPage.WELCOME, NavigationAction.WELCOME, "确定") else NavigationPageMatch(NavigationPage.WELCOME)
        if (exact("进入打卡") != null && (exact("学号绑定") != null || welcomeCompleted)) return if (targetSelected && !modal)
            tap(NavigationPage.ENTRY, NavigationAction.ENTER, "进入打卡") else NavigationPageMatch(NavigationPage.ENTRY)

        if (exact("小程序") != null && (upper("发现") != null || exact("朋友圈") != null))
            return tap(NavigationPage.DISCOVER, NavigationAction.MINI_PROGRAMS, "小程序")
        if (bottom("微信") != null && bottom("发现") != null && bottom("我") != null)
            return NavigationPageMatch(NavigationPage.HOME, NavigationDecision(NavigationAction.DISCOVER, bottom("发现")!!.bounds))
        if (bottom("按住说话") != null || bottom("发送") != null) return NavigationPageMatch(NavigationPage.CHAT)
        if (upper("取消") != null && (upper("搜索") != null || upper("搜索指定内容") != null))
            return NavigationPageMatch(NavigationPage.SEARCH)
        return NavigationPageMatch(NavigationPage.UNKNOWN)
    }
}

package com.wananjiaoda.assistant.automation

import kotlin.math.abs

enum class NavigationAction { DISCOVER, MINI_PROGRAMS, RECENT, TARGET, WELCOME, ENTER, BACK, HANDOFF, PAGE_OUTSIDE_TIME, PAGE_REACHED, PRIMARY_WECHAT }
data class NavigationDecision(val action: NavigationAction, val bounds: ScreenBounds? = null)

class WeChatNavigationAttempt(private val navigationOnly: Boolean = false) {
    var failed = false
        private set
    var reason = "等待微信页面"
        private set
    var page = NavigationPage.UNKNOWN
        private set
    var needsResample = false
        private set
    private var candidate: Pair<NavigationDecision, RecognizedFrame>? = null
    private var pending: NavigationDecision? = null
    private var lastAction: NavigationAction? = null
    private var completedAt = Long.MIN_VALUE
    private var firstSeenAt: Long? = null
    private var targetSelected = false
    private var handedOff = false
    private var entryRecovered = false
    private var backCount = 0
    private var transientMisses = 0
    private val attempts = mutableMapOf<NavigationAction, Int>()
    private var actions = 0

    fun onFrame(frame: RecognizedFrame, nowMillis: Long, miniProgram: Boolean): NavigationDecision? {
        if (failed || handedOff || pending != null) return null
        if (frame.packageName !in setOf("com.tencent.mm", WeChatInstanceChooser.PACKAGE) ||
            nowMillis - frame.requestedAtMillis !in 0..10000 || frame.requestedAtMillis <= completedAt) {
            candidate = null
            return null
        }
        if (firstSeenAt == null) firstSeenAt = frame.requestedAtMillis
        if (completedAt != Long.MIN_VALUE && frame.requestedAtMillis - completedAt < 450) return null
        candidate?.second?.let { old -> if (!sameWindow(old, frame)) candidate = null }
        val match = NavigationPageMatcher.recognize(frame, targetSelected, lastAction == NavigationAction.WELCOME)
        page = match.page
        needsResample = match.decision == null
        if (match.blocked) {
            candidate = null
            reason = "检测到授权或编辑确认，请手动处理"
            return null
        }
        if (page in setOf(NavigationPage.HOME, NavigationPage.DISCOVER, NavigationPage.MINI_OVERVIEW, NavigationPage.RECENT_LIST))
            entryRecovered = true
        if (page == NavigationPage.CHECK_IN && targetSelected && miniProgram) {
            handedOff = true
            val outside = frame.recognition.state == CheckInPageState.OUTSIDE_TIME
            reason = if (outside) "已自动到达签到页，当前不在打卡时间，未提交"
                else if (navigationOnly) "已自动到达签到页，本轮仅核对打开路径，未提交" else "已进入目标签到页"
            return NavigationDecision(if (outside) NavigationAction.PAGE_OUTSIDE_TIME
                else if (navigationOnly) NavigationAction.PAGE_REACHED else NavigationAction.HANDOFF)
        }
        var decision = match.decision
        if (decision == null) {
            val previous = candidate
            if (page == NavigationPage.UNKNOWN && previous != null && previous.first.action != NavigationAction.BACK &&
                transientMisses == 0 && sameWindow(previous.second, frame) &&
                frame.requestedAtMillis - previous.second.requestedAtMillis <= 2500) {
                transientMisses++
                reason = "目标暂时漏识别，等待新画面再次确认"
                return null
            }
            val unconfirmedMini = miniProgram && !targetSelected && page in
                setOf(NavigationPage.CHECK_IN, NavigationPage.ENTRY, NavigationPage.WELCOME)
            val bodyEvidence = frame.lines.count { it.bounds.top > frame.height * 0.05 &&
                ScreenTextResolver.normalize(it.text).length >= 2 && it.text !in setOf("微信", "通讯录", "发现") }
            val recoverableUnknown = page == NavigationPage.UNKNOWN && bodyEvidence >= 2 &&
                ScreenTextResolver.unique(frame.lines, "晚安交大地学人") == null
            val startupRecovery = !entryRecovered && !targetSelected &&
                (recoverableUnknown || page in setOf(NavigationPage.CHAT, NavigationPage.SEARCH))
            if (unconfirmedMini || startupRecovery) {
                if (backCount >= 3) { failed = true; reason = "入口恢复已返回三次，仍无法确认页面"; candidate = null; return null }
                decision = NavigationDecision(NavigationAction.BACK)
            } else {
                candidate = null
                reason = if (page == NavigationPage.RECENT_LIST) "最近使用列表中尚未读到完整目标名称，正在补取画面"
                    else "等待页面稳定，正在重新核对目标"
                return null
            }
        }
        if (decision.action == lastAction && decision.action != NavigationAction.BACK) {
            val repeatable = decision.action in setOf(NavigationAction.DISCOVER, NavigationAction.MINI_PROGRAMS,
                NavigationAction.RECENT, NavigationAction.TARGET, NavigationAction.WELCOME, NavigationAction.ENTER)
            if (!repeatable || frame.requestedAtMillis - completedAt < 4000 || (attempts[decision.action] ?: 0) >= 2) {
                needsResample = true
                candidate = null
                reason = "动作已完成，等待新页面，不重复提交"
                return null
            }
        }
        val box = decision.bounds
        if (box != null && (box.left < 0 || box.top < 0 || box.right <= box.left || box.bottom <= box.top ||
                box.right > frame.width || box.bottom > frame.height)) { candidate = null; return null }
        val previous = candidate
        candidate = decision to frame
        transientMisses = 0
        if (previous == null || previous.first.action != decision.action || !sameWindow(previous.second, frame) ||
            frame.requestedAtMillis - previous.second.requestedAtMillis !in 500..10000) {
            reason = "已发现候选位置，等待第二帧确认"
            return null
        }
        val oldBox = previous.first.bounds
        if (box != null && (oldBox == null || abs((box.left + box.right - oldBox.left - oldBox.right) / 2) > 40 ||
                abs(box.centerY - oldBox.centerY) > 40)) return null
        if (decision.action == NavigationAction.BACK) {
            val visibleBody = frame.lines.any { it.bounds.top > frame.height * 0.05 &&
                ScreenTextResolver.normalize(it.text).length >= 2 && it.text !in setOf("微信", "通讯录", "发现") }
            if (frame.requestedAtMillis - (firstSeenAt ?: frame.requestedAtMillis) <
                if (visibleBody || page != NavigationPage.UNKNOWN) 1000 else 5000) return null
            backCount++
        }
        if (++actions > 12) { failed = true; reason = "导航动作超过上限，已停止"; return null }
        attempts[decision.action] = (attempts[decision.action] ?: 0) + 1
        pending = decision
        candidate = null
        needsResample = false
        reason = if (decision.action == NavigationAction.BACK) "正在从聊天或其他页面恢复入口（$backCount/3）"
            else "目标位置已确认，正在切换页面"
        return decision
    }

    private fun sameWindow(a: RecognizedFrame, b: RecognizedFrame) = a.sessionId == b.sessionId &&
        a.windowId == b.windowId && a.packageName == b.packageName && a.width == b.width && a.height == b.height

    fun actionCompleted(completed: Boolean, nowMillis: Long) {
        val action = pending ?: return
        pending = null
        if (!completed) { failed = true; reason = "导航动作未完成，已停止"; return }
        lastAction = action.action
        completedAt = nowMillis
        if (action.action == NavigationAction.TARGET) targetSelected = true
        reason = "等待导航后的新页面"
    }
}

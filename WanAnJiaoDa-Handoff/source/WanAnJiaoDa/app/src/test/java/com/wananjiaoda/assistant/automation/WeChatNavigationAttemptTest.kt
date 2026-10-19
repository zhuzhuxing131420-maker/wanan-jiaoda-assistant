package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class WeChatNavigationAttemptTest {
    private fun frame(at: Long, vararg labels: String, state: CheckInPageState = CheckInPageState.UNKNOWN,
        x: Int = 100) = RecognizedFrame(1, at, 1272, 2800, CheckInPageRecognition(state),
            labels.mapIndexed { index, text -> ScreenTextLine(text, ScreenBounds(x, 200 + index * 150, x + 400, 260 + index * 150)) })
    private fun stable(attempt: WeChatNavigationAttempt, vararg labels: String): NavigationDecision? {
        assertNull(attempt.onFrame(frame(1000, *labels), 1000, false))
        return attempt.onFrame(frame(3000, *labels), 3000, false)
    }
    private fun select(attempt: WeChatNavigationAttempt) {
        assertEquals(NavigationAction.TARGET, stable(attempt, "最近使用", "晚安交大地学人")?.action)
        attempt.actionCompleted(true, 3200)
    }
    @Test fun exactNameSelectsTarget() {
        assertEquals(NavigationAction.TARGET, stable(WeChatNavigationAttempt(), "最近使用", "晚安交大地学人")?.action)
    }
    @Test fun truncatedNameOpensRecentListInstead() {
        assertEquals(NavigationAction.RECENT, stable(WeChatNavigationAttempt(), "小程序", "最近使用", "晚安交大…")?.action)
    }
    @Test fun cannotTrustAnAlreadyOpenMiniProgram() {
        val attempt = WeChatNavigationAttempt()
        assertNull(attempt.onFrame(frame(1000, state = CheckInPageState.ALREADY_DONE), 1000, true))
        assertEquals(NavigationAction.BACK, attempt.onFrame(frame(3000, state = CheckInPageState.ALREADY_DONE), 3000, true)?.action)
    }
    @Test fun handoffRequiresSelectedTargetAndNewMiniProgramFrame() {
        val attempt = WeChatNavigationAttempt()
        select(attempt)
        assertNull(attempt.onFrame(frame(3000, state = CheckInPageState.ALREADY_DONE), 5000, true))
        assertEquals(NavigationAction.HANDOFF, attempt.onFrame(frame(6000, state = CheckInPageState.ALREADY_DONE), 6000, true)?.action)
    }
    @Test fun otherWindowCannotHandoff() {
        val attempt = WeChatNavigationAttempt()
        select(attempt)
        assertNull(attempt.onFrame(frame(6000, state = CheckInPageState.READY), 6000, false))
    }
    @Test fun gestureIsNotRequestedTwice() {
        val attempt = WeChatNavigationAttempt()
        stable(attempt, "最近使用", "晚安交大地学人")
        assertNull(attempt.onFrame(frame(5000, "最近使用", "晚安交大地学人"), 5000, false))
        attempt.actionCompleted(true, 5200)
        assertNull(attempt.onFrame(frame(8000, "最近使用", "晚安交大地学人"), 8000, false))
    }
    @Test fun staleOrMovedFrameResetsCandidate() {
        val attempt = WeChatNavigationAttempt()
        attempt.onFrame(frame(1000, "最近使用", "晚安交大地学人"), 12000, false)
        assertNull(attempt.onFrame(frame(13000, "最近使用", "晚安交大地学人"), 13000, false))
        assertNull(attempt.onFrame(frame(15000, "最近使用", "晚安交大地学人", x = 300), 15000, false))
    }
    @Test fun invalidBoundsCannotAct() {
        assertNull(stable(WeChatNavigationAttempt(), "晚安交大…"))
        val attempt = WeChatNavigationAttempt()
        attempt.onFrame(frame(1000, "最近使用", "晚安交大地学人", x = -1), 1000, false)
        assertNull(attempt.onFrame(frame(3000, "最近使用", "晚安交大地学人", x = -1), 3000, false))
    }
    @Test fun cancelledGestureStopsNavigation() {
        val attempt = WeChatNavigationAttempt()
        stable(attempt, "最近使用", "晚安交大地学人")
        attempt.actionCompleted(false, 3200)
        assertTrue(attempt.failed)
        assertNull(attempt.onFrame(frame(6000, "最近使用", "晚安交大地学人"), 6000, false))
    }
    @Test fun welcomeOnlyAfterTargetSelectionWithKnownEntryPage() {
        val attempt = WeChatNavigationAttempt()
        select(attempt)
        val labels = arrayOf("欢迎", "确定", "进入打卡", "学号绑定")
        assertNull(attempt.onFrame(frame(6000, *labels), 6000, true))
        assertEquals(NavigationAction.WELCOME, attempt.onFrame(frame(8000, *labels), 8000, true)?.action)
    }
    @Test fun unknownModalIsNotDismissed() {
        val attempt = WeChatNavigationAttempt()
        select(attempt)
        assertNull(attempt.onFrame(frame(6000, "确定", "授权"), 6000, true))
        assertNull(attempt.onFrame(frame(8000, "确定", "授权"), 8000, true))
    }
    @Test fun entryButtonRequiresConfirmedTarget() {
        val attempt = WeChatNavigationAttempt()
        select(attempt)
        assertNull(attempt.onFrame(frame(6000, "学号绑定", "进入打卡"), 6000, true))
        assertEquals(NavigationAction.ENTER, attempt.onFrame(frame(8000, "学号绑定", "进入打卡"), 8000, true)?.action)
    }
    @Test fun discoverPageOpensMiniPrograms() {
        assertEquals(NavigationAction.MINI_PROGRAMS, stable(WeChatNavigationAttempt(), "朋友圈", "小程序")?.action)
    }
    @Test fun duplicateExactNamesDoNotSelectAnAmbiguousTarget() {
        assertNull(stable(WeChatNavigationAttempt(), "最近使用", "晚安交大地学人", "晚安交大地学人"))
    }
    @Test fun unchangedPageDoesNotTriggerAnotherTapAfterCompletion() {
        val attempt = WeChatNavigationAttempt()
        assertEquals(NavigationAction.RECENT, stable(attempt, "小程序", "最近使用", "晚安交大…")?.action)
        attempt.actionCompleted(true, 3200)
        assertNull(attempt.onFrame(frame(6000, "小程序", "最近使用", "晚安交大…"), 6000, false))
        assertNull(attempt.onFrame(frame(8000, "小程序", "最近使用", "晚安交大…"), 8000, false))
    }
    @Test fun observedRecentTitleMisreadStillRequiresExactTargetName() {
        assertEquals(NavigationAction.TARGET, stable(WeChatNavigationAttempt(), "最近便用", "晚安交大地学人")?.action)
        assertNull(stable(WeChatNavigationAttempt(), "最近便用", "晚安交大地学…"))
    }
    @Test fun observedRecentHeaderExtraCharacterOpensRecentList() {
        assertEquals(NavigationAction.RECENT,
            stable(WeChatNavigationAttempt(), "小程序", "最近使便用")?.action)
    }
    @Test fun observedRecentHeaderExtraCharacterAllowsOnlyExactTargetName() {
        assertEquals(NavigationAction.TARGET,
            stable(WeChatNavigationAttempt(), "最近使便用", "晚安交大地学人")?.action)
        for (label in listOf("晚安交大地学…", "晚安交大地学入")) {
            assertNull(stable(WeChatNavigationAttempt(), "最近使便用", label))
        }
    }
    @Test fun recentHeaderAliasDoesNotBroadenChatOrPartialLabels() {
        for (header in listOf("聊天中最近使便用", "最近使便", "最近使便用的聊天")) {
            val action = stable(WeChatNavigationAttempt(), header, "晚安交大地学人")?.action
            assertNotEquals(NavigationAction.RECENT, action)
            assertNotEquals(NavigationAction.TARGET, action)
        }
        assertNotEquals(NavigationAction.TARGET,
            stable(WeChatNavigationAttempt(), "最近使便用", "聊天中的晚安交大地学人")?.action)
    }
    @Test fun outsideTimeReachesPageWithoutStartingASubmission() {
        val attempt = WeChatNavigationAttempt()
        select(attempt)
        assertEquals(NavigationAction.PAGE_OUTSIDE_TIME,
            attempt.onFrame(frame(6000, state = CheckInPageState.OUTSIDE_TIME), 6000, true)?.action)
    }
    @Test fun homeUsesVisibleBottomTabsWhenContactsTextIsNotRecognized() {
        val attempt = WeChatNavigationAttempt()
        val labels = listOf(ScreenTextLine("微信", ScreenBounds(580, 190, 680, 240)),
            ScreenTextLine("微信", ScreenBounds(120, 2680, 190, 2720)),
            ScreenTextLine("发现", ScreenBounds(760, 2680, 830, 2720)),
            ScreenTextLine("我", ScreenBounds(1090, 2680, 1130, 2720)))
        fun home(at: Long) = RecognizedFrame(1, at, 1272, 2800, CheckInPageRecognition(CheckInPageState.UNKNOWN), labels)
        assertNull(attempt.onFrame(home(1000), 1000, false))
        val result = attempt.onFrame(home(3000), 3000, false)
        assertEquals(NavigationAction.DISCOVER, result?.action)
        assertEquals(labels[2].bounds, result?.bounds)
    }
    @Test fun knownWelcomeWorksWhenDimmedBindingHeadingIsUnreadable() {
        val attempt = WeChatNavigationAttempt()
        select(attempt)
        assertNull(attempt.onFrame(frame(6000, "欢迎", "确定", "进入打卡"), 6000, true))
        assertEquals(NavigationAction.WELCOME,
            attempt.onFrame(frame(8000, "欢迎", "确定", "进入打卡"), 8000, true)?.action)
    }
    @Test fun unknownDialogOverEntryPageBlocksEntryTap() {
        val attempt = WeChatNavigationAttempt()
        select(attempt)
        assertNull(attempt.onFrame(frame(6000, "学号绑定", "进入打卡", "授权", "确定"), 6000, true))
        assertNull(attempt.onFrame(frame(8000, "学号绑定", "进入打卡", "授权", "确定"), 8000, true))
    }
    @Test fun discoverTitleAndMiniProgramEntryWorkWithoutMomentsLabel() {
        val attempt = WeChatNavigationAttempt()
        val lines = listOf(ScreenTextLine("发现", ScreenBounds(580, 190, 680, 240)),
            ScreenTextLine("小程序", ScreenBounds(170, 2020, 320, 2080)),
            ScreenTextLine("微信", ScreenBounds(120, 2680, 190, 2720)),
            ScreenTextLine("发现", ScreenBounds(760, 2680, 830, 2720)),
            ScreenTextLine("我", ScreenBounds(1090, 2680, 1130, 2720)))
        fun page(at: Long) = RecognizedFrame(1, at, 1272, 2800, CheckInPageRecognition(CheckInPageState.UNKNOWN), lines)
        assertNull(attempt.onFrame(page(1000), 1000, false))
        assertEquals(NavigationAction.MINI_PROGRAMS, attempt.onFrame(page(1800), 1800, false)?.action)
    }
    @Test fun stableFramesCanAdvanceSoonAfterCompletedGesture() {
        val attempt = WeChatNavigationAttempt()
        select(attempt)
        assertNull(attempt.onFrame(frame(4000, "欢迎", "确定", "进入打卡"), 4000, true))
        assertEquals(NavigationAction.WELCOME,
            attempt.onFrame(frame(4800, "欢迎", "确定", "进入打卡"), 4800, true)?.action)
    }
    @Test fun navigationOnlyCannotHandoffAnOpenCheckInButton() {
        val attempt = WeChatNavigationAttempt(navigationOnly = true)
        select(attempt)
        assertEquals(NavigationAction.PAGE_REACHED,
            attempt.onFrame(frame(5000, state = CheckInPageState.READY), 5000, true)?.action)
        assertNull(attempt.onFrame(frame(6000, state = CheckInPageState.READY), 6000, true))
    }
}

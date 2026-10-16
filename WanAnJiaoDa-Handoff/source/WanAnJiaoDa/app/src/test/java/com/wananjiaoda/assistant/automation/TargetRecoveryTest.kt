package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class TargetRecoveryTest {
    private fun line(text: String, x: Int, y: Int, width: Int = 250) =
        ScreenTextLine(text, ScreenBounds(x, y, x + width, y + 50))
    private fun frame(at: Long, labels: List<ScreenTextLine>, window: Int = 1) = RecognizedFrame(1, at, 1272, 2800,
        CheckInPageRecognition(CheckInPageState.UNKNOWN), labels, window)
    private val recent = listOf(line("最近使用", 450, 200), line("晚安交大地学人", 280, 400, 450))

    @Test fun joinsExactTargetFragmentsOnSameRow() {
        val result = ScreenTextResolver.unique(listOf(line("晚安交大", 280, 400), line("地学人", 540, 400, 190)), "晚安交大地学人")
        assertEquals("晚安交大地学人", result?.text)
        assertEquals(ScreenBounds(280, 400, 730, 450), result?.bounds)
    }
    @Test fun deduplicatesOverlappingOcrResults() {
        val result = ScreenTextResolver.unique(listOf(line("最近使用", 450, 200), line("最近使用", 454, 202)), "最近使用")
        assertNotNull(result)
    }
    @Test fun rejectsSpatiallySeparateExactTargets() {
        assertNull(ScreenTextResolver.unique(recent + line("晚安交大地学人", 280, 900, 450), "晚安交大地学人"))
    }
    @Test fun neverCompletesTruncatedTargetByGuessing() {
        assertNull(ScreenTextResolver.unique(listOf(line("晚安交大…", 280, 400)), "晚安交大地学人"))
    }
    @Test fun miniActivityClassDoesNotOverrideKnownRecentList() {
        val attempt = WeChatNavigationAttempt(true)
        assertNull(attempt.onFrame(frame(1000, recent), 1000, true))
        assertEquals(NavigationAction.TARGET, attempt.onFrame(frame(1800, recent), 1800, true)?.action)
    }
    @Test fun oneTransientMissRequiresANewFrameButKeepsCandidate() {
        val attempt = WeChatNavigationAttempt(true)
        assertNull(attempt.onFrame(frame(1000, recent), 1000, false))
        assertNull(attempt.onFrame(frame(1800, emptyList()), 1800, false))
        assertEquals(NavigationAction.TARGET, attempt.onFrame(frame(2500, recent), 2500, false)?.action)
    }
    @Test fun changedWindowCannotReuseOldCandidate() {
        val attempt = WeChatNavigationAttempt(true)
        attempt.onFrame(frame(1000, recent), 1000, false)
        attempt.onFrame(frame(1800, emptyList(), 2), 1800, false)
        assertNull(attempt.onFrame(frame(2500, recent, 2), 2500, false))
    }
    @Test fun chatStartupRecoversWithFreshBackConfirmation() {
        val attempt = WeChatNavigationAttempt(true)
        val chat = listOf(line("聊天", 450, 200), line("按住说话", 300, 2580))
        assertNull(attempt.onFrame(frame(1000, chat), 1000, false))
        assertEquals(NavigationAction.BACK, attempt.onFrame(frame(3000, chat), 3000, false)?.action)
    }
    @Test fun startupBackRecoveryIsBounded() {
        val attempt = WeChatNavigationAttempt(true)
        val chat = listOf(line("聊天", 450, 200), line("按住说话", 300, 2580))
        repeat(3) { index ->
            val at = 1000L + index * 5000
            assertNull(attempt.onFrame(frame(at, chat), at, false))
            assertEquals(NavigationAction.BACK, attempt.onFrame(frame(at + 2000, chat), at + 2000, false)?.action)
            attempt.actionCompleted(true, at + 2200)
        }
        assertNull(attempt.onFrame(frame(17000, chat), 17000, false))
        assertTrue(attempt.failed)
    }
    @Test fun discardDraftPromptIsNeverConfirmedOrBackedOutAutomatically() {
        val attempt = WeChatNavigationAttempt(true)
        val prompt = listOf(line("放弃编辑", 300, 1200), line("确定", 700, 1400))
        assertNull(attempt.onFrame(frame(1000, prompt), 1000, false))
        assertNull(attempt.onFrame(frame(3000, prompt), 3000, false))
    }
    @Test fun wrappedFullNameStillNeedsAllCharacters() {
        assertNotNull(ScreenTextResolver.unique(listOf(line("晚安交大", 280, 400), line("地学人", 280, 460)), "晚安交大地学人"))
    }
    @Test fun completedNavigationMayRetryOnceFromFreshFramesAfterDelay() {
        val attempt = WeChatNavigationAttempt(true)
        attempt.onFrame(frame(1000, recent), 1000, false)
        assertEquals(NavigationAction.TARGET, attempt.onFrame(frame(1800, recent), 1800, false)?.action)
        attempt.actionCompleted(true, 1900)
        assertNull(attempt.onFrame(frame(3000, recent), 3000, false))
        assertNull(attempt.onFrame(frame(6000, recent), 6000, false))
        assertEquals(NavigationAction.TARGET, attempt.onFrame(frame(6800, recent), 6800, false)?.action)
        attempt.actionCompleted(true, 6900)
        assertNull(attempt.onFrame(frame(12000, recent), 12000, false))
        assertNull(attempt.onFrame(frame(13000, recent), 13000, false))
    }
    @Test fun unchangedPageAfterActionRequestsDetailCaptureInsteadOfSilentlyRepeating() {
        val attempt = WeChatNavigationAttempt(true)
        attempt.onFrame(frame(1000, recent), 1000, false)
        attempt.onFrame(frame(1800, recent), 1800, false)
        attempt.actionCompleted(true, 1900)
        assertNull(attempt.onFrame(frame(3000, recent), 3000, false))
        assertTrue(attempt.needsResample)
    }
    @Test fun incompleteMarkerMustNotCauseReturnFromKnownRoute() {
        val attempt = WeChatNavigationAttempt(true)
        attempt.onFrame(frame(1000, recent), 1000, false)
        attempt.onFrame(frame(1800, recent), 1800, false)
        attempt.actionCompleted(true, 1900)
        val loading = listOf(line("正在加载", 400, 900), line("请稍候", 400, 1000))
        assertNull(attempt.onFrame(frame(3000, loading), 3000, true))
        assertNull(attempt.onFrame(frame(3800, loading), 3800, true))
    }
    @Test fun confirmedWelcomeAllowsEntryEvenIfBindingHeadingIsMissed() {
        val attempt = WeChatNavigationAttempt(true)
        attempt.onFrame(frame(1000, recent), 1000, false)
        attempt.onFrame(frame(1800, recent), 1800, false)
        attempt.actionCompleted(true, 1900)
        val welcome = listOf(line("欢迎", 450, 1200), line("确定", 450, 1600), line("进入打卡", 450, 1800))
        attempt.onFrame(frame(3000, welcome), 3000, true)
        assertEquals(NavigationAction.WELCOME, attempt.onFrame(frame(3800, welcome), 3800, true)?.action)
        attempt.actionCompleted(true, 3900)
        val entry = listOf(line("进入打卡", 450, 1800))
        assertNull(attempt.onFrame(frame(5000, entry), 5000, true))
        assertEquals(NavigationAction.ENTER, attempt.onFrame(frame(5800, entry), 5800, true)?.action)
    }
}

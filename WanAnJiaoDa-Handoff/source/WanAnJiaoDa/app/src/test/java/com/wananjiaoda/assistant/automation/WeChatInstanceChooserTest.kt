package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class WeChatInstanceChooserTest {
    private val normal = ScreenBounds(280, 2400, 440, 2460)
    private val labels = listOf(ScreenTextLine("选择要使用的应用", ScreenBounds(400, 2040, 900, 2140)),
        ScreenTextLine("微信", normal), ScreenTextLine("Ⅱ·微信", ScreenBounds(850, 2400, 1030, 2460)))
    @Test fun selectsOrdinaryWeChatOnlyInKnownChooser() {
        assertEquals(normal, WeChatInstanceChooser.ordinaryBounds(WeChatInstanceChooser.PACKAGE, labels))
    }
    @Test fun ignoresSameTextsInAnotherApplication() {
        assertNull(WeChatInstanceChooser.ordinaryBounds("com.tencent.mm", labels))
    }
    @Test fun missingHeadingIsNotAChooser() {
        assertNull(WeChatInstanceChooser.ordinaryBounds(WeChatInstanceChooser.PACKAGE, labels.drop(1)))
    }
    @Test fun ambiguousOrdinaryLabelsAreNotSelected() {
        assertNull(WeChatInstanceChooser.ordinaryBounds(WeChatInstanceChooser.PACKAGE,
            labels + ScreenTextLine("微信", ScreenBounds(850, 2400, 1030, 2460))))
    }
    @Test fun cloneMustBeClearlyDistinguished() {
        assertNull(WeChatInstanceChooser.ordinaryBounds(WeChatInstanceChooser.PACKAGE, labels.take(2)))
    }
    @Test fun navigationUsesTwoStableChooserFramesAndRequestsOnlyOnce() {
        val attempt = WeChatNavigationAttempt(navigationOnly = true)
        fun frame(at: Long) = RecognizedFrame(1, at, 1272, 2800,
            CheckInPageRecognition(CheckInPageState.UNKNOWN), labels, 1, WeChatInstanceChooser.PACKAGE)
        assertNull(attempt.onFrame(frame(1000), 1000, false))
        val decision = attempt.onFrame(frame(1800), 1800, false)
        assertEquals(NavigationAction.PRIMARY_WECHAT, decision?.action)
        assertEquals(normal, decision?.bounds)
        assertNull(attempt.onFrame(frame(2500), 2500, false))
        attempt.actionCompleted(true, 2600)
        assertNull(attempt.onFrame(frame(3500), 3500, false))
    }
    @Test fun observedAdjacentOptionBoundsAreDistinctChoices() {
        val adjacent = labels.map { line -> when (line.text) {
            "微信" -> line.copy(bounds = ScreenBounds(98, 2450, 630, 2502))
            "Ⅱ·微信" -> line.copy(bounds = ScreenBounds(630, 2450, 1162, 2502))
            else -> line
        } }
        assertEquals(ScreenBounds(98, 2450, 630, 2502),
            WeChatInstanceChooser.ordinaryBounds(WeChatInstanceChooser.PACKAGE, adjacent))
    }
}

package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class WeChatWindowTrackerTest {
    @Test fun genericWidgetDoesNotEraseKnownMiniProgramActivity() {
        val tracker = WeChatWindowTracker()
        tracker.update("com.tencent.mm", "com.tencent.mm.plugin.appbrand.ui.AppBrandUI00")
        assertTrue(tracker.isMiniProgram)
        tracker.update("com.tencent.mm", "android.widget.FrameLayout")
        tracker.update("com.tencent.mm", "android.widget.Toast")
        assertTrue(tracker.isMiniProgram)
    }
    @Test fun miniProgramListIsNotABusinessMiniProgram() {
        val tracker = WeChatWindowTracker()
        tracker.update("com.tencent.mm", "com.tencent.mm.plugin.appbrand.ui.AppBrandUI00")
        tracker.update("com.tencent.mm", "com.tencent.mm.plugin.appbrand.ui.AppBrandPluginUI")
        assertFalse(tracker.isMiniProgram)
    }
    @Test fun foreignPackageClearsMiniProgramContext() {
        val tracker = WeChatWindowTracker()
        tracker.update("com.tencent.mm", "com.tencent.mm.plugin.appbrand.ui.AppBrandUI00")
        tracker.update("com.android.settings", "com.android.settings.Settings")
        assertFalse(tracker.isMiniProgram)
    }
    @Test fun launcherReplacesMiniProgramContext() {
        val tracker = WeChatWindowTracker()
        tracker.update("com.tencent.mm", "com.tencent.mm.plugin.appbrand.ui.AppBrandUI00")
        tracker.update("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
        assertFalse(tracker.isMiniProgram)
        assertEquals("com.tencent.mm.ui.LauncherUI", tracker.activityClass)
    }
}

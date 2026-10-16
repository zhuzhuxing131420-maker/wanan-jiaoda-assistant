package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class KeyboardWindowPolicyTest {
    @Test fun keyboardDoesNotHideFocusedWechatApplication() {
        val windows = listOf(AppWindowCandidate("com.tencent.mm", true, true, 1),
            AppWindowCandidate("keyboard.pkg", false, true, 2))
        assertEquals(0, KeyboardWindowPolicy.focusedWechatIndex("keyboard.pkg", "keyboard.pkg", windows))
    }
    @Test fun otherForegroundAppCannotExposeBackgroundWechat() {
        val windows = listOf(AppWindowCandidate("com.tencent.mm", true, false, 1),
            AppWindowCandidate("other.pkg", true, true, 2))
        assertNull(KeyboardWindowPolicy.focusedWechatIndex("keyboard.pkg", "keyboard.pkg", windows))
    }
    @Test fun systemDialogIsNotTreatedAsKeyboard() {
        assertNull(KeyboardWindowPolicy.focusedWechatIndex("com.android.systemui", "keyboard.pkg",
            listOf(AppWindowCandidate("com.tencent.mm", true, true, 1))))
    }
}

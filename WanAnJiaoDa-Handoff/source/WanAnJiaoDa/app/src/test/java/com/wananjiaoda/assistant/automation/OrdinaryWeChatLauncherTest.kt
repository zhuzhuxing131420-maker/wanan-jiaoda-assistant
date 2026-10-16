package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class OrdinaryWeChatLauncherTest {
    @Test fun ownerProfileLaunchAvoidsGenericChooser() {
        var ownerCalls = 0
        var fallbackCalls = 0
        val method = OrdinaryWeChatLauncher.launch({ ownerCalls++ }, { fallbackCalls++ })
        assertEquals(WeChatLaunchMethod.OWNER_PROFILE, method)
        assertEquals(1, ownerCalls)
        assertEquals(0, fallbackCalls)
    }
    @Test fun unsupportedOwnerLaunchFallsBackOnce() {
        var fallbackCalls = 0
        val method = OrdinaryWeChatLauncher.launch({ throw SecurityException("unsupported") }, { fallbackCalls++ })
        assertEquals(WeChatLaunchMethod.INTENT_CHOOSER, method)
        assertEquals(1, fallbackCalls)
    }
    @Test(expected = IllegalArgumentException::class) fun bothLaunchFailuresReachCaller() {
        OrdinaryWeChatLauncher.launch({ throw SecurityException("unsupported") }, { throw IllegalArgumentException("missing") })
    }
    @Test fun recordsOwnerFailureSoFallbackIsNotMistakenForOwnerSuccess() {
        var reason: Exception? = null
        val method = OrdinaryWeChatLauncher.launch({ throw SecurityException("hidden package") }, {}, { reason = it })
        assertEquals(WeChatLaunchMethod.INTENT_CHOOSER, method)
        assertEquals("hidden package", reason?.message)
    }
}

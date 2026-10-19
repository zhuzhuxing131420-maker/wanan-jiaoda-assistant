package com.wananjiaoda.assistant.service

import org.junit.Assert.*
import org.junit.Test

class ConnectionProtectionPolicyTest {
    @Test fun enablingPreferenceSaveFailureIsVisibleWhileStillDisabled() {
        val state = ConnectionProtectionPolicy.state(false, false, false, "无法保存保护开关")
        assertFalse(state.enabled)
        assertFalse(state.running)
        assertTrue(state.message.contains("无法保存保护开关"))
    }
    @Test fun disablingPreferenceSaveFailureKeepsActualRunningState() {
        val state = ConnectionProtectionPolicy.state(true, true, true, "无法保存保护开关")
        assertTrue(state.enabled)
        assertTrue(state.running)
        assertTrue(state.message.contains("运行中"))
        assertTrue(state.message.contains("无法保存保护开关"))
    }
    @Test fun defaultDisabledDoesNotRestore() {
        assertFalse(ConnectionProtectionPolicy.shouldStart(false, false))
        assertFalse(ConnectionProtectionPolicy.state(false, false, false).enabled)
    }
    @Test fun optedInStickyRecreationRestoresProtection() {
        assertTrue(ConnectionProtectionPolicy.shouldStart(true, false))
        assertFalse(ConnectionProtectionPolicy.shouldStart(true, true))
    }
    @Test fun disabledStickyRecreationCannotRestore() {
        assertFalse(ConnectionProtectionPolicy.shouldStart(false, false))
        assertFalse(ConnectionProtectionPolicy.state(false, true, true).running)
    }
    @Test fun runningProtectionDoesNotClaimAccessibilityConnected() {
        val state = ConnectionProtectionPolicy.state(true, true, false)
        assertTrue(state.running)
        assertTrue(state.message.contains("未连接"))
        assertFalse(state.message.contains("已连接"))
    }
    @Test fun foregroundStartFailureIsVisibleAndRetryable() {
        val state = ConnectionProtectionPolicy.state(true, false, false, "通知已关闭")
        assertTrue(state.enabled)
        assertFalse(state.running)
        assertTrue(state.message.contains("通知已关闭"))
        assertTrue(ConnectionProtectionPolicy.shouldStart(state.enabled, state.running))
    }
    @Test fun disablingClearsRunningAndFailure() {
        val state = ConnectionProtectionPolicy.state(false, true, true)
        assertFalse(state.running)
        assertEquals("连接保护已关闭", state.message)
    }
}


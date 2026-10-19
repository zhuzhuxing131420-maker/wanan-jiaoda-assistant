package com.wananjiaoda.assistant.permission

import org.junit.Assert.*
import org.junit.Test

class AccessibilityPermissionTest {
    private val pkg = "com.wananjiaoda.assistant"
    private val cls = "$pkg.service.WanAnJiaoDaAccessibilityService"

    @Test fun fullAndRelativeComponentsAuthorizeOnlyOurService() {
        assertTrue(AccessibilityPermission.isAuthorized("other/.Service:$pkg/$cls", pkg, cls))
        assertTrue(AccessibilityPermission.isAuthorized("$pkg/.service.WanAnJiaoDaAccessibilityService", pkg, cls))
        assertFalse(AccessibilityPermission.isAuthorized("other/$cls:$pkg/.OtherService", pkg, cls))
        assertFalse(AccessibilityPermission.isAuthorized("$pkg/$cls.extra", pkg, cls))
        assertFalse(AccessibilityPermission.isAuthorized(null, pkg, cls))
        assertFalse(AccessibilityPermission.isAuthorized("", pkg, cls))
        assertFalse(AccessibilityPermission.isAuthorized("invalid:$pkg/", pkg, cls))
    }

    @Test fun secureSettingReadFailureIsUnknownRatherThanUnauthorized() {
        val authorization = AccessibilityPermission.readAuthorization(pkg, cls) { throw SecurityException("denied") }
        assertNull(authorization)
        assertEquals("状态未知", PermissionState(accessibilityEnabled = authorization).accessibilityStatusText)
    }

    @Test fun authorizedDisconnectedIsNotMissingAuthorization() {
        val state = PermissionState(accessibilityEnabled = true).withAccessibilityConnection(false)
        assertEquals(true, state.accessibilityEnabled)
        assertFalse(state.accessibilityConnected)
        assertEquals("已授权未连接", state.accessibilityStatusText)
    }

    @Test fun failedReadRemainsUnknownUntilActualConnectionProvesAuthorization() {
        val unknown = PermissionState(accessibilityEnabled = null)
        assertEquals("状态未知", unknown.withAccessibilityConnection(false).accessibilityStatusText)
        val connected = unknown.withAccessibilityConnection(true)
        assertEquals(true, connected.accessibilityEnabled)
        assertEquals("已连接", connected.accessibilityStatusText)
        assertEquals("已授权未连接", connected.withAccessibilityConnection(false).accessibilityStatusText)
    }

    @Test fun callbackOverridesStaleSnapshotButDisconnectedDoesNotRevokeAuthorization() {
        val snapshot = PermissionState(accessibilityEnabled = false)
        assertEquals("未授权", snapshot.accessibilityStatusText)
        val connected = snapshot.withAccessibilityConnection(true)
        assertEquals("已连接", connected.accessibilityStatusText)
        assertEquals("已授权未连接", connected.withAccessibilityConnection(false).accessibilityStatusText)
    }
}

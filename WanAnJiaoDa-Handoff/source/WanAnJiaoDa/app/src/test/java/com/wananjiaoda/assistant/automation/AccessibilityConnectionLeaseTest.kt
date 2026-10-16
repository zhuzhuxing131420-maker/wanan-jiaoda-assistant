package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class AccessibilityConnectionLeaseTest {
    @Test fun missingServiceInfoCannotBeUpdated() {
        val connection = AccessibilityConnectionLease()
        val token = connection.connect()
        assertFalse(connection.canConfigure(token, false))
    }
    @Test fun disconnectStopsOldObservationCallbackBeforeCancellation() {
        val connection = AccessibilityConnectionLease()
        val token = connection.connect()
        assertTrue(connection.canConfigure(token, true))
        connection.disconnect()
        assertFalse(connection.canConfigure(token, true))
    }
    @Test fun reconnectDoesNotReviveOldCallback() {
        val connection = AccessibilityConnectionLease()
        val old = connection.connect()
        connection.disconnect()
        val fresh = connection.connect()
        assertFalse(connection.canConfigure(old, true))
        assertTrue(connection.canConfigure(fresh, true))
    }
}

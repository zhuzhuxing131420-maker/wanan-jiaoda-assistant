package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class ObservationLeaseTest {
    private var now = 0L
    private val lease = ObservationLease({ now })

    @Test fun expiresAtTwoMinutes() {
        val token = lease.start()
        now = 119999
        assertFalse(lease.shouldExpire(token))
        now = 120000
        assertTrue(lease.shouldExpire(token))
    }

    @Test fun oldTimerCannotExpireRestartedSession() {
        val oldToken = lease.start()
        now = 100000
        val newToken = lease.start()
        now = 120000
        assertNotEquals(oldToken, newToken)
        assertFalse(lease.shouldExpire(oldToken))
        assertFalse(lease.shouldExpire(newToken))
        now = 220000
        assertTrue(lease.shouldExpire(newToken))
    }

    @Test fun stoppedSessionCannotExpire() {
        val token = lease.start()
        lease.stop()
        assertNotEquals(token, lease.token)
        now = 200000
        assertFalse(lease.shouldExpire(token))
        assertFalse(lease.shouldExpire(lease.token))
    }
}

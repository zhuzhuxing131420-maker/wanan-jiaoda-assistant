package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class CaptureThrottleTest {
    @Test fun firstRequestAtZeroIsAllowed() { assertTrue(CaptureThrottle().tryAcquire(0)) }
    @Test fun rejectsRequestsWithinMinimumInterval() {
        val gate = CaptureThrottle()
        assertTrue(gate.tryAcquire(1000))
        assertFalse(gate.tryAcquire(1649))
        assertTrue(gate.tryAcquire(1650))
    }
    @Test fun rejectedRequestDoesNotPostponeNextAllowedRequest() {
        val gate = CaptureThrottle()
        gate.tryAcquire(1000)
        assertFalse(gate.tryAcquire(1600))
        assertTrue(gate.tryAcquire(1650))
    }
    @Test fun decreasingClockCannotCauseAnotherCapture() {
        val gate = CaptureThrottle()
        gate.tryAcquire(1000)
        assertFalse(gate.tryAcquire(900))
        assertTrue(gate.tryAcquire(1650))
    }
}

package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class DetailCaptureLeaseTest {
    @Test fun twoUnresolvedFramesEnableDetailedCapture() {
        val lease = DetailCaptureLease()
        lease.observe(true, 1000)
        assertFalse(lease.requested(1000))
        lease.observe(true, 1800)
        assertTrue(lease.requested(1800))
    }
    @Test fun firstRecoveredFrameDoesNotImmediatelyDowngradeResolution() {
        val lease = DetailCaptureLease()
        lease.observe(true, 1000); lease.observe(true, 1800)
        lease.observe(false, 2600)
        assertTrue(lease.requested(3500))
        assertFalse(lease.requested(5800))
    }
    @Test fun resetDoesNotCarryHighResolutionIntoAnotherTest() {
        val lease = DetailCaptureLease()
        lease.observe(true, 1000); lease.observe(true, 1800)
        lease.reset()
        assertFalse(lease.requested(2000))
    }
}

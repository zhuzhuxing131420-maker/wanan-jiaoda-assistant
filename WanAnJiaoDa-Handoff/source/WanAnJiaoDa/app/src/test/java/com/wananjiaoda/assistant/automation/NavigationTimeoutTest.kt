package com.wananjiaoda.assistant.automation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NavigationTimeoutTest {
    @Test fun expiryInvokesCoordinatorCallbackExactlyOnce() = runBlocking {
        val stopped = CompletableDeferred<Unit>()
        var calls = 0
        val timer = navigationTimeout(this, 20) {
            calls++
            stopped.complete(Unit)
        }

        withTimeout(5000) { stopped.await(); timer.join() }
        assertEquals(1, calls)
    }

    @Test fun canceledTimeoutDoesNotInvokeCallback() = runBlocking {
        var called = false
        val timer = navigationTimeout(this, 20) { called = true }
        yield()
        timer.cancel()

        withTimeout(5000) { timer.join() }
        assertFalse(called)
    }

    @Test fun cancelingOldSessionDoesNotStopNewSessionTimer() = runBlocking {
        var oldCalls = 0
        val newStopped = CompletableDeferred<Unit>()
        val oldTimer = navigationTimeout(this, 20) { oldCalls++ }
        yield()
        oldTimer.cancel()
        val newTimer = navigationTimeout(this, 40) { newStopped.complete(Unit) }

        withTimeout(5000) { newStopped.await(); oldTimer.join(); newTimer.join() }
        assertEquals(0, oldCalls)
        assertFalse(newTimer.isCancelled)
    }
}

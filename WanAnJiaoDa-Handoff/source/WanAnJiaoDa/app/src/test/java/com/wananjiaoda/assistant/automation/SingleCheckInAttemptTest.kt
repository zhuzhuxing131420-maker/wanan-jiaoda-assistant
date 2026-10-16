package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class SingleCheckInAttemptTest {
    private val start = ZonedDateTime.parse("2026-10-11T19:40+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
    private val bounds = ScreenBounds(500, 1150, 800, 1230)
    private fun frame(at: Long, state: CheckInPageState = CheckInPageState.READY, button: ScreenBounds? = bounds) =
        RecognizedFrame(1, at, 1272, 2800, CheckInPageRecognition(state, button))
    private fun ready(attempt: SingleCheckInAttempt) {
        assertEquals(AttemptEffect.WAIT, attempt.onFrame(frame(start), start, true))
        assertEquals(AttemptEffect.TAP, attempt.onFrame(frame(start + 2000), start + 2000, true))
    }

    @Test fun twoFramesRequestOnlyOneTap() {
        val attempt = SingleCheckInAttempt(start, true)
        ready(attempt)
        assertEquals(AttemptPhase.TAP_REQUESTED, attempt.phase)
        assertEquals(AttemptEffect.WAIT, attempt.onFrame(frame(start + 4000), start + 4000, true))
    }
    @Test fun unconfirmedTargetCannotRequestTap() {
        val attempt = SingleCheckInAttempt(start, false)
        attempt.onFrame(frame(start), start, true)
        assertEquals(AttemptEffect.WAIT, attempt.onFrame(frame(start + 2000), start + 2000, true))
        assertNull(attempt.tapRequestedAtMillis)
    }
    @Test fun wrongWindowResetsReadiness() {
        val attempt = SingleCheckInAttempt(start, true)
        attempt.onFrame(frame(start), start, true)
        attempt.onFrame(frame(start + 1000), start + 1000, false)
        assertEquals(AttemptEffect.WAIT, attempt.onFrame(frame(start + 2000), start + 2000, true))
    }
    @Test fun lateTimeBlocksTap() {
        val late = ZonedDateTime.parse("2026-10-11T22:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
        val attempt = SingleCheckInAttempt(late, true)
        attempt.onFrame(frame(late), late, true)
        assertEquals(AttemptEffect.WAIT, attempt.onFrame(frame(late + 2000), late + 2000, true))
        assertNull(attempt.tapRequestedAtMillis)
    }
    @Test fun staleFrameCannotEstablishReadiness() {
        val attempt = SingleCheckInAttempt(start, true)
        attempt.onFrame(frame(start), start + 15000, true)
        assertEquals(AttemptEffect.WAIT, attempt.onFrame(frame(start + 16000), start + 16000, true))
    }
    @Test fun invalidBoundsCannotRequestTap() {
        val attempt = SingleCheckInAttempt(start, true)
        attempt.onFrame(frame(start, button = ScreenBounds(-1, 1, 10, 20)), start, true)
        assertEquals(AttemptEffect.WAIT, attempt.onFrame(frame(start + 2000, button = ScreenBounds(-1, 1, 10, 20)), start + 2000, true))
        assertNull(attempt.tapRequestedAtMillis)
    }
    @Test fun alreadyDoneSkipsWithoutTap() {
        val attempt = SingleCheckInAttempt(start, true)
        assertEquals(AttemptEffect.FINISH, attempt.onFrame(frame(start, CheckInPageState.ALREADY_DONE, null), start, true))
        assertEquals(AttemptPhase.SKIPPED, attempt.phase)
        assertNull(attempt.tapRequestedAtMillis)
    }
    @Test fun successNeedsFreshCompletedRow() {
        val attempt = SingleCheckInAttempt(start, true)
        ready(attempt)
        assertEquals(AttemptEffect.VERIFY, attempt.gestureResult(true))
        assertEquals(AttemptEffect.WAIT, attempt.onFrame(frame(start + 3000, CheckInPageState.SUCCESS_MESSAGE, null), start + 3000, true))
        assertEquals(AttemptEffect.WAIT, attempt.onFrame(frame(start, CheckInPageState.ALREADY_DONE, null), start + 3500, true))
        assertEquals(AttemptEffect.FINISH, attempt.onFrame(frame(start + 4000, CheckInPageState.ALREADY_DONE, null), start + 4000, true))
        assertEquals(AttemptPhase.SUCCESS, attempt.phase)
    }
    @Test fun interruptedGestureIsNotRetried() {
        val attempt = SingleCheckInAttempt(start, true)
        ready(attempt)
        assertEquals(AttemptEffect.FINISH, attempt.gestureResult(false))
        assertEquals(AttemptPhase.FAILED, attempt.phase)
        assertEquals(AttemptEffect.WAIT, attempt.onFrame(frame(start + 4000), start + 4000, true))
    }
}

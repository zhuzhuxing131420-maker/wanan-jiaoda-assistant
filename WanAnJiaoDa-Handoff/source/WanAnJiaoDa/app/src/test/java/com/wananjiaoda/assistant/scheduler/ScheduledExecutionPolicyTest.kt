package com.wananjiaoda.assistant.scheduler

import com.wananjiaoda.assistant.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class ScheduledExecutionPolicyTest {
    private val at = ZonedDateTime.of(2026, 10, 14, 21, 30, 0, 0, CheckInTimeZone.zoneId).toInstant().toEpochMilli()
    private val settings = CheckInSettings()
    private fun ticket(test: Boolean = false, state: String = "WAITING") = ScheduledTicket("alarm:$at", at,
        if (test) at + 600000 else at + 1800000, test, state)
    private fun gate(t: ScheduledTicket = ticket(), now: Long = at, s: CheckInSettings = settings,
        connected: Boolean = true, unlocked: Boolean = true, busy: Boolean = false, done: Boolean = false) =
        ScheduledExecutionPolicy.evaluate(t, now, s, connected, unlocked, busy, done)

    @Test fun unlockedTaskStarts() { assertEquals(ScheduledGate.START, gate()) }
    @Test fun lockedTaskWaitsAndResumesBeforeDeadline() {
        assertEquals(ScheduledGate.WAIT_UNLOCK, gate(unlocked = false))
        assertEquals(ScheduledGate.START, gate(now = at + 600000))
    }
    @Test fun disconnectedTaskWaitsForConnection() { assertEquals(ScheduledGate.WAIT_SERVICE, gate(connected = false)) }
    @Test fun deadlinePreventsLateUnlock() { assertEquals(ScheduledGate.EXPIRED, gate(now = at + 1800000, unlocked = true)) }
    @Test fun nextDayNeverReplays() { assertEquals(ScheduledGate.EXPIRED, gate(now = at + 86400000)) }
    @Test fun disabledSettingsCancelWaitingTask() { assertEquals(ScheduledGate.CANCELLED, gate(s = settings.copy(autoEnabled = false))) }
    @Test fun changedScheduleCancelsOldTask() { assertEquals(ScheduledGate.CANCELLED, gate(s = settings.copy(minute = 35))) }
    @Test fun removedWeekdayCancelsTask() { assertEquals(ScheduledGate.CANCELLED, gate(s = settings.copy(weekdays = setOf(1)))) }
    @Test fun semiAutoCannotSubmit() { assertEquals(ScheduledGate.CANCELLED, gate(s = settings.copy(mode = CheckInMode.SEMI_AUTO))) }
    @Test fun alreadyDoneSkipsEvenWhenLocked() { assertEquals(ScheduledGate.ALREADY_DONE, gate(done = true, unlocked = false)) }
    @Test fun busyTaskWaits() { assertEquals(ScheduledGate.WAIT_BUSY, gate(busy = true)) }
    @Test fun terminalAndRunningTasksCannotReplay() {
        for (state in listOf("RUNNING", "SUCCESS", "UNKNOWN", "CANCELLED", "EXPIRED"))
            assertEquals(ScheduledGate.IGNORE, gate(t = ticket(state = state)))
    }
    @Test fun noSubmitTestDoesNotDependOnFormalScheduleOrDone() {
        assertEquals(ScheduledGate.START, gate(t = ticket(test = true), s = settings.copy(autoEnabled = false), done = true))
    }
    @Test fun noSubmitTestStillWaitsForUnlockAndExpires() {
        assertEquals(ScheduledGate.WAIT_UNLOCK, gate(t = ticket(test = true), unlocked = false))
        assertEquals(ScheduledGate.EXPIRED, gate(t = ticket(test = true), now = at + 600000))
    }
    @Test fun cannotRunBeforePlannedTime() { assertEquals(ScheduledGate.NOT_DUE, gate(now = at - 1000)) }
    @Test fun outsideRealSignInTimeCannotStart() {
        val morning = at - 12 * 3600000
        assertEquals(ScheduledGate.CANCELLED, gate(t = ScheduledTicket("early", morning, at + 1800000, false),
            now = morning, s = settings.copy(hour = 9)))
    }
}

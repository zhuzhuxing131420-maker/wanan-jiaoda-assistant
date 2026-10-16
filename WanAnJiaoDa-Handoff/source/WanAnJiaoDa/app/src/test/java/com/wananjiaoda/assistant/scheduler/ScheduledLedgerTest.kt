package com.wananjiaoda.assistant.scheduler

import org.junit.Assert.*
import org.junit.Test

class ScheduledLedgerTest {
    private val ticket = ScheduledTicket("same-alarm", 1000, 10000, false)
    @Test fun duplicateCannotReplaceRunningOrCompletedTicket() {
        val ledger = ScheduledLedger(listOf(ticket.copy(status = "RUNNING")))
        assertFalse(ledger.add(ticket))
        assertEquals("RUNNING", ledger.records.single().status)
    }
    @Test fun claimRunningIsPersistableAndOnlyOnce() {
        val ledger = ScheduledLedger(listOf(ticket))
        assertTrue(ledger.claim(ticket.id, 2000))
        assertFalse(ledger.claim(ticket.id, 2001))
        assertEquals("RUNNING", ledger.records.single().status)
    }
    @Test fun restartMarksRunningUnknownButPreservesWaitingUnlock() {
        val ledger = ScheduledLedger(listOf(ticket.copy(status = "RUNNING"), ticket.copy(id = "waiting")))
        ledger.recover(3000)
        assertEquals("UNKNOWN", ledger.records.first().status)
        assertEquals("WAITING", ledger.records.last().status)
        assertFalse(ledger.claim(ticket.id, 4000))
    }
    @Test fun cancellingTestLeavesFormalTaskAndRunningTaskAlone() {
        val ledger = ScheduledLedger(listOf(ticket, ticket.copy(id = "test", navigationOnly = true),
            ticket.copy(id = "active", navigationOnly = true, status = "RUNNING")))
        ledger.cancelTests(3000)
        assertEquals(listOf("WAITING", "CANCELLED", "RUNNING"), ledger.records.map { it.status })
    }
    @Test fun boundedJournalKeepsPendingWhilePruningOldFinishedRecords() {
        val ledger = ScheduledLedger((1..30).map { ticket.copy(id = "$it", status = "SUCCESS") } + ticket)
        ledger.add(ticket.copy(id = "new"))
        assertTrue(ledger.records.any { it.id == ticket.id })
        assertTrue(ledger.records.size <= 22)
    }
    @Test fun earlyDeliveryMustKeepPlannedTicketTrackedUntilDue() {
        val planned = ticket.copy(navigationOnly = true, status = "PLANNED")
        val ledger = ScheduledLedger(listOf(planned))
        val settings = com.wananjiaoda.assistant.domain.model.CheckInSettings()
        assertEquals(ScheduledGate.NOT_DUE, ScheduledExecutionPolicy.evaluate(planned, 500, settings, true, true, false, false))
        assertTrue(ledger.hasPending)
        assertEquals(ScheduledGate.START, ScheduledExecutionPolicy.evaluate(planned, 1000, settings, true, true, false, false))
        ledger.claim(planned.id, 1000)
        assertFalse(ledger.hasPending)
    }
}

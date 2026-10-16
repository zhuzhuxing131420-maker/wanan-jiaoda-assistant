package com.wananjiaoda.assistant.scheduler

import com.wananjiaoda.assistant.domain.model.*
import java.time.Instant

data class ScheduledTicket(val id: String, val plannedAtMillis: Long, val deadlineMillis: Long,
    val navigationOnly: Boolean, val status: String = "WAITING", val reason: String = "等待任务时间",
    val updatedAtMillis: Long = plannedAtMillis)
enum class ScheduledGate { START, WAIT_UNLOCK, WAIT_SERVICE, WAIT_BUSY, EXPIRED, CANCELLED, ALREADY_DONE, NOT_DUE, IGNORE }

object ScheduledExecutionPolicy {
    fun evaluate(ticket: ScheduledTicket, nowMillis: Long, settings: CheckInSettings,
        connected: Boolean, unlocked: Boolean, busy: Boolean, alreadyDone: Boolean): ScheduledGate {
        if (ticket.status !in setOf("WAITING", "PLANNED")) return ScheduledGate.IGNORE
        if (nowMillis >= ticket.deadlineMillis) return ScheduledGate.EXPIRED
        if (nowMillis < ticket.plannedAtMillis) return ScheduledGate.NOT_DUE
        if (!ticket.navigationOnly) {
            val planned = Instant.ofEpochMilli(ticket.plannedAtMillis).atZone(CheckInTimeZone.zoneId)
            val now = Instant.ofEpochMilli(nowMillis).atZone(CheckInTimeZone.zoneId)
            if (!settings.autoEnabled || settings.mode != CheckInMode.AUTO ||
                planned.dayOfWeek.value !in settings.weekdays || planned.hour != settings.hour ||
                planned.minute != settings.minute || planned.toLocalDate() != now.toLocalDate() ||
                now.hour !in 19..21) return ScheduledGate.CANCELLED
            if (alreadyDone) return ScheduledGate.ALREADY_DONE
        }
        if (!connected) return ScheduledGate.WAIT_SERVICE
        if (!unlocked) return ScheduledGate.WAIT_UNLOCK
        if (busy) return ScheduledGate.WAIT_BUSY
        return ScheduledGate.START
    }
}

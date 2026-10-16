package com.wananjiaoda.assistant.scheduler

import com.wananjiaoda.assistant.domain.model.CheckInSettings
import com.wananjiaoda.assistant.domain.model.CheckInTimeZone
import java.time.ZonedDateTime

object NextCheckInTime {
    fun calculate(settings: CheckInSettings, now: ZonedDateTime): ZonedDateTime? {
        if (!settings.autoEnabled || settings.weekdays.isEmpty()) return null
        val beijingNow = now.withZoneSameInstant(CheckInTimeZone.zoneId)
        return (0..7).asSequence()
            .map { beijingNow.toLocalDate().plusDays(it.toLong()) }
            .filter { it.dayOfWeek.value in settings.weekdays }
            .map { it.atTime(settings.hour, settings.minute).atZone(CheckInTimeZone.zoneId) }
            .firstOrNull { it.isAfter(beijingNow) }
    }
}

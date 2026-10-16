package com.wananjiaoda.assistant.domain.model

import java.time.ZoneId

/** Check-in times and weekdays are always interpreted in Beijing time. */
object CheckInTimeZone {
    val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")
}

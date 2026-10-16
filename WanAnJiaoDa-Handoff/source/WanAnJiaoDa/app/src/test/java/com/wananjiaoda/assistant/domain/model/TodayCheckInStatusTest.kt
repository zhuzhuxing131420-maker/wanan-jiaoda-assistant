package com.wananjiaoda.assistant.domain.model

import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayCheckInStatusTest {
    private val today = LocalDate.of(2026, 10, 11)
    private fun record(day: String, time: String, outcome: String) = CheckInRecord(
        occurredAtMillis = ZonedDateTime.parse("${day}T$time+08:00[Asia/Shanghai]").toInstant().toEpochMilli(),
        outcome = outcome)

    @Test fun laterSkipDoesNotEraseConfirmedSuccess() {
        assertEquals("已完成", TodayCheckInStatus.resolve(listOf(
            record("2026-10-11", "19:50", "success"), record("2026-10-11", "19:55", "skipped")), today))
    }
    @Test fun oldSuccessDoesNotCountToday() {
        assertEquals("未执行", TodayCheckInStatus.resolve(listOf(record("2026-10-10", "19:50", "success")), today))
    }
    @Test fun latestFailureIsShownWithoutSuccess() {
        assertEquals("失败", TodayCheckInStatus.resolve(listOf(
            record("2026-10-11", "19:50", "skipped"), record("2026-10-11", "19:55", "failure")), today))
    }
    @Test fun skippedAttemptIsShownWithoutSuccess() {
        assertEquals("已跳过", TodayCheckInStatus.resolve(listOf(record("2026-10-11", "19:50", "skipped")), today))
    }
}

package com.wananjiaoda.assistant.scheduler

import com.wananjiaoda.assistant.domain.model.CheckInSettings
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class NextCheckInTimeTest {
    private fun at(value: String) = ZonedDateTime.parse(value)

    @Test fun fridayAfterDeadlineGoesToMonday() {
        assertEquals(at("2026-10-12T21:30+08:00[Asia/Shanghai]"),
            NextCheckInTime.calculate(CheckInSettings(), at("2026-10-09T21:31+08:00[Asia/Shanghai]")))
    }

    @Test fun exactDeadlineIsNotScheduledAgainToday() {
        assertEquals(at("2026-10-13T21:30+08:00[Asia/Shanghai]"),
            NextCheckInTime.calculate(CheckInSettings(), at("2026-10-12T21:30+08:00[Asia/Shanghai]")))
    }

    @Test fun utcInputUsesBeijingTime() {
        assertEquals(at("2026-10-09T21:30+08:00[Asia/Shanghai]"),
            NextCheckInTime.calculate(CheckInSettings(), at("2026-10-09T13:00Z")))
    }

    @Test fun selectedSundayCanBeScheduledAWeekAhead() {
        assertEquals(at("2026-10-18T21:30+08:00[Asia/Shanghai]"),
            NextCheckInTime.calculate(CheckInSettings(weekdays = setOf(7)), at("2026-10-11T21:31+08:00[Asia/Shanghai]")))
    }

    @Test fun disabledAndEmptyDaysHaveNoNextTime() {
        val now = at("2026-10-09T13:00Z")
        assertNull(NextCheckInTime.calculate(CheckInSettings(autoEnabled = false), now))
        assertNull(NextCheckInTime.calculate(CheckInSettings(weekdays = emptySet()), now))
    }
}

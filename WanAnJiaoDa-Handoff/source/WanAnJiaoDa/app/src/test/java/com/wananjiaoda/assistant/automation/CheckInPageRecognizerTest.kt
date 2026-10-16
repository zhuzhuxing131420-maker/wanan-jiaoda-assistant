package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class CheckInPageRecognizerTest {
    private fun line(text: String, y: Int, x: Int = 100) =
        ScreenTextLine(text, ScreenBounds(x, y, x + 300, y + 60))
    private fun page(button: String = "签到", todayStatus: String = "未打卡") = listOf(
        line("签到", 180), line("2026年10月9日", 650), line("姓名：测试", 850),
        line("学号：省略", 950), line(button, 1150), line("打卡时间段：19:00 - 22:00", 1300),
        line("近7日打卡记录", 1450), line("2026-10-09", 1580), line(todayStatus, 1580, 900),
        line("2026-10-08", 1720), line("已打卡", 1720, 900))
    private fun at(time: String) = ZonedDateTime.parse("2026-10-09T$time+08:00[Asia/Shanghai]")

    @Test fun readsOutsideTimeFromDisabledButton() {
        assertEquals(CheckInPageState.OUTSIDE_TIME, CheckInPageRecognizer.recognize(page("未到时间"), at("15:14:00")).state)
    }
    @Test fun readyUsesBodyButtonInsteadOfHeader() {
        val result = CheckInPageRecognizer.recognize(page(), at("19:00:00"))
        assertEquals(CheckInPageState.READY, result.state)
        assertEquals(1150, result.button!!.top)
    }
    @Test fun oldCompletedRowIsNotTodayCompleted() {
        assertEquals(CheckInPageState.READY, CheckInPageRecognizer.recognize(page(), at("21:30:00")).state)
    }
    @Test fun todaysCompletedRowIsTerminalAfterHours() {
        assertEquals(CheckInPageState.ALREADY_DONE, CheckInPageRecognizer.recognize(page(todayStatus = "已打卡"), at("23:00:00")).state)
    }
    @Test fun closingTimeBlocksClickEvenIfButtonLooksReady() {
        val result = CheckInPageRecognizer.recognize(page(), at("22:00:00"))
        assertEquals(CheckInPageState.OUTSIDE_TIME, result.state)
        assertNull(result.button)
    }
    @Test fun staleDateBlocksClick() {
        assertEquals(CheckInPageState.UNKNOWN, CheckInPageRecognizer.recognize(page(), ZonedDateTime.parse("2026-10-10T21:30:00+08:00[Asia/Shanghai]")).state)
    }
    @Test fun unrelatedPageIsNotRecognized() {
        assertEquals(CheckInPageState.UNKNOWN, CheckInPageRecognizer.recognize(listOf(line("签到", 1150)), at("21:30:00")).state)
    }
    @Test fun successMessageNeedsRecognizedPage() {
        assertEquals(CheckInPageState.SUCCESS_MESSAGE, CheckInPageRecognizer.recognize(page() + line("签到成功", 1100), at("21:30:00")).state)
    }
}

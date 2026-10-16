package com.wananjiaoda.assistant.automation

import java.time.ZonedDateTime
import java.time.LocalTime
import java.text.Normalizer
import kotlin.math.abs
import com.wananjiaoda.assistant.domain.model.CheckInTimeZone

data class ScreenBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}
data class ScreenTextLine(val text: String, val bounds: ScreenBounds)
enum class CheckInPageState { UNKNOWN, OUTSIDE_TIME, READY, ALREADY_DONE, SUCCESS_MESSAGE }
data class CheckInPageRecognition(val state: CheckInPageState, val button: ScreenBounds? = null)

object CheckInPageRecognizer {
    fun recognize(lines: List<ScreenTextLine>, now: ZonedDateTime): CheckInPageRecognition {
        val values = lines.map { it.copy(text = normalize(it.text)) }
        val heading = values.firstOrNull { it.text.contains("近7日打卡记录") }
            ?: return CheckInPageRecognition(CheckInPageState.UNKNOWN)
        val fields = values.filter { it.text.contains("姓名") || it.text.contains("学号") }
        if (fields.none { it.text.contains("姓名") } || fields.none { it.text.contains("学号") } ||
            values.none { it.text.contains("打卡时间") }) return CheckInPageRecognition(CheckInPageState.UNKNOWN)

        val beijingNow = now.withZoneSameInstant(CheckInTimeZone.zoneId)
        val today = beijingNow.toLocalDate()
        val chineseDate = "${today.year}年${today.monthValue}月${today.dayOfMonth}日"
        val headerDates = values.filter { Regex("\\d{4}年\\d{1,2}月\\d{1,2}日").containsMatchIn(it.text) }
        val todayRows = values.filter { it.bounds.top > heading.bounds.bottom && it.text.startsWith(today.toString()) }
        if (headerDates.isNotEmpty() && headerDates.none { it.text.contains(chineseDate) })
            return CheckInPageRecognition(CheckInPageState.UNKNOWN)
        if (headerDates.isEmpty() && todayRows.isEmpty()) return CheckInPageRecognition(CheckInPageState.UNKNOWN)

        val completed = todayRows.any { date ->
            date.text.endsWith("已打卡") || values.any { status ->
                status.text == "已打卡" && status.bounds.left >= date.bounds.right &&
                    abs(status.bounds.centerY - date.bounds.centerY) <= maxOf(date.bounds.height, status.bounds.height) / 2
            }
        }
        if (completed) return CheckInPageRecognition(CheckInPageState.ALREADY_DONE)
        if (values.any { it.text == "签到成功" || it.text == "打卡成功" })
            return CheckInPageRecognition(CheckInPageState.SUCCESS_MESSAGE)

        val fieldBottom = fields.maxOf { it.bounds.bottom }
        val bodyLines = values.filter { it.bounds.top > fieldBottom && it.bounds.bottom < heading.bounds.top }
        val time = beijingNow.toLocalTime()
        if (time.isBefore(LocalTime.of(19, 0)) || !time.isBefore(LocalTime.of(22, 0)) ||
            bodyLines.any { it.text.contains("未到时间") || it.text.contains("不在打卡时间") })
            return CheckInPageRecognition(CheckInPageState.OUTSIDE_TIME)
        val button = bodyLines.firstOrNull { it.text in setOf("签到", "打卡", "立即签到", "立即打卡") }
            ?: return CheckInPageRecognition(CheckInPageState.UNKNOWN)
        return CheckInPageRecognition(CheckInPageState.READY, button.bounds)
    }

    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace(Regex("\\s+"), "").replace('－', '-').replace('—', '-').replace('–', '-')
}

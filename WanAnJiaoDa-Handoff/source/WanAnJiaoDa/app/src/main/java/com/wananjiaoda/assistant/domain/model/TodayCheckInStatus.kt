package com.wananjiaoda.assistant.domain.model

import java.time.Instant
import java.time.LocalDate

object TodayCheckInStatus {
    fun resolve(history: List<CheckInRecord>, today: LocalDate): String {
        val records = history.filter {
            Instant.ofEpochMilli(it.occurredAtMillis).atZone(CheckInTimeZone.zoneId).toLocalDate() == today
        }
        if (records.any { it.outcome.equals("success", ignoreCase = true) }) return "已完成"
        return when (records.maxByOrNull { it.occurredAtMillis }?.outcome?.lowercase()) {
            "success" -> "已完成"
            "failure" -> "失败"
            "skipped" -> "已跳过"
            else -> "未执行"
        }
    }
}

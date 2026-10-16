package com.wananjiaoda.assistant.domain.model

enum class CheckInMode { AUTO, SEMI_AUTO }

data class CheckInSettings(
    val hour: Int = 21,
    val minute: Int = 30,
    val weekdays: Set<Int> = (1..5).toSet(),
    val autoEnabled: Boolean = true,
    val mode: CheckInMode = CheckInMode.AUTO,
)

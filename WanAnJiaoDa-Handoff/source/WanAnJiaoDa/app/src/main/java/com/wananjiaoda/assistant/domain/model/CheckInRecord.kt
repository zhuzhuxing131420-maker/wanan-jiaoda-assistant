package com.wananjiaoda.assistant.domain.model

data class CheckInRecord(
    val id: Long = 0,
    val occurredAtMillis: Long,
    val outcome: String,
    val detail: String? = null,
)

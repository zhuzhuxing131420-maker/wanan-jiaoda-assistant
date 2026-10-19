package com.wananjiaoda.assistant.service

data class ConnectionProtectionState(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val message: String = "连接保护已关闭",
)

/** Pure decisions shared by visible recovery and sticky service recreation. */
object ConnectionProtectionPolicy {
    fun shouldStart(enabled: Boolean, running: Boolean): Boolean = enabled && !running

    fun state(enabled: Boolean, running: Boolean, connected: Boolean, failure: String? = null): ConnectionProtectionState {
        if (!enabled && failure == null) return ConnectionProtectionState()
        val connection = if (connected) "无障碍已连接" else "无障碍未连接，请到系统设置检查并重新连接"
        return ConnectionProtectionState(enabled, running, when {
            failure != null -> "${if (running) "连接保护运行中" else "保护未运行"}：$failure；$connection"
            running -> "连接保护运行中；$connection"
            else -> "保护尚未运行；$connection"
        })
    }
}

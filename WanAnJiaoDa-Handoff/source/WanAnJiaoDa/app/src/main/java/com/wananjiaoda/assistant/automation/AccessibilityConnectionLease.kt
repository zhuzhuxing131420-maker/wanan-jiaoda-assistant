package com.wananjiaoda.assistant.automation

class AccessibilityConnectionLease {
    private var generation = 0L
    private var connected = false
    fun connect(): Long { connected = true; return ++generation }
    fun disconnect() { connected = false; generation++ }
    fun canConfigure(token: Long, hasServiceInfo: Boolean): Boolean = connected && generation == token && hasServiceInfo
}

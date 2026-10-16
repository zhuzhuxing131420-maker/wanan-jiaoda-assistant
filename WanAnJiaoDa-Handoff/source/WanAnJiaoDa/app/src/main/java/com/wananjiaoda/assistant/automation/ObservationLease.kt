package com.wananjiaoda.assistant.automation

class ObservationLease(private val clock: () -> Long) {
    var token = 0L
        private set
    private var deadline: Long? = null
    fun start(): Long {
        token++
        deadline = clock() + 120000L
        return token
    }
    fun stop() {
        token++
        deadline = null
    }
    fun shouldExpire(candidate: Long): Boolean =
        candidate == token && deadline?.let { clock() >= it } == true
}

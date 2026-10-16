package com.wananjiaoda.assistant.automation

class CaptureThrottle {
    private var lastRequest: Long? = null
    fun tryAcquire(nowElapsed: Long): Boolean {
        val previous = lastRequest
        if (previous != null && nowElapsed - previous < 650) return false
        lastRequest = nowElapsed
        return true
    }
}

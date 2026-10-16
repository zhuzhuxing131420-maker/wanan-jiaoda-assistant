package com.wananjiaoda.assistant.automation

class DetailCaptureLease {
    private var misses = 0
    private var untilElapsed = Long.MIN_VALUE
    fun reset() { misses = 0; untilElapsed = Long.MIN_VALUE }
    fun observe(unresolved: Boolean, nowElapsed: Long) {
        misses = if (unresolved) misses + 1 else 0
        if (misses >= 2) untilElapsed = nowElapsed + 4000
    }
    fun requested(nowElapsed: Long): Boolean = nowElapsed < untilElapsed
}

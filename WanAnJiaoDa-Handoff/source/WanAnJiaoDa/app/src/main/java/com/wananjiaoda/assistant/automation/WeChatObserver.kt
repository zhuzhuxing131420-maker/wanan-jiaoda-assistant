package com.wananjiaoda.assistant.automation

import android.view.accessibility.AccessibilityEvent
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory, opt-in observation of WeChat accessibility events only. */
object WeChatObserver {
    const val WECHAT_PACKAGE = "com.tencent.mm"
    private const val MAX_ENTRIES = 100
    private val lock = Any()
    private val _observing = MutableStateFlow(false)
    private val _detected = MutableStateFlow(false)
    private val _entries = MutableStateFlow<List<WeChatEventLog>>(emptyList())
    val observing = _observing.asStateFlow()
    val detected = _detected.asStateFlow()
    val entries = _entries.asStateFlow()
    private var nextId = 0L
    private val lease = ObservationLease(SystemClock::elapsedRealtime)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var expireJob: Job? = null
    val sessionId: Long get() = synchronized(lock) { lease.token }

    fun start() = synchronized(lock) {
        expireJob?.cancel()
        val token = lease.start()
        WeChatTreeDiagnostics.resetReport()
        WeChatVisionDiagnostics.resetReport()
        _entries.value = emptyList()
        _detected.value = false
        _observing.value = true
        expireJob = scope.launch {
            delay(120000L)
            synchronized(lock) { if (lease.shouldExpire(token)) stop() }
        }
    }

    fun stop() = synchronized(lock) {
        lease.stop()
        expireJob?.cancel()
        expireJob = null
        _observing.value = false
        _detected.value = false
        _entries.value = emptyList()
    }

    fun clear() = synchronized(lock) {
        _entries.value = emptyList()
    }

    fun record(event: AccessibilityEvent) {
        if (event.packageName?.toString() != WECHAT_PACKAGE) return
        synchronized(lock) {
            if (!_observing.value) return
            if (lease.shouldExpire(lease.token)) {
                stop()
                return
            }
            val text = AccessibilityText.summarize(event.text)
            val source = runCatching { event.source }.getOrNull()
            val node = AccessibilityNodeSnapshot.from(source)
            _detected.value = true
            _entries.value = (_entries.value + WeChatEventLog(
                id = ++nextId,
                recordedAtMillis = System.currentTimeMillis(),
                packageName = WECHAT_PACKAGE,
                eventType = AccessibilityEvent.eventTypeToString(event.eventType),
                className = event.className?.toString().orEmpty().take(160),
                text = text,
                viewIdResourceName = node.viewIdResourceName,
                bounds = node.bounds,
            )).takeLast(MAX_ENTRIES)
        }
    }
}

data class WeChatEventLog(
    val id: Long,
    val recordedAtMillis: Long,
    val packageName: String,
    val eventType: String,
    val className: String,
    val text: String,
    val viewIdResourceName: String,
    val bounds: String,
)

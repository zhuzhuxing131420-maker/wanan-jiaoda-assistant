package com.wananjiaoda.assistant.service

import android.view.accessibility.AccessibilityEvent
import com.wananjiaoda.assistant.automation.AccessibilityText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccessibilityEventLog(
    val id: Long,
    val recordedAtMillis: Long,
    val packageName: String,
    val eventType: String,
    val className: String,
    val text: String,
)

/** Process-local, bounded test log. It never keeps an AccessibilityEvent or Service reference. */
object AccessibilityEventLogger {
    private const val MAX_ENTRIES = 50
    private const val DUPLICATE_WINDOW_MILLIS = 350L
    private val lock = Any()
    private val _entries = MutableStateFlow<List<AccessibilityEventLog>>(emptyList())
    private val _connected = MutableStateFlow(false)
    val entries = _entries.asStateFlow()
    val connected = _connected.asStateFlow()

    private var nextId = 0L
    private var lastSignature: String? = null
    private var lastRecordedAt = 0L

    fun onServiceConnected() {
        synchronized(lock) {
            _entries.value = emptyList()
            lastSignature = null
            _connected.value = true
            append("", "SERVICE_CONNECTED", "", "")
        }
    }

    fun record(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString().orEmpty()
        val eventType = AccessibilityEvent.eventTypeToString(event.eventType)
        val className = event.className?.toString().orEmpty()
        val text = AccessibilityText.summarize(event.text)
        synchronized(lock) {
            if (_connected.value) append(packageName, eventType, className, text)
        }
    }

    fun onInterrupt() {
        synchronized(lock) {
            if (_connected.value) append("", "SERVICE_INTERRUPTED", "", "")
        }
    }

    fun onServiceDisconnected() {
        synchronized(lock) {
            _connected.value = false
            _entries.value = emptyList()
            lastSignature = null
            lastRecordedAt = 0L
        }
    }

    private fun append(packageName: String, eventType: String, className: String, text: String) {
        val now = System.currentTimeMillis()
        val signature = "$packageName|$eventType|$className|$text"
        if (signature == lastSignature && now - lastRecordedAt < DUPLICATE_WINDOW_MILLIS) return
        lastSignature = signature
        lastRecordedAt = now
        _entries.value = (_entries.value + AccessibilityEventLog(
            id = ++nextId,
            recordedAtMillis = now,
            packageName = packageName,
            eventType = eventType,
            className = className,
            text = text,
        )).takeLast(MAX_ENTRIES)
    }
}

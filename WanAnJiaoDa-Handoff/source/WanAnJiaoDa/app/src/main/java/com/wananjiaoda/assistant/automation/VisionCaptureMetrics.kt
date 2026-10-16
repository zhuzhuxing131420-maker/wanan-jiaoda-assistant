package com.wananjiaoda.assistant.automation

import org.json.JSONArray
import org.json.JSONObject

/** Bounded metadata only; never holds bitmaps or OCR text. */
class VisionCaptureMetrics {
    private var session = -1L
    private var requested = 0
    private var recognized = 0
    private var discarded = 0
    private val errors = linkedMapOf<Int, Int>()
    private val latencies = mutableListOf<Long>()

    @Synchronized fun requested(id: Long) {
        if (session != id) {
            session = id; requested = 0; recognized = 0; discarded = 0
            errors.clear(); latencies.clear()
        }
        requested++
    }
    @Synchronized fun error(id: Long, code: Int) {
        if (session == id) errors[code] = (errors[code] ?: 0) + 1
    }
    @Synchronized fun discarded(id: Long) { if (session == id) discarded++ }
    @Synchronized fun recognized(id: Long, elapsedMillis: Long) {
        if (session != id) return
        recognized++
        latencies.add(elapsedMillis)
        if (latencies.size > 30) latencies.removeAt(0)
    }
    @Synchronized fun snapshot(): JSONObject = JSONObject().put("sessionId", session)
        .put("requested", requested).put("recognized", recognized).put("discarded", discarded)
        .put("screenshotErrors", JSONObject(errors.mapKeys { it.key.toString() }))
        .put("recentRecognitionLatenciesMillis", JSONArray(latencies.toList()))
}

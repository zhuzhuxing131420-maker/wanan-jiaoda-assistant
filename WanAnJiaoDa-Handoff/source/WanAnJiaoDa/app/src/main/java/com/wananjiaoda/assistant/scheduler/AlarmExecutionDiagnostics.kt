package com.wananjiaoda.assistant.scheduler

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** A bounded receipt log. Receiving an alarm is not evidence of a completed check-in. */
object AlarmExecutionDiagnostics {
    private val lock = Any()

    fun record(context: Context, source: String, plannedAtMillis: Long, receivedAtMillis: Long) = synchronized(lock) {
        try {
            val directory = File(context.applicationContext.filesDir, "diagnostics")
            check(directory.isDirectory || directory.mkdirs())
            val file = AtomicFile(File(directory, "alarm-executions.json"))
            val oldRecords = runCatching {
                file.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()).getJSONArray("records") }
            }.getOrElse { JSONArray() }
            val records = JSONArray()
            for (i in maxOf(0, oldRecords.length() - 19) until oldRecords.length()) {
                oldRecords.optJSONObject(i)?.let { records.put(it) }
            }
            records.put(JSONObject().put("source", source).put("status", "received")
                .put("plannedAtMillis", if (plannedAtMillis > 0) plannedAtMillis else JSONObject.NULL)
                .put("receivedAtMillis", receivedAtMillis)
                .put("latenessMillis", if (plannedAtMillis > 0) receivedAtMillis - plannedAtMillis else JSONObject.NULL))
            val document = JSONObject().put("schemaVersion", 1).put("timeZone", "Asia/Shanghai")
                .put("records", records)
            val stream = file.startWrite()
            try {
                stream.write(document.toString(2).toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        } catch (error: Exception) {
            Log.w(CheckInScheduler.TAG, "保存闹钟接收记录失败：${error.javaClass.simpleName}")
        }
    }
}

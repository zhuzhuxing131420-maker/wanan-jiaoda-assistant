package com.wananjiaoda.assistant.scheduler

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** A synchronous durable claim is required before launching WeChat; no asynchronous apply(). */
class ScheduledExecutionJournal(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "diagnostics").also { it.mkdirs() }
    private val file = AtomicFile(File(directory, "scheduled-executions.json"))
    fun read(): ScheduledLedger {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return ScheduledLedger()
        val rows = file.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()).getJSONArray("records") }
        return ScheduledLedger((0 until rows.length()).map { index ->
            val r = rows.getJSONObject(index)
            ScheduledTicket(r.getString("id"), r.getLong("plannedAtMillis"), r.getLong("deadlineMillis"),
                r.getBoolean("navigationOnly"), r.getString("status"), r.getString("reason"), r.getLong("updatedAtMillis"))
        })
    }
    fun write(ledger: ScheduledLedger) {
        val rows = JSONArray(ledger.records.map { r -> JSONObject().put("id", r.id)
            .put("plannedAtMillis", r.plannedAtMillis).put("deadlineMillis", r.deadlineMillis)
            .put("navigationOnly", r.navigationOnly).put("submissionEnabled", !r.navigationOnly)
            .put("status", r.status).put("reason", r.reason).put("updatedAtMillis", r.updatedAtMillis) })
        val stream = file.startWrite()
        try { stream.write(JSONObject().put("schemaVersion", 1).put("timeZone", "Asia/Shanghai")
            .put("records", rows).toString(2).toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}

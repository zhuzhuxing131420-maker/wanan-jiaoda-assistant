package com.wananjiaoda.assistant.scheduler

/** Pure journal transitions; caller persists before any externally visible action. */
class ScheduledLedger(initial: List<ScheduledTicket> = emptyList()) {
    private val entries = initial.toMutableList()
    val records: List<ScheduledTicket> get() = entries.toList()
    val hasPending: Boolean get() = entries.any { it.status in setOf("PLANNED", "WAITING") }
    fun add(ticket: ScheduledTicket): Boolean {
        if (entries.any { it.id == ticket.id }) return false
        entries.add(ticket)
        val finished = entries.filter { it.status !in setOf("WAITING", "PLANNED", "RUNNING") }.takeLast(20).map { it.id }.toSet()
        entries.removeAll { it.status !in setOf("WAITING", "PLANNED", "RUNNING") && it.id !in finished }
        return true
    }
    fun claim(id: String, now: Long): Boolean {
        val old = entries.firstOrNull { it.id == id } ?: return false
        if (old.status !in setOf("WAITING", "PLANNED")) return false
        update(id, "RUNNING", "正在自动进入微信", now)
        return true
    }
    fun update(id: String, status: String, reason: String, now: Long) {
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) entries[index] = entries[index].copy(status = status, reason = reason, updatedAtMillis = now)
    }
    fun recover(now: Long) {
        entries.filter { it.status == "RUNNING" }.forEach {
            update(it.id, "UNKNOWN", "执行进程中断，结果未确认，不自动重试", now)
        }
    }
    fun cancelTests(now: Long) {
        entries.filter { it.navigationOnly && it.status in setOf("WAITING", "PLANNED") }.forEach {
            update(it.id, "CANCELLED", "定时导航测试已取消", now)
        }
    }
}

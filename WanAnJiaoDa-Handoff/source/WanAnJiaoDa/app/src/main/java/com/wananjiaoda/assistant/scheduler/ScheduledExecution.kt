package com.wananjiaoda.assistant.scheduler

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.wananjiaoda.assistant.automation.*
import com.wananjiaoda.assistant.data.local.*
import com.wananjiaoda.assistant.data.repository.*
import com.wananjiaoda.assistant.domain.model.CheckInTimeZone
import com.wananjiaoda.assistant.service.AccessibilityEventLogger
import java.lang.ref.WeakReference
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialized durable commands, executed using the actual bound accessibility service context. */
object ScheduledExecution {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var service = WeakReference<Context>(null)
    private var recovered = false
    private var activeId: String? = null
    private var ticker: Job? = null
    private val _status = MutableStateFlow("尚无定时执行记录")
    private val _running = MutableStateFlow(false)
    val status = _status.asStateFlow()
    val running = _running.asStateFlow()
    val ownsObservation: Boolean get() = _running.value

    fun attach(context: Context) { service = WeakReference(context); resume(context.applicationContext) }
    fun detach(context: Context) {
        if (service.get() === context) service.clear()
        ticker?.cancel(); ticker = null
        resume(context.applicationContext)
    }

    suspend fun receive(context: Context, plannedAtMillis: Long) = withContext(Dispatchers.Main.immediate) {
        if (plannedAtMillis <= 0) return@withContext
        mutex.withLock {
            val journal = ScheduledExecutionJournal(context)
            val ledger = load(journal)
            val day = Instant.ofEpochMilli(plannedAtMillis).atZone(CheckInTimeZone.zoneId)
            val deadline = day.toLocalDate().atTime(22, 0).atZone(CheckInTimeZone.zoneId).toInstant().toEpochMilli()
            ledger.add(ScheduledTicket("alarm:$plannedAtMillis", plannedAtMillis, deadline, false))
            journal.write(ledger)
        }
        processPending(context.applicationContext)
    }

    private fun load(journal: ScheduledExecutionJournal): ScheduledLedger {
        val ledger = journal.read()
        if (!recovered) {
            ledger.recover(System.currentTimeMillis())
            journal.write(ledger)
            recovered = true
        }
        return ledger
    }

    fun resume(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try { processPending(app) }
            catch (error: Exception) {
                _status.value = "定时记录或执行异常，已停止：${error.javaClass.simpleName}"
                Log.e(CheckInScheduler.TAG, _status.value, error)
                ScheduledTaskNotification.show(app, _status.value)
            }
        }
    }

    /** Receiver keeps goAsync alive until pending state is durably written, not for the full UI task. */
    suspend fun processPending(context: Context) = withContext(Dispatchers.Main.immediate) {
        mutex.withLock { evaluatePending(context.applicationContext) }
    }

    private suspend fun evaluatePending(app: Context) {
        val journal = ScheduledExecutionJournal(app)
        val ledger = load(journal)
        val settings = withContext(Dispatchers.IO) { DataStoreSettingsRepository(AppPreferencesDataStore(app)).settings.first() }
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(CheckInTimeZone.zoneId).toLocalDate()
        val records = withContext(Dispatchers.IO) { OfflineCheckInHistoryRepository(AppDatabase.getInstance(app).checkInRecordDao()).observeRecords().first() }
        val done = records.any { it.outcome in setOf("success", "skipped") &&
            Instant.ofEpochMilli(it.occurredAtMillis).atZone(CheckInTimeZone.zoneId).toLocalDate() == today }
        val uncertain = ledger.records.any { !it.navigationOnly && it.status == "UNKNOWN" &&
            Instant.ofEpochMilli(it.plannedAtMillis).atZone(CheckInTimeZone.zoneId).toLocalDate() == today }
        for (ticket in ledger.records.filter { it.status in setOf("PLANNED", "WAITING") }) {
            val bound = service.get()
            val unlocked = app.getSystemService(PowerManager::class.java).isInteractive &&
                !app.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            val gate = if (!ticket.navigationOnly && uncertain) ScheduledGate.CANCELLED else
                ScheduledExecutionPolicy.evaluate(ticket, now, settings, bound != null && AccessibilityEventLogger.connected.value,
                    unlocked, activeId != null || WeChatNavigationTest.running || SingleSceneCheckInTest.running, done)
            val message = when (gate) {
                ScheduledGate.START -> "定时任务正在进入微信" 
                ScheduledGate.WAIT_UNLOCK -> "请解锁手机，解锁后自动继续；" + if (ticket.navigationOnly) "测试截至" +
                    Instant.ofEpochMilli(ticket.deadlineMillis).atZone(CheckInTimeZone.zoneId).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    else "今日22:00前有效"
                ScheduledGate.WAIT_SERVICE -> "等待恢复无障碍服务；截止前连接后自动执行"
                ScheduledGate.WAIT_BUSY -> "其他任务正在执行，等待结束"
                ScheduledGate.EXPIRED -> "已超过执行截止时间，未再启动"
                ScheduledGate.CANCELLED -> if (uncertain) "今日有未确认任务，避免重复提交，已停止" else "设置已变更、非自动模式或不在签到时段，已取消"
                ScheduledGate.ALREADY_DONE -> "今日已有成功记录，跳过定时签到"
                else -> ticket.reason
            }
            if (gate == ScheduledGate.START) {
                check(bound != null)
                check(ledger.claim(ticket.id, now))
                journal.write(ledger) // Durable claim BEFORE launch.
                activeId = ticket.id
                _running.value = true
                _status.value = message
                ScheduledTaskNotification.show(app, message)
                WeChatNavigationTest.start(bound, onlyNavigate = ticket.navigationOnly)
                scope.launch { monitor(app, ticket) }
            } else if (gate !in setOf(ScheduledGate.IGNORE, ScheduledGate.NOT_DUE)) {
                val terminal = when (gate) {
                    ScheduledGate.EXPIRED -> "EXPIRED"
                    ScheduledGate.CANCELLED -> "CANCELLED"
                    ScheduledGate.ALREADY_DONE -> "SKIPPED"
                    else -> "WAITING"
                }
                ledger.update(ticket.id, terminal, message, now)
                journal.write(ledger)
                _status.value = message
                if (ticket.reason != message) ScheduledTaskNotification.show(app, message,
                    requiresAction = gate in setOf(ScheduledGate.WAIT_UNLOCK, ScheduledGate.WAIT_SERVICE))
            }
        }
        val latest = ledger.records.lastOrNull()
        if (activeId == null && latest != null) _status.value = latest.reason
        ScheduledTestAlarm.refreshExpiry(app, ledger.records)
        ScheduledTestAlarm.restoreTest(app, ledger.records)
        if (ledger.hasPending && service.get() != null && ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive && service.get() != null) {
                    delay(5000)
                    resume(app)
                    if (!ScheduledExecutionJournal(app).read().hasPending) break
                }
            }
        }
    }

    private suspend fun monitor(app: Context, ticket: ScheduledTicket) {
        try {
            withTimeout(185000) {
                delay(500)
                while (WeChatNavigationTest.running || SingleSceneCheckInTest.running) delay(500)
            }
            val nav = WeChatNavigationTest.lastOutcome
            val scene = SingleSceneCheckInTest.lastOutcome
            val result = when {
                ticket.navigationOnly && nav in setOf("page_reached", "page_reached_outside_time") -> "NAVIGATION_OK"
                nav == "handed_off" && scene == "success" -> "SUCCESS"
                nav == "handed_off" && scene == "skipped" -> "SKIPPED"
                nav == "page_reached_outside_time" -> "EXPIRED"
                else -> "UNKNOWN"
            }
            val message = when (result) {
                "NAVIGATION_OK" -> "定时导航测试已到达签到页，全程未提交"
                "SUCCESS" -> "定时签到已确认成功"
                "SKIPPED" -> "已识别今日打卡完成，跳过提交"
                "EXPIRED" -> "已到达签到页但时段未开放，未提交"
                else -> "定时执行结果未确认：" + if (nav == "handed_off") SingleSceneCheckInTest.uiState.value.message else WeChatNavigationTest.state.value.message
            }
            finish(app, ticket.id, result, message)
        } catch (error: Exception) {
            WeChatNavigationTest.cancel("定时执行超时或异常")
            SingleSceneCheckInTest.cancel("定时执行超时或异常，结果未确认")
            finish(app, ticket.id, "UNKNOWN", "定时执行超时或异常，结果未确认，不自动重试")
        } finally { activeId = null; _running.value = false; resume(app) }
    }

    private suspend fun finish(app: Context, id: String, result: String, message: String) = mutex.withLock {
        val journal = ScheduledExecutionJournal(app)
        val ledger = journal.read()
        ledger.update(id, result, message, System.currentTimeMillis())
        journal.write(ledger)
        _status.value = message
        ScheduledTaskNotification.show(app, message)
    }

    fun scheduleTest(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try { mutex.withLock {
                check(CheckInScheduler.canScheduleExactAlarms(app)) { "请先允许精确闹钟" }
                val journal = ScheduledExecutionJournal(app)
                val ledger = load(journal)
                val now = System.currentTimeMillis()
                ledger.cancelTests(now)
                val planned = now + 60000
                val ticket = ScheduledTicket("test:$planned", planned, planned + 600000, true, "PLANNED", "已安排1分钟后定时导航，不打卡", now)
                ledger.add(ticket)
                journal.write(ledger)
                ScheduledTestAlarm.schedule(app, ticket)
                _status.value = ticket.reason
            }; resume(app) } catch (error: Exception) { _status.value = "安排测试失败：${error.message}" }
        }
    }

    fun cancelTest(context: Context) {
        val app = context.applicationContext
        scope.launch { mutex.withLock {
            val journal = ScheduledExecutionJournal(app)
            val ledger = load(journal)
            ledger.cancelTests(System.currentTimeMillis())
            journal.write(ledger)
            ScheduledTestAlarm.cancel(app)
            _status.value = "待执行的定时导航测试已取消"
        }; resume(app) }
    }
}

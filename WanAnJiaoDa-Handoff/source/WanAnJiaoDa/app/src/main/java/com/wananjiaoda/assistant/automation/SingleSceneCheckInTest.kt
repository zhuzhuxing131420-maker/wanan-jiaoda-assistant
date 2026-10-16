package com.wananjiaoda.assistant.automation

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.wananjiaoda.assistant.data.local.AppDatabase
import com.wananjiaoda.assistant.data.repository.OfflineCheckInHistoryRepository
import com.wananjiaoda.assistant.domain.model.CheckInRecord
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class SceneTestUiState(val message: String = "未启动单次签到测试", val running: Boolean = false,
    val phase: AttemptPhase? = null)
data class SceneTapRequest(val generation: Long, val sessionId: Long, val bounds: ScreenBounds)

/** Explicit manual scene testing; scheduled tasks do not arm this coordinator. */
object SingleSceneCheckInTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(SceneTestUiState())
    val uiState = _uiState.asStateFlow()
    private var attempt: SingleCheckInAttempt? = null
    private var generation = 0L
    private var session = -1L
    private var timeoutJob: Job? = null
    private var appContext: Context? = null
    private var targetConfirmation = "manual"
    val running: Boolean get() = _uiState.value.running
    var lastOutcome: String? = null
        private set
    val verifying: Boolean get() = attempt?.phase == AttemptPhase.VERIFYING

    fun start(context: Context, confirmation: String = "manual") {
        if (running) return
        lastOutcome = null
        appContext = context.applicationContext
        targetConfirmation = confirmation
        generation++
        WeChatObserver.start()
        session = WeChatObserver.sessionId
        attempt = SingleCheckInAttempt(System.currentTimeMillis(), targetConfirmed = true)
        refresh()
        timeoutJob = scope.launch {
            delay(90000)
            cancel("等待页面或结果超过90秒，结果未确认")
        }
    }

    fun accept(frame: RecognizedFrame, targetWindow: Boolean): SceneTapRequest? {
        val active = attempt ?: return null
        if (!active.running || frame.sessionId != session) return null
        val effect = active.onFrame(frame, System.currentTimeMillis(), targetWindow)
        refresh()
        if (effect == AttemptEffect.FINISH) finish(active)
        if (effect == AttemptEffect.TAP) return SceneTapRequest(generation, session, requireNotNull(frame.recognition.button))
        return null
    }

    fun gestureResult(id: Long, completed: Boolean): Boolean {
        val active = attempt ?: return false
        if (id != generation || !active.running) return false
        val effect = active.gestureResult(completed)
        refresh()
        if (effect == AttemptEffect.FINISH) finish(active)
        return effect == AttemptEffect.VERIFY
    }

    fun cancel(message: String) {
        val active = attempt ?: return
        if (active.cancel(message) == AttemptEffect.FINISH) {
            refresh()
            finish(active)
        }
    }

    private fun refresh() {
        val active = attempt ?: return
        _uiState.value = SceneTestUiState(active.reason, active.running, active.phase)
    }

    private fun finish(active: SingleCheckInAttempt) {
        timeoutJob?.cancel()
        timeoutJob = null
        val context = appContext ?: return
        val id = generation
        val completedAt = System.currentTimeMillis()
        val outcome = when (active.phase) {
            AttemptPhase.SUCCESS -> "success"
            AttemptPhase.SKIPPED -> "skipped"
            else -> "failure"
        }
        lastOutcome = outcome
        val detail = if (targetConfirmation == "manual") "单次真实测试（目标人工确认）：${active.reason}"
            else "自动导航签到测试（完整名称选择）：${active.reason}"
        val receipt = JSONObject().put("schemaVersion", 1).put("generation", id)
            .put("targetName", "晚安交大地学人").put("targetConfirmation", targetConfirmation)
            .put("actionMethod", if (active.tapRequestedAtMillis != null) "AccessibilityService.dispatchGesture" else JSONObject.NULL)
            .put("startedAtMillis", active.startedAtMillis)
            .put("tapRequestedAtMillis", active.tapRequestedAtMillis ?: JSONObject.NULL)
            .put("completedAtMillis", completedAt).put("phase", active.phase.name)
            .put("reason", active.reason).put("outcome", outcome)
        Log.i("WanAnJiaoDaClick", "scene finished: phase=${active.phase} tapRequested=${active.tapRequestedAtMillis != null}")
        WeChatObserver.stop()
        scope.launch(Dispatchers.IO) {
            try {
                OfflineCheckInHistoryRepository(AppDatabase.getInstance(context).checkInRecordDao())
                    .addRecord(CheckInRecord(occurredAtMillis = completedAt, outcome = outcome, detail = detail))
                val directory = File(context.filesDir, "diagnostics")
                check(directory.isDirectory || directory.mkdirs())
                val file = AtomicFile(File(directory, "scene-test.json"))
                val stream = file.startWrite()
                try { stream.write(receipt.toString(2).toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
                catch (error: Exception) { file.failWrite(stream); throw error }
                Log.i("WanAnJiaoDaClick", "scene history saved: outcome=$outcome")
            } catch (error: Exception) {
                Log.w("WanAnJiaoDaClick", "scene history export failed: ${error.javaClass.simpleName}")
                withContext(Dispatchers.Main) {
                    if (generation == id) _uiState.value = _uiState.value.copy(message = active.reason + "（记录保存异常）")
                }
            }
        }
    }
}

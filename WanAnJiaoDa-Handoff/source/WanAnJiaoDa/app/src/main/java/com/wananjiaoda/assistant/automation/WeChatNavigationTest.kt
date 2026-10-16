package com.wananjiaoda.assistant.automation

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.Process
import android.os.SystemClock
import android.os.PowerManager
import android.util.AtomicFile
import android.util.Log
import com.wananjiaoda.assistant.data.local.AppDatabase
import com.wananjiaoda.assistant.data.repository.OfflineCheckInHistoryRepository
import com.wananjiaoda.assistant.domain.model.CheckInRecord
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class NavigationUiState(val running: Boolean = false, val message: String = "未启动自动导航")
data class NavigationRequest(val generation: Long, val session: Long, val decision: NavigationDecision)

/** Shared native navigation coordinator; scheduled commands originate from the bound service. */
object WeChatNavigationTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(NavigationUiState())
    val state = _state.asStateFlow()
    val running get() = _state.value.running
    var lastOutcome: String? = null
        private set
    private var policy: WeChatNavigationAttempt? = null
    private var context: Context? = null
    private var generation = 0L
    private var session = -1L
    private var timeout: Job? = null
    private var steps = JSONArray()
    private var navigationOnly = false
    private var launchMethod: WeChatLaunchMethod? = null
    private var ownerLaunchError: String? = null
    private var unresolvedFrames = 0
    private val detailCapture = DetailCaptureLease()
    val needsDetailCapture: Boolean get() = running && detailCapture.requested(SystemClock.elapsedRealtime())
    var startedAtMillis = 0L
        private set

    fun start(owner: Context, onlyNavigate: Boolean = false) {
        if (running || SingleSceneCheckInTest.running) return
        lastOutcome = null
        val app = owner.applicationContext
        if (!app.getSystemService(PowerManager::class.java).isInteractive ||
            app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            _state.value = NavigationUiState(message = "请先解锁并保持手机亮屏")
            return
        }
        context = app
        generation++
        navigationOnly = onlyNavigate
        launchMethod = null
        ownerLaunchError = null
        unresolvedFrames = 0
        detailCapture.reset()
        steps = JSONArray()
        startedAtMillis = System.currentTimeMillis()
        policy = WeChatNavigationAttempt(navigationOnly)
        _state.value = NavigationUiState(true, "正在打开微信")
        WeChatObserver.start()
        session = WeChatObserver.sessionId
        timeout = scope.launch { delay(90000); cancel("自动导航超过90秒，已停止") }
        try {
            launchMethod = requestLaunch(owner) { ownerLaunchError = "${it.javaClass.simpleName}: ${it.message.orEmpty().take(240)}" }
        } catch (error: Exception) { cancel("无法打开微信：${error.javaClass.simpleName}") }
    }

    /** Isolates launcher routing without requiring or starting accessibility observation. */
    fun probeLaunch(owner: Context) {
        if (running || SingleSceneCheckInTest.running) return
        WeChatObserver.stop()
        val started = System.currentTimeMillis()
        var method: WeChatLaunchMethod? = null
        var ownerError: String? = null
        var failure: String? = null
        try {
            method = requestLaunch(owner) { ownerError = "${it.javaClass.simpleName}: ${it.message.orEmpty().take(240)}" }
            _state.value = NavigationUiState(message = "已请求打开普通微信，本轮不读画面、不打卡")
        } catch (error: Exception) {
            failure = "${error.javaClass.simpleName}: ${error.message.orEmpty().take(240)}"
            _state.value = NavigationUiState(message = "普通微信启动失败，原因已保存到本机诊断")
        }
        val document = JSONObject().put("startedAtMillis", started).put("launchProfileId", Process.myUid() / 100000)
            .put("launchMethod", method?.name ?: JSONObject.NULL).put("ownerLaunchError", ownerError ?: JSONObject.NULL)
            .put("failure", failure ?: JSONObject.NULL).put("observationStarted", false).put("submissionEnabled", false)
        val app = owner.applicationContext
        scope.launch(Dispatchers.IO) {
            try {
                val directory = File(app.filesDir, "diagnostics")
                check(directory.isDirectory || directory.mkdirs())
                val file = AtomicFile(File(directory, "wechat-launch.json"))
                val stream = file.startWrite()
                try { stream.write(document.toString(2).toByteArray()); file.finishWrite(stream) }
                catch (error: Exception) { file.failWrite(stream); throw error }
            } catch (error: Exception) { Log.w("WanAnJiaoDaNavigation", "launch diagnostic failed: ${error.javaClass.simpleName}") }
        }
    }

    private fun requestLaunch(owner: Context, onOwnerFailure: (Exception) -> Unit): WeChatLaunchMethod =
        OrdinaryWeChatLauncher.launch(
            ownerProfileLaunch = {
                owner.applicationContext.getSystemService(LauncherApps::class.java).startMainActivity(
                    ComponentName(WeChatObserver.WECHAT_PACKAGE, "com.tencent.mm.ui.LauncherUI"),
                    Process.myUserHandle(), null, null)
            },
            fallbackLaunch = {
                owner.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(WeChatObserver.WECHAT_PACKAGE, "com.tencent.mm.ui.LauncherUI")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, onOwnerFailure = onOwnerFailure)

    fun accept(frame: RecognizedFrame, miniProgram: Boolean): NavigationRequest? {
        val active = policy ?: return null
        if (!running || frame.sessionId != session) return null
        val decision = active.onFrame(frame, System.currentTimeMillis(), miniProgram)
        unresolvedFrames = if (active.needsResample) unresolvedFrames + 1 else 0
        detailCapture.observe(active.needsResample, SystemClock.elapsedRealtime())
        _state.value = NavigationUiState(true, active.reason)
        if (active.failed) { cancel(active.reason); return null }
        if (decision?.action == NavigationAction.PAGE_OUTSIDE_TIME || decision?.action == NavigationAction.PAGE_REACHED) {
            timeout?.cancel()
            _state.value = NavigationUiState(false, active.reason)
            saveReceipt(if (decision.action == NavigationAction.PAGE_REACHED) "page_reached" else "page_reached_outside_time",
                active.reason, failure = false)
            WeChatObserver.stop()
            return null
        }
        if (decision?.action == NavigationAction.HANDOFF) {
            timeout?.cancel()
            _state.value = NavigationUiState(false, "已自动进入目标签到页，正在核对当天记录")
            saveReceipt("handed_off", active.reason, failure = false)
            context?.let { SingleSceneCheckInTest.start(it, "full_name_selection") }
            return null
        }
        if (decision != null) {
            steps.put(JSONObject().put("action", decision.action.name).put("requestedAtMillis", System.currentTimeMillis()))
            Log.i("WanAnJiaoDaNavigation", "navigation action requested: ${decision.action}")
            return NavigationRequest(generation, session, decision)
        }
        return null
    }

    fun actionCompleted(request: NavigationRequest, completed: Boolean) {
        if (!running || request.generation != generation || request.session != session) return
        val active = policy ?: return
        active.actionCompleted(completed, System.currentTimeMillis())
        if (steps.length() > 0) steps.getJSONObject(steps.length() - 1)
            .put("completed", completed).put("completedAtMillis", System.currentTimeMillis())
        _state.value = NavigationUiState(true, active.reason)
        if (active.failed) cancel(active.reason)
    }

    fun cancel(message: String) {
        if (!running) return
        timeout?.cancel()
        _state.value = NavigationUiState(false, message)
        saveReceipt("failure", message, failure = true)
        WeChatObserver.stop()
    }

    private fun saveReceipt(outcome: String, reason: String, failure: Boolean) {
        lastOutcome = outcome
        val app = context ?: return
        val completedAt = System.currentTimeMillis()
        val receipt = JSONObject().put("schemaVersion", 1).put("generation", generation)
            .put("launchMethod", launchMethod?.name ?: JSONObject.NULL).put("launchProfileId", Process.myUid() / 100000)
            .put("ownerLaunchError", ownerLaunchError ?: JSONObject.NULL)
            .put("lastRecognizedPage", policy?.page?.name ?: JSONObject.NULL)
            .put("unresolvedFrames", unresolvedFrames)
            .put("submissionEnabled", !navigationOnly)
            .put("targetName", "晚安交大地学人").put("targetConfirmation", "full_name_selection")
            .put("targetSelectionCompleted", (0 until steps.length()).any { index ->
                steps.getJSONObject(index).let { it.optString("action") == "TARGET" && it.optBoolean("completed") }
            })
            .put("startedAtMillis", startedAtMillis).put("completedAtMillis", completedAt)
            .put("outcome", outcome).put("reason", reason).put("steps", steps)
        scope.launch(Dispatchers.IO) {
            try {
                val directory = File(app.filesDir, "diagnostics")
                check(directory.isDirectory || directory.mkdirs())
                val file = AtomicFile(File(directory, "navigation-test.json"))
                val stream = file.startWrite()
                try { stream.write(receipt.toString(2).toByteArray()); file.finishWrite(stream) }
                catch (error: Exception) { file.failWrite(stream); throw error }
                if (failure) OfflineCheckInHistoryRepository(AppDatabase.getInstance(app).checkInRecordDao())
                    .addRecord(CheckInRecord(occurredAtMillis = completedAt, outcome = "failure", detail = "自动导航测试：$reason"))
            } catch (error: Exception) { Log.w("WanAnJiaoDaNavigation", "receipt save failed: ${error.javaClass.simpleName}") }
        }
    }
}

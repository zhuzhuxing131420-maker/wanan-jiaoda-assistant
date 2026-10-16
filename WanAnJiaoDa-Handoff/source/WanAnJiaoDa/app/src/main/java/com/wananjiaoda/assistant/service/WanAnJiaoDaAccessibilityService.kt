package com.wananjiaoda.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import com.wananjiaoda.assistant.scheduler.ScheduledExecution
import android.view.accessibility.AccessibilityEvent
import com.wananjiaoda.assistant.automation.WeChatObserver
import com.wananjiaoda.assistant.automation.WeChatTreeDiagnostics
import com.wananjiaoda.assistant.automation.WeChatVisionDiagnostics
import com.wananjiaoda.assistant.automation.MiniProgramVision
import com.wananjiaoda.assistant.automation.SingleSceneCheckInTest
import com.wananjiaoda.assistant.automation.RecognizedFrame
import com.wananjiaoda.assistant.automation.WeChatNavigationTest
import com.wananjiaoda.assistant.automation.NavigationRequest
import com.wananjiaoda.assistant.automation.NavigationAction
import com.wananjiaoda.assistant.automation.WeChatWindowTracker
import com.wananjiaoda.assistant.automation.WeChatInstanceChooser
import com.wananjiaoda.assistant.automation.WeChatChooserNodes
import com.wananjiaoda.assistant.automation.CheckInPageRecognition
import com.wananjiaoda.assistant.automation.CheckInPageState
import com.wananjiaoda.assistant.automation.AccessibilityConnectionLease
import com.wananjiaoda.assistant.automation.KeyboardWindowPolicy
import com.wananjiaoda.assistant.automation.AppWindowCandidate
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.os.SystemClock
import android.graphics.Rect
import android.util.AtomicFile
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Observes by default; the explicit single-scene test may request one native tap. */
class WanAnJiaoDaAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowTracker = WeChatWindowTracker()
    private var vision: MiniProgramVision? = null
    private var visionJob: Job? = null
    private var chooserWriteJob: Job? = null
    private var observationJob: Job? = null
    private val connection = AccessibilityConnectionLease()
    private var unlockReceiverRegistered = false
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { ScheduledExecution.resume(applicationContext) }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        observationJob?.cancel()
        val token = connection.connect()
        AccessibilityEventLogger.onServiceConnected()
        if (!unlockReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_USER_PRESENT).apply { addAction(Intent.ACTION_SCREEN_ON) }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(unlockReceiver, filter)
            unlockReceiverRegistered = true
        }
        Log.i("WanAnJiaoDaNodes", "accessibility service connected")
        observationJob = serviceScope.launch {
            WeChatObserver.observing.collect { observing ->
                if (!connection.canConfigure(token, true)) return@collect
                if (!observing) {
                    WeChatNavigationTest.cancel("观察已停止，自动导航已取消")
                    SingleSceneCheckInTest.cancel("观察已停止，测试结果未确认")
                    visionJob?.cancel()
                    if (Build.VERSION.SDK_INT >= 30) vision?.stop()
                } else startSampling()
                val info = runCatching { serviceInfo }.getOrNull()
                if (info != null && connection.canConfigure(token, true)) {
                    runCatching {
                        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                            AccessibilityEvent.TYPE_VIEW_CLICKED or
                            (if (observing) AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED else 0)
                        serviceInfo = info
                    }.onFailure { Log.w("WanAnJiaoDaNodes", "service info update skipped: ${it.javaClass.simpleName}") }
                }
            }
        }
        ScheduledExecution.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            windowTracker.update(event.packageName?.toString().orEmpty(), event.className?.toString().orEmpty())
        }
        if (event.packageName?.toString() == WeChatObserver.WECHAT_PACKAGE) {
            WeChatObserver.record(event)
            WeChatTreeDiagnostics.capture(this)
        } else if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            AccessibilityEventLogger.record(event)
        }
    }

    override fun onInterrupt() {
        AccessibilityEventLogger.onInterrupt()
    }

    override fun onUnbind(intent: Intent): Boolean {
        disconnectObservation()
        WeChatNavigationTest.cancel("无障碍服务断开，自动导航停止")
        SingleSceneCheckInTest.cancel("无障碍服务断开，测试结果未确认")
        WeChatObserver.stop()
        AccessibilityEventLogger.onServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnectObservation()
        WeChatNavigationTest.cancel("无障碍服务停止，自动导航停止")
        SingleSceneCheckInTest.cancel("无障碍服务停止，测试结果未确认")
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= 30) vision?.close()
        WeChatObserver.stop()
        AccessibilityEventLogger.onServiceDisconnected()
        super.onDestroy()
    }

    private fun disconnectObservation() {
        ScheduledExecution.detach(this)
        if (unlockReceiverRegistered) {
            runCatching { unregisterReceiver(unlockReceiver) }
            unlockReceiverRegistered = false
        }
        connection.disconnect()
        observationJob?.cancel()
        observationJob = null
        visionJob?.cancel()
        if (Build.VERSION.SDK_INT >= 30) vision?.stop()
    }

    private fun isMiniProgramWindow() = windowTracker.isMiniProgram
    private fun isVisionWindow() = WeChatNavigationTest.running || isMiniProgramWindow() || (SingleSceneCheckInTest.verifying &&
        windowTracker.activityClass.startsWith("com.tencent.mm.plugin.appbrand.ui."))

    private fun startSampling() {
        visionJob?.cancel()
        if (Build.VERSION.SDK_INT < 30) {
            WeChatVisionDiagnostics.update("图像识别需要 Android 11 或更高版本")
            return
        }
        visionJob = serviceScope.launch {
            var ordinaryWindow = ""
            var ordinaryCount = 0
            var sawWeChat = false
            var foreignSince: Long? = null
            while (isActive && WeChatObserver.observing.value) {
                val root = activeNavigationRoot()
                val inWeChat: Boolean
                val inChooser: Boolean
                val windowId: Int
                var chooserFrame: RecognizedFrame? = null
                try {
                    inWeChat = root?.packageName?.toString() == WeChatObserver.WECHAT_PACKAGE
                    inChooser = root?.packageName?.toString() == WeChatInstanceChooser.PACKAGE
                    windowId = root?.windowId ?: -1
                    if (inChooser && WeChatNavigationTest.running && root != null) {
                        val bounds = Rect().also(root::getBoundsInScreen)
                        chooserFrame = RecognizedFrame(WeChatObserver.sessionId, System.currentTimeMillis(),
                            maxOf(bounds.right, resources.displayMetrics.widthPixels),
                            maxOf(bounds.bottom, resources.displayMetrics.heightPixels),
                            CheckInPageRecognition(CheckInPageState.UNKNOWN), WeChatChooserNodes.collect(root),
                            windowId, WeChatInstanceChooser.PACKAGE)
                    }
                } finally { if (Build.VERSION.SDK_INT < 33) { @Suppress("DEPRECATION") root?.recycle() } }
                if (inWeChat) { sawWeChat = true; foreignSince = null }
                else if (inChooser && WeChatNavigationTest.running) {
                    foreignSince = null
                    chooserFrame?.let { frame ->
                        saveChooserDiagnostic(frame)
                        WeChatVisionDiagnostics.update("正在核对分身弹窗并选择普通微信")
                        onRecognizedFrame(frame)
                    }
                }
                else if (WeChatNavigationTest.running && (sawWeChat ||
                    System.currentTimeMillis() - WeChatNavigationTest.startedAtMillis > 10000)) {
                    val elapsed = SystemClock.elapsedRealtime()
                    val since = foreignSince ?: elapsed.also { foreignSince = it }
                    if (elapsed - since >= 3000) {
                        WeChatNavigationTest.cancel("微信持续离开前台，自动导航停止")
                        return@launch
                    }
                }
                val automated = WeChatNavigationTest.running || SingleSceneCheckInTest.running
                val key = "$windowId:${windowTracker.activityClass}"
                if (key != ordinaryWindow) { ordinaryWindow = key; ordinaryCount = 0 }
                if (inWeChat && isVisionWindow() && (automated || ordinaryCount < 3)) {
                    val reader = vision ?: MiniProgramVision(this@WanAnJiaoDaAccessibilityService, ::isVisionWindow, ::onRecognizedFrame, ::activeNavigationRoot)
                        .also { vision = it }
                    if (reader.capture() && !automated) ordinaryCount++
                }
                delay(if (WeChatNavigationTest.running) 750L else if (SingleSceneCheckInTest.running) 1000L else 3500L)
            }
        }
    }

    private fun onRecognizedFrame(frame: RecognizedFrame) {
        if (WeChatNavigationTest.running) {
            val request = WeChatNavigationTest.accept(frame, isMiniProgramWindow())
            if (request != null) performNavigation(request)
            return
        }
        val request = SingleSceneCheckInTest.accept(frame, isVisionWindow()) ?: return
        val root = activeNavigationRoot()
        val valid = try {
            isMiniProgramWindow() && WeChatObserver.observing.value && WeChatObserver.sessionId == request.sessionId &&
                root?.packageName?.toString() == WeChatObserver.WECHAT_PACKAGE
        } finally {
            if (Build.VERSION.SDK_INT < 33) { @Suppress("DEPRECATION") root?.recycle() }
        }
        if (!valid) {
            SingleSceneCheckInTest.gestureResult(request.generation, false)
            return
        }
        val box = request.bounds
        val path = Path().apply { moveTo((box.left + box.right) / 2f, (box.top + box.bottom) / 2f) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build()
        Log.i("WanAnJiaoDaClick", "native tap requested: x=${(box.left + box.right) / 2} y=${box.centerY}")
        try {
            val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    SingleSceneCheckInTest.gestureResult(request.generation, true)
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    SingleSceneCheckInTest.gestureResult(request.generation, false)
                }
            }, null)
            if (!accepted) SingleSceneCheckInTest.gestureResult(request.generation, false)
        } catch (error: Exception) {
            SingleSceneCheckInTest.gestureResult(request.generation, false)
        }
    }

    private fun performNavigation(request: NavigationRequest) {
        val root = activeNavigationRoot()
        val expectedPackage = if (request.decision.action == NavigationAction.PRIMARY_WECHAT)
            WeChatInstanceChooser.PACKAGE else WeChatObserver.WECHAT_PACKAGE
        val valid = try { WeChatNavigationTest.running && WeChatObserver.observing.value &&
            request.session == WeChatObserver.sessionId && root?.packageName?.toString() == expectedPackage }
        finally { if (Build.VERSION.SDK_INT < 33) { @Suppress("DEPRECATION") root?.recycle() } }
        if (!valid) { WeChatNavigationTest.actionCompleted(request, false); return }
        if (request.decision.action == NavigationAction.BACK) {
            WeChatNavigationTest.actionCompleted(request, performGlobalAction(GLOBAL_ACTION_BACK))
            return
        }
        val box = request.decision.bounds ?: run { WeChatNavigationTest.actionCompleted(request, false); return }
        val path = Path().apply { moveTo((box.left + box.right) / 2f, box.centerY.toFloat()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build()
        try {
            val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) { WeChatNavigationTest.actionCompleted(request, true) }
                override fun onCancelled(gestureDescription: GestureDescription) { WeChatNavigationTest.actionCompleted(request, false) }
            }, null)
            if (!accepted) WeChatNavigationTest.actionCompleted(request, false)
        } catch (error: Exception) { WeChatNavigationTest.actionCompleted(request, false) }
    }

    private fun activeNavigationRoot(): AccessibilityNodeInfo? {
        val current = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val ime = runCatching { Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }
            .getOrNull()?.substringBefore('/').orEmpty()
        if (current.packageName?.toString() != ime || ime.isBlank()) return current
        val visibleWindows = runCatching { windows }.getOrNull().orEmpty()
        val appRoots = mutableListOf<AccessibilityNodeInfo?>()
        try {
            val descriptors = visibleWindows.map { window ->
                val app = window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    (Build.VERSION.SDK_INT < 30 || window.displayId == 0)
                val root = if (app && window.isFocused) runCatching { window.root }.getOrNull() else null
                appRoots.add(root)
                AppWindowCandidate(root?.packageName?.toString().orEmpty(), app, window.isFocused, window.layer)
            }
            val index = KeyboardWindowPolicy.focusedWechatIndex(current.packageName?.toString().orEmpty(), ime, descriptors)
            if (index != null) {
                val selected = appRoots[index]
                appRoots[index] = null
                if (Build.VERSION.SDK_INT < 33) { @Suppress("DEPRECATION") current.recycle() }
                return selected
            }
            return current
        } finally {
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION")
                appRoots.forEach { it?.recycle() }
                @Suppress("DEPRECATION")
                visibleWindows.forEach { it.recycle() }
            }
        }
    }

    private fun saveChooserDiagnostic(frame: RecognizedFrame) {
        if (chooserWriteJob?.isActive == true) return
        val document = JSONObject().put("packageName", frame.packageName).put("windowId", frame.windowId)
            .put("sessionId", frame.sessionId).put("capturedAtMillis", frame.requestedAtMillis)
            .put("lines", JSONArray(frame.lines.map { line -> JSONObject().put("text", line.text)
                .put("bounds", JSONArray(listOf(line.bounds.left, line.bounds.top, line.bounds.right, line.bounds.bottom))) }))
        chooserWriteJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val directory = File(filesDir, "diagnostics")
                check(directory.isDirectory || directory.mkdirs())
                val file = AtomicFile(File(directory, "wechat-instance.json"))
                val stream = file.startWrite()
                try { stream.write(document.toString(2).toByteArray()); file.finishWrite(stream) }
                catch (error: Exception) { file.failWrite(stream); throw error }
            } catch (error: Exception) { Log.w("WanAnJiaoDaNavigation", "chooser diagnostic failed: ${error.javaClass.simpleName}") }
        }
    }
}

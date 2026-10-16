package com.wananjiaoda.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.wananjiaoda.assistant.domain.model.CheckInTimeZone
import java.io.File
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.view.accessibility.AccessibilityNodeInfo

/** Local, read-only recognition. Does not save screenshots or execute actions. */
@RequiresApi(30)
class MiniProgramVision(
    private val service: AccessibilityService,
    private val windowStillActive: () -> Boolean,
    private val onFrame: (RecognizedFrame) -> Unit = {},
    private val activeRoot: () -> AccessibilityNodeInfo? = { service.rootInActiveWindow },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recognizer: TextRecognizer? = null
    private var busy = false
    @Volatile private var closed = false
    private var writeJob: Job? = null
    private val throttle = CaptureThrottle()
    private val metrics = VisionCaptureMetrics()

    fun capture(): Boolean {
        val session = WeChatObserver.sessionId
        if (busy || writeJob?.isActive == true) return false
        val windowId = activeWindowId(session) ?: return false
        val requestedElapsed = SystemClock.elapsedRealtime()
        if (!throttle.tryAcquire(requestedElapsed)) return false
        metrics.requested(session)
        busy = true
        val requestedAt = System.currentTimeMillis()
        val maximumSide = if (WeChatNavigationTest.needsDetailCapture) 2800 else 2000
        WeChatVisionDiagnostics.update("正在读取小程序画面")
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onFailure(errorCode: Int) {
                        busy = false
                        metrics.error(session, errorCode)
                        if (current(session, windowId)) WeChatVisionDiagnostics.update("截图失败：系统错误码 $errorCode")
                        Log.w("WanAnJiaoDaVision", "screenshot failed: code=$errorCode")
                    }

                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        var software: Bitmap? = null
                        try {
                            val buffer = result.hardwareBuffer
                            try {
                                val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                try { software = wrapped?.copy(Bitmap.Config.ARGB_8888, false) }
                                finally { wrapped?.recycle() }
                            } finally { buffer.close() }
                            val bitmap = software ?: error("No bitmap")
                            if (!current(session, windowId)) {
                                metrics.discarded(session)
                                bitmap.recycle()
                                busy = false
                                return
                            }
                            recognize(bitmap, session, requestedAt, requestedElapsed, windowId, maximumSide)
                        } catch (error: Exception) {
                            software?.takeUnless { it.isRecycled }?.recycle()
                            busy = false
                            if (current(session, windowId)) WeChatVisionDiagnostics.update("画面读取失败：${error.javaClass.simpleName}")
                        }
                    }
                })
        } catch (error: Exception) {
            busy = false
            if (current(session, windowId)) WeChatVisionDiagnostics.update("截图请求失败：${error.javaClass.simpleName}")
        }
        return true
    }

    private fun recognize(bitmap: Bitmap, session: Long, requestedAt: Long, requestedElapsed: Long, windowId: Int, maximumSide: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = minOf(1.0, maximumSide.toDouble() / maxOf(width, height))
        val inputBitmap = if (ratio < 1.0) Bitmap.createScaledBitmap(bitmap,
            maxOf(1, (width * ratio).toInt()), maxOf(1, (height * ratio).toInt()), true) else bitmap
        if (inputBitmap !== bitmap) bitmap.recycle()
        try {
            val client = recognizer ?: TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                .also { recognizer = it }
            val scaleX = width.toDouble() / inputBitmap.width
            val scaleY = height.toDouble() / inputBitmap.height
            client.process(InputImage.fromBitmap(inputBitmap, 0))
                .addOnSuccessListener(service.mainExecutor) { text ->
                    if (!current(session, windowId)) {
                        metrics.discarded(session)
                        return@addOnSuccessListener
                    }
                    metrics.recognized(session, SystemClock.elapsedRealtime() - requestedElapsed)
                    val lines = text.textBlocks.flatMap { it.lines }.take(80).mapNotNull { line ->
                        val bounds = line.boundingBox ?: return@mapNotNull null
                        ScreenTextLine(line.text.take(240), ScreenBounds(
                            (bounds.left * scaleX).toInt(), (bounds.top * scaleY).toInt(),
                            (bounds.right * scaleX).toInt(), (bounds.bottom * scaleY).toInt()))
                    }
                    val result = CheckInPageRecognizer.recognize(lines, ZonedDateTime.now(CheckInTimeZone.zoneId))
                    val document = JSONObject().put("schemaVersion", 1).put("source", "local_ocr")
                        .put("captureRequestedAtMillis", requestedAt).put("recognizedAtMillis", System.currentTimeMillis())
                        .put("screenWidth", width).put("screenHeight", height).put("sessionId", session)
                        .put("windowId", windowId).put("captureMetrics", metrics.snapshot())
                        .put("maximumInputSide", maximumSide)
                        .put("navigationLabelHints", JSONArray(if (WeChatNavigationTest.running) lines.mapNotNull { line ->
                            val normalized = ScreenTextResolver.normalize(line.text)
                            val known = listOf("小程序", "朋友圈", "最近使用").firstOrNull {
                                normalized.contains(it) && normalized.length <= it.length + 4
                            } ?: return@mapNotNull null
                            JSONObject().put("label", known).put("exact", normalized == known)
                                .put("extraCodePoints", JSONArray(normalized.replace(known, "").map { it.code }))
                        } else emptyList<JSONObject>()))
                        .put("identityVerified", false).put("pageState", result.state.name)
                        .put("candidateButtonBounds", result.button?.let(::boundsJson) ?: JSONObject.NULL)
                        .put("lines", JSONArray((if (WeChatNavigationTest.running) lines.filter { line ->
                            line.text.replace(Regex("\\s+"), "") in setOf("微信", "通讯录", "发现", "我", "朋友圈",
                                "小程序", "最近使用", "最近便用", "晚安交大地学人", "欢迎", "确定", "学号绑定", "进入打卡")
                        } else lines).map { line ->
                            val value = if (line.text.contains("姓名")) "姓名：[已省略]"
                                else if (line.text.contains("学号")) "学号：[已省略]" else line.text
                            JSONObject().put("text", value).put("bounds", boundsJson(line.bounds))
                        }))
                    save(document, result, session, RecognizedFrame(session, requestedAt, width, height, result, lines, windowId))
                }
                .addOnFailureListener(service.mainExecutor) { error ->
                    if (current(session, windowId)) WeChatVisionDiagnostics.update("文字识别失败：${error.javaClass.simpleName}")
                    Log.w("WanAnJiaoDaVision", "recognition failed: ${error.javaClass.simpleName}")
                }
                .addOnCompleteListener(service.mainExecutor) {
                    inputBitmap.recycle()
                    busy = false
                }
        } catch (error: Exception) {
            inputBitmap.takeUnless { it.isRecycled }?.recycle()
            busy = false
            if (current(session, windowId)) WeChatVisionDiagnostics.update("文字识别初始化失败：${error.javaClass.simpleName}")
        }
    }

    private fun save(document: JSONObject, result: CheckInPageRecognition, session: Long, frame: RecognizedFrame) {
        val directory = File(service.applicationContext.filesDir, "diagnostics")
        writeJob = scope.launch {
            try {
                if (!current(session, frame.windowId)) { metrics.discarded(session); return@launch }
                check(directory.isDirectory || directory.mkdirs())
                val file = AtomicFile(File(directory, "wechat-ocr.json"))
                val stream = file.startWrite()
                try {
                    stream.write(document.toString(2).toByteArray(Charsets.UTF_8))
                    if (current(session, frame.windowId)) file.finishWrite(stream) else {
                        metrics.discarded(session)
                        file.failWrite(stream)
                        return@launch
                    }
                } catch (error: Exception) { file.failWrite(stream); throw error }
                withContext(Dispatchers.Main) {
                    if (current(session, frame.windowId)) {
                        WeChatVisionDiagnostics.update(when (result.state) {
                        CheckInPageState.UNKNOWN -> "画面已识别，签到页面状态待确认"
                        CheckInPageState.OUTSIDE_TIME -> "画面识别：当前不在打卡时间"
                        CheckInPageState.READY -> "画面识别：发现可签到按钮（尚未点击）"
                        CheckInPageState.ALREADY_DONE -> "画面识别：今天已打卡"
                        CheckInPageState.SUCCESS_MESSAGE -> "画面识别：出现成功提示，待业务校验"
                        })
                        onFrame(frame)
                    }
                }
                Log.i("WanAnJiaoDaVision", "local recognition saved: state=${result.state}")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                withContext(Dispatchers.Main) {
                    if (current(session, frame.windowId)) WeChatVisionDiagnostics.update("识别记录保存失败：${error.javaClass.simpleName}")
                }
            }
        }
    }

    private fun current(session: Long, windowId: Int) = activeWindowId(session) == windowId

    private fun activeWindowId(session: Long): Int? {
        if (closed || !WeChatObserver.observing.value || WeChatObserver.sessionId != session || !windowStillActive()) return null
        val root = runCatching { activeRoot() }.getOrNull() ?: return null
        return try { if (root.packageName?.toString() == WeChatObserver.WECHAT_PACKAGE) root.windowId else null }
        finally {
            if (Build.VERSION.SDK_INT < 33) { @Suppress("DEPRECATION") root.recycle() }
        }
    }

    private fun boundsJson(bounds: ScreenBounds) = JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom))

    fun stop() {
        writeJob?.cancel()
        recognizer?.close()
        recognizer = null
    }
    fun close() {
        closed = true
        stop()
        scope.cancel()
    }
}

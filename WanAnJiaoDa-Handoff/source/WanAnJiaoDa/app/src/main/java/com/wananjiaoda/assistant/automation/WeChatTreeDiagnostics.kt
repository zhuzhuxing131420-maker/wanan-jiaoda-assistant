package com.wananjiaoda.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Opt-in, bounded snapshots. Only copied values cross into the IO coroutine. */
object WeChatTreeDiagnostics {
    private const val INTERVAL_MILLIS = 1500L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var writeJob: Job? = null
    private var lastCaptureAt = -INTERVAL_MILLIS
    private var lastSession = -1L
    private val _report = MutableStateFlow("尚未采集页面节点")
    val report = _report.asStateFlow()
    fun resetReport() { _report.value = "尚未采集页面节点" }

    fun capture(service: AccessibilityService) {
        if (!WeChatObserver.observing.value || writeJob?.isActive == true) return
        val session = WeChatObserver.sessionId
        if (session != lastSession) {
            lastSession = session
            lastCaptureAt = -INTERVAL_MILLIS
        }
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastCaptureAt < INTERVAL_MILLIS) return
        lastCaptureAt = elapsed
        val root = try { service.rootInActiveWindow } catch (error: Exception) {
            _report.value = "读取窗口失败：${error.javaClass.simpleName}"
            return
        }
        if (root == null) {
            _report.value = "当前窗口没有可读取的根节点"
            return
        }
        try {
            if (root.packageName?.toString() != WeChatObserver.WECHAT_PACKAGE) return
            val tree = BoundedTreeWalker.collect(
                root = root,
                childCount = { it.childCount },
                childAt = { node, i -> node.getChild(i) },
                describe = { node, index, parent, depth -> describe(node, index, parent, depth) },
                release = ::release,
            )
            val document = JSONObject()
                .put("schemaVersion", 1)
                .put("capturedAtMillis", System.currentTimeMillis())
                .put("packageName", WeChatObserver.WECHAT_PACKAGE)
                .put("sessionId", session)
                .put("maxNodes", 200).put("maxDepth", 12)
                .put("nodeCount", tree.values.size)
                .put("truncated", tree.truncated)
                .put("nodes", JSONArray(tree.values))
                .put("events", JSONArray(WeChatObserver.entries.value.map { event ->
                    JSONObject().put("id", event.id).put("recordedAtMillis", event.recordedAtMillis)
                        .put("eventType", event.eventType).put("className", event.className)
                        .put("text", event.text).put("viewIdResourceName", event.viewIdResourceName)
                        .put("bounds", event.bounds)
                }))
            val directory = File(service.applicationContext.filesDir, "diagnostics")
            val nodeCount = tree.values.size
            val truncated = tree.truncated
            writeJob = scope.launch {
                var message: String
                try {
                    if (!currentSession(session)) return@launch
                    check(directory.isDirectory || directory.mkdirs()) { "Cannot create diagnostics directory" }
                    val file = AtomicFile(File(directory, "wechat-tree.json"))
                    val stream = file.startWrite()
                    try {
                        stream.write(document.toString(2).toByteArray(Charsets.UTF_8))
                        if (currentSession(session)) file.finishWrite(stream) else {
                            file.failWrite(stream)
                            return@launch
                        }
                    } catch (error: Exception) {
                        file.failWrite(stream)
                        throw error
                    }
                    message = "已保存 $nodeCount 个节点" + if (truncated) "（达到采集上限）" else ""
                    Log.i("WanAnJiaoDaNodes", "snapshot saved: nodes=$nodeCount truncated=$truncated")
                } catch (error: Exception) {
                    message = "保存失败：${error.javaClass.simpleName}"
                    Log.w("WanAnJiaoDaNodes", "snapshot export failed: ${error.javaClass.simpleName}")
                }
                withContext(Dispatchers.Main) {
                    if (currentSession(session)) _report.value = message
                }
            }
        } catch (error: Exception) {
            _report.value = "采集失败：${error.javaClass.simpleName}"
        } finally {
            release(root)
        }
    }

    private fun currentSession(session: Long) =
        WeChatObserver.observing.value && WeChatObserver.sessionId == session

    private fun describe(node: AccessibilityNodeInfo, index: Int, parent: Int, depth: Int): JSONObject {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return JSONObject().put("index", index).put("parentIndex", parent).put("depth", depth)
            .put("packageName", node.packageName?.toString().orEmpty().take(160))
            .put("className", node.className?.toString().orEmpty().take(160))
            .put("text", if (node.isPassword) "[密码字段]" else node.text?.toString().orEmpty().take(240))
            .put("contentDescription", if (node.isPassword) "" else node.contentDescription?.toString().orEmpty().take(240))
            .put("viewId", node.viewIdResourceName.orEmpty().take(160))
            .put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
            .put("clickable", node.isClickable).put("enabled", node.isEnabled)
            .put("visibleToUser", node.isVisibleToUser).put("childCount", node.childCount)
    }

    private fun release(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }
}

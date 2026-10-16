package com.wananjiaoda.assistant.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wananjiaoda.assistant.automation.WeChatEventLog
import com.wananjiaoda.assistant.automation.SceneTestUiState
import com.wananjiaoda.assistant.automation.NavigationUiState
import android.os.Build
import com.wananjiaoda.assistant.domain.model.CheckInTimeZone
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun WeChatObserverScreen(
    serviceConnected: Boolean,
    observing: Boolean,
    detected: Boolean,
    events: List<WeChatEventLog>,
    nodeCaptureReport: String,
    visionReport: String,
    sceneTest: SceneTestUiState,
    onStartSceneTest: () -> Unit,
    navigationTest: NavigationUiState,
    onStartNavigationTest: () -> Unit,
    onStartNavigationOnly: () -> Unit,
    onProbeLaunch: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("微信观察测试", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            when {
                !serviceConnected -> "无障碍服务未连接"
                !observing -> "观察模式未开启"
                detected -> "微信已检测"
                else -> "微信未打开"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!serviceConnected) {
            OutlinedButton(onClick = onOpenAccessibilitySettings) { Text("打开无障碍设置") }
            Text("服务尚未连接。即使系统开关显示开启，也请关闭“晚安交大助手权限服务”后重新开启，再返回本页。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = if (observing) onStop else onStart) {
                Text(if (observing) "停止观察" else "开始观察")
            }
            OutlinedButton(onClick = onClear, enabled = events.isNotEmpty()) { Text("清空日志") }
        }
        Text(
            "开始观察后，请打开微信页面，再返回查看事件。观察两分钟后自动停止；退出此页也会停止并清空记录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onProbeLaunch, enabled = !navigationTest.running && !sceneTest.running) {
            Text("仅打开普通微信（不读画面）")
        }
        OutlinedButton(onClick = if (serviceConnected) onStartNavigationOnly else onOpenAccessibilitySettings,
            enabled = !navigationTest.running && !sceneTest.running && Build.VERSION.SDK_INT >= 30) {
            Text(if (serviceConnected) "仅测试打开路径（不打卡）" else "恢复无障碍服务以测试")
        }
        OutlinedButton(onClick = if (serviceConnected) onStartNavigationTest else onOpenAccessibilitySettings,
            enabled = !navigationTest.running && !sceneTest.running && Build.VERSION.SDK_INT >= 30) {
            Text(if (serviceConnected) "自动进入微信并测试签到" else "恢复无障碍服务以测试")
        }
        Text("保持手机解锁，使用本机普通微信。自动选择完整名称“晚安交大地学人”，进入签到页后真实测试；今天已打卡则跳过。",
            style = MaterialTheme.typography.bodySmall)
        Text(navigationTest.message, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onStartSceneTest, enabled = serviceConnected && !navigationTest.running && !sceneTest.running && Build.VERSION.SDK_INT >= 30) {
            Text("单次测试晚安交大签到")
        }
        Text("此测试会真实点击一次。启动后仅打开“晚安交大地学人”签到页；90 秒内核对当天记录并保存结果。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(sceneTest.message, style = MaterialTheme.typography.bodySmall)
        Text("最近 ${events.size}/100 条 · 仅保存在内存 · 北京时间 UTC+8",
            style = MaterialTheme.typography.bodySmall)
        Text(nodeCaptureReport, style = MaterialTheme.typography.bodySmall)
        Text(visionReport, style = MaterialTheme.typography.bodySmall)
        Text("普通观察每个窗口最多识别三次；自动测试持续采样。仅测试打开路径到达签到页就结束，不提交。模型在手机端运行，截图不保存。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("观察时每 1.5 秒最多采集一次，最多 200 个节点。最新节点和事件保存到本机 diagnostics/wechat-tree.json，停止后保留文件。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (events.isEmpty()) {
            Text("暂无微信事件", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events.asReversed(), key = { it.id }) { event -> WeChatEventCard(event) }
            }
        }
    }
}

private val eventTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

@Composable
private fun WeChatEventCard(event: WeChatEventLog) {
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(Instant.ofEpochMilli(event.recordedAtMillis)
                .atZone(CheckInTimeZone.zoneId).format(eventTimeFormat),
                style = MaterialTheme.typography.labelLarge)
            Text("packageName=${event.packageName}")
            Text("eventType=${event.eventType}")
            Text("className=${event.className.ifEmpty { "未提供" }}")
            Text("text=${event.text.ifEmpty { "未提供" }}")
            Text("viewIdResourceName=${event.viewIdResourceName}")
            Text("bounds=${event.bounds}")
        }
    }
}


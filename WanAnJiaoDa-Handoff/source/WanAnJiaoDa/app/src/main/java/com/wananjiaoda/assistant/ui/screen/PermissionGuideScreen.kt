package com.wananjiaoda.assistant.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wananjiaoda.assistant.permission.PermissionState
import com.wananjiaoda.assistant.ui.component.ToolGlyph
import com.wananjiaoda.assistant.ui.component.ToolGlyphKind
import com.wananjiaoda.assistant.ui.theme.SuccessGreen
import com.wananjiaoda.assistant.ui.theme.WarningAmber

@Composable
fun PermissionGuideScreen(state: PermissionState?, firstRun: Boolean, onNotificationSettings: () -> Unit,
    onAccessibilitySettings: () -> Unit, onBatterySettings: () -> Unit, onContinue: () -> Unit) {
    var showAutoStartInfo by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolGlyph(ToolGlyphKind.PERMISSION)
            Column {
                Text("配置检查", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("晚安交大助手运行环境", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("状态会在返回应用时刷新。仅列出本机设置，不会执行签到或自动操作。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PermissionRow("通知权限", when {
            state == null -> "正在检查"
            !state.notificationsRequired -> "此系统版本无需授权"
            state.notificationsGranted -> "已开启"
            else -> "待开启"
        }, state == null, state?.notificationsGranted == true || state?.notificationsRequired == false,
            "查看设置", onNotificationSettings)
        PermissionRow("无障碍服务", when (state?.accessibilityEnabled) {
            true -> "已开启"
            false -> "待开启"
            null -> "正在检查"
        }, state == null, state?.accessibilityEnabled == true,
            if (state?.accessibilityEnabled == true) null else "去开启", onAccessibilitySettings)
        PermissionRow("电池优化", when (state?.ignoringBatteryOptimizations) {
            true -> "已忽略电池优化"
            false -> "建议关闭限制"
            null -> "正在检查"
        }, state == null, state?.ignoringBatteryOptimizations == true,
            if (state?.ignoringBatteryOptimizations == true) null else "查看设置", onBatterySettings,
            suggested = state?.ignoringBatteryOptimizations == false)
        PermissionRow("自启动", "请按设备系统说明检查", false, false, "查看说明", { showAutoStartInfo = true }, suggested = true)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(if (firstRun) "进入应用" else "返回设置") }
    }
    if (showAutoStartInfo) AlertDialog(onDismissRequest = { showAutoStartInfo = false }, title = { Text("自启动说明") },
        text = { Text("不同设备的入口不同。可在系统设置中查找“自启动”“应用启动”或“后台运行”，按需允许晚安交大助手。") },
        confirmButton = { TextButton(onClick = { showAutoStartInfo = false }) { Text("知道了") } })
}

@Composable
private fun PermissionRow(title: String, status: String, loading: Boolean, complete: Boolean,
    actionLabel: String?, onAction: () -> Unit, suggested: Boolean = false) {
    val accent = when { complete -> SuccessGreen; suggested -> WarningAmber; else -> MaterialTheme.colorScheme.error }
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (loading) "…" else if (complete) "✓" else if (suggested) "i" else "!",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}


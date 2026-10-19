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
import com.wananjiaoda.assistant.permission.accessibilityStatusText
import com.wananjiaoda.assistant.service.AccessibilityEventLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AccessibilityTestScreen(
    accessibilityEnabled: Boolean?,
    serviceConnected: Boolean,
    logs: List<AccessibilityEventLog>,
    onBack: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("无障碍事件测试", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            accessibilityStatusText(accessibilityEnabled, serviceConnected),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!serviceConnected) {
            OutlinedButton(onClick = onOpenAccessibilitySettings) { Text("打开无障碍设置") }
        }
        OutlinedButton(onClick = onOpenSystemSettings) { Text("打开系统设置以产生测试事件") }
        Text("最近 ${logs.size}/50 条 · 仅保存在内存，服务关闭后清空",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (logs.isEmpty()) {
            Text("暂无事件。可打开系统设置并返回应用查看。",
                style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs.asReversed(), key = { it.id }) { log -> EventLogCard(log) }
            }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
private fun EventLogCard(log: AccessibilityEventLog) {
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(Instant.ofEpochMilli(log.recordedAtMillis).atZone(ZoneId.systemDefault())
                .format(timeFormatter), style = MaterialTheme.typography.labelLarge)
            if (log.packageName.isNotEmpty()) Text("package=${log.packageName}")
            Text("event=${log.eventType}")
            if (log.className.isNotEmpty()) Text("class=${log.className}")
            if (log.text.isNotEmpty()) Text("text=${log.text}")
        }
    }
}


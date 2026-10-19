package com.wananjiaoda.assistant.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wananjiaoda.assistant.service.ConnectionProtectionState

@Composable
fun ConnectionProtectionCard(state: ConnectionProtectionState, onEnabledChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("保持服务连接", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
            }
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            Text("开启后显示常驻通知，降低后台清理风险，会占用少量系统资源。空闲保护不读取屏幕、不执行签到。", style = MaterialTheme.typography.bodySmall)
            Text("请保持通知开启。系统强制停止后可能需要手动打开助手；若仍未连接，可在“配置检查”中查看状态并处理。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

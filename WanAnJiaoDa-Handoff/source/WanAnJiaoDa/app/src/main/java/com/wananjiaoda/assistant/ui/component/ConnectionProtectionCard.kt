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
            Text("开启常驻通知，降低后台回收风险，会占用少量系统资源。空闲保护不读取屏幕、不唤醒设备、不执行签到。", style = MaterialTheme.typography.bodySmall)
            Text("清理器或系统强制停止后可能需要手动打开助手；保护不会自动开启或重连无障碍。未连接时请到系统设置检查；通知关闭时无法运行保护。", style = MaterialTheme.typography.bodySmall)
        }
    }
}


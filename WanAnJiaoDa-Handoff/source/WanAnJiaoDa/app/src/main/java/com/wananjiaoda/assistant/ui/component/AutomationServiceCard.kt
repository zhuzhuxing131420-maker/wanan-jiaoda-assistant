package com.wananjiaoda.assistant.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AutomationServiceCard(accessibilityEnabled: Boolean?, onTest: () -> Unit, onWeChatObserver: () -> Unit) {
    SettingCard("自动化服务") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("无障碍状态", style = MaterialTheme.typography.bodyMedium)
            Text(
                when (accessibilityEnabled) {
                    true -> "✓ 已开启"
                    false -> "× 未开启"
                    null -> "检查中"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        OutlinedButton(onClick = onTest) { Text("测试无障碍") }
        OutlinedButton(onClick = onWeChatObserver) { Text("微信观察测试") }
    }
}

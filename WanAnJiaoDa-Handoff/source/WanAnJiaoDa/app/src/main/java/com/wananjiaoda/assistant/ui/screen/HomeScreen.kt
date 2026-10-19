package com.wananjiaoda.assistant.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wananjiaoda.assistant.permission.PermissionState
import com.wananjiaoda.assistant.ui.component.StatusCard
import com.wananjiaoda.assistant.ui.component.TianyiHeader
import com.wananjiaoda.assistant.ui.component.ToolGlyph
import com.wananjiaoda.assistant.ui.component.ToolGlyphKind
import com.wananjiaoda.assistant.ui.viewmodel.HomeUiState

@Composable
fun HomeScreen(state: HomeUiState, permissionState: PermissionState? = null, onPermissionGuide: () -> Unit = {}, modifier: Modifier = Modifier,
    onTestCheckIn: () -> Unit = {}) {
    val needsAttention = permissionState?.let { (it.notificationsRequired && !it.notificationsGranted) || !it.accessibilityConnected } == true
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TianyiHeader()
        if (needsAttention) {
            Surface(onClick = onPermissionGuide, shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToolGlyph(ToolGlyphKind.PERMISSION)
                    Column(Modifier.weight(1f)) {
                        Text("部分配置待检查", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(permissionState.accessibilityStatusText, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        StatusCard(state.todayStatus, state.nextCheckIn, onTestClick = onTestCheckIn)
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToolGlyph(ToolGlyphKind.STATS)
                    Text("签到统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Statistic("执行记录", state.total, Modifier.weight(1f))
                    Statistic("成功", state.success, Modifier.weight(1f))
                    Statistic("失败", state.failure, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Statistic(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}



package com.wananjiaoda.assistant.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wananjiaoda.assistant.ui.theme.FailureRed
import com.wananjiaoda.assistant.ui.theme.SuccessGreen
import com.wananjiaoda.assistant.ui.theme.WarningAmber

enum class CheckInVisualState(val label: String, val mark: String) {
    WAITING("未执行", "–"), SUCCESS("已完成", "✓"), FAILED("失败", "!"), SKIPPED("已跳过", "↷")
}

@Composable
fun CheckInStateBadge(state: CheckInVisualState, modifier: Modifier = Modifier) {
    val foreground = when (state) {
        CheckInVisualState.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
        CheckInVisualState.SUCCESS -> SuccessGreen
        CheckInVisualState.FAILED -> FailureRed
        CheckInVisualState.SKIPPED -> WarningAmber
    }
    val background = when (state) {
        CheckInVisualState.WAITING -> MaterialTheme.colorScheme.surfaceVariant
        CheckInVisualState.SUCCESS -> SuccessGreen.copy(alpha = .12f)
        CheckInVisualState.FAILED -> FailureRed.copy(alpha = .12f)
        CheckInVisualState.SKIPPED -> WarningAmber.copy(alpha = .14f)
    }
    Row(modifier.background(background, CircleShape).padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(state.mark, color = foreground, fontWeight = FontWeight.Bold)
        Text(state.label, color = foreground, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

fun checkInVisualState(status: String): CheckInVisualState = when (status.lowercase()) {
    "已完成", "success" -> CheckInVisualState.SUCCESS
    "失败", "failure", "failed" -> CheckInVisualState.FAILED
    "已跳过", "跳过", "skipped" -> CheckInVisualState.SKIPPED
    else -> CheckInVisualState.WAITING
}

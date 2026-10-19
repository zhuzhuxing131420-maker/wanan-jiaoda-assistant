package com.wananjiaoda.assistant.ui.screen

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wananjiaoda.assistant.domain.model.CheckInMode
import com.wananjiaoda.assistant.domain.model.CheckInSettings
import com.wananjiaoda.assistant.ui.component.AutomationServiceCard
import com.wananjiaoda.assistant.ui.component.SchedulerSettingCard
import com.wananjiaoda.assistant.ui.component.SettingCard
import com.wananjiaoda.assistant.ui.component.ToolGlyph
import com.wananjiaoda.assistant.ui.component.ToolGlyphKind

@Composable
fun SettingsScreen(
    settings: CheckInSettings,
    onTimeChange: (Int, Int) -> Unit,
    onWeekdayChange: (Int, Boolean) -> Unit,
    onAutoEnabledChange: (Boolean) -> Unit,
    onModeChange: (CheckInMode) -> Unit,
    nextTaskText: String,
    exactAlarmAllowed: Boolean,
    onExactAlarmPermissionRequest: () -> Unit,
    onTestTask: () -> Unit,
    onPermissionGuide: () -> Unit,
    accessibilityEnabled: Boolean?,
    accessibilityConnected: Boolean,
    onAccessibilityTest: () -> Unit,
    onWeChatObserver: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolGlyph(ToolGlyphKind.SETTINGS)
            Column {
                Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("偏好设置保存在本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingCard("签到时间") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("每天计划时间", style = MaterialTheme.typography.bodyMedium)
                    Text("北京时间（UTC+8）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, hour, minute -> onTimeChange(hour, minute) }, settings.hour, settings.minute, true).show()
                }) { Text("%02d:%02d".format(settings.hour, settings.minute)) }
            }
        }
        SettingCard("签到星期") {
            listOf((1..4).toList(), (5..7).toList()).forEach { rowDays ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowDays.forEach { day ->
                        FilterChip(selected = day in settings.weekdays,
                            onClick = { onWeekdayChange(day, day !in settings.weekdays) },
                            label = { Text(weekdayNames[day - 1]) })
                    }
                }
            }
        }
        SettingCard("自动签到") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (settings.autoEnabled) "已开启" else "已关闭", fontWeight = FontWeight.Medium)
                    Text("根据时间和星期安排本地任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.autoEnabled, onCheckedChange = onAutoEnabledChange)
            }
        }
        SettingCard("签到模式") {
            ModeOption("自动模式", settings.mode == CheckInMode.AUTO) { onModeChange(CheckInMode.AUTO) }
            ModeOption("半自动模式", settings.mode == CheckInMode.SEMI_AUTO) { onModeChange(CheckInMode.SEMI_AUTO) }
        }
        SchedulerSettingCard(nextTaskText, exactAlarmAllowed, onExactAlarmPermissionRequest, onTestTask)
        SettingCard("权限与运行环境") {
            Text("查看通知、无障碍和电池优化状态。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onPermissionGuide) { Text("打开配置检查") }
        }
        AutomationServiceCard(accessibilityEnabled, accessibilityConnected, onAccessibilityTest, onWeChatObserver)
    }
}

private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

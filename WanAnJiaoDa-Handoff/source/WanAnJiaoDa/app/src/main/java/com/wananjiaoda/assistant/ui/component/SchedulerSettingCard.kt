package com.wananjiaoda.assistant.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wananjiaoda.assistant.scheduler.ScheduledExecution
import androidx.compose.ui.unit.dp

@Composable
fun SchedulerSettingCard(
    nextTaskText: String,
    exactAlarmAllowed: Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onTestTask: () -> Unit,
) {
    val context = LocalContext.current
    val executionStatus by ScheduledExecution.status.collectAsStateWithLifecycle()
    val scheduledRunning by ScheduledExecution.running.collectAsStateWithLifecycle()
    SettingCard("本地任务") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("下一次任务：" + nextTaskText)
            Text("最近定时执行：" + executionStatus)
            Text(
                "按北京时间（UTC+8）安排，系统会显示下一次任务的闹钟提醒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!exactAlarmAllowed) {
                Text(
                    "精确任务需要系统允许“闹钟和提醒”。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onRequestExactAlarmPermission) {
                    Text("允许精确闹钟")
                }
            }
            OutlinedButton(onClick = onTestTask) {
                Text("立即触发任务测试")
            }
            OutlinedButton(onClick = { ScheduledExecution.scheduleTest(context) }, enabled = exactAlarmAllowed && !scheduledRunning) {
                Text("1分钟后测试定时导航（不打卡）")
            }
            OutlinedButton(onClick = { ScheduledExecution.cancelTest(context) }) { Text("取消待执行的定时测试") }
            Text("到点时锁屏将等待解锁；正式任务最晚等到当天22:00。定时导航测试只打开页面，不提交，触发后10分钟到期。",
                style = MaterialTheme.typography.bodySmall)
            Text(
                "“立即触发”只记录接收时间；“1分钟后”会自动打开微信并导航，全程不打卡。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

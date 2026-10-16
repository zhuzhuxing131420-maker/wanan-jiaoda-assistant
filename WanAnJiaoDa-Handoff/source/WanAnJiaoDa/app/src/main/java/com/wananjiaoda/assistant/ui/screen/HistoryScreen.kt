package com.wananjiaoda.assistant.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wananjiaoda.assistant.R
import com.wananjiaoda.assistant.domain.model.CheckInRecord
import com.wananjiaoda.assistant.domain.model.CheckInTimeZone
import com.wananjiaoda.assistant.ui.component.CheckInStateBadge
import com.wananjiaoda.assistant.ui.component.ToolGlyph
import com.wananjiaoda.assistant.ui.component.ToolGlyphKind
import com.wananjiaoda.assistant.ui.component.checkInVisualState
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(records: List<CheckInRecord>) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolGlyph(ToolGlyphKind.HISTORY)
            Column {
                Text("历史记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("本机保存的签到任务记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Image(painterResource(R.drawable.tianyi_chibi), contentDescription = "洛天依 Q 版空状态插画",
                            modifier = Modifier.size(76.dp), contentScale = ContentScale.Fit)
                        Text("暂无签到记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("任务执行后，记录会显示在这里。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(records, key = { it.id }) { record ->
                    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(record.outcome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                CheckInStateBadge(checkInVisualState(record.outcome))
                            }
                            Text(formatRecordTime(record.occurredAtMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            record.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRecordTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(CheckInTimeZone.zoneId)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))


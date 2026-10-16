package com.wananjiaoda.assistant.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wananjiaoda.assistant.R

@Composable
fun TianyiHeader(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(if (compact) 44.dp else 60.dp)) {
            Image(painterResource(R.drawable.tianyi_chibi), contentDescription = "洛天依 Q 版头像",
                modifier = Modifier.padding(3.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("晚安交大助手", style = if (compact) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(if (compact) "本地校园工具" else "轻量 · 本地运行 · 北京时间 UTC+8",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

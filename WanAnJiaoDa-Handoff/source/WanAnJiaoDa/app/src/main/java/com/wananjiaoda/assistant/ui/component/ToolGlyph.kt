package com.wananjiaoda.assistant.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ToolGlyphKind { HOME, SETTINGS, HISTORY, TIME, CALENDAR, MODE, PERMISSION, SERVICE, STATS }

@Composable
fun ToolGlyph(kind: ToolGlyphKind, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * .085f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1, y1), Offset(x2, y2), stroke, StrokeCap.Round)
        when (kind) {
            ToolGlyphKind.TIME, ToolGlyphKind.HISTORY -> {
                drawCircle(color, radius = w * .38f, style = Stroke(stroke))
                line(w*.5f,h*.5f,w*.5f,h*.27f); line(w*.5f,h*.5f,w*.68f,h*.61f)
            }
            ToolGlyphKind.CALENDAR -> {
                drawRoundRect(color, topLeft=Offset(w*.15f,h*.2f), size=Size(w*.7f,h*.66f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.08f), style=Stroke(stroke))
                line(w*.15f,h*.4f,w*.85f,h*.4f); line(w*.34f,h*.13f,w*.34f,h*.29f); line(w*.66f,h*.13f,w*.66f,h*.29f)
                drawCircle(color, w*.035f, Offset(w*.34f,h*.56f)); drawCircle(color, w*.035f, Offset(w*.55f,h*.56f)); drawCircle(color, w*.035f, Offset(w*.34f,h*.72f))
            }
            ToolGlyphKind.SETTINGS, ToolGlyphKind.MODE -> {
                drawCircle(color, w*.28f, style=Stroke(stroke)); drawCircle(color, w*.08f)
                for (index in 0 until 8) {
                    val angle = index * (Math.PI / 4.0)
                    val x1 = w*.5f + kotlin.math.cos(angle).toFloat()*w*.34f
                    val y1 = h*.5f + kotlin.math.sin(angle).toFloat()*h*.34f
                    val x2 = w*.5f + kotlin.math.cos(angle).toFloat()*w*.43f
                    val y2 = h*.5f + kotlin.math.sin(angle).toFloat()*h*.43f
                    line(x1,y1,x2,y2)
                }
            }
            ToolGlyphKind.PERMISSION, ToolGlyphKind.SERVICE -> {
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(w*.5f,h*.1f); lineTo(w*.84f,h*.22f); lineTo(w*.8f,h*.58f); quadraticTo(w*.74f,h*.8f,w*.5f,h*.92f); quadraticTo(w*.26f,h*.8f,w*.2f,h*.58f); lineTo(w*.16f,h*.22f); close() }
                drawPath(p,color,style=Stroke(stroke))
                line(w*.34f,h*.51f,w*.46f,h*.63f); line(w*.46f,h*.63f,w*.69f,h*.38f)
            }
            ToolGlyphKind.STATS -> {
                line(w*.17f,h*.84f,w*.17f,h*.55f); line(w*.42f,h*.84f,w*.42f,h*.3f); line(w*.67f,h*.84f,w*.67f,h*.44f); line(w*.9f,h*.84f,w*.9f,h*.16f)
                line(w*.12f,h*.86f,w*.93f,h*.86f)
            }
            ToolGlyphKind.HOME -> {
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(w*.12f,h*.48f); lineTo(w*.5f,h*.15f); lineTo(w*.88f,h*.48f); lineTo(w*.8f,h*.48f); lineTo(w*.8f,h*.86f); lineTo(w*.58f,h*.86f); lineTo(w*.58f,h*.62f); lineTo(w*.42f,h*.62f); lineTo(w*.42f,h*.86f); lineTo(w*.2f,h*.86f); lineTo(w*.2f,h*.48f); close() }
                drawPath(p,color,style=Stroke(stroke, join=androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

@Composable
fun GlyphBadge(kind: ToolGlyphKind, modifier: Modifier = Modifier) {
    Surface(modifier.size(38.dp), shape=RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment=androidx.compose.ui.Alignment.Center) { ToolGlyph(kind, size=20.dp) }
    }
}

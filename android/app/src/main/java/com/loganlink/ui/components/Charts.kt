package com.loganlink.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loganlink.ui.theme.Ember
import com.loganlink.ui.theme.EmberDeep
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateWarning
import java.util.Locale
import kotlin.math.max

@Composable
fun ArcGauge(
    value: Double?, max: Double, label: String, unit: String, modifier: Modifier = Modifier,
    size: Dp = 168.dp, redline: Double? = null, estimated: Boolean = false, decimals: Int = 0,
) {
    val f by animateFloatAsState(((value ?: 0.0) / max).toFloat().coerceIn(0f, 1f), tween(180), label = "gauge")
    val track = MaterialTheme.colorScheme.outlineVariant
    val red = redline?.let { (it / max).toFloat() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.width * 0.06f
            val inset = sw / 2 + 4
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(track, 150f, 240f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            red?.let { drawArc(StateCritical.copy(alpha = .3f), 150f + 240f * it, 240f * (1 - it), false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round)) }
            val over = red != null && f >= red
            if (f > 0.003f) drawArc(
                Brush.sweepGradient(if (over) listOf(StateWarning, StateCritical, StateWarning) else listOf(EmberDeep, Ember, EmberDeep), center),
                150f, 240f * f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (value == null) "—" else (if (estimated) "≈" else "") + String.format(Locale.US, "%.${decimals}f", value),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = (size.value * 0.22f).sp),
            )
            Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, letterSpacing = 1.4.sp)
        }
    }
}

@Composable
fun TimeSeriesChart(
    points: List<Pair<Long, Double>>, color: Color, unit: String, modifier: Modifier = Modifier,
    height: Dp = 180.dp, decimals: Int = 1, timePattern: String = "HH:mm", guide: Double? = null,
) {
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Not enough data yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val t0 = points.first().first; val t1 = max(points.last().first, t0 + 1)
    val vMin0 = points.minOf { it.second }; val vMax0 = points.maxOf { it.second }
    val pad = ((vMax0 - vMin0) * 0.12).coerceAtLeast(0.5)
    val vMin = vMin0 - pad; val vMax = vMax0 + pad
    var sel by remember(points) { mutableStateOf<Int?>(null) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        val s = sel?.let { points.getOrNull(it) }
        Text(
            s?.let { "%.${decimals}f %s  ·  %s".format(it.second, unit, fmtDate(it.first, timePattern)) }
                ?: "%.${decimals}f – %.${decimals}f %s".format(vMin0, vMax0, unit),
            style = MaterialTheme.typography.labelMedium, color = labelColor,
        )
        Canvas(
            Modifier.fillMaxWidth().height(height)
                .pointerInput(points) {
                    detectDragGestures(onDragEnd = { sel = null }) { ch, _ ->
                        val x = ch.position.x / this.size.width
                        sel = (x * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                    }
                }
                .pointerInput(points) {
                    detectTapGestures { o -> sel = (o.x / this.size.width * (points.size - 1)).toInt().coerceIn(0, points.size - 1) }
                },
        ) {
            fun px(t: Long) = (t - t0).toFloat() / (t1 - t0) * size.width
            fun py(v: Double) = size.height - ((v - vMin) / (vMax - vMin)).toFloat() * size.height
            for (i in 0..3) {
                val y = size.height * i / 3
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            }
            guide?.let { g -> if (g in vMin..vMax) drawLine(StateWarning.copy(alpha = .7f), Offset(0f, py(g)), Offset(size.width, py(g)), 2f) }
            val line = Path(); val fill = Path()
            points.forEachIndexed { i, (t, v) ->
                val x = px(t); val y = py(v)
                if (i == 0) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { line.lineTo(x, y); fill.lineTo(x, y) }
            }
            fill.lineTo(px(points.last().first), size.height); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .28f), Color.Transparent)))
            drawPath(line, color, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            sel?.let { i ->
                val (t, v) = points[i]
                drawLine(labelColor.copy(alpha = .5f), Offset(px(t), 0f), Offset(px(t), size.height), 2f)
                drawCircle(color, 9f, Offset(px(t), py(v)))
            }
        }
    }
}

@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier.fillMaxWidth().height(36.dp)) {
    if (values.size < 2) return
    val lo = values.min(); val hi = max(values.max(), lo + 1e-6)
    Canvas(modifier) {
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = i.toFloat() / (values.size - 1) * size.width
            val y = size.height - ((v - lo) / (hi - lo)).toFloat() * size.height
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, color, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun BarChart(bars: List<Pair<String, Double>>, color: Color, modifier: Modifier = Modifier, height: Dp = 140.dp) {
    if (bars.isEmpty()) return
    val hi = max(bars.maxOf { it.second }, 1e-6)
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width / bars.size
            bars.forEachIndexed { i, (_, v) ->
                val h = (v / hi).toFloat() * size.height
                drawRoundRect(track, Offset(i * w + w * .18f, 0f), Size(w * .64f, size.height), CornerRadius(8f))
                drawRoundRect(color, Offset(i * w + w * .18f, size.height - h), Size(w * .64f, h), CornerRadius(8f))
            }
        }
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            bars.forEach { (l, _) ->
                Text(l, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = label,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: Dp = 64.dp, color: Color = Ember) {
    val f by animateFloatAsState(score / 100f, tween(700), label = "ring")
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.width * .1f
            drawArc(track, -90f, 360f, false, Offset(sw / 2, sw / 2), Size(this.size.width - sw, this.size.height - sw), style = Stroke(sw))
            drawArc(color, -90f, 360f * f, false, Offset(sw / 2, sw / 2), Size(this.size.width - sw, this.size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text("$score", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * .3f).sp))
    }
}

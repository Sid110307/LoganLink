package com.loganlink.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loganlink.R
import com.loganlink.ui.theme.Severity
import java.util.Locale

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) = Column {
    Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BackButton(onBack: (() -> Unit)?) {
    if (onBack != null) IconButton(onClick = onBack) { Icon(painterResource(R.drawable.nav_arrow), "Back", Modifier.size(24.dp)) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(), modifier.padding(top = 6.dp, start = 4.dp),
    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
internal fun Modifier.tappable(shape: Shape, onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return pressScale(source).clip(shape).clickable(source, androidx.compose.material3.ripple()) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onClick()
    }
}

@Composable
fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    actions: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.reveal().fillMaxWidth().tappable(MaterialTheme.shapes.large, onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(painterResource(it), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                actions()
                if (onClick != null) Icon(painterResource(R.drawable.nav_arrow), null,
                    Modifier.size(16.dp).rotateDeg(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

internal fun Modifier.rotateDeg(d: Float) = this.rotate(d)

@Composable
fun KpiTile(
    label: String, value: String, modifier: Modifier = Modifier, unit: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary, onClick: (() -> Unit)? = null, estimated: Boolean = false,

    deltaPct: Double? = null, upIsGood: Boolean = true,
) {
    Card(
        modifier = modifier.reveal().tappable(MaterialTheme.shapes.medium, onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                if (estimated && value != "--") Text("≈", style = MaterialTheme.typography.titleLarge, color = accent.copy(alpha = .7f))
                CountUpText(value, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), accent,
                    Modifier.weight(1f, fill = false))
                unit?.takeIf { it.isNotBlank() }?.let {
                    Text(" $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
                }
            }
            deltaPct?.takeIf { it.isFinite() && kotlin.math.abs(it) >= 1 }?.let { d ->
                val good = (d > 0) == upIsGood
                Text((if (d > 0) "▲ " else "▼ ") + "%.0f%%".format(kotlin.math.abs(d)), style = MaterialTheme.typography.labelSmall,
                    color = if (good) com.loganlink.ui.theme.StateGood else com.loganlink.ui.theme.StateWarning)
            }
        }
    }
}

private val NUMBER = Regex("""^(-?)([\d,]*\.?\d+)(.*)$""")

@Composable
fun CountUpText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, durationMs: Int = 700) {
    val match = NUMBER.matchEntire(text)
    val target = match?.let { (it.groupValues[1] + it.groupValues[2].replace(",", "")).toDoubleOrNull() }
    if (match == null || target == null || !target.isFinite() || LocalInspectionMode.current) {
        Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier); return
    }
    val digits = match.groupValues[2]
    val decimals = digits.substringAfter('.', "").length
    val grouped = ',' in digits
    val anim = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target) { anim.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing)) }
    val shown = if (anim.isRunning) anim.value.toDouble() else target
    val n = if (grouped) String.format(Locale.US, "%,.${decimals}f", shown) else String.format(Locale.US, "%.${decimals}f", shown)
    Text(n + match.groupValues[3], style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun StatusChip(label: String, color: Color, modifier: Modifier = Modifier, textColor: Color = color) =
    MorphText(label, MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), modifier.padding(vertical = 2.dp), textColor)

@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, emphasis: Color? = null) {
    Column(
        modifier.reveal().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            CountUpText(value, MaterialTheme.typography.titleMedium, emphasis ?: MaterialTheme.colorScheme.onSurface, durationMs = 320)
            unit?.let { Text(" $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f)) }
        }
    }
}

@Composable
fun AlertBanner(
    title: String, detail: String, severity: Severity, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.reveal().fillMaxWidth().background(severity.color.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(if (severity >= Severity.CheckSoon) R.drawable.warning else R.drawable.info), null,
                Modifier.size(18.dp), tint = severity.color)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(severity.label, style = MaterialTheme.typography.labelSmall, color = severity.color)
        }
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(actionLabel)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) =
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

@Composable
fun IconBadge(@DrawableRes icon: Int, tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
              bg: Color = MaterialTheme.colorScheme.primaryContainer, size: Dp = 40.dp) {
    Box(Modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(size * 0.5f), tint = tint)
    }
}

@Composable
fun NavRow(@DrawableRes icon: Int, title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val navSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val navHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        onClick = { navHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onClick() },
        modifier = Modifier.reveal().fillMaxWidth().pressScale(navSource), shape = MaterialTheme.shapes.large, interactionSource = navSource,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.invoke()
            Icon(painterResource(R.drawable.nav_arrow), null, Modifier.size(18.dp).rotateDeg(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, detail: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconBadge(icon, size = 56.dp)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "meter")
    Box(modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(f).height(6.dp).background(color, RoundedCornerShape(3.dp)))
    }
}

fun ago(t: Long, now: Long = System.currentTimeMillis()): String {
    val s = (now - t) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86_400 -> "${s / 3600} h ago"
        s < 7 * 86_400 -> "${s / 86_400} days ago"
        else -> java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(java.util.Date(t))
    }
}

fun fmtDate(t: Long, pattern: String = "EEE d MMM, HH:mm"): String =
    java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(java.util.Date(t))

fun fmtDuration(s: Long): String = if (s >= 3600) "%d h %02d min".format(s / 3600, s % 3600 / 60) else "${s / 60} min"

fun fmtRupees(paise: Long): String = "₹" + String.format(Locale.US, "%,d", paise / 100)

@Composable
fun HeroCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    container: Color = com.loganlink.ui.theme.BlockSignal,
    accent: Color = com.loganlink.ui.theme.BlockInk,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Card(
        modifier = modifier.reveal().fillMaxWidth().tappable(RoundedCornerShape(28.dp), onClick),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = com.loganlink.ui.theme.BlockInk),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
                color = accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = com.loganlink.ui.theme.BlockInk.copy(alpha = .7f)) }
            content()
        }
    }
}

@Composable
fun TextTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Column(
                Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onSelect(i)
                }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(o, style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.padding(top = 4.dp).size(width = if (on) 18.dp else 0.dp, height = 2.dp)
                    .background(com.loganlink.ui.theme.Signal, RoundedCornerShape(1.dp)))
            }
        }
    }
}

@Composable
fun SuccessDialog(title: String, message: String, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    icon = { IconBadge(R.drawable.check, size = 56.dp) },
    title = { Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
    text = { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
    confirmButton = {
        com.loganlink.ui.components.Button(onClick = onDismiss, Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
    },
)

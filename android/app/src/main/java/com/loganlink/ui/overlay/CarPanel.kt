package com.loganlink.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loganlink.R
import com.loganlink.core.VehicleHub
import com.loganlink.core.nav.MapsNav
import com.loganlink.ui.theme.BlockCharcoal
import com.loganlink.ui.theme.BlockSignal
import com.loganlink.ui.theme.LoganTheme
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateWarning
import com.loganlink.ui.theme.ThemeMode

@Composable
fun CarPanel(hub: VehicleHub, onDrag: (Float, Float) -> Unit, onClose: () -> Unit, onOpenApp: () -> Unit) {
    val f by hub.live.collectAsState()
    val nav by MapsNav.info.collectAsState()
    LoganTheme(ThemeMode.Dark) {
        Column(
            Modifier
                .widthIn(max = 260.dp)
                .background(Color(0xF00E0E0E), RoundedCornerShape(20.dp))
                .pointerInput(Unit) { detectDragGestures { ch, d -> ch.consume(); onDrag(d.x, d.y) } }
                .clickable(onClick = onOpenApp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            nav?.let { n ->
                Row(
                    Modifier.background(BlockCharcoal, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    n.arrow?.let { Image(it.asImageBitmap(), null, Modifier.size(34.dp)) }
                    if (n.arrow != null) Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(n.primary, color = BlockSignal, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        n.secondary?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        n.eta?.let { Text(it, color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(f?.speed?.let { "%.0f".format(it) } ?: "--", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 52.sp), color = Color.White)
                Text(" km/h", color = Color.White.copy(alpha = .6f), modifier = Modifier.padding(bottom = 10.dp))
                Spacer(Modifier.weight(1f))
                Icon(painterResource(R.drawable.xmark), "Close", Modifier.size(22.dp).clickable(onClick = onClose), tint = Color.White.copy(alpha = .6f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("RPM", f?.rpm?.toString() ?: "--")
                Stat("Coolant", f?.coolant?.let { "%.0f°".format(it) } ?: "--", (f?.coolant ?: 0.0) > 105)
                Stat("Battery", f?.vbat?.let { "%.1f".format(it) } ?: "--", f?.engineRunning == true && (f?.vbat ?: 14.0) < 12.8)
            }
            val alert = when {
                (f?.coolant ?: 0.0) > 105 -> "Engine overheating"
                f?.engineRunning == true && (f?.vbat ?: 14.0) < 12.8 && (f?.rpm ?: 0) > 1200 -> "Battery not charging"
                f?.milOn == true -> "Check-engine light on"
                else -> null
            }
            alert?.let { Text(it, color = if (it.startsWith("Engine")) StateCritical else StateWarning, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, warn: Boolean = false) = Column {
    Text(label, color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
    Text(value, color = if (warn) StateCritical else Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

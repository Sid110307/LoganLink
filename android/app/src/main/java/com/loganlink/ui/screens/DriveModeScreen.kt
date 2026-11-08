package com.loganlink.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import com.loganlink.core.nav.MapsNav
import com.loganlink.core.service.DriveService
import com.loganlink.core.service.OverlayControl
import com.loganlink.ui.components.OutlinedButton
import com.loganlink.ui.theme.BlockCharcoal
import com.loganlink.ui.theme.BlockSignal
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.core.protocol.Source
import com.loganlink.ui.components.ArcGauge
import com.loganlink.ui.theme.LoganTheme
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateWarning
import com.loganlink.ui.theme.ThemeMode

@Composable
fun DriveModeScreen(onExit: () -> Unit) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        val prev = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = prev ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val hub = LocalApp.current.hub
    val f by hub.live.collectAsStateWithLifecycle()
    val trip by hub.activeTrip.collectAsStateWithLifecycle()
    val nav by MapsNav.info.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LoganTheme(ThemeMode.Dark) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = {}, onLongClick = onExit).safeDrawingPadding().padding(16.dp),
        ) {
            val alert = when {
                (f?.coolant ?: 0.0) > 105 -> "ENGINE OVERHEATING, pull over safely" to StateCritical
                f?.engineRunning == true && (f?.vbat ?: 14.0) < 12.8 && (f?.rpm ?: 0) > 1200 -> "Battery not charging" to StateWarning
                f?.milOn == true -> "Check-engine light on" to StateWarning
                else -> null
            }
            alert?.let { (text, c) ->
                Text(text, Modifier.align(Alignment.TopCenter).background(c.copy(alpha = .2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp), color = c, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                Side("Coolant", f?.coolant?.let { "%.0f°".format(it) } ?: "--", "Battery", f?.vbat?.let { "%.1f V".format(it) } ?: "--")
                ArcGauge(f?.rpm?.toDouble(), 7000.0, "rpm ×1", "", size = 150.dp, redline = 5750.0)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val est = f?.speedSource == Source.Phone
                    Text((if (est) "≈" else "") + (f?.speed?.let { "%.0f".format(it) } ?: "--"),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, fontWeight = FontWeight.Light))
                    Text("km/h", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Side("Trip", trip?.let { "%.1f km".format(it.distanceKm) } ?: "--",
                    "Economy", f?.kmPerLitre?.let { "≈%.1f".format(it) } ?: "--")
            }
            nav?.let { n ->
                Row(
                    Modifier.align(Alignment.TopStart).background(BlockCharcoal, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    n.arrow?.let { Image(it.asImageBitmap(), null, Modifier.size(40.dp)) }
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(n.primary, color = BlockSignal, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        listOfNotNull(n.secondary, n.eta).joinToString("  ·  ").takeIf { it.isNotEmpty() }?.let { Text(it, color = Color.White) }
                    }
                }
            }
            OutlinedButton(onClick = {
                if (!Settings.canDrawOverlays(ctx)) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                } else {
                    OverlayControl.pinned.value = true
                    DriveService.start(ctx)
                    ctx.packageManager.getLaunchIntentForPackage(MapsNav.MAPS_PACKAGE)?.let { ctx.startActivity(it) }
                }
            }, modifier = Modifier.align(Alignment.BottomEnd)) { Text("Float over Maps") }
            Text("Long-press to exit", Modifier.align(Alignment.BottomCenter), color = Color.Gray,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Side(l1: String, v1: String, l2: String, v2: String) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        listOf(l1 to v1, l2 to v2).forEach { (l, v) ->
            Column {
                Text(l.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Normal))
            }
        }
    }
}

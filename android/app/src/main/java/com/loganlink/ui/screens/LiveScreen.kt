package com.loganlink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.loganlink.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.loganlink.ui.components.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.link.LinkState
import com.loganlink.core.protocol.LiveFrame
import com.loganlink.core.protocol.Source
import com.loganlink.ui.Routes
import com.loganlink.ui.components.ArcGauge
import com.loganlink.ui.components.EmptyState
import com.loganlink.ui.components.MetricTile
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.SectionLabel
import com.loganlink.ui.components.Sparkline
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.theme.Signal
import com.loganlink.ui.theme.StateConnected
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateInfo
import com.loganlink.ui.theme.StateWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(go: (String) -> Unit) {
    val hub = LocalApp.current.hub
    val state by hub.state.collectAsStateWithLifecycle()
    val live by hub.live.collectAsStateWithLifecycle()
    val history by hub.history.collectAsStateWithLifecycle()
    val rssi by hub.rssi.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(
            title = { ScreenTitle("Live", if (live == null) state.label else null) },
            actions = { OutlinedButton(onClick = { go(Routes.DRIVE) }, modifier = Modifier.padding(end = 8.dp)) { Text("Drive mode") } },
        )
    }) { pad ->
        val f = live
        if (state != LinkState.Ready || f == null) {
            Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center) {
                EmptyState(R.drawable.bluetooth, state.label,
                    "Start the engine to see live data.") {
                    if (state == LinkState.Idle || state == LinkState.BluetoothOff) Button(onClick = { hub.reconnect() }) { Text("Try again") }
                }
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ArcGauge(f.speed, 160.0, "Speed", "km/h", estimated = f.speedSource == Source.Phone || f.speedSource == Source.Gnss)
                ArcGauge(f.rpm?.toDouble(), 7000.0, "Engine", "rpm", redline = 5750.0, estimated = f.rpmSource == Source.Estimated)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!f.ecuOnline) StatusChip("No engine data", StateInfo)
                if (f.milOn) StatusChip("Check engine · ${f.dtcCount}", StateCritical)
            }

            SectionLabel("Engine")
            Grid(listOf(
                Tile("Coolant", f.coolant, "°C", 0, warn = f.coolant?.let { it > 105 } ?: false),
                Tile("Intake air", f.iat, "°C", 0),
                Tile("Manifold", f.map, "kPa", 0),
                Tile("Throttle", f.throttle, "%", 0),
                Tile("Load", f.load?.toDouble(), "%", 0),
                Tile("Advance", f.advance?.toDouble(), "°", 0),
                Tile("Short trim", f.stft?.toDouble(), "%", 0, warn = (f.stft ?: 0) !in -15..15),
                Tile("Long trim", f.ltft?.toDouble(), "%", 0, warn = (f.ltft ?: 0) !in -10..10),
            ))

            SectionLabel("Electrical & economy")
            Grid(listOf(
                Tile("Battery", f.vbat, "V", 2, warn = (f.vbat ?: 13.0) < 12.0 || (f.vbat ?: 13.0) > 14.9),
                Tile("Fuel rate", f.fuelRate, "L/h", 2, est = true),
                Tile("Economy", f.kmPerLitre, "km/L", 1, est = true),
                Tile("Fuel level", f.fuelLevel, "%", 0, est = true),
            ))

            Panel("Last minute", subtitle = "Speed · RPM · battery") {
                Sparkline(history.mapNotNull { it.speed }, Signal)
                Sparkline(history.mapNotNull { it.rpm?.toDouble() }, StateWarning)
                Sparkline(history.mapNotNull { it.vbat }, StateConnected)
            }

            SectionLabel("Motion")
            Grid(listOf(
                Tile("Accel / brake", f.accelX, "g", 2, warn = (f.accelX ?: 0.0) < -0.35),
                Tile("Cornering", f.accelY, "g", 2),
                Tile("Trip", f.tripM / 1000.0, "km", 1),
            ))
        }
    }
}

private data class Tile(val label: String, val v: Double?, val unit: String, val dec: Int, val warn: Boolean = false, val est: Boolean = false)

@Composable
private fun Grid(tiles: List<Tile>) {
    tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { t ->
                MetricTile(
                    t.label,
                    t.v?.let { (if (t.est) "≈" else "") + "%.${t.dec}f".format(it) } ?: "—",
                    Modifier.weight(1f), t.unit, if (t.warn) StateCritical else null,
                )
            }
            if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
    }
}

package com.loganlink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.link.LinkState
import com.loganlink.ui.components.AlertBanner
import com.loganlink.ui.components.BackButton
import com.loganlink.ui.components.MetricTile
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.SectionLabel
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.components.TimeSeriesChart
import com.loganlink.ui.theme.Signal
import com.loganlink.ui.theme.Severity
import com.loganlink.ui.theme.StateConnected
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateGood
import com.loganlink.ui.theme.StateIdle
import com.loganlink.ui.theme.StateInfo
import com.loganlink.ui.theme.StateWarning
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val live by app.hub.live.collectAsStateWithLifecycle()
    val history by app.hub.history.collectAsStateWithLifecycle()
    val linkState by app.hub.state.collectAsStateWithLifecycle()

    val f = live
    val ecu = f?.ecuOnline == true
    val thinned = history.filterIndexed { i, _ -> i % 5 == 0 || i == history.lastIndex }
    val coolantPts = thinned.mapNotNull { fr -> fr.coolant?.let { fr.receivedAt to it } }
    val ltftPts = thinned.mapNotNull { fr -> fr.ltft?.let { fr.receivedAt to it.toDouble() } }
    val stftPts = thinned.mapNotNull { fr -> fr.stft?.let { fr.receivedAt to it.toDouble() } }
    val avgLtft = history.mapNotNull { it.ltft }.takeIf { it.isNotEmpty() }?.average()

    Scaffold(topBar = {
        TopAppBar(
            title = { ScreenTitle("Engine", "Live sensors from the engine computer") },
            navigationIcon = { BackButton(onBack) },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (linkState != LinkState.Ready) {
                AlertBanner("Not connected", "Showing the last readings.", Severity.Info)
            } else if (!ecu) {
                AlertBanner("No engine data", "Switch the ignition on.", Severity.Info)
            }

            val (warmText, warmColor) = warmup(f?.coolant)
            Panel("Temperature", icon = R.drawable.thermometer, actions = { StatusChip(warmText, warmColor) }) {
                TileRow {
                    MetricTile("Coolant", n0(f?.coolant), Modifier.weight(1f), unit = "°C", emphasis = coolantColor(f?.coolant))
                    MetricTile("Intake air", n0(f?.iat), Modifier.weight(1f), unit = "°C")
                }
            }

            Panel("Air & load", icon = R.drawable.gauge) {
                TileRow {
                    MetricTile("Manifold pressure", n0(f?.map), Modifier.weight(1f), unit = "kPa")
                    MetricTile("Throttle", n0(f?.throttle), Modifier.weight(1f), unit = "%")
                }
                TileRow {
                    MetricTile("Engine load", f?.load?.toString() ?: "—", Modifier.weight(1f), unit = "%")
                    MetricTile("Ignition advance", f?.advance?.let { String.format(Locale.US, "%+d", it) } ?: "—",
                        Modifier.weight(1f), unit = "°")
                }
            }

            Panel("Fuel trims", icon = R.drawable.fuel,
                subtitle = avgLtft?.let { "Long-term average over the last minute: ${String.format(Locale.US, "%+.1f", it)} %" }) {
                TileRow {
                    MetricTile("Short-term", f?.stft?.let { String.format(Locale.US, "%+d", it) } ?: "—", Modifier.weight(1f),
                        unit = "%", emphasis = trimColor(f?.stft?.toDouble(), 15.0))
                    MetricTile("Long-term", f?.ltft?.let { String.format(Locale.US, "%+d", it) } ?: "—", Modifier.weight(1f),
                        unit = "%", emphasis = trimColor(f?.ltft?.toDouble(), 10.0))
                }
            }

            SectionLabel("Last minute")
            Panel("Coolant temperature", icon = R.drawable.thermometer, subtitle = "Line at 105 °C") {
                TimeSeriesChart(coolantPts, Signal, "°C", decimals = 0, timePattern = "HH:mm:ss", guide = 105.0)
            }
            Panel("Long-term fuel trim", icon = R.drawable.fuel, subtitle = "Within ±10 % is normal") {
                TimeSeriesChart(ltftPts, StateConnected, "%", decimals = 0, timePattern = "HH:mm:ss")
            }
            if (stftPts.size >= 2) Panel("Short-term fuel trim", icon = R.drawable.fuel) {
                TimeSeriesChart(stftPts, StateInfo, "%", height = 120.dp, decimals = 0, timePattern = "HH:mm:ss")
            }
        }
    }
}

@Composable
private fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)


private fun n0(v: Double?) = v?.let { String.format(Locale.US, "%.0f", it) } ?: "—"

private fun coolantColor(c: Double?): Color? = when {
    c == null -> null
    c >= 105 -> StateCritical
    c >= 100 -> StateWarning
    else -> null
}

private fun trimColor(v: Double?, limit: Double): Color? = if (v != null && abs(v) > limit) StateWarning else null

private fun warmup(c: Double?): Pair<String, Color> = when {
    c == null -> "No reading" to StateIdle
    c >= 105 -> "Overheating" to StateCritical
    c >= 100 -> "Hot" to StateWarning
    c >= 80 -> "Warm" to StateGood
    c >= 60 -> "Warming up" to StateInfo
    else -> "Cold" to StateInfo
}

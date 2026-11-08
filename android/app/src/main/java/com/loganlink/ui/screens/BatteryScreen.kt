package com.loganlink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.analytics.Insights
import com.loganlink.core.link.LinkState
import com.loganlink.core.protocol.LiveFrame
import com.loganlink.ui.components.BackButton
import com.loganlink.ui.components.CountUpText
import com.loganlink.ui.components.EmptyState
import com.loganlink.ui.components.KpiTile
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScoreRing
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.components.TimeSeriesChart
import com.loganlink.ui.components.ago
import com.loganlink.ui.components.fmtDate
import com.loganlink.ui.theme.Signal
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateGood
import com.loganlink.ui.theme.StateIdle
import com.loganlink.ui.theme.StateInfo
import com.loganlink.ui.theme.StateWarning
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val live by app.hub.live.collectAsStateWithLifecycle()
    val linkState by app.hub.state.collectAsStateWithLifecycle()
    val cranks by app.repo.cranks.collectAsStateWithLifecycle(initialValue = emptyList())

    val connected = linkState == LinkState.Ready
    val vbat = live?.vbat
    val (statusText, statusColor) = chargeStatus(live, connected)
    val last = cranks.firstOrNull()

    Scaffold(topBar = {
        TopAppBar(
            title = { ScreenTitle("Battery", "12 V starter battery and charging") },
            navigationIcon = { BackButton(onBack) },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel("Right now", icon = R.drawable.flash, subtitle = if (connected) "Live from the OBD port" else "Not connected") {
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUpText(
                        vbat?.let { v2(it) } ?: "—",
                        MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                        statusColor.takeIf { vbat != null } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        Modifier.weight(1f, fill = false),
                    )
                    Text(" V", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                StatusChip(statusText, statusColor)
            }

            if (last == null) {
                EmptyState(R.drawable.battery_car, "No engine starts yet", "Health appears after the first engine start.")
            } else {
                val score = Insights.batteryScore(last)
                val restV = last.restMv / 1000.0
                Panel("Health", icon = R.drawable.heart, subtitle = "From the last start, ${ago(last.t)}") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ScoreRing(score, size = 76.dp, color = scoreColor(score))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(scoreLabel(score), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(scoreAdvice(score), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiTile("Charge (at rest)", "${Insights.socPercent(restV)}", Modifier.weight(1f), unit = "%",
                            accent = socColor(Insights.socPercent(restV)), estimated = true)
                        KpiTile("Resting", v2(restV), Modifier.weight(1f), unit = "V")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiTile("Cranking low", v2(last.minMv / 1000.0), Modifier.weight(1f), unit = "V",
                            accent = minColor(last.minMv / 1000.0))
                        KpiTile("Crank time", String.format(Locale.US, "%.1f", last.crankMs / 1000.0), Modifier.weight(1f), unit = "s")
                    }
                }

                val pts = cranks.reversed().map { it.t to it.minMv / 1000.0 }
                Panel("Cranking voltage", icon = R.drawable.analytics,
                    subtitle = "Lowest voltage during each start · line at 9.6 V") {
                    TimeSeriesChart(pts, Signal, "V", decimals = 2, timePattern = "d MMM, HH:mm", guide = 9.6)
                }

                Panel("Recent starts", icon = R.drawable.list, subtitle = "Last ${minOf(10, cranks.size)}") {
                    StartRow("When", "Rest", "Low", "Crank", header = true)
                    cranks.take(10).forEach { c ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StartRow(
                            fmtDate(c.t, "d MMM, HH:mm"),
                            v2(c.restMv / 1000.0) + " V",
                            v2(c.minMv / 1000.0) + " V",
                            "${c.crankMs} ms",
                            lowColor = minColor(c.minMv / 1000.0),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartRow(a: String, b: String, c: String, d: String, header: Boolean = false, lowColor: Color? = null) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val col = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(a, Modifier.weight(1.6f), style = style, color = col, maxLines = 1)
        Text(b, Modifier.weight(1f), style = style, color = col, textAlign = TextAlign.End, maxLines = 1)
        Text(c, Modifier.weight(1f), style = style, color = lowColor ?: col, textAlign = TextAlign.End, maxLines = 1)
        Text(d, Modifier.weight(1f), style = style, color = col, textAlign = TextAlign.End, maxLines = 1)
    }
}

private fun chargeStatus(f: LiveFrame?, connected: Boolean): Pair<String, Color> {
    val v = f?.vbat
    if (f == null || v == null) {
        return (if (connected) "No voltage reading" else "Connect to the car to see live voltage") to StateIdle
    }
    val running = f.rpm?.let { it > 400 } ?: f.engineRunning
    return when {
        v > 14.9 -> "Over-charging — have the alternator regulator checked" to StateCritical
        running && v >= 13.5 -> "Charging normally" to StateGood
        running && v >= 13.0 -> "Charging low — alternator not keeping up" to StateWarning
        running -> "Not charging — check the alternator and belt" to StateCritical
        else -> "Engine off · ≈${Insights.socPercent(v)} % charge" to StateInfo
    }
}

private fun v2(v: Double) = String.format(Locale.US, "%.2f", v)

private fun scoreColor(score: Int): Color = when {
    score >= 70 -> StateGood
    score >= 45 -> StateWarning
    else -> StateCritical
}

private fun scoreLabel(score: Int) = when {
    score >= 80 -> "Healthy"
    score >= 60 -> "Fair"
    score >= 45 -> "Weakening"
    else -> "Weak"
}

private fun scoreAdvice(score: Int) = when {
    score >= 80 -> "Starts strongly and holds its charge. Nothing to do."
    score >= 60 -> "Working fine, but showing some age. Keep an eye on the trend."
    score >= 45 -> "Struggling more than it should. Plan a replacement within a few months."
    else -> "Have the battery load-tested before a long trip."
}

private fun socColor(soc: Int): Color = when {
    soc >= 70 -> StateGood
    soc >= 45 -> StateWarning
    else -> StateCritical
}

private fun minColor(v: Double): Color = when {
    v >= 10.0 -> StateGood
    v >= 9.6 -> StateWarning
    else -> StateCritical
}

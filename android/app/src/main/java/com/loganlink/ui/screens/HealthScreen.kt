package com.loganlink.ui.screens

import com.loganlink.ui.components.fadeScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.analytics.Insight
import com.loganlink.core.analytics.Insights
import com.loganlink.core.data.ServiceItem
import com.loganlink.core.link.LinkState
import com.loganlink.ui.Routes
import com.loganlink.ui.components.AlertBanner
import com.loganlink.ui.components.IconBadge
import com.loganlink.ui.components.MeterBar
import com.loganlink.ui.components.MetricTile
import com.loganlink.ui.components.NavRow
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScoreRing
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.SectionLabel
import com.loganlink.ui.components.Sparkline
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.components.ago
import com.loganlink.ui.components.fmtRupees
import com.loganlink.ui.theme.Signal
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateGood
import com.loganlink.ui.theme.StateWarning
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(go: (String) -> Unit) {
    val app = LocalApp.current
    val dtcs by app.repo.dtcs.collectAsStateWithLifecycle(initialValue = emptyList())
    val cranks by app.repo.cranks.collectAsStateWithLifecycle(initialValue = emptyList())
    val service by app.repo.service.collectAsStateWithLifecycle(initialValue = emptyList())
    val fills by app.repo.fills.collectAsStateWithLifecycle(initialValue = emptyList())
    val history by app.hub.history.collectAsStateWithLifecycle()
    val live by app.hub.live.collectAsStateWithLifecycle()
    val linkState by app.hub.state.collectAsStateWithLifecycle()
    val vehicle by app.prefs.vehicle.collectAsStateWithLifecycle()

    val now = System.currentTimeMillis()
    val odo = vehicle.odometerKm
    val ltfts = history.mapNotNull { it.ltft }
    val avgLtft = ltfts.takeIf { it.size >= 20 }?.average()
    val insights: List<Insight> = listOfNotNull(
        Insights.dtcInsight(dtcs),
        Insights.batteryInsight(cranks),
        Insights.trimInsight(avgLtft),
        Insights.serviceInsight(service, odo, now),
    )
    val active = dtcs.filter { it.cleared == null }

    Scaffold(topBar = {
        TopAppBar(title = { ScreenTitle("Health", vehicle.nickname.takeIf { it.isNotBlank() }) })
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).fadeScroll().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (insights.isEmpty()) AllGoodCard()
            else insights.forEach { i ->
                val route = routeFor(i.route)
                AlertBanner(
                    title = i.title,
                    detail = "${i.detail}\n${i.action}",
                    severity = i.severity,
                    actionLabel = if (route != null) "Open" else null,
                    onAction = route?.let { r -> { go(r) } },
                )
            }

            SectionLabel("Systems")

            val mil = live?.milOn == true
            NavRow(
                icon = R.drawable.engine,
                title = "Diagnostics",
                subtitle = when {
                    active.isNotEmpty() -> "${active.size} active fault code${if (active.size > 1) "s" else ""}" +
                        (if (mil) " · check-engine light on" else "")
                    mil -> "Check-engine light is on — read codes"
                    else -> "No active fault codes" + (dtcs.firstOrNull()?.let { " · last seen ${ago(it.lastSeen)}" } ?: "")
                },
                trailing = {
                    StatusChip(
                        if (active.isEmpty() && !mil) "Clear" else if (mil) "MIL on" else "${active.size}",
                        if (active.isEmpty() && !mil) StateGood else if (mil) StateCritical else StateWarning,
                        Modifier.padding(end = 8.dp),
                    )
                },
                onClick = { go(Routes.DIAG) },
            )

            val lastCrank = cranks.firstOrNull()
            Panel("Battery", icon = R.drawable.battery_car, subtitle = lastCrank?.let { "Last start ${ago(it.t)}" }
                ?: "No engine starts recorded yet", onClick = { go(Routes.BATTERY) }) {
                if (lastCrank != null) {
                    val score = Insights.batteryScore(lastCrank)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ScoreRing(score, size = 60.dp, color = scoreColor(score))
                        Column(Modifier.weight(1f)) {
                            Text(scoreLabel(score), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Lowest voltage while cranking: ${v2(lastCrank.minMv / 1000.0)} V",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val mins = cranks.take(30).reversed().map { it.minMv / 1000.0 }
                    if (mins.size >= 2) {
                        Sparkline(mins, Signal, Modifier.fillMaxWidth().height(36.dp))
                        Text("Cranking voltage, last ${mins.size} starts", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Appears after the first engine start.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val f = live
            Panel("Engine", icon = R.drawable.thermometer,
                subtitle = when {
                    linkState != LinkState.Ready -> "Connect to the car for live engine data"
                    f == null || !f.ecuOnline -> "Engine computer not responding (ignition off?)"
                    else -> "Live from the engine computer"
                },
                onClick = { go(Routes.ENGINE) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val c = f?.coolant
                    MetricTile("Coolant", c?.let { String.format(Locale.US, "%.0f", it) } ?: "—", Modifier.weight(1f), unit = "°C",
                        emphasis = when { c == null -> null; c >= 105 -> StateCritical; c >= 100 -> StateWarning; else -> null })
                    val lt = avgLtft ?: f?.ltft?.toDouble()
                    MetricTile("Long-term fuel trim", lt?.let { String.format(Locale.US, "%+.0f", it) } ?: "—", Modifier.weight(1f),
                        unit = "%", emphasis = if (lt != null && abs(lt) > 10) StateWarning else null)
                }
            }

            val next = service.maxByOrNull { it.used(odo, now) }
            Panel("Maintenance", icon = R.drawable.wrench,
                subtitle = if (next == null) "No service items" else "Next due: ${next.name}",
                onClick = { go(Routes.MAINT) }) {
                if (next != null) {
                    val used = next.used(odo, now)
                    MeterBar(used.toFloat(), usedColor(used))
                    Text(leftText(next, odo, now), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val lastFill = fills.firstOrNull()
            NavRow(
                icon = R.drawable.fuel,
                title = "Fuel log",
                subtitle = lastFill?.let {
                    "Last fill ${ago(it.t)} · ${String.format(Locale.US, "%.1f", it.litres)} L · ${fmtRupees(it.pricePaise)}"
                } ?: "Log fill-ups to track real-world mileage and cost",
                onClick = { go(Routes.FUEL) },
            )
        }
    }
}

@Composable
private fun AllGoodCard() {
    Row(
        Modifier.fillMaxWidth().background(StateGood.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(R.drawable.check, tint = StateGood, bg = StateGood.copy(alpha = 0.18f))
        Column(Modifier.weight(1f)) {
            Text("No issues", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "No fault codes, the battery and fuel trims look healthy and nothing is due for service.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun routeFor(r: String?): String? = when (r) {
    "diagnostics" -> Routes.DIAG
    "battery" -> Routes.BATTERY
    "engine" -> Routes.ENGINE
    "maintenance" -> Routes.MAINT
    else -> null
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
    else -> "Weak — have it tested"
}

private fun usedColor(used: Double): Color = when {
    used < 0.75 -> StateGood
    used < 1.0 -> StateWarning
    else -> StateCritical
}

private fun leftText(s: ServiceItem, odo: Int, now: Long): String = listOfNotNull(
    s.kmLeft(odo)?.let { if (it < 0) String.format(Locale.US, "%,d km overdue", -it) else String.format(Locale.US, "%,d km left", it) },
    s.daysLeft(now)?.let { if (it < 0) "${-it} days overdue" else "$it days left" },
).joinToString(" · ").ifEmpty { "No interval set" }

package com.loganlink.ui.screens

import com.loganlink.ui.components.fadeScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import com.loganlink.ui.components.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.analytics.Insights
import com.loganlink.core.link.LinkState
import com.loganlink.ui.Routes
import com.loganlink.ui.components.AlertBanner
import com.loganlink.ui.components.HeroCard
import com.loganlink.ui.components.CountUpText
import com.loganlink.ui.components.KpiTile
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScoreRing
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.components.ago
import com.loganlink.ui.components.fmtDuration
import com.loganlink.ui.components.fmtRupees
import com.loganlink.ui.theme.Signal
import com.loganlink.ui.theme.StateConnected
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateGood
import com.loganlink.ui.theme.StateIdle
import com.loganlink.ui.theme.StateWarning
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(go: (String) -> Unit, onTab: (Int) -> Unit) {
    val app = LocalApp.current
    val hub = app.hub
    val state by hub.state.collectAsStateWithLifecycle()
    val live by hub.live.collectAsStateWithLifecycle()
    val vehicle by app.prefs.vehicle.collectAsStateWithLifecycle()
    val demo by app.prefs.demo.collectAsStateWithLifecycle()
    val trips by app.repo.trips.collectAsStateWithLifecycle(emptyList())
    val park by app.repo.lastPark.collectAsStateWithLifecycle(null)
    val dtcs by app.repo.dtcs.collectAsStateWithLifecycle(emptyList())
    val cranks by app.repo.cranks.collectAsStateWithLifecycle(emptyList())
    val service by app.repo.service.collectAsStateWithLifecycle(emptyList())
    val active by hub.activeTrip.collectAsStateWithLifecycle()
    val sync by hub.syncStatus.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()

    val insights = listOfNotNull(
        Insights.dtcInsight(dtcs), Insights.batteryInsight(cranks), Insights.serviceInsight(service, vehicle.odometerKm, now),
    ).sortedByDescending { it.severity.ordinal }.take(2)

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    com.loganlink.ui.components.TypewriterText(greeting, MaterialTheme.typography.headlineSmall)
                    Text(vehicle.nickname + if (demo) " · demo mode" else "", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
    }) { pad ->
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var refreshing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(refreshing, onRefresh = {
            scope.launch {
                refreshing = true
                if (state == LinkState.Ready) { hub.readDtcs(); hub.refreshInfo() } else hub.reconnect()
                refreshing = false
            }
        }, modifier = Modifier.padding(pad)) {
        Column(
            Modifier.fillMaxSize().fadeScroll().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroCard(vehicle.nickname, subtitle = listOf(vehicle.plate.ifBlank { "No plate set" }, "%,d km".format(vehicle.odometerKm)).joinToString("  ·  ")) {
                val link = when (state) {
                    LinkState.Ready -> "Connected"
                    else -> state.label
                }
                val readings = listOfNotNull(
                    live?.vbat?.let { "battery %.1f V".format(it) },
                    live?.coolant?.let { "engine %.0f °C".format(it) },
                ).joinToString(", ")
                Text(if (readings.isEmpty()) link else "$link, $readings", style = MaterialTheme.typography.bodyLarge,
                    color = com.loganlink.ui.theme.BlockInk)
                if (live?.milOn == true) Text("Check-engine light is on", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color(0xFF8A1C0A))
                sync?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = com.loganlink.ui.theme.BlockInk.copy(alpha = .7f)) }
                if (state == LinkState.Ready) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.loganlink.ui.components.Button(
                        onClick = { onTab(1) }, Modifier.weight(1f).height(48.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.loganlink.ui.theme.BlockInk,
                            contentColor = androidx.compose.ui.graphics.Color.White),
                    ) { Text("Open live", fontWeight = FontWeight.SemiBold) }
                    com.loganlink.ui.components.OutlinedButton(
                        onClick = { go(Routes.DRIVE) }, Modifier.weight(1f).height(48.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = com.loganlink.ui.theme.BlockInk),
                    ) { Text("Drive mode") }
                }
            }

            active?.let { t ->
                Panel("Driving now", subtitle = "Started ${ago(t.startMs)}", icon = R.drawable.steering, onClick = { onTab(1) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiTile("Distance", "%.1f".format(t.distanceKm), Modifier.weight(1f), "km", Signal)
                        KpiTile("Fuel", "%.2f".format(t.fuelL), Modifier.weight(1f), "L", Signal, estimated = true)
                        KpiTile("Top", "%.0f".format(t.maxSpeed), Modifier.weight(1f), "km/h", Signal)
                    }
                }
            }

            insights.forEach { i ->
                AlertBanner(i.title, i.detail, i.severity, actionLabel = "Details", onAction = {
                    go(when (i.route) { "battery" -> Routes.BATTERY; "engine" -> Routes.ENGINE; "maintenance" -> Routes.MAINT; else -> Routes.DIAG })
                })
            }

            Panel("Parked", subtitle = park?.let { "Saved ${ago(it.t)}" } ?: "Saved automatically when you switch off",
                icon = R.drawable.parking, onClick = { go(Routes.PARKING) }) {
                Text(
                    park?.let { "%.5f, %.5f  ·  ±%.0f m".format(it.lat, it.lon, it.accuracyM) } ?: "No parking spot yet",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            trips.firstOrNull()?.let { t ->
                Panel("Last trip", subtitle = ago(t.startMs), icon = R.drawable.route, onClick = { go(Routes.trip(t.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                CountUpText("%.1f".format(t.km), MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), Signal)
                                Text(" km", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp))
                            }
                            Text(
                                listOfNotNull(fmtDuration(t.durationS), t.kmPerL?.let { "%.1f km/L".format(it) },
                                    t.costPaise.takeIf { it > 0 }?.let { fmtRupees(it) }).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ScoreRing(t.score)
                    }
                }
            }

            val week = trips.filter { now - it.startMs < 7 * 86_400_000L }
            val prev = trips.filter { now - it.startMs in 7 * 86_400_000L until 14 * 86_400_000L }
            fun eco(l: List<com.loganlink.core.data.Trip>) = l.filter { it.fuelMl > 50 }.let { f -> if (f.isEmpty()) null else f.sumOf { it.km } / f.sumOf { it.litres } }
            fun pct(a: Double?, b: Double?) = if (a == null || b == null || b == 0.0) null else (a - b) / b * 100
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiTile("This week", "%.0f".format(week.sumOf { it.km }), Modifier.weight(1f), "km", Signal, onClick = { onTab(2) },
                    deltaPct = pct(week.sumOf { it.km }, prev.sumOf { it.km }), upIsGood = true)
                KpiTile("Economy", eco(week)?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f), "km/L", Signal, onClick = { onTab(2) },
                    deltaPct = pct(eco(week), eco(prev)))
                KpiTile("Spend", "%,d".format(week.sumOf { it.costPaise } / 100), Modifier.weight(1f), "₹", Signal, onClick = { go(Routes.FUEL) },
                    deltaPct = pct(week.sumOf { it.costPaise }.toDouble(), prev.sumOf { it.costPaise }.toDouble()), upIsGood = false)
            }

            service.maxByOrNull { it.used(vehicle.odometerKm, now) }?.let { s ->
                val used = s.used(vehicle.odometerKm, now)
                Panel("Next service", subtitle = s.name, icon = R.drawable.wrench, onClick = { go(Routes.MAINT) }) {
                    com.loganlink.ui.components.MeterBar(used.toFloat(),
                        if (used >= 1) StateCritical else if (used >= .75) StateWarning else StateGood)
                    Text(listOfNotNull(
                        s.kmLeft(vehicle.odometerKm)?.let { if (it < 0) "%,d km overdue".format(-it) else "%,d km left".format(it) },
                        s.daysLeft(now)?.let { if (it < 0) "${-it} days overdue" else "$it days left" },
                    ).joinToString(" or "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (insights.isEmpty() && dtcs.none { it.cleared == null }) Row(
                Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.shield), null, Modifier.size(18.dp), tint = StateGood)
                Spacer(Modifier.width(8.dp))
                Text("No faults and nothing due.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
        }
    }
}

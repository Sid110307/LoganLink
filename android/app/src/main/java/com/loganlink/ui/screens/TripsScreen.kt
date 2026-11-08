package com.loganlink.ui.screens

import androidx.compose.foundation.layout.height
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.loganlink.ui.components.TextTabs
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.ActiveTrip
import com.loganlink.core.data.Trip
import com.loganlink.ui.Routes
import com.loganlink.ui.components.BarChart
import com.loganlink.ui.components.ConfirmDialog
import com.loganlink.ui.components.EmptyState
import com.loganlink.ui.components.IconBadge
import com.loganlink.ui.components.KpiTile
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScoreRing
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.SectionLabel
import com.loganlink.ui.components.fmtDate
import com.loganlink.ui.components.fmtDuration
import com.loganlink.ui.components.fmtRupees
import com.loganlink.ui.theme.Signal
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateGood
import com.loganlink.ui.theme.StateWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class TripRange(val label: String, val days: Int?) { Week("7 days", 7), Month("30 days", 30), All("All", null) }

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(go: (String) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trips by app.repo.trips.collectAsStateWithLifecycle(initialValue = emptyList())
    val active by app.hub.activeTrip.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TripRange.Week) }
    var pendingDelete by remember { mutableStateOf<Trip?>(null) }
    var exporting by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val week = remember(trips) { trips.filter { now - it.startMs < 7 * DAY_MS } }
    val weekKm = week.sumOf { it.km }
    val weekFuelTrips = week.filter { it.kmPerL != null }
    val weekKmPerL = weekFuelTrips.sumOf { it.litres }.takeIf { it > 0.05 }?.let { weekFuelTrips.sumOf { t -> t.km } / it }
    val weekSpend = week.sumOf { it.costPaise }
    val weekScore = week.takeIf { it.isNotEmpty() }?.let { w -> w.sumOf { it.score } / w.size }

    val bars = remember(trips) {
        val today = LocalDate.now(zone)
        (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val km = trips.filter { localDate(it.startMs, zone) == d }.sumOf { it.km }
            d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) to km
        }
    }
    val shown = remember(trips, range) {
        range.days?.let { d -> trips.filter { now - it.startMs < d * DAY_MS } } ?: trips
    }
    val groups = remember(shown) { shown.groupBy { localDate(it.startMs, zone) }.toList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Trips", if (trips.isEmpty()) null else "${trips.size} recorded") },
                actions = {
                    if (trips.isNotEmpty()) IconButton(enabled = !exporting, onClick = {
                        exporting = true
                        scope.launch {
                            runCatching { shareCsv(context, app.repo.exportCsv()) }
                                .onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show() }
                            exporting = false
                        }
                    }) { Icon(painterResource(R.drawable.download), "Export CSV", Modifier.size(22.dp)) }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            active?.let { a -> item(key = "active") { DrivingNowCard(a, now) { go(Routes.DRIVE) } } }

            if (trips.isEmpty()) {
                if (active == null) item(key = "empty") {
                    EmptyState(R.drawable.route, "No trips yet",
                        "Your drives will show up here.")
                }
                return@LazyColumn
            }

            item(key = "kpis") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiTile("This week", "%.0f".format(Locale.US, weekKm), Modifier.weight(1f), unit = "km", accent = Signal)
                        KpiTile("Economy", weekKmPerL?.let { "%.1f".format(Locale.US, it) } ?: "—", Modifier.weight(1f), unit = "km/L")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiTile("Fuel spend", if (week.isEmpty()) "—" else fmtRupees(weekSpend), Modifier.weight(1f))
                        KpiTile("Avg score", weekScore?.toString() ?: "—", Modifier.weight(1f), unit = "/100",
                            accent = weekScore?.let(::scoreColor) ?: MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item(key = "chart") {
                Panel("Distance per day", subtitle = "Last 7 days · %.0f km".format(Locale.US, bars.sumOf { it.second }),
                    icon = R.drawable.analytics) {
                    BarChart(bars, Signal, Modifier.fillMaxWidth(), height = 120.dp)
                }
            }

            item(key = "insights") { TripInsights(trips, now) }
            item(key = "range") {
                TextTabs(TripRange.entries.map { it.label }, range.ordinal, { range = TripRange.entries[it] })
            }

            if (groups.isEmpty()) item(key = "none-in-range") {
                Text("No trips in this period.", Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            groups.forEach { (day, list) ->
                item(key = "h$day") {
                    val km = list.sumOf { it.km }
                    Row(verticalAlignment = Alignment.Bottom) {
                        SectionLabel(dayLabel(day, zone), Modifier.weight(1f))
                        Text("%.1f km".format(Locale.US, km), style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                    }
                }
                items(list, key = { it.id }) { t ->
                    TripRow(t, onOpen = { go(Routes.trip(t.id)) }, onDelete = { pendingDelete = t })
                }
            }
        }
    }

    pendingDelete?.let { t ->
        ConfirmDialog(
            title = "Delete trip?",
            message = "The trip from ${fmtDate(t.startMs)} (%.1f km) and its route will be removed from this phone.".format(Locale.US, t.km),
            confirmLabel = "Delete",
            onConfirm = { scope.launch(Dispatchers.IO) { app.repo.deleteTrip(t.id) } },
            onDismiss = { pendingDelete = null },
            destructive = true,
        )
    }
}

@Composable
private fun DrivingNowCard(a: ActiveTrip, now: Long, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Signal.copy(alpha = .14f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(R.drawable.steering, tint = Signal, bg = Signal.copy(alpha = .18f))
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Driving now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "%.1f km · %s · since %s".format(Locale.US, a.distanceKm, fmtDuration((now - a.startMs) / 1000), fmtDate(a.startMs, "HH:mm")),
                    style = MaterialTheme.typography.bodyMedium.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (a.harshBrake + a.harshAccel > 0) Text(
                    "${a.harshBrake} harsh brakes · ${a.harshAccel} harsh accelerations",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.nav_arrow), null, Modifier.size(18.dp).rotate(180f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripRow(t: Trip, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(t.score, size = 44.dp, color = scoreColor(t.score))
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtDate(t.startMs, "HH:mm"), style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.SemiBold)
                    Text("  →  " + fmtDate(t.endMs, "HH:mm"), style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                }
                Text(
                    "%.1f km · %s".format(Locale.US, t.km, fmtDuration(t.durationS)),
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                )
                Text(
                    listOfNotNull(
                        t.kmPerL?.let { "%.1f km/L".format(Locale.US, it) },
                        t.costPaise.takeIf { it > 0 }?.let(::fmtRupees),
                        "max %.0f km/h".format(Locale.US, t.maxSpeed),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.more_vert), "Trip options", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        leadingIcon = { Icon(painterResource(R.drawable.route), null, Modifier.size(18.dp)) },
                        onClick = { menu = false; onOpen() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(painterResource(R.drawable.trash), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

private fun localDate(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

private fun dayLabel(d: LocalDate, zone: ZoneId): String {
    val today = LocalDate.now(zone)
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", Locale.getDefault()))
    }
}

private fun scoreColor(score: Int) = when {
    score >= 80 -> StateGood
    score >= 60 -> StateWarning
    else -> StateCritical
}

private fun androidx.compose.ui.text.TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private fun shareCsv(context: Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "LoganLink trips")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Export trips").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun TripInsights(trips: List<com.loganlink.core.data.Trip>, now: Long) {
    val month = trips.filter { now - it.startMs < 30 * 86_400_000L }
    if (month.size < 2) return
    val km = month.sumOf { it.km }
    val spend = month.sumOf { it.costPaise }
    val drive = month.sumOf { it.durationS }
    val idle = month.sumOf { it.idleS }
    val trend = month.sortedBy { it.startMs }.mapNotNull { it.kmPerL }
    val best = month.filter { it.kmPerL != null }.maxByOrNull { it.kmPerL!! }
    val avgSpeed = if (drive > 0) km / (drive / 3600.0) else null
    Panel("Last 30 days", subtitle = "${month.size} trips · %,.0f km".format(Locale.US, km), icon = R.drawable.analytics) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.loganlink.ui.components.MetricTile("Cost per km", if (km > 0) "₹%.1f".format(Locale.US, spend / 100.0 / km) else "—", Modifier.weight(1f))
            com.loganlink.ui.components.MetricTile("Stuck in traffic", if (drive > 0) "%.0f".format(Locale.US, idle * 100.0 / drive) else "—", Modifier.weight(1f), "%")
            com.loganlink.ui.components.MetricTile("Avg speed", avgSpeed?.let { "%.0f".format(Locale.US, it) } ?: "—", Modifier.weight(1f), "km/h")
        }
        if (trend.size >= 3) {
            Text("Economy per trip", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.loganlink.ui.components.Sparkline(trend, Signal, Modifier.fillMaxWidth().height(44.dp))
        }
        best?.let {
            Text("Best: %.1f km/L on %s".format(Locale.US, it.kmPerL, com.loganlink.ui.components.fmtDate(it.startMs, "d MMM")),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

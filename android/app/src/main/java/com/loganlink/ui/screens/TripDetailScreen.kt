package com.loganlink.ui.screens

import com.loganlink.ui.components.fadeScroll
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.data.Sample
import com.loganlink.core.data.Trip
import com.loganlink.core.data.TripPoint
import com.loganlink.ui.components.BackButton
import com.loganlink.ui.components.EmptyState
import com.loganlink.ui.components.IconBadge
import com.loganlink.ui.components.MetricTile
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScoreRing
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.TimeSeriesChart
import com.loganlink.ui.components.fmtDate
import com.loganlink.ui.components.fmtDuration
import com.loganlink.ui.components.fmtRupees
import com.loganlink.ui.map.RouteMap
import com.loganlink.ui.theme.Signal
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateGood
import com.loganlink.ui.theme.StateInfo
import com.loganlink.ui.theme.StateWarning
import java.util.Locale

private sealed interface TripLoad {
    object Loading : TripLoad
    object Missing : TripLoad
    data class Ready(val trip: Trip, val points: List<TripPoint>, val samples: List<Sample>) : TripLoad
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(id: Long, onBack: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val load by produceState<TripLoad>(TripLoad.Loading, id) {
        val t = app.repo.trip(id)
        value = if (t == null) TripLoad.Missing
        else TripLoad.Ready(t, app.repo.tripPoints(id), app.repo.samples(t.startMs, t.endMs))
    }
    val ready = load as? TripLoad.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ScreenTitle(
                        ready?.let { fmtDate(it.trip.startMs, "EEE d MMM") } ?: "Trip",
                        ready?.let { fmtDate(it.trip.startMs, "HH:mm") + " – " + fmtDate(it.trip.endMs, "HH:mm") },
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    ready?.let { r ->
                        IconButton(onClick = { shareTrip(context, r.trip, r.points) }) {
                            Icon(painterResource(R.drawable.share), "Share trip", Modifier.size(22.dp))
                        }
                    }
                },
            )
        },
    ) { pad ->
        when (val l = load) {
            TripLoad.Loading -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            TripLoad.Missing -> Box(Modifier.fillMaxSize().padding(pad)) {
                EmptyState(R.drawable.route, "Trip not found", "This trip may have been deleted.")
            }
            is TripLoad.Ready -> TripBody(l, Modifier.padding(pad))
        }
    }
}

@Composable
private fun TripBody(r: TripLoad.Ready, modifier: Modifier) {
    val t = r.trip
    val route = remember(r.points) { r.points.map { it.lat to it.lon } }
    val routeSpeeds = remember(r.points) { r.points.map { it.speed }.takeIf { s -> s.any { it > 0.0 } } }
    val speedSeries = remember(r) {
        r.samples.mapNotNull { s -> s.speed?.let { s.t to it } }.ifEmpty { r.points.map { it.t to it.speed } }
    }
    val rpmSeries = remember(r.samples) { r.samples.mapNotNull { s -> s.rpm?.let { s.t to it.toDouble() } } }
    val coolantSeries = remember(r.samples) { r.samples.mapNotNull { s -> s.coolant?.let { s.t to it } } }

    Column(
        modifier.fillMaxSize().fadeScroll().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (route.size >= 2) {
            RouteMap(route, Modifier.height(240.dp), speeds = routeSpeeds)
            if (routeSpeeds != null) SpeedLegend()
        } else {
            Box(
                Modifier.fillMaxWidth().height(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconBadge(R.drawable.map)
                    Text("No GPS route recorded", style = MaterialTheme.typography.titleSmall)
                    Text("Allow location access to record routes on future trips.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Panel("Summary", icon = R.drawable.road) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Distance", "%.1f".format(Locale.US, t.km), Modifier.weight(1f), unit = "km")
                MetricTile("Duration", fmtDuration(t.durationS), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Avg speed", "%.0f".format(Locale.US, t.avgSpeed), Modifier.weight(1f), unit = "km/h")
                MetricTile("Max speed", "%.0f".format(Locale.US, t.maxSpeed), Modifier.weight(1f), unit = "km/h")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Fuel", if (t.fuelMl > 0) "%.2f".format(Locale.US, t.litres) else "--", Modifier.weight(1f), unit = "L")
                MetricTile("Economy", t.kmPerL?.let { "%.1f".format(Locale.US, it) } ?: "--", Modifier.weight(1f), unit = "km/L")
                MetricTile("Cost", if (t.costPaise > 0) fmtRupees(t.costPaise) else "--", Modifier.weight(1f))
            }
        }

        ScorePanel(t)

        Panel("Speed", icon = R.drawable.gauge) {
            TimeSeriesChart(speedSeries, Signal, "km/h", decimals = 0, height = 150.dp)
        }
        if (rpmSeries.isNotEmpty()) Panel("Engine speed", icon = R.drawable.engine) {
            TimeSeriesChart(rpmSeries, StateInfo, "rpm", decimals = 0, height = 150.dp)
        }
        if (coolantSeries.isNotEmpty()) Panel("Coolant temperature", icon = R.drawable.thermometer) {
            TimeSeriesChart(coolantSeries, StateWarning, "°C", decimals = 0, height = 150.dp, guide = 105.0)
        }
    }
}

@Composable
private fun SpeedLegend() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Slower", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.padding(horizontal = 8.dp).weight(1f).height(6.dp)
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(StateGood, StateWarning, StateCritical)),
                    MaterialTheme.shapes.small),
        )
        Text("Faster", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScorePanel(t: Trip) {
    val color = scoreTint(t.score)
    val idlePct = (t.idleS * 100.0 / t.durationS).coerceIn(0.0, 100.0)
    Panel("Driving score", icon = R.drawable.shield, subtitle = scoreVerdict(t.score)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(t.score, size = 76.dp, color = color)
            Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Out of 100", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Smoother driving, less idling and lower revs raise the score.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ScoreLine("Harsh braking", "${t.harshBrake}", t.harshBrake,
            if (t.harshBrake == 0) "No sudden stops." else "Look further ahead and ease off earlier.")
        ScoreLine("Harsh acceleration", "${t.harshAccel}", t.harshAccel,
            if (t.harshAccel == 0) "Smooth pulling away." else "Gentler throttle saves fuel and the clutch.")
        ScoreLine("Harsh cornering", "${t.harshCorner}", t.harshCorner,
            if (t.harshCorner == 0) "Steady through the bends." else "Slow down before the turn, not in it.")
        ScoreLine("Idling", "%.0f %%".format(Locale.US, idlePct), if (idlePct > 20) 2 else if (idlePct > 10) 1 else 0,
            if (idlePct > 10) "Switch off when parked for more than a minute." else "Little time spent idling.")
        ScoreLine("Over-revving", "${t.overrevS} s", if (t.overrevS > 30) 2 else if (t.overrevS > 0) 1 else 0,
            if (t.overrevS > 0) "Shift up earlier to stay out of the high-rpm range." else "Engine kept in a healthy rpm range.")
    }
}

@Composable
private fun ScoreLine(label: String, value: String, badness: Int, hint: String) {
    val tint = when {
        badness == 0 -> StateGood
        badness <= 2 -> StateWarning
        else -> StateCritical
    }
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium.tnum(), fontWeight = FontWeight.SemiBold, color = tint,
            modifier = Modifier.padding(start = 12.dp))
    }
}

private fun scoreTint(score: Int): Color = when {
    score >= 80 -> StateGood
    score >= 60 -> StateWarning
    else -> StateCritical
}

private fun scoreVerdict(score: Int) = when {
    score >= 90 -> "Smooth"
    score >= 80 -> "Good"
    score >= 60 -> "Some harsh moments"
    else -> "Harsh"
}

private fun TextStyle.tnum() = copy(fontFeatureSettings = "tnum")

private fun shareTrip(context: Context, t: Trip, points: List<TripPoint>) {
    val text = buildString {
        appendLine("LoganLink trip · ${fmtDate(t.startMs)}")
        appendLine("%.1f km in %s (avg %.0f km/h, max %.0f km/h)".format(Locale.US, t.km, fmtDuration(t.durationS), t.avgSpeed, t.maxSpeed))
        t.kmPerL?.let { appendLine("Economy: %.1f km/L · %.2f L".format(Locale.US, it, t.litres)) }
        if (t.costPaise > 0) appendLine("Fuel cost: ${fmtRupees(t.costPaise)}")
        appendLine("Driving score: ${t.score}/100")
        if (points.size >= 2) {
            val a = points.first(); val b = points.last()
            append("Route: https://www.google.com/maps/dir/?api=1&origin=%.6f,%.6f&destination=%.6f,%.6f"
                .format(Locale.US, a.lat, a.lon, b.lat, b.lon))
        }
    }.trim()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Trip on ${fmtDate(t.startMs, "d MMM")}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share trip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

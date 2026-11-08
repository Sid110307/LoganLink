package com.loganlink.ui.screens

import com.loganlink.ui.components.fadeScroll
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.loganlink.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.loganlink.ui.components.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.data.Park
import com.loganlink.ui.components.BackButton
import com.loganlink.ui.components.EmptyState
import com.loganlink.ui.components.MetricTile
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.ago
import com.loganlink.ui.components.fmtDate
import com.loganlink.ui.map.RouteMap
import com.loganlink.ui.map.openInMaps
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.util.Locale

private class ParkSlot(val park: Park?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val slotFlow = remember { app.repo.lastPark.map { ParkSlot(it) } }
    val slot by slotFlow.collectAsStateWithLifecycle(initialValue = null)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val park = slot?.park

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Parking", park?.let { "Parked ${ago(it.t, now)}" }) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { pad ->
        when {
            slot == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            park == null -> Box(Modifier.fillMaxSize().padding(pad)) {
                EmptyState(
                    R.drawable.parking, "No parking spot yet",
                    "Saved when you switch off.",
                )
            }
            else -> Column(
                Modifier.fillMaxSize().padding(pad).fadeScroll().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RouteMap(emptyList(), Modifier.height(320.dp), marker = park.lat to park.lon, markerAccuracyM = park.accuracyM)

                Panel("Your car", icon = R.drawable.parking, subtitle = "Parked ${ago(park.t, now)} · ${fmtDate(park.t)}") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Accuracy", if (park.accuracyM > 0f) "± %.0f".format(Locale.US, park.accuracyM) else "—",
                            Modifier.weight(1f), unit = "m")
                        MetricTile("Location", "%.5f, %.5f".format(Locale.US, park.lat, park.lon), Modifier.weight(2f))
                    }
                    if (park.accuracyM > 50f) Text(
                        "GPS was weak when the car was parked, so the spot may be off by a little.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(onClick = { openInMaps(context, park.lat, park.lon, "My car") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.nav_arrow), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Walk there")
                }
                OutlinedButton(onClick = { shareParking(context, park) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.share), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share location")
                }
                Text(
                    "Saved when you switch off.",
                    Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun shareParking(context: Context, p: Park) {
    val ll = "%.6f,%.6f".format(Locale.US, p.lat, p.lon)
    val text = "My car is parked here (since ${fmtDate(p.t, "HH:mm, d MMM")}):\nhttps://www.google.com/maps/search/?api=1&query=$ll"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Where the car is parked")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share location").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

package com.loganlink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.data.Fill
import com.loganlink.ui.components.BackButton
import com.loganlink.ui.components.ConfirmDialog
import com.loganlink.ui.components.EmptyState
import com.loganlink.ui.components.IconBadge
import com.loganlink.ui.components.KpiTile
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.SectionLabel
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.components.fmtDate
import com.loganlink.ui.components.fmtRupees
import com.loganlink.ui.theme.Ember
import com.loganlink.ui.theme.StateGood
import com.loganlink.ui.theme.StateIdle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class FuelStats(
    val totalPaise: Long,
    val totalLitres: Double,
    val kmPerL: Double?,
    val paisePerKm: Double?,
    val trackedKm: Int,
    val perFill: Map<Long, Double>,
)

private fun fuelStats(fills: List<Fill>): FuelStats {
    val asc = fills.sortedWith(compareBy<Fill> { it.odometerKm }.thenBy { it.t })
    var lastFull: Fill? = null
    var segL = 0.0; var segP = 0L
    var km = 0; var litres = 0.0; var paise = 0L
    val per = HashMap<Long, Double>()
    for (f in asc) {
        if (lastFull != null) { segL += f.litres; segP += f.pricePaise }
        if (f.full) {
            val prev = lastFull
            if (prev != null) {
                val d = f.odometerKm - prev.odometerKm
                if (d > 0 && segL > 0.1) {
                    per[f.id] = d / segL
                    km += d; litres += segL; paise += segP
                }
            }
            lastFull = f; segL = 0.0; segP = 0L
        }
    }
    return FuelStats(
        totalPaise = fills.sumOf { it.pricePaise },
        totalLitres = fills.sumOf { it.litres },
        kmPerL = if (km > 0 && litres > 0) km / litres else null,
        paisePerKm = if (km > 0) paise.toDouble() / km else null,
        trackedKm = km,
        perFill = per,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val fills by app.repo.fills.collectAsStateWithLifecycle(initialValue = emptyList())
    val vehicle by app.prefs.vehicle.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Fill?>(null) }

    val stats = remember(fills) { fuelStats(fills) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Fuel log", "${fills.size} fill-up${if (fills.size == 1) "" else "s"}") },
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(painterResource(R.drawable.plus), "Add fill-up", Modifier.size(24.dp))
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel("Summary", icon = R.drawable.analytics,
                subtitle = if (stats.trackedKm > 0) String.format(Locale.US, "Mileage from %,d km between full tanks", stats.trackedKm)
                else "Log two \"filled to full\" fill-ups to see real mileage") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile("Average", stats.kmPerL?.let { f1(it) } ?: "—", Modifier.weight(1f), unit = "km/L", accent = Ember)
                    KpiTile("Cost per km", stats.paisePerKm?.let { String.format(Locale.US, "%.2f", it / 100) }?.let { "₹$it" } ?: "—",
                        Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile("Total spend", fmtRupees(stats.totalPaise), Modifier.weight(1f))
                    KpiTile("Total fuel", String.format(Locale.US, "%.0f", stats.totalLitres), Modifier.weight(1f), unit = "L")
                }
            }

            if (fills.isEmpty()) {
                EmptyState(R.drawable.fuel, "No fill-ups yet", "Tap + after you refuel.")
            } else {
                SectionLabel("Fill-ups")
                fills.forEach { f -> FillRow(f, stats.perFill[f.id], onDelete = { deleting = f }) }
            }
            Spacer(Modifier.height(72.dp))
        }
    }

    if (adding) AddFillDialog(
        odometer = vehicle.odometerKm,
        pricePerLitrePaise = vehicle.fuelPricePaise,
        onDismiss = { adding = false },
        onSave = { litres, paise, odo, full ->
            adding = false
            scope.launch {
                withContext(Dispatchers.IO) {
                    app.repo.addFill(Fill(0, System.currentTimeMillis(), litres, paise, odo, full))
                    val v = app.prefs.vehicle.value
                    if (odo > v.odometerKm) app.prefs.saveVehicle(v.copy(odometerKm = odo))
                }
                val sent = runCatching { app.hub.logFill(litres, paise).ok }.getOrDefault(false)
                snack.showSnackbar(if (sent) "Fill-up saved and sent to the module" else "Fill-up saved")
            }
        },
    )

    deleting?.let { f ->
        ConfirmDialog(
            title = "Delete this fill-up?",
            message = "${fmtDate(f.t, "d MMM yyyy")} · ${f1(f.litres)} L · ${fmtRupees(f.pricePaise)}. Mileage figures will be recalculated.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { app.repo.deleteFill(f.id) }
                    snack.showSnackbar("Fill-up deleted")
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun FillRow(f: Fill, kmPerL: Double?, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(R.drawable.fuel)
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${f1(f.litres)} L", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("  ·  ${fmtRupees(f.pricePaise)}", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    fmtDate(f.t, "d MMM yyyy") + String.format(Locale.US, " · %,d km", f.odometerKm) +
                        (if (f.litres > 0) String.format(Locale.US, " · ₹%.2f/L", f.pricePaise / 100.0 / f.litres) else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(if (f.full) "Full tank" else "Partial", if (f.full) StateGood else StateIdle)
                    kmPerL?.let { StatusChip("${f1(it)} km/L", Ember) }
                }
            }
            IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.trash), "Delete", Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun AddFillDialog(
    odometer: Int, pricePerLitrePaise: Int, onDismiss: () -> Unit,
    onSave: (litres: Double, paise: Long, odo: Int, full: Boolean) -> Unit,
) {
    var litres by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf(if (odometer > 0) odometer.toString() else "") }
    var full by remember { mutableStateOf(true) }

    val l = litres.replace(',', '.').toDoubleOrNull()
    val a = amount.replace(',', '.').toDoubleOrNull()
    val o = odo.trim().toIntOrNull()
    val valid = l != null && l > 0 && l < 200 && a != null && a >= 0 && o != null && o >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add fill-up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    litres, { litres = it }, label = { Text("Litres") }, singleLine = true,
                    isError = litres.isNotBlank() && (l == null || l <= 0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true,
                    isError = amount.isNotBlank() && a == null,
                    supportingText = {
                        val hint = l?.takeIf { it > 0 }?.let {
                            if (a != null && a > 0) String.format(Locale.US, "₹%.2f per litre", a / it)
                            else String.format(Locale.US, "≈ ₹%.0f at ₹%.2f/L", it * pricePerLitrePaise / 100.0, pricePerLitrePaise / 100.0)
                        }
                        Text(hint ?: "What you paid at the pump")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    odo, { odo = it.filter(Char::isDigit) }, label = { Text("Odometer (km)") }, singleLine = true,
                    isError = odo.isNotBlank() && o == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Filled to full", style = MaterialTheme.typography.bodyLarge)
                        Text("Pump clicked off — needed for mileage", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = full, onCheckedChange = { full = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(l!!, Math.round(a!! * 100), o!!, full)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)

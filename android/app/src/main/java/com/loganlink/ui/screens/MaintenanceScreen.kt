package com.loganlink.ui.screens

import com.loganlink.ui.components.fadeScroll
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.data.ServiceItem
import com.loganlink.ui.components.BackButton
import com.loganlink.ui.components.ConfirmDialog
import com.loganlink.ui.components.EmptyState
import com.loganlink.ui.components.MeterBar
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.components.ago
import com.loganlink.ui.components.fmtDate
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateGood
import com.loganlink.ui.theme.StateWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val repo = app.repo
    val items by repo.service.collectAsStateWithLifecycle(initialValue = emptyList())
    val vehicle by app.prefs.vehicle.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var markDone by remember { mutableStateOf<ServiceItem?>(null) }
    var editing by remember { mutableStateOf<ServiceItem?>(null) }
    var deleting by remember { mutableStateOf<ServiceItem?>(null) }

    val odo = vehicle.odometerKm
    val now = System.currentTimeMillis()
    val sorted = items.sortedByDescending { it.used(odo, now) }

    fun save(s: ServiceItem, msg: String) = scope.launch {
        withContext(Dispatchers.IO) { repo.saveService(s) }
        snack.showSnackbar(msg)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Maintenance", String.format(Locale.US, "Odometer %,d km", odo)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = ServiceItem(0, "", 10_000, 365, odo, System.currentTimeMillis())
            }) { Icon(painterResource(R.drawable.plus), "Add service item", Modifier.size(24.dp)) }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).fadeScroll().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sorted.isEmpty()) EmptyState(R.drawable.wrench, "No service items", "Tap + to add one.")
            sorted.forEach { s ->
                ServiceCard(
                    s, odo, now,
                    onDone = { markDone = s },
                    onEdit = { editing = s },
                    onDelete = { deleting = s },
                )
            }
            Spacer(Modifier.height(72.dp))
        }
    }

    markDone?.let { s ->
        ConfirmDialog(
            title = "Mark \"${s.name}\" done?",
            message = String.format(Locale.US, "This resets the countdown from today at %,d km.", odo),
            confirmLabel = "Mark done",
            onConfirm = { save(s.copy(lastKm = odo, lastMs = System.currentTimeMillis()), "${s.name} marked done") },
            onDismiss = { markDone = null },
        )
    }

    deleting?.let { s ->
        ConfirmDialog(
            title = "Delete \"${s.name}\"?",
            message = "This removes the item and its reminder. It can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.deleteService(s.id) }
                    snack.showSnackbar("${s.name} deleted")
                }
            },
            onDismiss = { deleting = null },
        )
    }

    editing?.let { s ->
        ServiceDialog(
            initial = s,
            onSave = { updated ->
                editing = null
                save(updated, if (s.id == 0L) "${updated.name} added" else "${updated.name} updated")
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ServiceCard(s: ServiceItem, odo: Int, now: Long, onDone: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val used = s.used(odo, now)
    val color = usedColor(used)
    Panel(
        title = s.name,
        subtitle = intervalText(s),
        actions = {
            IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.edit_pencil), "Edit", Modifier.size(20.dp)) }
            IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.trash), "Delete", Modifier.size(20.dp)) }
        },
    ) {
        MeterBar(used.toFloat(), color)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(leftText(s, odo, now), style = MaterialTheme.typography.bodyMedium)
                Text(
                    String.format(Locale.US, "Last done %s at %,d km", fmtDate(s.lastMs, "d MMM yyyy"), s.lastKm) +
                        " · ${ago(s.lastMs, now)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(
                when { used >= 1.0 -> "Overdue"; used >= 0.75 -> "Due soon"; else -> "OK" },
                color,
            )
        }
        TextButton(onClick = onDone, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Icon(painterResource(R.drawable.check), null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Mark done")
        }
    }
}

@Composable
private fun ServiceDialog(initial: ServiceItem, onSave: (ServiceItem) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var km by remember { mutableStateOf(if (initial.intervalKm > 0) initial.intervalKm.toString() else "") }
    var days by remember { mutableStateOf(if (initial.intervalDays > 0) initial.intervalDays.toString() else "") }
    val kmVal = km.trim().toIntOrNull() ?: 0
    val daysVal = days.trim().toIntOrNull() ?: 0
    val kmOk = km.isBlank() || km.trim().toIntOrNull()?.let { it >= 0 } == true
    val daysOk = days.isBlank() || days.trim().toIntOrNull()?.let { it >= 0 } == true
    val valid = name.isNotBlank() && kmOk && daysOk && (kmVal > 0 || daysVal > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add service item" else "Edit service item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    km, { km = it.filter(Char::isDigit) }, label = { Text("Every (km)") }, singleLine = true,
                    isError = !kmOk, supportingText = { Text("Leave empty for time-only") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    days, { days = it.filter(Char::isDigit) }, label = { Text("Every (days)") }, singleLine = true,
                    isError = !daysOk, supportingText = { Text("Leave empty for distance-only") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(initial.copy(name = name.trim(), intervalKm = kmVal, intervalDays = daysVal))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun usedColor(used: Double): Color = when {
    used < 0.75 -> StateGood
    used < 1.0 -> StateWarning
    else -> StateCritical
}

private fun intervalText(s: ServiceItem): String = listOfNotNull(
    s.intervalKm.takeIf { it > 0 }?.let { String.format(Locale.US, "%,d km", it) },
    s.intervalDays.takeIf { it > 0 }?.let { d ->
        when {
            d % 365 == 0 -> "${d / 365} year${if (d / 365 > 1) "s" else ""}"
            d % 30 == 0 -> "${d / 30} months"
            else -> "$d days"
        }
    },
).joinToString(" or ", prefix = "Every ").let { if (it == "Every ") "No interval" else it }

private fun leftText(s: ServiceItem, odo: Int, now: Long): String = listOfNotNull(
    s.kmLeft(odo)?.let { if (it < 0) String.format(Locale.US, "%,d km overdue", -it) else String.format(Locale.US, "%,d km left", it) },
    s.daysLeft(now)?.let { if (it < 0) "${-it} days overdue" else "$it days left" },
).joinToString(" · ").ifEmpty { "No interval set" }

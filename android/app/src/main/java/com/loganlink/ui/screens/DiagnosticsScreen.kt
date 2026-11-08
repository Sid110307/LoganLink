package com.loganlink.ui.screens

import com.loganlink.ui.components.fadeScroll
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.loganlink.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.loganlink.ui.components.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.analytics.DtcCatalog
import com.loganlink.core.data.DtcEntry
import com.loganlink.core.link.LinkState
import com.loganlink.core.protocol.Status
import com.loganlink.ui.components.BackButton
import com.loganlink.ui.components.ConfirmDialog
import com.loganlink.ui.components.EmptyState
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.SectionLabel
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.components.ago
import com.loganlink.ui.components.fmtDate
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateGood
import com.loganlink.ui.theme.StateInfo
import com.loganlink.ui.theme.StateWarning
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val hub = app.hub
    val context = LocalContext.current
    val dtcs by app.repo.dtcs.collectAsStateWithLifecycle(initialValue = emptyList())
    val live by hub.live.collectAsStateWithLifecycle()
    val linkState by hub.state.collectAsStateWithLifecycle()
    val vehicle by app.prefs.vehicle.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val connected = linkState == LinkState.Ready
    val owner = hub.isOwner
    val active = dtcs.filter { it.cleared == null }
    val past = dtcs.filter { it.cleared != null }
    val mil = live?.milOn == true

    fun readCodes() = scope.launch {
        reading = true
        val r = hub.readDtcs()
        reading = false
        snack.showSnackbar(if (r.ok) "Fault codes read from the engine computer" else Status.describe(r.status))
    }

    fun share() {
        val text = buildReport(vehicle.nickname, vehicle.plate, mil, active, past)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Fault code report — ${vehicle.nickname}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share fault report"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Diagnostics", "Fault codes from the engine computer") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { share() }) { Icon(painterResource(R.drawable.share), "Share report", Modifier.size(22.dp)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).fadeScroll().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel(
                "Check-engine light",
                icon = R.drawable.engine,
                subtitle = when {
                    !connected -> "Connect to the car to read the light and codes"
                    live?.ecuOnline != true -> "Engine computer not responding — switch the ignition on"
                    mil -> "On — the engine computer has stored a fault"
                    else -> "Off"
                },
                actions = {
                    if (connected && live?.ecuOnline == true)
                        StatusChip(if (mil) "On" else "Off", if (mil) StateCritical else StateGood)
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { readCodes() }, enabled = connected && !reading && !clearing) {
                        if (reading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(painterResource(R.drawable.refresh), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Read codes")
                    }
                    OutlinedButton(onClick = { share() }) { Text("Share report") }
                }
                if (owner && active.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            if (live?.engineRunning == true) scope.launch { snack.showSnackbar("Switch the engine off first (ignition on is fine)") }
                            else confirmClear = true
                        },
                        enabled = connected && !reading && !clearing,
                    ) {
                        if (clearing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(painterResource(R.drawable.trash), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Clear codes")
                    }
                }
            }

            if (dtcs.isEmpty()) {
                EmptyState(
                    R.drawable.check, "No fault codes",
                    if (connected) "Tap Read codes to ask the engine computer for stored faults."
                    else "Nothing recorded yet. Codes are read automatically whenever the car is connected.",
                )
            }

            if (active.isNotEmpty()) {
                SectionLabel("Active (${active.size})")
                active.forEach { DtcCard(it) }
            }
            if (past.isNotEmpty()) {
                SectionLabel("History")
                past.forEach { DtcCard(it) }
            }

        }
    }

    if (confirmClear) ConfirmDialog(
        title = "Clear fault codes?",
        message = "This turns off the check-engine light and erases the stored codes and freeze-frame data. " +
            "It does not fix anything: if the fault is still there, the code and the light will come back.\n\n" +
            "It only works with the engine off and the ignition on. Share the report first if a mechanic needs it.",
        confirmLabel = "Clear codes",
        destructive = true,
        onConfirm = {
            scope.launch {
                clearing = true
                val r = hub.clearDtcs()
                clearing = false
                snack.showSnackbar(if (r.ok) "Codes cleared — the light should go out" else Status.describe(r.status))
            }
        },
        onDismiss = { confirmClear = false },
    )
}

@Composable
private fun DtcCard(d: DtcEntry) {
    val info = DtcCatalog.lookup(d.code)
    val isActive = d.cleared == null
    Panel(
        title = d.code,
        subtitle = info.title,
        actions = {
            if (isActive) StatusChip(info.severity.label, info.severity.color)
            else StatusChip("Cleared", StateInfo)
        },
    ) {
        if (d.pending && isActive) StatusChip("Pending — not yet confirmed", StateWarning)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Likely causes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(info.causes, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stamp("First seen", d.firstSeen, Modifier.weight(1f))
            Stamp("Last seen", d.lastSeen, Modifier.weight(1f))
            d.cleared?.let { Stamp("Cleared", it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Stamp(label: String, t: Long, modifier: Modifier = Modifier) = Column(modifier) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(fmtDate(t, "d MMM yyyy"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    Text(ago(t), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun buildReport(nick: String, plate: String, mil: Boolean, active: List<DtcEntry>, past: List<DtcEntry>): String =
    buildString {
        appendLine("LoganLink fault code report")
        appendLine("Vehicle: $nick${if (plate.isNotBlank()) " ($plate)" else ""} — Mahindra Renault Logan 1.6 (K7M)")
        appendLine("Generated: ${fmtDate(System.currentTimeMillis(), "d MMM yyyy, HH:mm")}")
        appendLine("Check-engine light: ${if (mil) "ON" else "off / unknown"}")
        appendLine()
        if (active.isEmpty()) appendLine("Active codes: none")
        else {
            appendLine("Active codes (${active.size}):")
            active.forEach { d ->
                val i = DtcCatalog.lookup(d.code)
                appendLine("• ${d.code}${if (d.pending) " (pending)" else ""} — ${i.title} [${i.severity.label}]")
                appendLine("  Likely causes: ${i.causes}")
                appendLine("  First seen ${fmtDate(d.firstSeen, "d MMM yyyy")}, last seen ${fmtDate(d.lastSeen, "d MMM yyyy")}")
            }
        }
        if (past.isNotEmpty()) {
            appendLine()
            appendLine("History (cleared):")
            past.forEach { d ->
                appendLine("• ${d.code} — ${DtcCatalog.lookup(d.code).title}, cleared ${fmtDate(d.cleared ?: d.lastSeen, "d MMM yyyy")}")
            }
        }
    }.trimEnd()

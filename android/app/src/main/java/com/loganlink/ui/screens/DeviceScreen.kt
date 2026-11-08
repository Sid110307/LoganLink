package com.loganlink.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import com.loganlink.ui.components.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.loganlink.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.link.LinkState
import com.loganlink.core.protocol.Status
import com.loganlink.ui.components.ConfirmDialog
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.theme.StateConnected
import com.loganlink.ui.theme.StateCritical
import com.loganlink.ui.theme.StateIdle
import com.loganlink.ui.theme.StateWarning
import kotlinx.coroutines.launch

@Composable
fun DeviceScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val hub = app.hub
    val state by hub.state.collectAsStateWithLifecycle()
    val rssi by hub.rssi.collectAsStateWithLifecycle()
    val info by hub.info.collectAsStateWithLifecycle()
    val ota by hub.ota.collectAsStateWithLifecycle()
    val pairing by app.prefs.pairing.collectAsStateWithLifecycle()
    val demo by app.prefs.demo.collectAsStateWithLifecycle()
    val live by hub.live.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var dmm by remember { mutableStateOf("") }
    var askReboot by remember { mutableStateOf(false) }
    val ready = state == LinkState.Ready

    LaunchedEffect(ready) { if (ready) hub.refreshInfo() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.size < 64 * 1024) scope.launch { snack.showSnackbar("That doesn't look like a LoganLink firmware image") }
        else hub.startOta(bytes)
    }
    val run: (suspend () -> Int) -> Unit = { block -> scope.launch { snack.showSnackbar(Status.describe(block())) } }

    Page("LoganLink module", if (demo) "Demo mode" else pairing?.name, onBack, snack) {
        Panel("Connection", icon = R.drawable.bluetooth) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(state.label, when (state) {
                    LinkState.Ready -> StateConnected; LinkState.AuthFailed -> StateCritical
                    LinkState.Connecting, LinkState.Authenticating -> StateWarning; else -> StateIdle
                })
                rssi?.let { StatusChip("$it dBm", if (it > -75) StateConnected else StateWarning) }
                if (!hub.isOwner && ready) StatusChip("Driver access", StateIdle)
            }
            pairing?.let { Kv("Address", it.address); Kv("Device ID", it.deviceId.uppercase()) }
            live?.let { Kv("Module state", it.stateName) }
            OutlinedButton(onClick = { hub.reconnect() }) { Text("Reconnect") }
        }

        info?.let { i ->
            Panel("Module", icon = R.drawable.engine) {
                Kv("Firmware", i.fw)
                if (i.ecuId.isNotBlank()) Kv("ECU ID", i.ecuId)
                i.vin?.let { Kv("VIN", it) }
            }
        }

        Panel("Reconnect to engine", subtitle = "Ignition on.", icon = R.drawable.search) {
            Button(enabled = ready && hub.isOwner, onClick = {
                run { hub.runProbe().status }
                scope.launch { kotlinx.coroutines.delay(25_000); hub.refreshInfo() }
            }) { Text("Detect again") }
        }

        Panel("Battery reading", subtitle = "Engine off. Enter your multimeter reading.", icon = R.drawable.battery_car) {
            Kv("Module reads", live?.vbat?.let { "%.3f V".format(it) } ?: "—")
            OutlinedTextField(dmm, { dmm = it }, Modifier.fillMaxWidth(), label = { Text("Multimeter reading (V)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            Button(enabled = ready && hub.isOwner && (dmm.toDoubleOrNull() ?: 0.0) in 9.0..16.0,
                onClick = { run { hub.calibrateVbat(dmm.toDouble()).status } }) { Text("Calibrate") }
        }

        Panel("Firmware update", subtitle = "Engine off, battery above 12.2 V, keep the phone near the car.", icon = R.drawable.upload) {
            ota?.let { p ->
                if (p.total > 0) LinearProgressIndicator({ p.sent.toFloat() / p.total }, Modifier.fillMaxWidth())
                Text(p.message, color = if (p.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.done || p.error) OutlinedButton(onClick = { hub.clearOta() }) { Text("OK") }
            } ?: Button(enabled = ready && hub.isOwner, onClick = { pick.launch(arrayOf("application/octet-stream", "*/*")) }) {
                Text("Choose firmware file (.bin)")
            }
        }

        OutlinedButton(enabled = ready && hub.isOwner, onClick = { askReboot = true }) { Text("Restart module") }
    }
    if (askReboot) ConfirmDialog("Restart the module?", "It reconnects in a few seconds. Live data pauses briefly.", "Restart",
        onConfirm = { run { hub.reboot().status } }, onDismiss = { askReboot = false })
}

@Composable
internal fun Kv(k: String, v: String) = Row {
    Text(k, Modifier.weight(.38f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(v, Modifier.weight(.62f), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
}

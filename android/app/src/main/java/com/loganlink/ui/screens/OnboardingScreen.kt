package com.loganlink.ui.screens

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.loganlink.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.loganlink.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.loganlink.LocalApp
import com.loganlink.MainActivity
import com.loganlink.R
import com.loganlink.core.data.Pairing
import com.loganlink.core.link.FoundModule
import com.loganlink.core.link.scanModules
import com.loganlink.core.protocol.DriverInvite
import com.loganlink.core.protocol.PairingCard
import com.loganlink.core.protocol.deriveUserKey
import com.loganlink.core.service.DriveService
import com.loganlink.ui.components.NavRow
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.theme.Signal
import com.loganlink.ui.theme.StateConnected
import java.util.concurrent.Executor
import java.util.regex.Pattern

private data class Creds(val deviceId: String, val userId: Int, val kUser: ByteArray)

private fun parseCode(text: String): Creds? =
    PairingCard.parse(text)?.let { Creds(it.deviceId, 0, deriveUserKey(it.kDev, 0)) }
        ?: DriverInvite.parse(text)?.let { Creds(it.deviceId, it.userId, it.kUser) }

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var creds by remember { mutableStateOf<Creds?>(null) }
    var manual by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.ic_launcher_fg), null, Modifier.size(84.dp))
        when (step) {
            0 -> {
                Text("Your Logan, connected.", style = MaterialTheme.typography.headlineLarge)
                Text("Live data, trips, fault codes and battery health for your car.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { (ctx as? MainActivity)?.requestRuntimePermissions(); step = 1 }, Modifier.fillMaxWidth().height(54.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) { Text("Pair my module", style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(onClick = {
                    app.prefs.setDemo(true); onDone()
                }, Modifier.fillMaxWidth().height(54.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) { Text("Explore with a simulated car") }
            }
            1 -> {
                Text("Scan the pairing card", style = MaterialTheme.typography.headlineMedium)
                Text("Scan the card from the box, or a driver invite.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                NavRow(R.drawable.qr, "Scan QR code", "Uses the camera via Google Play services") {
                    GmsBarcodeScanning.getClient(ctx).startScan()
                        .addOnSuccessListener { b ->
                            val c = b.rawValue?.let(::parseCode)
                            if (c == null) error = "That QR code isn't a LoganLink pairing card" else { creds = c; error = null; step = 2 }
                        }
                        .addOnFailureListener { error = "Scanner unavailable. Type the code instead." }
                }
                OutlinedTextField(manual, { manual = it.trim() }, Modifier.fillMaxWidth(), label = { Text("…or paste the code (LL1:…)") }, singleLine = true)
                Button(enabled = manual.isNotBlank(), onClick = {
                    val c = parseCode(manual)
                    if (c == null) error = "Code not recognised. Check for typos." else { creds = c; error = null; step = 2 }
                }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                TextButton(onClick = { step = 0 }) { Text("Back") }
            }
            else -> FindModule(creds!!, onPaired = { onDone() }, onBack = { step = 1 })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun FindModule(creds: Creds, onPaired: () -> Unit, onBack: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val found by remember { scanModules(ctx) }.collectAsState(emptyList())
    val expectMac = creds.deviceId.uppercase().chunked(2).joinToString(":")
    val match = found.firstOrNull { it.address.equals(expectMac, ignoreCase = true) }

    val cdmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { onPaired() }

    fun finish(m: FoundModule) {
        app.prefs.savePairing(Pairing(m.address, creds.deviceId, creds.userId, creds.kUser, m.name))
        DriveService.start(ctx)
        if (Build.VERSION.SDK_INT >= 33) {
            val cdm = ctx.getSystemService(CompanionDeviceManager::class.java)
            val req = AssociationRequest.Builder().setSingleDevice(true)
                .addDeviceFilter(BluetoothLeDeviceFilter.Builder().setNamePattern(Pattern.compile(Pattern.quote(m.name))).build())
                .build()
            val exec = Executor { it.run() }
            runCatching {
                cdm.associate(req, exec, object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(sender: IntentSender) {
                        cdmLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    override fun onAssociationCreated(info: AssociationInfo) {
                        runCatching { @Suppress("DEPRECATION") cdm.startObservingDevicePresence(m.address) }
                    }
                    override fun onFailure(error: CharSequence?) { onPaired() }
                })
            }.onFailure { onPaired() }
        } else onPaired()
    }

    Text("Find your module", style = MaterialTheme.typography.headlineMedium)
    Text("Start the engine and press PAIR on the module, then accept the pairing request.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (found.isEmpty()) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            CircularProgressIndicator(color = Signal)
            Text("Searching…", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    match?.let { m ->
        StatusChip("Found your module · ${m.rssi} dBm", StateConnected)
        Button(onClick = { finish(m) }, Modifier.fillMaxWidth()) { Text("Connect to ${m.name}") }
    }
    found.filter { it != match }.forEach { m ->
        NavRow(R.drawable.bluetooth, m.name, "${m.address} · ${m.rssi} dBm" + (m.vbat?.let { " · battery %.1f V".format(it) } ?: "")) { finish(m) }
    }
    TextButton(onClick = onBack) { Text("Back") }
}

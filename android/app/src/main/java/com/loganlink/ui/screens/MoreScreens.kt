package com.loganlink.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import com.loganlink.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import com.loganlink.ui.components.TextTabs
import androidx.compose.material3.MaterialTheme
import com.loganlink.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.BuildConfig
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.core.protocol.DriverInvite
import com.loganlink.core.protocol.Status
import com.loganlink.ui.Routes
import com.loganlink.ui.components.BackButton
import com.loganlink.ui.components.ConfirmDialog
import com.loganlink.ui.components.NavRow
import com.loganlink.ui.components.Panel
import com.loganlink.ui.components.ScreenTitle
import com.loganlink.ui.components.SectionLabel
import com.loganlink.ui.components.StatusChip
import com.loganlink.ui.theme.StateConnected
import com.loganlink.ui.theme.StateIdle
import com.loganlink.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Page(title: String, subtitle: String? = null, onBack: (() -> Unit)?, snackbar: SnackbarHostState? = null,
                  content: @Composable () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { ScreenTitle(title, subtitle) }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
fun MoreScreen(go: (String) -> Unit) {
    val app = LocalApp.current
    val pairing by app.prefs.pairing.collectAsStateWithLifecycle()
    val demo by app.prefs.demo.collectAsStateWithLifecycle()
    val vehicle by app.prefs.vehicle.collectAsStateWithLifecycle()
    Page("Garage", onBack = null) {
        NavRow(R.drawable.car, "Vehicle", "${vehicle.nickname} · %,d km".format(vehicle.odometerKm)) { go(Routes.VEHICLE) }
        NavRow(R.drawable.bluetooth, "LoganLink module",
            if (demo) "Demo mode (simulated car)" else pairing?.name ?: "Not paired") { go(Routes.DEVICE) }
        NavRow(R.drawable.parking, "Parking", "Where you left the car") { go(Routes.PARKING) }
        NavRow(R.drawable.fuel, "Fuel log", "Fill-ups, spend and real economy") { go(Routes.FUEL) }
        NavRow(R.drawable.wrench, "Maintenance", "Service schedule") { go(Routes.MAINT) }
        NavRow(R.drawable.users, "Drivers", "Share access with family") { go(Routes.DRIVERS) }
        NavRow(R.drawable.settings, "Settings", "Alerts, theme, data") { go(Routes.SETTINGS) }
        var about by remember { mutableStateOf(false) }
        NavRow(R.drawable.info, "About", "LoganLink v${BuildConfig.VERSION_NAME}") { about = true }
        if (about) {
            val ctx = LocalContext.current
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { about = false },
                title = { AboutHeader() },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("v${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Made by Siddharth Praveen Bharadwaj")
                        Text("sid110307.github.io/Sid110307", color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sid110307.github.io/Sid110307")))
                            })
                    }
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { about = false }) { Text("Close") } },
            )
        }
    }
}

@Composable
fun VehicleScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val v by app.prefs.vehicle.collectAsStateWithLifecycle()
    var nick by rememberSaveable { mutableStateOf(v.nickname) }
    var plate by rememberSaveable { mutableStateOf(v.plate) }
    var odo by rememberSaveable { mutableStateOf(v.odometerKm.toString()) }
    var price by rememberSaveable { mutableStateOf("%.2f".format(v.fuelPricePaise / 100.0)) }
    var tank by rememberSaveable { mutableStateOf(v.tankLitres.toString()) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    Page("Vehicle", "Mahindra Renault Logan 1.6", onBack, snack) {
        Panel("Details") {
            OutlinedTextField(nick, { nick = it }, Modifier.fillMaxWidth(), label = { Text("Nickname") }, singleLine = true)
            OutlinedTextField(plate, { plate = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("Registration") }, singleLine = true)
            OutlinedTextField(odo, { odo = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Odometer (km, from the cluster)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Petrol price (₹/L)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(tank, { tank = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Tank size (L)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Button(onClick = {
                val odoKm = odo.toIntOrNull() ?: v.odometerKm
                app.prefs.saveVehicle(v.copy(nickname = nick.ifBlank { "My Logan" }, plate = plate.trim(), odometerKm = odoKm,
                    fuelPricePaise = ((price.toDoubleOrNull() ?: 105.0) * 100).toInt(), tankLitres = tank.toIntOrNull() ?: 50))
                scope.launch {
                    if (odoKm != v.odometerKm) { app.hub.setOdometer(odoKm); app.repo.rebaseService(odoKm) }
                    snack.showSnackbar("Saved")
                }
            }, Modifier.fillMaxWidth()) { Text("Save") }
        }
        Panel("Specification") {
            listOf("Engine" to "1.6 L petrol (K7M)",
                "Gearbox" to "5-speed manual", "Tank" to "50 L").forEach { (k, x) ->
                Row { Text(k, Modifier.weight(.4f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(x, Modifier.weight(.6f)) }
            }
        }
    }
}

@Composable
fun DriversScreen(onBack: () -> Unit) {
    val hub = LocalApp.current.hub
    val pairing by LocalApp.current.prefs.pairing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var invite by remember { mutableStateOf<String?>(null) }
    var revoke by remember { mutableStateOf<Int?>(null) }
    Page("Drivers", "Up to 3 extra phones", onBack, snack) {
        Text("Drivers can see live data, trips and diagnostics. Only the owner can clear fault codes, update firmware or change settings.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!hub.isOwner) Text("You're paired as a driver, so only the owner's phone can manage drivers.", color = MaterialTheme.colorScheme.error)
        (1..3).forEach { id ->
            Panel("Driver $id", icon = R.drawable.users) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = hub.isOwner, onClick = {
                        scope.launch {
                            val r = hub.addDriver(id)
                            if (r.ok && r.data.size >= 32) invite = DriverInvite(pairing?.deviceId ?: "000000000000", id, r.data.copyOfRange(0, 32)).encode()
                            else snack.showSnackbar(Status.describe(r.status))
                        }
                    }) { Text("Create invite") }
                    OutlinedButton(enabled = hub.isOwner, onClick = { revoke = id }) { Text("Revoke") }
                }
            }
        }
        invite?.let { code ->
            Panel("Invite code", subtitle = "Share privately. Anyone with this code can pair as this driver.") {
                SelectionContainer { Text(code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                val ctx = LocalContext.current
                Button(onClick = {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "LoganLink driver invite: $code"), "Share invite"))
                }) { Text("Share") }
            }
        }
    }
    revoke?.let { id ->
        ConfirmDialog("Revoke driver $id?", "Their phone will no longer be able to connect.", "Revoke", destructive = true,
            onConfirm = { scope.launch { snack.showSnackbar(Status.describe(hub.revokeDriver(id).status)) } }, onDismiss = { revoke = null })
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onForgotten: () -> Unit) {
    val app = LocalApp.current
    val theme by app.prefs.theme.collectAsStateWithLifecycle()
    val alerts by app.prefs.alerts.collectAsStateWithLifecycle()
    val autoDrive by app.prefs.autoDriveMode.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var wipe by remember { mutableStateOf(false) }
    var forget by remember { mutableStateOf(false) }
    Page("Settings", onBack = onBack) {
        Panel("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextTabs(ThemeMode.entries.map { it.name }, theme.ordinal, { app.prefs.setTheme(ThemeMode.entries[it]) })
            }
        }
        Panel("Driving") {
            Toggle("Alerts", "Overheating, charging faults, new fault codes", alerts) { app.prefs.setAlerts(it) }
            Toggle("Open Drive Mode on engine start", "Only while the app is open", autoDrive) { app.prefs.setAutoDriveMode(it) }
        }
        val overlayAuto by app.prefs.overlayWithMaps.collectAsStateWithLifecycle()
        var resumeTick by remember { mutableStateOf(0) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { resumeTick++; onPauseOrDispose { } }
        val notifOk = remember(resumeTick) { com.loganlink.core.nav.MapsNav.accessGranted(ctx) }
        val overlayOk = remember(resumeTick) { android.provider.Settings.canDrawOverlays(ctx) }
        val autoFuel by app.prefs.autoFuel.collectAsStateWithLifecycle()
        Panel("Notifications from other apps", subtitle = if (notifOk) null else "Needs notification access") {
            Toggle("Log fill-ups automatically", "From GPay, PhonePe, Paytm and bank SMS at petrol pumps", autoFuel) { app.prefs.setAutoFuel(it) }
            Toggle("Car panel over Google Maps", "Speed, engine and next turn while navigating", overlayAuto) { app.prefs.setOverlayWithMaps(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !notifOk, onClick = {
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text(if (notifOk) "Access on" else "Allow access") }
                OutlinedButton(enabled = !overlayOk, onClick = {
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName)))
                }) { Text(if (overlayOk) "Panel allowed" else "Allow panel") }
            }
        }
        SectionLabel("Data")
        NavRow(R.drawable.download, "Export trips (CSV)", "Share to Sheets, Excel or email") {
            scope.launch {
                val f = app.repo.exportCsv()
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Export trips"))
            }
        }
        NavRow(R.drawable.trash, "Delete history", "Trips, samples, codes and parking on this phone") { wipe = true }
        NavRow(R.drawable.xmark, "Forget module", "Unpair this phone") { forget = true }
    }
    if (wipe) ConfirmDialog("Delete all history?", "This can't be undone.", "Delete",
        destructive = true, onConfirm = { app.repo.wipeAll() }, onDismiss = { wipe = false })
    if (forget) ConfirmDialog("Forget this module?", "You'll need the pairing card to connect again.", "Forget", destructive = true,
        onConfirm = { app.hub.forget(); onForgotten() }, onDismiss = { forget = false })
}

@Composable
internal fun Toggle(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}

@Composable
private fun AboutHeader() {
    var grin by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.runtime.LaunchedEffect(grin) { if (grin) { kotlinx.coroutines.delay(1400); grin = false } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(52.dp).pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); grin = true
                })
            },
        ) {
            androidx.compose.animation.Crossfade(grin, label = "") { g ->
                if (g) Grin(Modifier.size(52.dp))
                else androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_fg), null,
                    Modifier.size(52.dp).scale(1.5f))
            }
        }
        Text("LoganLink", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Grin(modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        val s = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.045f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val face = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .14f, h * .30f); cubicTo(w * .22f, h * .04f, w * .80f, h * .02f, w * .90f, h * .32f)
            cubicTo(w * .98f, h * .58f, w * .86f, h * .92f, w * .52f, h * .96f)
            cubicTo(w * .20f, h * .98f, w * .04f, h * .62f, w * .14f, h * .30f)
        }
        drawPath(face, ink, style = s)
        drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .26f, h * .34f); quadraticTo(w * .36f, h * .24f, w * .46f, h * .36f)
            moveTo(w * .58f, h * .36f); quadraticTo(w * .68f, h * .24f, w * .78f, h * .34f)
        }, ink, style = s)
        drawCircle(ink, w * .03f, androidx.compose.ui.geometry.Offset(w * .38f, h * .36f))
        drawCircle(ink, w * .03f, androidx.compose.ui.geometry.Offset(w * .70f, h * .36f))
        val mouth = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .18f, h * .52f); quadraticTo(w * .52f, h * .62f, w * .86f, h * .48f)
            quadraticTo(w * .76f, h * .90f, w * .48f, h * .88f); quadraticTo(w * .24f, h * .84f, w * .18f, h * .52f)
        }
        drawPath(mouth, ink, style = s)
        for (i in 1..5) {
            val x = w * (.18f + i * .115f)
            drawLine(ink, androidx.compose.ui.geometry.Offset(x, h * (.55f - (if (i in 2..4) .02f else 0f))),
                androidx.compose.ui.geometry.Offset(x - w * .02f, h * .85f - kotlin.math.abs(i - 3) * h * .04f), w * .025f)
        }
        drawLine(ink, androidx.compose.ui.geometry.Offset(w * .20f, h * .70f), androidx.compose.ui.geometry.Offset(w * .82f, h * .62f), w * .025f)
    }
}

package com.loganlink.ui.screens

import androidx.compose.ui.graphics.luminance
import com.loganlink.ui.components.fadeScroll
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
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
            Modifier.fillMaxSize().padding(pad).fadeScroll().padding(16.dp),
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
    val info by app.hub.info.collectAsStateWithLifecycle()
    val car = remember(info?.vin) { com.loganlink.core.data.VehicleId.decodeVin(info?.vin) }
    var odo by rememberSaveable { mutableStateOf(v.odometerKm.toString()) }
    var price by rememberSaveable { mutableStateOf("%.2f".format(v.fuelPricePaise / 100.0)) }
    var tank by rememberSaveable { mutableStateOf(v.tankLitres.toString()) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    Page("Vehicle", null, onBack, snack) {
        Panel("Details") {
            OutlinedTextField(nick, { nick = it }, Modifier.fillMaxWidth(), label = { Text("Nickname") }, singleLine = true)
            OutlinedTextField(plate, { plate = com.loganlink.core.data.VehicleId.formatPlate(it) }, Modifier.fillMaxWidth(), label = { Text("Registration") }, singleLine = true,
                supportingText = com.loganlink.core.data.VehicleId.registration(plate)?.let { r -> { Text(r) } })
            OutlinedTextField(odo, { odo = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Odometer (km, from the cluster)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Petrol price (₹/L)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(tank, { tank = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Tank size (L)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Button(onClick = {
                val odoKm = odo.toIntOrNull() ?: v.odometerKm
                app.prefs.saveVehicle(v.copy(nickname = nick.ifBlank { "My car" }, plate = plate.trim(), odometerKm = odoKm,
                    fuelPricePaise = ((price.toDoubleOrNull() ?: 105.0) * 100).toInt(), tankLitres = tank.toIntOrNull() ?: 50))
                scope.launch {
                    if (odoKm != v.odometerKm) { app.hub.setOdometer(odoKm); app.repo.rebaseService(odoKm) }
                    snack.showSnackbar("Saved")
                }
            }, Modifier.fillMaxWidth()) { Text("Save") }
        }
        car?.let { c ->
            Panel("From your car", icon = R.drawable.car) {
                Kv("VIN", c.vin)
                c.maker?.let { Kv("Maker", it) }
                c.year?.let { Kv("Model year", "$it") }
                val suggestion = listOfNotNull(c.maker, c.year?.toString()).joinToString(" ")
                if (suggestion.isNotBlank() && nick != suggestion) OutlinedButton(onClick = { nick = suggestion }) { Text("Name it \"$suggestion\"") }
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
    val flip = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val p = flip.value
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(52.dp)
                .graphicsLayer {
                    rotationY = if (p < 0.5f) p * 180f else (p - 1f) * 180f
                    cameraDistance = 12f * density
                }
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        if (flip.isRunning || flip.value > 0f) return@detectTapGestures
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        scope.launch {
                            flip.animateTo(1f, androidx.compose.animation.core.tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                            kotlinx.coroutines.delay(1200)
                            flip.animateTo(0f, androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        }
                    })
                },
        ) {
            if (p < 0.5f) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_fg), null,
                Modifier.size(52.dp).scale(1.5f))
            else androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.face), null, Modifier.size(52.dp),
                colorFilter = if (dark) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))) else null)
        }
        Text("LoganLink", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 12.dp))
    }
}


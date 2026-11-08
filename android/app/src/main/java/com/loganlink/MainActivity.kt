package com.loganlink

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loganlink.core.VehicleHub
import com.loganlink.core.service.DriveService
import com.loganlink.ui.AppNav
import com.loganlink.ui.theme.LoganTheme
import kotlinx.coroutines.flow.MutableSharedFlow

val LocalApp = staticCompositionLocalOf<LoganApp> { error("LoganApp not provided") }

class MainActivity : ComponentActivity() {
    val openDrive = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val app = application as LoganApp
        if (app.prefs.onboarded) { app.hub.reconnect(); DriveService.start(this) }
    }

    private val driveReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { openDrive.tryEmit(Unit) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LoganApp
        ContextCompat.registerReceiver(this, driveReceiver, IntentFilter(VehicleHub.ACTION_OPEN_DRIVE), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (app.prefs.onboarded) { requestRuntimePermissions(); if (!app.prefs.demo.value) DriveService.start(this) }
        setContent {
            val theme by app.prefs.theme.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalApp provides app) {
                LoganTheme(theme) { AppNav(openDrive) }
            }
        }
    }

    override fun onDestroy() { unregisterReceiver(driveReceiver); super.onDestroy() }

    fun requestRuntimePermissions() {
        val p = buildList {
            if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) }
            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(p.toTypedArray())
    }
}

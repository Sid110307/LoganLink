package com.loganlink.core.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.loganlink.LoganApp
import com.loganlink.MainActivity
import com.loganlink.R
import com.loganlink.core.data.Trip
import com.loganlink.core.link.LinkState
import com.loganlink.ui.theme.Severity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.loganlink.core.nav.MapsNav
import com.loganlink.ui.overlay.CarPanel

class Notifier(private val context: Context) {
    init {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Vehicle alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_TRIP, "Trip summaries", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_LINK, "Connection", NotificationManager.IMPORTANCE_LOW))
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun allowed() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun alert(id: Int, sev: Severity, title: String, text: String) {
        if (!allowed()) return
        NotificationManagerCompat.from(context).notify(id, NotificationCompat.Builder(context, CH_ALERT)
            .setSmallIcon(if (sev >= Severity.CheckSoon) R.drawable.warning else R.drawable.info)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (sev == Severity.StopSafely) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(open()).build())
    }

    @SuppressLint("MissingPermission")
    fun tripSummary(t: Trip) {
        if (!allowed()) return
        val text = buildString {
            append("%.1f km · %d min".format(t.km, t.durationS / 60))
            t.kmPerL?.let { append(" · %.1f km/L".format(it)) }
            if (t.costPaise > 0) append(" · ₹%.0f".format(t.costPaise / 100.0))
            append(" · score ${t.score}")
        }
        NotificationManagerCompat.from(context).notify(2001, NotificationCompat.Builder(context, CH_TRIP)
            .setSmallIcon(R.drawable.route).setContentTitle("Trip finished").setContentText(text)
            .setAutoCancel(true).setContentIntent(open()).build())
    }

    fun linkNotification(text: String): Notification = NotificationCompat.Builder(context, CH_LINK)
        .setSmallIcon(R.drawable.car).setContentTitle("LoganLink").setContentText(text)
        .setOngoing(true).setSilent(true).setContentIntent(open()).build()

    companion object { const val CH_ALERT = "alerts"; const val CH_TRIP = "trips"; const val CH_LINK = "link" }
}

object OverlayControl {
    val pinned = MutableStateFlow(false)
    internal val dismissedForNav = MutableStateFlow(false)
    fun dismiss() { pinned.value = false; dismissedForNav.value = true }
}

class DriveService : LifecycleService(), SavedStateRegistryOwner {
    private val savedState = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    private var overlay: ComposeView? = null

    override fun onCreate() {
        savedState.performAttach()
        savedState.performRestore(null)
        super.onCreate()
        val app = application as LoganApp
        val hub = app.hub
        val notifier = Notifier(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, 7, notifier.linkNotification("Waiting for your car…"), type)
        lifecycleScope.launch {
            combine(hub.state, hub.activeTrip) { s, t ->
                when {
                    t != null -> "Driving · %.1f km".format(kotlin.math.floor(t.distanceKm * 2) / 2)
                    s == LinkState.Ready -> "Connected to your car"
                    else -> s.label
                }
            }.distinctUntilChanged().collect { text ->
                getSystemService(NotificationManager::class.java).notify(7, notifier.linkNotification(text))
            }
        }
        lifecycleScope.launch {
            MapsNav.info.collect { if (it == null) OverlayControl.dismissedForNav.value = false }
        }
        lifecycleScope.launch {
            combine(OverlayControl.pinned, MapsNav.info, app.prefs.overlayWithMaps, OverlayControl.dismissedForNav) { pin, nav, auto, dismissed ->
                pin || (auto && nav != null && !dismissed)
            }.collect { show -> if (show && Settings.canDrawOverlays(this@DriveService)) showOverlay() else hideOverlay() }
        }
    }

    private fun showOverlay() {
        if (overlay != null) return
        val wm = getSystemService(WindowManager::class.java)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 160 }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DriveService)
            setViewTreeSavedStateRegistryOwner(this@DriveService)
            setContent {
                CarPanel(
                    hub = (application as LoganApp).hub,
                    onDrag = { dx, dy ->
                        lp.x += dx.toInt(); lp.y += dy.toInt()
                        runCatching { wm.updateViewLayout(this, lp) }
                    },
                    onClose = { OverlayControl.dismiss() },
                    onOpenApp = {
                        startActivity(Intent(this@DriveService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                )
            }
        }
        runCatching { wm.addView(view, lp); overlay = view }
    }

    private fun hideOverlay() {
        overlay?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        overlay = null
    }

    override fun onDestroy() { hideOverlay(); super.onDestroy() }

    companion object {
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DriveService::class.java)) }
        }
        fun stop(context: Context) { context.stopService(Intent(context, DriveService::class.java)) }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
class PresenceService : CompanionDeviceService() {
    @Deprecated("Deprecated in API 33")
    override fun onDeviceAppeared(address: String) { DriveService.start(this); (application as LoganApp).hub.reconnect() }

    override fun onDeviceAppeared(info: AssociationInfo) {
        DriveService.start(this); (application as LoganApp).hub.reconnect()
    }
}

package com.loganlink.core.nav

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.loganlink.LoganApp
import com.loganlink.core.service.DriveService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NavInfo(
    val primary: String,
    val secondary: String?,
    val eta: String?,
    val distance: String?,
    val arrow: Bitmap?,
    val updatedAt: Long = System.currentTimeMillis(),
)

object MapsNav {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"
    private val _info = MutableStateFlow<NavInfo?>(null)
    val info: StateFlow<NavInfo?> = _info.asStateFlow()

    private val distanceRx = Regex("""(\d+(?:[.,]\d+)?)\s?(km|m|mi|ft)\b""", RegexOption.IGNORE_CASE)

    internal fun publish(n: Notification) {
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        if (title.isEmpty() && text == null) return
        val distance = distanceRx.find(title)?.value ?: text?.let { distanceRx.find(it)?.value }
        _info.value = NavInfo(title, text, sub, distance, arrowOf(n))
    }

    internal fun clear() { _info.value = null }

    private fun arrowOf(n: Notification): Bitmap? = runCatching {
        val icon: Icon? = n.getLargeIcon()
        (icon?.loadDrawable(null) as? BitmapDrawable)?.bitmap
    }.getOrNull()

    fun accessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, MapsNavListener::class.java).flattenToString()
        return flat.split(':').any { it == me }
    }

    fun isNavigation(sbn: StatusBarNotification) =
        sbn.packageName == MAPS_PACKAGE && sbn.isOngoing &&
            (sbn.notification.category == Notification.CATEGORY_NAVIGATION || sbn.notification.category == null)
}

class MapsNavListener : NotificationListenerService() {
    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.firstOrNull(MapsNav::isNavigation)?.let { onNotificationPosted(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        FuelAutoLog.handle(application as LoganApp, sbn)
        if (!MapsNav.isNavigation(sbn)) return
        val first = MapsNav.info.value == null
        MapsNav.publish(sbn.notification)
        val prefs = (application as LoganApp).prefs
        if (first && prefs.overlayWithMaps.value) DriveService.start(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (MapsNav.isNavigation(sbn)) MapsNav.clear()
    }

    override fun onListenerDisconnected() = MapsNav.clear()
}

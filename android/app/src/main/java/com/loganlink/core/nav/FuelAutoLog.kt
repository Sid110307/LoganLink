package com.loganlink.core.nav

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.loganlink.LoganApp
import com.loganlink.core.data.Fill
import com.loganlink.core.service.Notifier
import com.loganlink.ui.theme.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FuelAutoLog {
    private val sources = setOf(
        "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
        "com.amazon.mShop.android.shopping", "com.mobikwik_new", "com.dreamplug.androidapp",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
    )
    private val fuelWords = Regex(
        """petrol|fuel|filling|service st|\bhp\b|hpcl|hindustan petro|iocl|indian oil|indianoil|bpcl|bharat petro|nayara|essar|jio[- ]?bp|reliance bp|shell|\bpump\b""",
        RegexOption.IGNORE_CASE,
    )
    private val debitWords = Regex("""paid|debited|sent|spent|payment of|purchase""", RegexOption.IGNORE_CASE)
    private val creditWords = Regex("""received|credited|refund|cashback""", RegexOption.IGNORE_CASE)
    private val amount = Regex("""(?:₹|rs\.?|inr)\s?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastKey = ""
    private var lastAt = 0L

    fun handle(app: LoganApp, sbn: StatusBarNotification) {
        if (sbn.packageName !in sources || !app.prefs.autoFuel.value) return
        val ex = sbn.notification.extras
        val text = listOfNotNull(
            ex.getCharSequence(Notification.EXTRA_TITLE), ex.getCharSequence(Notification.EXTRA_TEXT),
            ex.getCharSequence(Notification.EXTRA_BIG_TEXT),
        ).joinToString(" ")
        if (!fuelWords.containsMatchIn(text) || !debitWords.containsMatchIn(text) || creditWords.containsMatchIn(text)) return
        val rupees = amount.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return
        if (rupees < 100 || rupees > 10_000) return

        val now = System.currentTimeMillis()
        val key = "%.2f".format(rupees)
        if (key == lastKey && now - lastAt < 15 * 60_000) return
        lastKey = key; lastAt = now

        val v = app.prefs.vehicle.value
        val litres = rupees * 100 / v.fuelPricePaise
        val full = litres >= v.tankLitres * 0.6
        scope.launch {
            app.repo.addFill(Fill(0, now, litres, (rupees * 100).toLong(), v.odometerKm, full))
            runCatching { app.hub.logFill(litres, (rupees * 100).toLong()) }
        }
        Notifier(app).alert(3001, Severity.Info, "Fill-up logged",
            "₹%,.0f · about %.1f L at ₹%.2f/L. Edit it in Fuel log if that's off.".format(rupees, litres, v.fuelPricePaise / 100.0))
    }
}

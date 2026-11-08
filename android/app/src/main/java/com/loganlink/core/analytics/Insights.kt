package com.loganlink.core.analytics

import com.loganlink.core.data.Crank
import com.loganlink.core.data.DtcEntry
import com.loganlink.core.data.ServiceItem
import com.loganlink.core.data.Trip
import com.loganlink.ui.theme.Severity
import kotlin.math.max

data class Insight(val title: String, val detail: String, val action: String, val severity: Severity, val route: String? = null)

object Insights {
    fun tripScore(km: Double, hb: Int, ha: Int, hc: Int, idleS: Long, durationS: Long, overrevS: Int): Int {
        if (km < 0.5) return 100
        val events = (hb * 6 + ha * 4 + hc * 4) * (100 / km.coerceAtLeast(10.0)) / 10
        val idlePct = if (durationS == 0L) 0.0 else idleS * 100.0 / durationS
        val s = 100 - events - max(0.0, idlePct - 60) * 0.2 - overrevS * 0.2
        return s.coerceIn(0.0, 100.0).toInt()
    }

    fun batteryScore(c: Crank): Int {
        val soc = ((c.restMv / 1000.0 - 11.9) / (12.65 - 11.9)).coerceIn(0.0, 1.0)
        val crank = ((c.minMv / 1000.0 - 8.5) / (10.5 - 8.5)).coerceIn(0.0, 1.0)
        val rec = (1 - (c.recoverMs - 800) / 4000.0).coerceIn(0.0, 1.0)
        return (100 * (0.35 * soc + 0.5 * crank + 0.15 * rec)).toInt()
    }

    fun socPercent(restV: Double): Int = (((restV - 11.89) / (12.65 - 11.89)) * 100).coerceIn(0.0, 100.0).toInt()

    fun batteryInsight(cranks: List<Crank>): Insight? {
        if (cranks.size < 5) return null
        val last = cranks.take(10)
        val avgMin = last.map { it.minMv }.average() / 1000
        val avgRest = last.map { it.restMv }.average() / 1000
        val older = cranks.drop(10).take(10).takeIf { it.size >= 5 }?.map { it.minMv }?.average()?.div(1000)
        return when {
            avgMin < 9.6 -> Insight(
                "Battery is struggling to start the engine",
                "Cranking voltage averaged %.1f V over the last %d starts. A healthy battery stays above about 10 V.".format(avgMin, last.size),
                "Get the battery load-tested before a long trip.", Severity.CheckSoon, "battery")
            older != null && older - avgMin > 0.25 -> Insight(
                "Battery is weakening",
                "Cranking voltage dropped %.2f V compared with earlier starts. That's a typical sign of ageing.".format(older - avgMin),
                "No action yet. Plan a replacement within a few months.", Severity.Info, "battery")
            avgRest < 12.3 -> Insight(
                "Battery isn't fully charging",
                "Resting voltage averages %.2f V (≈%d %% charge). Short trips may not be recharging it.".format(avgRest, socPercent(avgRest)),
                "Take a 30-minute drive, or check the charging system if this continues.", Severity.Info, "battery")
            else -> null
        }
    }

    fun trimInsight(avgLtft: Double?): Insight? {
        val m = avgLtft ?: return null
        if (kotlin.math.abs(m) <= 10) return null
        val lean = m > 0
        return Insight(
            if (lean) "Engine is running lean" else "Engine is running rich",
            "The engine computer is correcting fuel by %+.0f %% long-term. On the K7M this commonly points to %s.".format(
                m, if (lean) "an intake vacuum leak, a weak fuel pump or clogged injectors"
                else "a leaking injector, a faulty MAP/temperature sensor or a sticking purge valve"),
            "Book a diagnosis. Ignoring it raises fuel use and can damage the catalytic converter.", Severity.CheckSoon, "engine")
    }

    fun dtcInsight(dtcs: List<DtcEntry>): Insight? {
        val active = dtcs.filter { it.cleared == null }
        if (active.isEmpty()) return null
        val worst = active.maxOf { DtcCatalog.lookup(it.code).severity.ordinal }
        return Insight(
            "${active.size} fault code${if (active.size > 1) "s" else ""} stored",
            active.joinToString(" · ") { "${it.code} ${DtcCatalog.lookup(it.code).title}" },
            "Open Diagnostics for what each code means.", Severity.entries[worst], "diagnostics")
    }

    fun serviceInsight(items: List<ServiceItem>, odo: Int, now: Long): Insight? {
        val due = items.filter { it.used(odo, now) >= 0.9 }.maxByOrNull { it.used(odo, now) } ?: return null
        val over = due.used(odo, now) >= 1.0
        val km = due.kmLeft(odo); val days = due.daysLeft(now)
        return Insight(
            "${due.name} ${if (over) "overdue" else "due soon"}",
            listOfNotNull(km?.let { if (it < 0) "%,d km over".format(-it) else "%,d km left".format(it) },
                days?.let { if (it < 0) "${-it} days over" else "$it days left" }).joinToString(" · "),
            "Mark it done in Maintenance after servicing.", if (over) Severity.CheckSoon else Severity.Info, "maintenance")
    }

    fun weeklyKm(trips: List<Trip>, now: Long) = trips.filter { now - it.startMs < 7 * 86_400_000L }.sumOf { it.km }
}

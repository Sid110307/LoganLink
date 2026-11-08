package com.loganlink.core.data

data class Trip(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val distanceM: Long,
    val idleS: Long,
    val fuelMl: Int,
    val maxSpeed: Double,
    val harshBrake: Int,
    val harshAccel: Int,
    val harshCorner: Int,
    val overrevS: Int,
    val score: Int,
    val costPaise: Long,
) {
    val km get() = distanceM / 1000.0
    val durationS get() = ((endMs - startMs) / 1000).coerceAtLeast(1)
    val avgSpeed get() = km / (durationS / 3600.0)
    val litres get() = fuelMl / 1000.0
    val kmPerL get() = if (fuelMl > 50) km / litres else null
}

data class TripPoint(val t: Long, val lat: Double, val lon: Double, val speed: Double)

data class Sample(
    val t: Long, val speed: Double?, val rpm: Int?, val coolant: Double?, val vbat: Double?,
    val fuelRate: Double?, val ltft: Int?, val map: Double?,
)

data class Crank(val t: Long, val restMv: Int, val minMv: Int, val crankMs: Int, val recoverMs: Int)

data class DtcEntry(val code: String, val pending: Boolean, val firstSeen: Long, val lastSeen: Long, val cleared: Long?)

data class Fill(val id: Long, val t: Long, val litres: Double, val pricePaise: Long, val odometerKm: Int, val full: Boolean)

data class Park(val t: Long, val lat: Double, val lon: Double, val accuracyM: Float)

data class ServiceItem(
    val id: Long, val name: String, val intervalKm: Int, val intervalDays: Int, val lastKm: Int, val lastMs: Long,
) {
    fun kmLeft(odo: Int) = if (intervalKm > 0) lastKm + intervalKm - odo else null
    fun daysLeft(now: Long) = if (intervalDays > 0) ((lastMs + intervalDays * 86_400_000L - now) / 86_400_000L).toInt() else null
    fun used(odo: Int, now: Long): Double {
        val a = if (intervalKm > 0) (odo - lastKm).toDouble() / intervalKm else 0.0
        val b = if (intervalDays > 0) (now - lastMs).toDouble() / (intervalDays * 86_400_000.0) else 0.0
        return maxOf(a, b).coerceAtLeast(0.0)
    }
}

data class AppEvent(val id: Long, val t: Long, val kind: String, val severity: Int, val title: String, val detail: String)

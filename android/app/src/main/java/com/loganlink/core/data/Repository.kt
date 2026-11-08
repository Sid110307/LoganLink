package com.loganlink.core.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.loganlink.core.analytics.Insights
import com.loganlink.core.protocol.LiveFrame
import com.loganlink.core.protocol.LogRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private class Db(context: Context) : SQLiteOpenHelper(context, "loganlink.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        listOf(
            """CREATE TABLE trip(id INTEGER PRIMARY KEY AUTOINCREMENT, start_ms INTEGER, end_ms INTEGER, distance_m INTEGER,
               idle_s INTEGER, fuel_ml INTEGER, max_speed REAL, harsh_brake INTEGER, harsh_accel INTEGER,
               harsh_corner INTEGER, overrev_s INTEGER, score INTEGER, cost_paise INTEGER, source_seq INTEGER UNIQUE)""",
            "CREATE TABLE point(trip_id INTEGER, t INTEGER, lat REAL, lon REAL, speed REAL)",
            "CREATE INDEX point_trip ON point(trip_id)",
            """CREATE TABLE sample(t INTEGER PRIMARY KEY, speed REAL, rpm INTEGER, coolant REAL, vbat REAL, fuel_rate REAL,
               ltft INTEGER, map REAL)""",
            "CREATE TABLE crank(t INTEGER PRIMARY KEY, rest_mv INTEGER, min_mv INTEGER, crank_ms INTEGER, recover_ms INTEGER)",
            "CREATE TABLE dtc(code TEXT PRIMARY KEY, pending INTEGER, first_ms INTEGER, last_ms INTEGER, cleared_ms INTEGER)",
            "CREATE TABLE fill(id INTEGER PRIMARY KEY AUTOINCREMENT, t INTEGER, litres REAL, price_paise INTEGER, odo_km INTEGER, full INTEGER)",
            "CREATE TABLE park(t INTEGER PRIMARY KEY, lat REAL, lon REAL, acc REAL)",
            """CREATE TABLE service(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, interval_km INTEGER, interval_days INTEGER,
               last_km INTEGER, last_ms INTEGER)""",
            "CREATE TABLE event(id INTEGER PRIMARY KEY AUTOINCREMENT, t INTEGER, kind TEXT, severity INTEGER, title TEXT, detail TEXT)",
        ).forEach(db::execSQL)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

class Repository(private val context: Context, private val prefs: Prefs) {
    private val db = Db(context).writableDatabase
    private val changes = MutableStateFlow(0L)
    private fun bump() { changes.value++ }

    private fun <T> live(query: () -> T): Flow<T> = changes.map { query() }.flowOn(Dispatchers.IO)

    private inline fun <T> Cursor.all(row: (Cursor) -> T): List<T> = use { c -> buildList { while (c.moveToNext()) add(row(c)) } }
    private fun Cursor.dOrNull(i: Int) = if (isNull(i)) null else getDouble(i)
    private fun Cursor.iOrNull(i: Int) = if (isNull(i)) null else getInt(i)

    val trips: Flow<List<Trip>> = live {
        db.rawQuery("SELECT * FROM trip ORDER BY start_ms DESC", null).all(::tripOf)
    }

    private fun tripOf(c: Cursor) = Trip(
        c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getInt(5), c.getDouble(6),
        c.getInt(7), c.getInt(8), c.getInt(9), c.getInt(10), c.getInt(11), c.getLong(12),
    )

    suspend fun trip(id: Long): Trip? = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT * FROM trip WHERE id=?", arrayOf("$id")).all(::tripOf).firstOrNull()
    }

    suspend fun tripPoints(id: Long): List<TripPoint> = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT t,lat,lon,speed FROM point WHERE trip_id=? ORDER BY t", arrayOf("$id"))
            .all { TripPoint(it.getLong(0), it.getDouble(1), it.getDouble(2), it.getDouble(3)) }
    }

    suspend fun samples(from: Long, to: Long): List<Sample> = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT * FROM sample WHERE t BETWEEN ? AND ? ORDER BY t", arrayOf("$from", "$to")).all {
            Sample(it.getLong(0), it.dOrNull(1), it.iOrNull(2), it.dOrNull(3), it.dOrNull(4), it.dOrNull(5), it.iOrNull(6), it.dOrNull(7))
        }
    }

    fun insertTrip(t: Trip, points: List<TripPoint>, sourceSeq: Long? = null): Long {
        val cv = ContentValues().apply {
            put("start_ms", t.startMs); put("end_ms", t.endMs); put("distance_m", t.distanceM); put("idle_s", t.idleS)
            put("fuel_ml", t.fuelMl); put("max_speed", t.maxSpeed); put("harsh_brake", t.harshBrake)
            put("harsh_accel", t.harshAccel); put("harsh_corner", t.harshCorner); put("overrev_s", t.overrevS)
            put("score", t.score); put("cost_paise", t.costPaise); sourceSeq?.let { put("source_seq", it) }
        }
        val id = db.insertWithOnConflict("trip", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        if (id > 0 && points.isNotEmpty()) {
            db.beginTransaction()
            try {
                points.forEach { p ->
                    db.insert("point", null, ContentValues().apply {
                        put("trip_id", id); put("t", p.t); put("lat", p.lat); put("lon", p.lon); put("speed", p.speed)
                    })
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        bump()
        return id
    }

    fun deleteTrip(id: Long) { db.delete("point", "trip_id=?", arrayOf("$id")); db.delete("trip", "id=?", arrayOf("$id")); bump() }

    fun ingest(records: List<LogRecord>) {
        if (records.isEmpty()) return
        db.beginTransaction()
        try {
            for (r in records) when (r) {
                is LogRecord.Sample -> insertSample(r.utc * 1000, r.live, notify = false)
                is LogRecord.Crank -> db.insertWithOnConflict("crank", null, ContentValues().apply {
                    put("t", r.utc * 1000); put("rest_mv", r.restMv); put("min_mv", r.minMv)
                    put("crank_ms", r.crankMs); put("recover_ms", r.recoverMs)
                }, SQLiteDatabase.CONFLICT_REPLACE)
                is LogRecord.TripEnd -> {
                    val s = r.s
                    val price = prefs.vehicle.value.fuelPricePaise
                    insertTrip(
                        Trip(0, s.startUtc * 1000, (s.startUtc + s.durationS) * 1000, s.distanceM, s.idleS, s.fuelMl, s.maxSpeed,
                            s.harshBrake, s.harshAccel, s.harshCorner, s.overrevS,
                            Insights.tripScore(s.distanceM / 1000.0, s.harshBrake, s.harshAccel, s.harshCorner, s.idleS, s.durationS, s.overrevS),
                            (s.fuelMl / 1000.0 * price).toLong()),
                        emptyList(), r.seq,
                    )
                }
                is LogRecord.Dtc -> r.codes.forEach { upsertDtc(it.code, it.pending, r.utc * 1000) }
                is LogRecord.Event -> if (r.kind == 1) addEvent("crash", 3, "Possible impact detected", "The module recorded a sharp impact (> 4 g).", r.utc * 1000)
                else -> {}
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        prefs.lastSeq = max(prefs.lastSeq, records.maxOf { it.seq })
        bump()
    }

    fun insertSample(t: Long, f: LiveFrame, notify: Boolean = true) {
        db.insertWithOnConflict("sample", null, ContentValues().apply {
            put("t", t); f.speed?.let { put("speed", it) }; f.rpm?.let { put("rpm", it) }; f.coolant?.let { put("coolant", it) }
            f.vbat?.let { put("vbat", it) }; f.fuelRate?.let { put("fuel_rate", it) }; f.ltft?.let { put("ltft", it) }
            f.map?.let { put("map", it) }
        }, SQLiteDatabase.CONFLICT_REPLACE)
        if (notify && t / 1000 % 30 == 0L) bump()
    }

    val cranks: Flow<List<Crank>> = live {
        db.rawQuery("SELECT * FROM crank ORDER BY t DESC LIMIT 120", null)
            .all { Crank(it.getLong(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4)) }
    }

    fun addCrank(c: Crank) {
        db.insertWithOnConflict("crank", null, ContentValues().apply {
            put("t", c.t); put("rest_mv", c.restMv); put("min_mv", c.minMv); put("crank_ms", c.crankMs); put("recover_ms", c.recoverMs)
        }, SQLiteDatabase.CONFLICT_REPLACE); bump()
    }

    val vbatDaily: Flow<List<Pair<Long, Double>>> = live {
        db.rawQuery(
            "SELECT (t/86400000)*86400000 d, AVG(vbat) FROM sample WHERE vbat IS NOT NULL AND t > ? GROUP BY d ORDER BY d",
            arrayOf("${System.currentTimeMillis() - 30 * 86_400_000L}"),
        ).all { it.getLong(0) to it.getDouble(1) }
    }

    val dtcs: Flow<List<DtcEntry>> = live {
        db.rawQuery("SELECT * FROM dtc ORDER BY cleared_ms IS NOT NULL, last_ms DESC", null).all {
            DtcEntry(it.getString(0), it.getInt(1) != 0, it.getLong(2), it.getLong(3), if (it.isNull(4)) null else it.getLong(4))
        }
    }

    fun upsertDtc(code: String, pending: Boolean, t: Long) {
        val exists = db.rawQuery("SELECT 1 FROM dtc WHERE code=?", arrayOf(code)).use { it.moveToFirst() }
        if (exists) db.execSQL("UPDATE dtc SET last_ms=?, pending=?, cleared_ms=NULL WHERE code=?", arrayOf<Any>(t, if (pending) 1 else 0, code))
        else db.insert("dtc", null, ContentValues().apply {
            put("code", code); put("pending", if (pending) 1 else 0); put("first_ms", t); put("last_ms", t)
        })
    }

    fun syncActiveDtcs(codes: List<Pair<String, Boolean>>) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            codes.forEach { (c, p) -> upsertDtc(c, p, now) }
            val keep = codes.map { it.first }.toSet()
            db.rawQuery("SELECT code FROM dtc WHERE cleared_ms IS NULL", null).all { it.getString(0) }
                .filter { it !in keep }.forEach { db.execSQL("UPDATE dtc SET cleared_ms=? WHERE code=?", arrayOf<Any>(now, it)) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        bump()
    }

    val lastPark: Flow<Park?> = live {
        db.rawQuery("SELECT * FROM park ORDER BY t DESC LIMIT 1", null)
            .all { Park(it.getLong(0), it.getDouble(1), it.getDouble(2), it.getFloat(3)) }.firstOrNull()
    }

    fun savePark(p: Park) {
        db.insertWithOnConflict("park", null, ContentValues().apply {
            put("t", p.t); put("lat", p.lat); put("lon", p.lon); put("acc", p.accuracyM)
        }, SQLiteDatabase.CONFLICT_REPLACE); bump()
    }

    val fills: Flow<List<Fill>> = live {
        db.rawQuery("SELECT * FROM fill ORDER BY t DESC", null)
            .all { Fill(it.getLong(0), it.getLong(1), it.getDouble(2), it.getLong(3), it.getInt(4), it.getInt(5) != 0) }
    }

    fun addFill(f: Fill) {
        db.insert("fill", null, ContentValues().apply {
            put("t", f.t); put("litres", f.litres); put("price_paise", f.pricePaise); put("odo_km", f.odometerKm); put("full", if (f.full) 1 else 0)
        }); bump()
    }

    fun deleteFill(id: Long) { db.delete("fill", "id=?", arrayOf("$id")); bump() }

    val service: Flow<List<ServiceItem>> = live {
        db.rawQuery("SELECT * FROM service ORDER BY name", null)
            .all { ServiceItem(it.getLong(0), it.getString(1), it.getInt(2), it.getInt(3), it.getInt(4), it.getLong(5)) }
    }

    fun saveService(s: ServiceItem) {
        val cv = ContentValues().apply {
            put("name", s.name); put("interval_km", s.intervalKm); put("interval_days", s.intervalDays)
            put("last_km", s.lastKm); put("last_ms", s.lastMs)
        }
        if (s.id == 0L) db.insert("service", null, cv) else db.update("service", cv, "id=?", arrayOf("${s.id}"))
        bump()
    }

    fun deleteService(id: Long) { db.delete("service", "id=?", arrayOf("$id")); bump() }

    fun ensureDefaultService(odoKm: Int) {
        val n = db.rawQuery("SELECT COUNT(*) FROM service", null).use { it.moveToFirst(); it.getInt(0) }
        if (n > 0) return
        val now = System.currentTimeMillis()
        listOf(
            Triple("Engine oil & filter", 10_000, 365), Triple("Air filter", 20_000, 730), Triple("Spark plugs", 20_000, 730),
            Triple("Timing belt & tensioner", 60_000, 1460), Triple("Coolant", 40_000, 730), Triple("Brake fluid", 40_000, 730),
            Triple("Fuel filter", 40_000, 1460), Triple("Battery check", 0, 180),
        ).forEach { (n2, km, d) -> saveService(ServiceItem(0, n2, km, d, odoKm, now)) }
    }

    val events: Flow<List<AppEvent>> = live {
        db.rawQuery("SELECT * FROM event ORDER BY t DESC LIMIT 100", null)
            .all { AppEvent(it.getLong(0), it.getLong(1), it.getString(2), it.getInt(3), it.getString(4), it.getString(5)) }
    }

    fun addEvent(kind: String, severity: Int, title: String, detail: String, t: Long = System.currentTimeMillis()) {
        db.insert("event", null, ContentValues().apply {
            put("t", t); put("kind", kind); put("severity", severity); put("title", title); put("detail", detail)
        }); bump()
    }

    suspend fun exportCsv(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "loganlink-trips-${System.currentTimeMillis() / 1000}.csv")
        f.bufferedWriter().use { w ->
            w.appendLine("start,end,distance_km,duration_min,fuel_l,km_per_l,max_kmh,harsh_brake,harsh_accel,harsh_corner,score,cost_inr")
            db.rawQuery("SELECT * FROM trip ORDER BY start_ms", null).all(::tripOf).forEach { t ->
                w.appendLine(listOf(
                    java.time.Instant.ofEpochMilli(t.startMs), java.time.Instant.ofEpochMilli(t.endMs), "%.2f".format(t.km),
                    "%.1f".format(t.durationS / 60.0), "%.2f".format(t.litres), t.kmPerL?.let { "%.1f".format(it) } ?: "",
                    "%.0f".format(t.maxSpeed), t.harshBrake, t.harshAccel, t.harshCorner, t.score, "%.2f".format(t.costPaise / 100.0),
                ).joinToString(","))
            }
        }
        f
    }

    fun wipeAll() {
        listOf("trip", "point", "sample", "crank", "dtc", "fill", "park", "event").forEach { db.delete(it, null, null) }
        prefs.lastSeq = -1
        bump()
    }

    fun seedDemo() {
        if (prefs.demoSeeded) return
        val r = Random(7)
        val now = System.currentTimeMillis()
        val home = 18.5362 to 73.8937
        db.beginTransaction()
        try {
            for (day in 30 downTo 0) {
                val dayStart = now - day * 86_400_000L
                repeat(if (day % 7 == 0) 1 else 2) { k ->
                    val start = dayStart - (if (k == 0) 9 else 3) * 3_600_000L + r.nextLong(1_800_000)
                    val km = if (day % 7 == 0) 45 + r.nextDouble() * 60 else 6 + r.nextDouble() * 12
                    val dur = (km / (if (km > 40) 52.0 else 22.0) * 3600).toLong()
                    val fuel = (km / (if (km > 40) 15.5 else 11.0 + r.nextDouble() * 2) * 1000).toInt()
                    val hb = r.nextInt(0, 4); val ha = r.nextInt(0, 3); val hc = r.nextInt(0, 2)
                    val idle = (dur * (0.08 + r.nextDouble() * 0.15)).toLong()
                    val trip = Trip(0, start, start + dur * 1000, (km * 1000).toLong(), idle, fuel, 40 + r.nextDouble() * (if (km > 40) 60 else 25),
                        hb, ha, hc, r.nextInt(0, 6), Insights.tripScore(km, hb, ha, hc, idle, dur, 0), (fuel / 1000.0 * 10500).toLong())
                    val pts = (0..40).map { i ->
                        val a = i / 40.0 * Math.PI * (1.2 + k)
                        val rad = km / 220.0
                        TripPoint(start + i * dur * 25, home.first + rad * sin(a) * 0.6, home.second + rad * (1 - cos(a)), 20 + r.nextDouble() * 40)
                    }
                    insertTrip(trip, pts)
                }
                val minMv = (10_300 - (30 - day) * 12 + r.nextInt(-80, 80))
                db.insertWithOnConflict("crank", null, ContentValues().apply {
                    put("t", dayStart - 9 * 3_600_000L); put("rest_mv", 12_520 - (30 - day) * 3); put("min_mv", minMv)
                    put("crank_ms", 900 + r.nextInt(400)); put("recover_ms", 1500 + r.nextInt(800))
                }, SQLiteDatabase.CONFLICT_REPLACE)
                for (h in 0 until 24 step 2) db.insertWithOnConflict("sample", null, ContentValues().apply {
                    put("t", dayStart - h * 3_600_000L); put("vbat", if (h in 8..10) 14.0 else 12.45 + r.nextDouble() * 0.15)
                    put("ltft", 3 + r.nextInt(3)); put("coolant", 88.0)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            upsertDtc("P0134", false, now - 4 * 86_400_000L)
            upsertDtc("P0420", true, now - 86_400_000L)
            listOf(48.2 to 25, 41.0 to 12, 45.5 to 2).forEach { (l, d) ->
                db.insert("fill", null, ContentValues().apply {
                    put("t", now - d * 86_400_000L); put("litres", l); put("price_paise", (l * 10500).toLong()); put("odo_km", 84_000 + (30 - d) * 40); put("full", 1)
                })
            }
            db.insertWithOnConflict("park", null, ContentValues().apply {
                put("t", now - 2 * 3_600_000L); put("lat", home.first); put("lon", home.second); put("acc", 8f)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        prefs.demoSeeded = true
        if (prefs.vehicle.value.odometerKm == 0) prefs.saveVehicle(prefs.vehicle.value.copy(odometerKm = 85_240, plate = "MH 12 AB 1234"))
        ensureDefaultService(prefs.vehicle.value.odometerKm - 6_400)
        bump()
    }
}

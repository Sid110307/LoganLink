package com.loganlink.core

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.loganlink.core.analytics.DtcCatalog
import com.loganlink.core.analytics.Insights
import com.loganlink.core.data.Crank
import com.loganlink.core.data.Pairing
import com.loganlink.core.data.Park
import com.loganlink.core.data.Prefs
import com.loganlink.core.data.Repository
import com.loganlink.core.data.Trip
import com.loganlink.core.data.TripPoint
import com.loganlink.core.link.BleVehicleLink
import com.loganlink.core.link.CmdResult
import com.loganlink.core.link.LinkState
import com.loganlink.core.link.SimVehicleLink
import com.loganlink.core.link.VehicleLink
import com.loganlink.core.protocol.AuthSession
import com.loganlink.core.protocol.Cmd
import com.loganlink.core.protocol.DeviceInfo
import com.loganlink.core.protocol.LiveFrame
import com.loganlink.core.protocol.LogRecord
import com.loganlink.core.protocol.Status
import com.loganlink.core.protocol.le16
import com.loganlink.core.protocol.le32
import com.loganlink.core.service.Notifier
import com.loganlink.ui.theme.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.math.abs

data class ActiveTrip(val startMs: Long, val distanceKm: Double, val fuelL: Double, val maxSpeed: Double,
                      val harshBrake: Int, val harshAccel: Int, val idleS: Double = 0.0)

data class OtaProgress(val sent: Long, val total: Long, val message: String, val done: Boolean = false, val error: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleHub(private val context: Context, val prefs: Prefs, val repo: Repository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notifier = Notifier(context)

    private val _link = MutableStateFlow<VehicleLink?>(null)
    val link: StateFlow<VehicleLink?> = _link.asStateFlow()

    val state: StateFlow<LinkState> = _link.flatMapLatest { it?.state ?: flowOf(LinkState.Idle) }
        .stateIn(scope, SharingStarted.Eagerly, LinkState.Idle)
    val rssi: StateFlow<Int?> = _link.flatMapLatest { it?.rssi ?: flowOf(null) }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _live = MutableStateFlow<LiveFrame?>(null)

    val live: StateFlow<LiveFrame?> = _live.asStateFlow()
    private val _history = MutableStateFlow<List<LiveFrame>>(emptyList())
    val history: StateFlow<List<LiveFrame>> = _history.asStateFlow()

    private val _info = MutableStateFlow<DeviceInfo?>(null)
    val info: StateFlow<DeviceInfo?> = _info.asStateFlow()
    private val _trip = MutableStateFlow<ActiveTrip?>(null)
    val activeTrip: StateFlow<ActiveTrip?> = _trip.asStateFlow()
    private val _sync = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _sync.asStateFlow()
    private val _ota = MutableStateFlow<OtaProgress?>(null)
    val ota: StateFlow<OtaProgress?> = _ota.asStateFlow()

    val demo get() = prefs.demo.value
    val isOwner get() = (_link.value?.role ?: 1) == 0

    private var jobs = mutableListOf<Job>()
    private var phoneLoc: Location? = null
    private val routePoints = mutableListOf<TripPoint>()
    private var lastSampleSec = 0L
    private var lastHistAt = 0L
    private var lastFrameAt = 0L
    private var foreground = true

    fun setForeground(fg: Boolean) {
        foreground = fg
        _link.value?.setForeground(fg)
        scope.launch { if (state.value == LinkState.Ready) command(Cmd.SET_LIVE_RATE, byteArrayOf(if (fg) 10 else 2)) }
    }
    private var engineOffSince = 0L

    init {
        scope.launch {
            combine(prefs.pairing, prefs.demo) { p, d -> p to d }.collect { (p, d) -> rebind(p, d) }
        }
    }

    private fun rebind(p: Pairing?, demo: Boolean) {
        jobs.forEach { it.cancel() }; jobs.clear()
        _link.value?.stop()
        _live.value = null; _info.value = null; _trip.value = null; _history.value = emptyList()
        val l: VehicleLink = when {
            demo -> { scope.launch(Dispatchers.IO) { repo.seedDemo() }; SimVehicleLink() }
            p != null -> BleVehicleLink(context, p.address, AuthSession(p.userId, p.kUser))
            else -> { _link.value = null; return }
        }
        _link.value = l
        jobs += scope.launch { l.state.collect { if (it == LinkState.Ready) onReady(l) else if (it != LinkState.Authenticating) onDisconnected() } }
        jobs += scope.launch { l.live.collect(::onLive) }
        jobs += scope.launch { l.dtcs.collect { codes -> repo.syncActiveDtcs(codes.map { it.code to it.pending }); alertDtcs(codes.map { it.code }) } }
        jobs += scope.launch { collectRecords(l) }
        l.start()
    }

    fun reconnect() = rebind(prefs.pairing.value, prefs.demo.value)

    private suspend fun onReady(l: VehicleLink) {
        endJob?.cancel()
        l.setForeground(foreground)
        l.command(Cmd.SET_LIVE_RATE, byteArrayOf(if (foreground) 10 else 2))
        val tz = java.util.TimeZone.getDefault().rawOffset / 60_000
        l.command(Cmd.SET_TIME, le32(System.currentTimeMillis() / 1000) + le16(tz))
        refreshInfo()
        l.command(Cmd.READ_DTC, byteArrayOf(0))
        if (!demo) syncLogs(l)
    }

    suspend fun refreshInfo() {
        val r = _link.value?.command(Cmd.GET_INFO) ?: return
        if (r.ok) _info.value = DeviceInfo.parse(r.data)
    }

    private suspend fun syncLogs(l: VehicleLink) {
        val from = prefs.lastSeq + 1
        _sync.value = "Syncing history…"
        val r = l.command(Cmd.SYNC_LOG, le32(from) + le16(5000), 8000)
        _sync.value = if (r.ok) null else "Sync failed: ${Status.describe(r.status)}"
    }

    private suspend fun collectRecords(l: VehicleLink) {
        val batch = ArrayList<LogRecord>()
        var lastFlush = System.currentTimeMillis()
        l.records.collect { raw ->
            LogRecord.parse(raw)?.let(batch::add)
            if (batch.size >= 200 || System.currentTimeMillis() - lastFlush > 1500) {
                repo.ingest(batch.toList()); batch.clear(); lastFlush = System.currentTimeMillis()
            }
        }
    }

    private var endJob: Job? = null

    private fun onDisconnected() {
        if (_trip.value == null) return
        endJob?.cancel()
        endJob = scope.launch {
            delay(TRIP_GAP_MS)
            if (state.value != LinkState.Ready) { if (!parkedSaved) captureParking(); finishTrip() }
        }
    }

    private fun onLive(f0: LiveFrame) {
        val loc = phoneLoc
        val f = if (loc != null && System.currentTimeMillis() - loc.time < 5000) f0.copy(
            lat = loc.latitude, lon = loc.longitude,
            speed = f0.speed ?: if (loc.hasSpeed()) loc.speed * 3.6 else null,
        ) else f0
        _live.value = f
        if (f.receivedAt - lastHistAt >= 200) { lastHistAt = f.receivedAt; _history.value = (_history.value + f).takeLast(300) }

        val now = System.currentTimeMillis()
        if (f.engineRunning && now / 1000 != lastSampleSec) { lastSampleSec = now / 1000; scope.launch(Dispatchers.IO) { repo.insertSample(now, f) } }

        trackTrip(f, now)
        checkAlerts(f)
    }

    private fun trackTrip(f: LiveFrame, now: Long) {
        val dt = if (lastFrameAt == 0L) 0.1 else ((f.receivedAt - lastFrameAt).coerceIn(20, 1000)) / 1000.0
        lastFrameAt = f.receivedAt
        val running = f.engineRunning
        val t = _trip.value
        if (running) {
            engineOffSince = 0
            if (t == null) {
                _trip.value = ActiveTrip(now, 0.0, 0.0, 0.0, 0, 0)
                routePoints.clear(); startLocation()
                if (prefs.autoDriveMode.value) context.sendBroadcast(android.content.Intent(ACTION_OPEN_DRIVE).setPackage(context.packageName))
            } else {
                val need = (0.3 / dt).toInt().coerceAtLeast(1)
                val ax = f.accelX ?: 0.0
                brakeRun = if (ax < -0.40) brakeRun + 1 else 0
                accelRun = if (ax > 0.35) accelRun + 1 else 0
                val idle = (f.speed ?: 0.0) < 2
                _trip.value = t.copy(
                    distanceKm = if (f.tripM > 0) f.tripM / 1000.0 else t.distanceKm + (f.speed ?: 0.0) * dt / 3600,
                    fuelL = t.fuelL + (f.fuelRate ?: 0.0) * dt / 3600,
                    maxSpeed = maxOf(t.maxSpeed, f.speed ?: 0.0),
                    harshBrake = t.harshBrake + if (brakeRun == need) 1 else 0,
                    harshAccel = t.harshAccel + if (accelRun == need) 1 else 0,
                    idleS = t.idleS + if (idle) dt else 0.0,
                )
            }
        } else if (t != null) {
            if (engineOffSince == 0L) engineOffSince = now
            if (!parkedSaved && now - engineOffSince > 45_000) { parkedSaved = true; captureParking() }
            if (now - engineOffSince > TRIP_GAP_MS) finishTrip()
        }
        if (running) parkedSaved = false
    }
    private var brakeRun = 0; private var accelRun = 0; private var parkedSaved = false

    private fun finishTrip() {
        val t = _trip.value ?: return
        _trip.value = null
        val end = System.currentTimeMillis()
        if (t.distanceKm < 0.2) return
        val durS = (end - t.startMs) / 1000
        val price = prefs.vehicle.value.fuelPricePaise
        val idleS = t.idleS.toLong()
        val trip = Trip(0, t.startMs, end, (t.distanceKm * 1000).toLong(), idleS, (t.fuelL * 1000).toInt(), t.maxSpeed,
            t.harshBrake, t.harshAccel, 0, 0, Insights.tripScore(t.distanceKm, t.harshBrake, t.harshAccel, 0, idleS, durS, 0),
            (t.fuelL * price).toLong())
        val pts = routePoints.toList()
        scope.launch(Dispatchers.IO) {
            repo.insertTrip(trip, pts)
            prefs.addOdometer(t.distanceKm)
        }
        notifier.tripSummary(trip)
        if (!parkedSaved) captureParking()
        stopLocation()
    }

    private fun saveParking(l: Location) =
        scope.launch(Dispatchers.IO) { repo.savePark(Park(System.currentTimeMillis(), l.latitude, l.longitude, l.accuracy)) }

    @SuppressLint("MissingPermission")
    private fun captureParking() {
        if (demo) return
        val good = lastGoodFix?.takeIf { System.currentTimeMillis() - it.time < 3 * 60_000 }
        val cur = phoneLoc
        when {
            cur != null && cur.accuracy <= 25 && System.currentTimeMillis() - cur.time < 60_000 -> saveParking(cur)
            good != null -> saveParking(good)
            else -> {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                runCatching {
                    if (Build.VERSION.SDK_INT >= 30) lm.getCurrentLocation(LocationManager.GPS_PROVIDER, null, context.mainExecutor) { l ->
                        (l ?: cur)?.let(::saveParking)
                    } else cur?.let(::saveParking)
                }
            }
        }
    }
    private var lastGoodFix: Location? = null

    private val locListener = LocationListener { l ->
        phoneLoc = l
        if (l.accuracy <= 20) lastGoodFix = l
        if (_trip.value != null && (routePoints.isEmpty() || l.time - routePoints.last().t > 4000))
            routePoints += TripPoint(l.time, l.latitude, l.longitude, if (l.hasSpeed()) l.speed * 3.6 else 0.0)
    }
    private var locOn = false

    @SuppressLint("MissingPermission")
    fun startLocation() {
        if (locOn || demo) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, locListener, Looper.getMainLooper())
            locOn = true
        }
    }

    private fun stopLocation() {
        if (!locOn) return
        (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(locListener)
        locOn = false
    }

    private var lowChargeSince = 0L

    private fun checkAlerts(f: LiveFrame) {
        if (!prefs.alerts.value) return
        f.coolant?.let { if (it > 105) alert("overheat", Severity.StopSafely, "Engine overheating", "Coolant at %.0f °C. Pull over safely and switch off.".format(it)) }
        f.vbat?.let {
            lowChargeSince = if ((f.rpm ?: 0) > 1200 && it < 12.8) (lowChargeSince.takeIf { s -> s > 0 } ?: System.currentTimeMillis()) else 0L
            if (lowChargeSince > 0 && System.currentTimeMillis() - lowChargeSince > 60_000) alert("charging", Severity.CheckSoon, "Battery not charging",
                "Battery at %.1f V with the engine running. The alternator or its belt may have failed.".format(it))
            if (it > 15.0) alert("overvolt", Severity.StopSafely, "Charging voltage too high", "%.1f V can damage electronics.".format(it))
        }
    }

    private fun alertDtcs(codes: List<String>) {
        if (!prefs.alerts.value) return
        codes.forEach { c ->
            val info = DtcCatalog.lookup(c)
            alert("dtc_$c", info.severity, "Fault code $c", info.title, cooldownMs = 24 * 3_600_000L)
        }
    }

    private fun alert(key: String, sev: Severity, title: String, detail: String, cooldownMs: Long = 10 * 60_000L) {
        val now = System.currentTimeMillis()
        if (now - prefs.lastAlertAt(key) < cooldownMs) return
        prefs.markAlert(key, now)
        repo.addEvent(key, sev.ordinal, title, detail)
        notifier.alert(key.hashCode(), sev, title, detail)
    }

    suspend fun command(cmd: Int, args: ByteArray = ByteArray(0), timeoutMs: Long = 5000): CmdResult =
        _link.value?.command(cmd, args, timeoutMs) ?: CmdResult(Status.DISCONNECTED)

    suspend fun readDtcs(): CmdResult = command(Cmd.READ_DTC, byteArrayOf(0), 8000)

    suspend fun clearDtcs(): CmdResult {
        val first = command(Cmd.CLEAR_DTC, ByteArray(0))
        if (!first.ok || first.data.size < 4) return first
        val second = command(Cmd.CLEAR_DTC, first.data.copyOfRange(0, 4), 8000)
        if (second.ok) { readDtcs(); repo.addEvent("dtc_clear", 1, "Fault codes cleared", "Cleared from the app") }
        return second
    }

    suspend fun calibrateVbat(measuredV: Double) = command(Cmd.CAL_VBAT, le16((measuredV * 1000).toInt()))
    suspend fun runProbe() = command(Cmd.RUN_PROBE)
    suspend fun reboot() = command(Cmd.REBOOT, byteArrayOf(0))
    suspend fun setOdometer(km: Int) = command(Cmd.SET_ODOMETER, le32(km.toLong()))
    suspend fun logFill(litres: Double, pricePaise: Long) = command(Cmd.FILL_UP, le16((litres * 100).toInt()) + le32(pricePaise))
    suspend fun addDriver(userId: Int): CmdResult = command(Cmd.USER_ADD, byteArrayOf(userId.toByte()))
    suspend fun revokeDriver(userId: Int) = command(Cmd.USER_REVOKE, byteArrayOf(userId.toByte()))

    fun startOta(image: ByteArray) = scope.launch {
        val l = _link.value ?: return@launch
        _ota.value = OtaProgress(0, image.size.toLong(), "Preparing…")
        val sha = MessageDigest.getInstance("SHA-256").digest(image)
        val begin = l.command(Cmd.OTA_BEGIN, le32(image.size.toLong()) + sha, 10_000)
        if (!begin.ok) { _ota.value = OtaProgress(0, 0, Status.describe(begin.status), error = true); return@launch }
        var acked = 0L
        val ackJob = launch { l.acks.collect { acked = maxOf(acked, it) } }
        var off = 0
        while (off < image.size) {
            val n = minOf(225, image.size - off)
            l.writeOta(off.toLong(), image.copyOfRange(off, off + n))
            off += n
            if ((off / 225) % 32 == 0) {
                var waited = 0
                while (acked < off && waited < 3000) { delay(10); waited += 10 }
                if (acked < off) { off = acked.toInt(); continue }
            }
            _ota.value = OtaProgress(off.toLong(), image.size.toLong(), "Sending firmware…")
        }
        ackJob.cancel()
        val end = l.command(Cmd.OTA_END, ByteArray(0), 30_000)
        _ota.value = if (end.ok) OtaProgress(image.size.toLong(), image.size.toLong(), "Installed. The module is restarting.", done = true)
        else OtaProgress(off.toLong(), image.size.toLong(), "Update rejected: ${Status.describe(end.status)}", error = true)
    }

    fun clearOta() { _ota.value = null }

    fun forget() {
        _link.value?.stop()
        prefs.forgetPairing(); prefs.setDemo(false)
    }

    companion object {
        const val ACTION_OPEN_DRIVE = "com.loganlink.OPEN_DRIVE"
        const val TRIP_GAP_MS = 8 * 60_000L
    }
}

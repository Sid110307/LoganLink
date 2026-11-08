package com.loganlink.core.link

import com.loganlink.core.protocol.Cmd
import com.loganlink.core.protocol.DtcCode
import com.loganlink.core.protocol.LiveFrame
import com.loganlink.core.protocol.Status
import com.loganlink.core.protocol.le16
import com.loganlink.core.protocol.le32
import com.loganlink.core.protocol.u32
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class SimVehicleLink : VehicleLink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _state = MutableStateFlow(LinkState.Idle)
    override val state: StateFlow<LinkState> = _state.asStateFlow()
    private val _rssi = MutableStateFlow<Int?>(null)
    override val rssi: StateFlow<Int?> = _rssi.asStateFlow()
    override val role = 0
    private val _live = MutableSharedFlow<LiveFrame>(extraBufferCapacity = 16)
    override val live: SharedFlow<LiveFrame> = _live
    override val records: SharedFlow<ByteArray> = MutableSharedFlow()
    private val _dtcs = MutableSharedFlow<List<DtcCode>>(extraBufferCapacity = 2)
    override val dtcs: SharedFlow<List<DtcCode>> = _dtcs
    private val _acks = MutableSharedFlow<Long>(extraBufferCapacity = 64)
    override val acks: SharedFlow<Long> = _acks

    private var activeDtcs = mutableListOf(DtcCode.fromText("P0134"), DtcCode.fromText("P0420", 0x04))
    private var clearToken = 0L
    private var otaSize = 0L

    private var t = 0.0
    private var speed = 0.0
    private var coolant = 32.0
    private var tripM = 0.0
    private var fuelLevel = 62.0
    private val gears = doubleArrayOf(7.6, 13.9, 21.4, 29.2, 37.3)

    override fun start() {
        if (job != null) return
        _state.value = LinkState.Connecting
        job = scope.launch {
            delay(900); _state.value = LinkState.Authenticating
            delay(500); _state.value = LinkState.Ready
            while (true) {
                step(0.1)
                _rssi.value = -58 - Random.nextInt(8)
                delay(100)
            }
        }
    }

    override fun stop() {
        job?.cancel(); job = null
        _state.value = LinkState.Idle; _rssi.value = null
    }

    private fun target(time: Double): Double {
        val c = time % 240
        return when {
            c < 15 -> 0.0
            c < 60 -> 32 + 14 * sin(c / 6)
            c < 75 -> 0.0
            c < 170 -> 78 + 9 * sin(c / 11)
            c < 190 -> 45.0
            else -> 0.0
        }
    }

    private fun step(dt: Double) {
        t += dt
        val tgt = target(t)
        val prev = speed
        val accel = (tgt - speed).coerceIn(-12.0 * dt, 6.0 * dt)
        speed = max(0.0, speed + accel)
        val aMs2 = (speed - prev) / 3.6 / dt
        tripM += speed / 3.6 * dt
        coolant = min(91.0 + 2 * sin(t / 30), coolant + dt * 0.25)
        fuelLevel = max(4.0, fuelLevel - dt * 0.0004)

        val gear = when { speed < 15 -> 0; speed < 28 -> 1; speed < 45 -> 2; speed < 62 -> 3; else -> 4 }
        val rpm = if (speed < 3) 820 + Random.nextInt(-15, 15) else max(900, (speed / gears[gear] * 1000).toInt())
        val throttle = when { accel > 0.2 -> 28 + accel * 30; speed > 3 && accel > -0.05 -> 14.0; else -> 1.2 }.coerceIn(0.0, 95.0)
        val map = (28 + throttle * 0.75).coerceAtMost(98.0)
        val iat = 38.0 + 4 * sin(t / 50)

        val mAir = map * 1000 * 0.001598 * (rpm / 120.0) * 0.7 / (287.05 * (iat + 273.15))
        val dfco = throttle < 1.5 && rpm > 1500
        val lph = if (dfco) 0.0 else mAir / 14.7 * 3600 / 0.745
        val vbat = if (t < 3) 12.55 else 14.05 + 0.08 * sin(t)

        _live.tryEmit(
            LiveFrame(
                uptimeMs = (t * 1000).toLong(), utc = System.currentTimeMillis() / 1000,
                rpm = rpm, speed = speed, coolant = coolant, iat = iat, map = map, throttle = throttle,
                stft = Random.nextInt(-3, 4), ltft = 4, advance = (10 + rpm / 400).coerceAtMost(34),
                load = (throttle * 0.9 + 18).toInt().coerceAtMost(100), vbat = vbat, fuelRate = lph,
                accelX = aMs2 / 9.81, accelY = 0.04 * sin(t / 3), state = 2,
                sourceFlags = 0x01 or 0x04 or 0x10 or 0x20, tripM = tripM.toLong(),
                dtcWord = (if (activeDtcs.any { !it.pending }) 0x8000 else 0) or activeDtcs.size,
                fuelLevel = fuelLevel,
            )
        )
    }

    override suspend fun command(cmd: Int, args: ByteArray, timeoutMs: Long): CmdResult {
        if (_state.value != LinkState.Ready) return CmdResult(Status.DISCONNECTED)
        delay(120)
        return when (cmd) {
            Cmd.GET_INFO -> {
                val id = "SIM K7M EMS3132".toByteArray()
                val pids = listOf(0xBE3EB811L, 0x80000001L, 0L, 0L)
                CmdResult(Status.OK, byteArrayOf(1, 1, 0, 'B'.code.toByte(), 1, 0x33, 0x8F.toByte(), 0xEF.toByte()) +
                    pids.fold(ByteArray(0)) { a, p -> a + le32(p) } + le32(0) + le32(1234) + le32(0) +
                    byteArrayOf(id.size.toByte()) + id + byteArrayOf(17) + "UU1LSDACH81234567".toByteArray())
            }
            Cmd.READ_DTC -> { _dtcs.tryEmit(activeDtcs.toList()); CmdResult(Status.OK) }
            Cmd.CLEAR_DTC -> {
                if (speed > 1) return CmdResult(Status.VEHICLE_STATE)
                if (args.size >= 4 && args.u32(0) == clearToken && clearToken != 0L) {
                    activeDtcs.clear(); clearToken = 0; CmdResult(Status.OK)
                } else {
                    clearToken = Random.nextLong(1, 0xFFFFFFFFL); CmdResult(Status.OK, le32(clearToken))
                }
            }
            Cmd.SYNC_LOG -> CmdResult(Status.OK, le32(0) + le32(0))
            Cmd.GET_CONFIG -> CmdResult(Status.OK, le32(0))
            Cmd.OTA_BEGIN -> { otaSize = args.u32(0); CmdResult(Status.OK) }
            Cmd.OTA_END -> CmdResult(Status.OK)
            Cmd.USER_ADD -> CmdResult(Status.OK, ByteArray(32) { Random.nextInt().toByte() })
            Cmd.WIFI_OTA -> CmdResult(Status.UNSUPPORTED)
            else -> CmdResult(Status.OK, le16(0))
        }
    }

    override suspend fun writeOta(offset: Long, chunk: ByteArray) {
        delay(4)
        _acks.tryEmit(offset + chunk.size)
    }

    val moving get() = speed > 1
}

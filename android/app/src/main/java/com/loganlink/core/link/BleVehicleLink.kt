package com.loganlink.core.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.loganlink.core.protocol.AuthSession
import com.loganlink.core.protocol.DtcCode
import com.loganlink.core.protocol.Frame
import com.loganlink.core.protocol.FrameType
import com.loganlink.core.protocol.LiveFrame
import com.loganlink.core.protocol.Status
import com.loganlink.core.protocol.le32
import com.loganlink.core.protocol.u32
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

val SERVICE_UUID: UUID = UUID.fromString("4c4c0001-6c6f-6761-6e6c-696e6b000000")
private val TX_UUID = UUID.fromString("4c4c0002-6c6f-6761-6e6c-696e6b000000")
private val RX_UUID = UUID.fromString("4c4c0003-6c6f-6761-6e6c-696e6b000000")
private val OTA_UUID = UUID.fromString("4c4c0004-6c6f-6761-6e6c-696e6b000000")
private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

@SuppressLint("MissingPermission")
class BleVehicleLink(
    private val context: Context,
    private val address: String,
    private val session: AuthSession,
) : VehicleLink {
    private val tag = "BleVehicleLink"
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(LinkState.Idle)
    override val state: StateFlow<LinkState> = _state.asStateFlow()
    private val _rssi = MutableStateFlow<Int?>(null)
    override val rssi: StateFlow<Int?> = _rssi.asStateFlow()
    override val role get() = session.role

    private val _live = MutableSharedFlow<LiveFrame>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val live: SharedFlow<LiveFrame> = _live
    private val _records = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1024)
    override val records: SharedFlow<ByteArray> = _records
    private val _dtcs = MutableSharedFlow<List<DtcCode>>(extraBufferCapacity = 4)
    override val dtcs: SharedFlow<List<DtcCode>> = _dtcs
    private val _acks = MutableSharedFlow<Long>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val acks: SharedFlow<Long> = _acks

    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var ota: BluetoothGattCharacteristic? = null
    private var seq = 0
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<CmdResult>>()
    private val opLock = Mutex()
    @Volatile private var opDone: CompletableDeferred<Int>? = null
    private var running = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val rssiPoll = object : Runnable {
        override fun run() {
            if (_state.value == LinkState.Ready) gatt?.readRemoteRssi()
            if (running) main.postDelayed(this, 3000)
        }
    }

    override fun start() {
        if (running) return
        running = true
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { _state.value = LinkState.BluetoothOff; running = false; return }
        _state.value = LinkState.Connecting
        val dev: BluetoothDevice = adapter.getRemoteDevice(address)
        gatt = dev.connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
        main.postDelayed(rssiPoll, 3000)
    }

    override fun stop() {
        running = false
        main.removeCallbacks(rssiPoll)
        gatt?.disconnect(); gatt?.close(); gatt = null
        session.reset(); failPending()
        _state.value = LinkState.Idle
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(tag, "conn state=$newState status=$status")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = LinkState.Authenticating
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                main.postDelayed({ g.requestMtu(247) }, 300)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                session.reset(); failPending(); _rssi.value = null
                rx = null; ota = null
                if (running) {
                    _state.value = LinkState.Connecting

                    if (status != BluetoothGatt.GATT_SUCCESS) main.postDelayed({ if (running) g.connect() }, 2000)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(SERVICE_UUID) ?: run { Log.w(tag, "service missing"); return }
            val tx = svc.getCharacteristic(TX_UUID)
            rx = svc.getCharacteristic(RX_UUID); ota = svc.getCharacteristic(OTA_UUID)
            g.setCharacteristicNotification(tx, true)
            val d = tx.getDescriptor(CCCD)

            if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            else @Suppress("DEPRECATION") { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) Log.w(tag, "CCCD write failed $status (bonding?)")
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            opDone?.complete(status)
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) { _rssi.value = rssi }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = onBytes(value)

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") c.value?.let { onBytes(it) }
        }
    }

    private fun onBytes(b: ByteArray) {
        val f = Frame.parse(b) ?: return
        when (f.type) {
            FrameType.AUTH_CHALLENGE -> {
                val resp = session.respond(f.payload)
                enqueueWrite(rx, Frame.build(FrameType.AUTH_RESPONSE, 0, seq++, resp))
            }
            FrameType.AUTH_RESULT -> {
                if (f.payload.isNotEmpty() && f.payload[0].toInt() == Status.OK) {
                    session.role = f.payload.getOrElse(1) { 1 }.toInt()
                    _state.value = LinkState.Ready
                } else _state.value = LinkState.AuthFailed
            }
            FrameType.LIVE -> LiveFrame.parse(f.payload)?.let { _live.tryEmit(it) }
            FrameType.RSP -> {
                if (f.payload.size < 6) return
                val ctr = f.payload.u32(0)
                val res = CmdResult(f.payload[5].toInt() and 0xFF, f.payload.copyOfRange(6, f.payload.size))
                val p = pending.remove(ctr)
                if (p != null) p.complete(res)
                else if (ctr == 0L && f.payload.size >= 10) _acks.tryEmit(f.payload.u32(6))
            }
            FrameType.LOG_CHUNK -> if (f.payload.size >= 5 + 128) _records.tryEmit(f.payload.copyOfRange(5, 5 + 128))
            FrameType.DTC_LIST -> _dtcs.tryEmit(DtcCode.parseList(f.payload))
            else -> {}
        }
    }

    private fun enqueueWrite(c: BluetoothGattCharacteristic?, data: ByteArray) {

        scope.launch { write(c, data) }
    }

    private suspend fun write(c: BluetoothGattCharacteristic?, data: ByteArray): Boolean = opLock.withLock {
        val g = gatt ?: return false
        val ch = c ?: return false
        val done = CompletableDeferred<Int>().also { opDone = it }
        val started = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(ch, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothGatt.GATT_SUCCESS
        } else @Suppress("DEPRECATION") {
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ch.value = data
            g.writeCharacteristic(ch)
        }
        if (!started) return false
        withTimeoutOrNull(1000) { done.await() } == BluetoothGatt.GATT_SUCCESS
    }

    override suspend fun command(cmd: Int, args: ByteArray, timeoutMs: Long): CmdResult {
        if (_state.value != LinkState.Ready) return CmdResult(Status.DISCONNECTED)
        val (ctr, frame) = session.buildCommand(seq++, cmd, args)
        val d = CompletableDeferred<CmdResult>()
        pending[ctr] = d
        if (!write(rx, frame)) { pending.remove(ctr); return CmdResult(Status.DISCONNECTED) }
        return withTimeoutOrNull(timeoutMs) { d.await() } ?: run { pending.remove(ctr); CmdResult(Status.TIMEOUT) }
    }

    override suspend fun writeOta(offset: Long, chunk: ByteArray) {
        write(ota, Frame.build(FrameType.OTA_DATA, 0, seq++, le32(offset) + chunk))
    }

    private fun failPending() {
        pending.values.forEach { it.complete(CmdResult(Status.DISCONNECTED)) }
        pending.clear()
    }
}

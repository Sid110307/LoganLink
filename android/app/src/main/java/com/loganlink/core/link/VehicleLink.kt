package com.loganlink.core.link

import com.loganlink.core.protocol.DtcCode
import com.loganlink.core.protocol.LiveFrame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class LinkState(val label: String) {
    Idle("Not connected"),
    BluetoothOff("Bluetooth is off"),
    Connecting("Looking for your car…"),
    Authenticating("Securing connection…"),
    Ready("Connected"),
    AuthFailed("Not authorised, pair again"),
}

data class CmdResult(val status: Int, val data: ByteArray = ByteArray(0)) {
    val ok get() = status == 0
}

interface VehicleLink {
    val state: StateFlow<LinkState>
    val rssi: StateFlow<Int?>
    val role: Int
    val live: SharedFlow<LiveFrame>
    val records: SharedFlow<ByteArray>
    val dtcs: SharedFlow<List<DtcCode>>
    val acks: SharedFlow<Long>
    fun start()
    fun stop()
    suspend fun command(cmd: Int, args: ByteArray = ByteArray(0), timeoutMs: Long = 5000): CmdResult
    suspend fun writeOta(offset: Long, chunk: ByteArray)
    fun setForeground(fg: Boolean) {}
}

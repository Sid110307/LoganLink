package com.loganlink.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FrameType {
    const val LIVE = 0x01; const val EVENT = 0x02; const val DTC_LIST = 0x03
    const val CMD = 0x10; const val RSP = 0x11; const val LOG_CHUNK = 0x20
    const val AUTH_CHALLENGE = 0x30; const val AUTH_RESPONSE = 0x31; const val AUTH_RESULT = 0x32
    const val OTA_DATA = 0x40
}

object Cmd {
    const val GET_INFO = 0x01; const val SET_TIME = 0x02; const val SET_LIVE_RATE = 0x03
    const val READ_DTC = 0x10; const val CLEAR_DTC = 0x11; const val RUN_PROBE = 0x12
    const val SYNC_LOG = 0x20; const val GET_CONFIG = 0x21; const val SET_CONFIG = 0x22
    const val SET_ODOMETER = 0x23; const val CAL_VBAT = 0x24; const val CAL_FUEL = 0x25; const val FILL_UP = 0x26
    const val AUX_OUT = 0x30
    const val OTA_BEGIN = 0x40; const val OTA_END = 0x41; const val WIFI_OTA = 0x42
    const val USER_ADD = 0x50; const val USER_REVOKE = 0x51; const val REBOOT = 0x60
}

object CfgKey {
    const val LIVE_HZ = 1; const val FUEL_PRICE_PAISE = 2; const val ODOMETER_KM = 3; const val VBAT_GAIN_PPM = 4
    const val VBAT_OFFSET_MV = 5; const val KRIP_MILLI = 6; const val VE_PERMILLE = 7; const val IGN_TAP_FITTED = 8
    const val FUEL_TAP_FITTED = 9; const val TANK_L_X10 = 10; const val HARSH_BRAKE_MG = 11; const val HARSH_ACCEL_MG = 12
    const val LOWBAT_HIBERNATE_MV = 13
}

object Status {
    const val OK = 0; const val BAD_ARGS = 1; const val NOT_AUTH = 2; const val FORBIDDEN = 3
    const val VEHICLE_STATE = 4; const val BUSY = 5; const val IO_ERROR = 6; const val UNSUPPORTED = 7
    const val TIMEOUT = 0x100; const val DISCONNECTED = 0x101

    fun describe(s: Int): String = when (s) {
        OK -> "Done"
        BAD_ARGS -> "The module rejected the request"
        NOT_AUTH -> "Not authenticated — reconnect the module"
        FORBIDDEN -> "Only the owner can do this"
        VEHICLE_STATE -> "Not allowed while the engine is running"
        BUSY -> "The module is busy, try again"
        IO_ERROR -> "Couldn't talk to the engine computer"
        UNSUPPORTED -> "Not supported by this firmware"
        TIMEOUT -> "The module didn't answer in time"
        DISCONNECTED -> "Not connected to the car"
        else -> "Error $s"
    }
}

const val MAGIC = 0xA5
const val VERSION = 0x01
const val HDR = 8
const val CRC_LEN = 2
const val MAX_PAYLOAD = 233
const val MAC_LEN = 8

fun crc16(d: ByteArray, len: Int = d.size): Int {
    var crc = 0xFFFF
    for (i in 0 until len) {
        crc = crc xor ((d[i].toInt() and 0xFF) shl 8)
        repeat(8) { crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF }
    }
    return crc
}

class Frame(val type: Int, val flags: Int, val seq: Int, val payload: ByteArray) {
    companion object {
        fun headerAndPayload(type: Int, flags: Int, seq: Int, payload: ByteArray): ByteArray {
            val out = ByteArray(HDR + payload.size)
            out[0] = MAGIC.toByte(); out[1] = VERSION.toByte(); out[2] = type.toByte(); out[3] = flags.toByte()
            out[4] = seq.toByte(); out[5] = (seq shr 8).toByte()
            out[6] = payload.size.toByte(); out[7] = (payload.size shr 8).toByte()
            payload.copyInto(out, HDR)
            return out
        }

        fun build(type: Int, flags: Int, seq: Int, payload: ByteArray): ByteArray {
            require(payload.size <= MAX_PAYLOAD) { "payload too long" }
            val hp = headerAndPayload(type, flags, seq, payload)
            val c = crc16(hp)
            return hp + byteArrayOf(c.toByte(), (c shr 8).toByte())
        }

        fun parse(d: ByteArray): Frame? {
            if (d.size < HDR + CRC_LEN) return null
            if ((d[0].toInt() and 0xFF) != MAGIC || d[1].toInt() != VERSION) return null
            val len = d.u16(6)
            if (len > MAX_PAYLOAD || d.size != HDR + len + CRC_LEN) return null
            if (d.u16(HDR + len) != crc16(d, HDR + len)) return null
            return Frame(d[2].toInt() and 0xFF, d[3].toInt() and 0xFF, d.u16(4), d.copyOfRange(HDR, HDR + len))
        }
    }
}

fun ByteArray.u8(o: Int) = this[o].toInt() and 0xFF
fun ByteArray.s8(o: Int) = this[o].toInt()
fun ByteArray.u16(o: Int) = (this[o].toInt() and 0xFF) or ((this[o + 1].toInt() and 0xFF) shl 8)
fun ByteArray.s16(o: Int) = u16(o).toShort().toInt()
fun ByteArray.u32(o: Int): Long = (u16(o).toLong()) or (u16(o + 2).toLong() shl 16)
fun ByteArray.s32(o: Int): Int = u32(o).toInt()

fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
fun le32(v: Long) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array()
fun le32(v: Int) = le32(v.toLong())

fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

package com.loganlink.core.protocol

sealed class LogRecord(val seq: Long, val utc: Long, val bootId: Int) {
    class Sample(seq: Long, utc: Long, boot: Int, val live: LiveFrame) : LogRecord(seq, utc, boot)
    class TripStart(seq: Long, utc: Long, boot: Int) : LogRecord(seq, utc, boot)
    class TripEnd(seq: Long, utc: Long, boot: Int, val s: TripSummary) : LogRecord(seq, utc, boot)
    class Crank(seq: Long, utc: Long, boot: Int, val restMv: Int, val minMv: Int, val crankMs: Int, val recoverMs: Int) :
        LogRecord(seq, utc, boot)
    class Dtc(seq: Long, utc: Long, boot: Int, val codes: List<DtcCode>) : LogRecord(seq, utc, boot)
    class Fill(seq: Long, utc: Long, boot: Int, val litres: Double, val pricePaise: Long) : LogRecord(seq, utc, boot)
    class Event(seq: Long, utc: Long, boot: Int, val kind: Int, val data: ByteArray) : LogRecord(seq, utc, boot)
    class Boot(seq: Long, utc: Long, boot: Int, val resetReason: Int, val wakeCause: Int, val fw: String) : LogRecord(seq, utc, boot)
    class Other(seq: Long, utc: Long, boot: Int, val type: Int, val data: ByteArray) : LogRecord(seq, utc, boot)

    companion object {
        const val SIZE = 128
        fun parse(r: ByteArray): LogRecord? {
            if (r.size < SIZE) return null
            val seq = r.u32(0); val utc = r.u32(4); val boot = r.u16(12)
            val type = r.u8(14); val len = r.u8(15).coerceAtMost(108)
            val d = r.copyOfRange(16, 16 + len)
            return when (type) {
                1 -> LiveFrame.parse(d.copyOf(LiveFrame.LENGTH).takeIf { len >= LiveFrame.LENGTH } ?: return null)
                    ?.let { Sample(seq, utc, boot, it) }
                3 -> TripStart(seq, utc, boot)
                4 -> if (len >= 24) TripEnd(seq, utc, boot, TripSummary.parse(d)) else null
                5 -> if (len >= 8) Crank(seq, utc, boot, d.u16(0), d.u16(2), d.u16(4), d.u16(6)) else null
                6 -> if (len >= 2) Dtc(seq, utc, boot, DtcCode.parseList(d)) else null
                7 -> if (len >= 6) Fill(seq, utc, boot, d.u16(0) / 100.0, d.u32(2)) else null
                9 -> if (len >= 4) Boot(seq, utc, boot, d.u8(0), d.u8(1), "${d.u8(2)}.${d.u8(3)}") else null
                2 -> Event(seq, utc, boot, if (len > 0) d.u8(0) else 0, d)
                else -> Other(seq, utc, boot, type, d)
            }
        }
    }
}

data class TripSummary(
    val startUtc: Long, val durationS: Long, val distanceM: Long, val idleS: Long,
    val fuelMl: Int, val maxSpeed: Double, val harshBrake: Int, val harshAccel: Int, val harshCorner: Int, val overrevS: Int,
) {
    companion object {
        fun parse(d: ByteArray) = TripSummary(
            d.u32(0), d.u32(4), d.u32(8), d.u32(12), d.u16(16), d.u16(18) / 10.0, d.u8(20), d.u8(21), d.u8(22), d.u8(23),
        )
    }
}

data class DtcCode(val raw: Int, val status: Int) {
    val code: String get() {
        val sys = "PCBU"[(raw shr 14) and 3]
        return "$sys${(raw shr 12) and 3}${"%03X".format(raw and 0xFFF)}"
    }
    val pending get() = status and 0x04 != 0

    companion object {
        fun parseList(p: ByteArray): List<DtcCode> {
            if (p.size < 2) return emptyList()
            val n = p.u8(0)
            return (0 until n).mapNotNull { i ->
                val o = 2 + i * 3
                if (o + 2 < p.size) DtcCode(p.u16(o), p.u8(o + 2)) else null
            }
        }
        fun fromText(code: String, status: Int = 0x08): DtcCode {
            val sys = "PCBU".indexOf(code[0].uppercaseChar()).coerceAtLeast(0)
            val v = (sys shl 14) or (code.substring(1).toInt(16) and 0x3FFF)
            return DtcCode(v, status)
        }
    }
}

data class DeviceInfo(
    val fw: String, val hw: Char, val protocol: Int, val ecuAddress: Int, val kb1: Int, val kb2: Int,
    val pidSupport: List<Long>, val oldestSeq: Long, val headSeq: Long, val guardDenied: Long, val ecuId: String,
) {
    val protocolName get() = when (protocol) {
        1 -> "KWP2000 fast init (ISO 14230)"; 2 -> "KWP2000 5-baud (ISO 14230)"; 3 -> "ISO 9141-2"
        else -> "No ECU found yet"
    }
    val supportedPids: List<Int> get() = buildList {
        pidSupport.forEachIndexed { blk, bits ->
            for (bit in 0 until 32) if (bits and (0x80000000L ushr bit) != 0L) add(blk * 32 + bit + 1)
        }
    }

    companion object {
        fun parse(d: ByteArray): DeviceInfo? {
            if (d.size < 37) return null
            val pids = (0 until 4).map { d.u32(8 + it * 4) }
            val idLen = d.u8(36)
            val id = if (d.size >= 37 + idLen) String(d, 37, idLen, Charsets.US_ASCII) else ""
            return DeviceInfo(
                "${d.u8(0)}.${d.u8(1)}.${d.u8(2)}", d.u8(3).toChar(), d.u8(4), d.u8(5), d.u8(6), d.u8(7),
                pids, d.u32(24), d.u32(28), d.u32(32), id,
            )
        }
    }
}

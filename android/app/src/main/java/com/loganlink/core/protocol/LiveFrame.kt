package com.loganlink.core.protocol

enum class Source { Ecu, Estimated, Phone, None }

data class LiveFrame(
    val uptimeMs: Long = 0,
    val utc: Long = 0,
    val rpm: Int? = null,
    val speed: Double? = null,
    val coolant: Double? = null,
    val iat: Double? = null,
    val map: Double? = null,
    val throttle: Double? = null,
    val stft: Int? = null,
    val ltft: Int? = null,
    val advance: Int? = null,
    val load: Int? = null,
    val vbat: Double? = null,
    val fuelRate: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val accelX: Double? = null,
    val accelY: Double? = null,
    val state: Int = 0,
    val sourceFlags: Int = 0,
    val tripM: Long = 0,
    val dtcWord: Int = 0,
    val fuelLevel: Double? = null,
    val receivedAt: Long = System.currentTimeMillis(),
) {
    val ecuOnline get() = sourceFlags and 0x10 != 0
    val milOn get() = dtcWord and 0x8000 != 0
    val dtcCount get() = dtcWord and 0xFF
    val rpmSource get() = when {
        sourceFlags and 0x01 != 0 -> Source.Ecu
        sourceFlags and 0x02 != 0 -> Source.Estimated
        else -> Source.None
    }
    val speedSource get() = when {
        sourceFlags and 0x04 != 0 -> Source.Ecu
        speed != null -> Source.Phone
        else -> Source.None
    }
    val engineRunning get() = (rpm ?: 0) > 400 || (vbat ?: 0.0) > 13.2
    val kmPerLitre: Double?
        get() {
            val s = speed ?: return null; val f = fuelRate ?: return null
            return if (f > 0.05 && s > 3) s / f else null
        }
    val stateName get() = STATES.getOrElse(state) { "--" }

    companion object {
        const val LENGTH = 43
        val STATES = listOf("Booting", "Standby", "Driving", "Asleep", "Safe mode")

        fun parse(p: ByteArray): LiveFrame? {
            if (p.size != LENGTH) return null
            fun u16n(o: Int) = p.u16(o).takeIf { it != 0xFFFF }
            fun s16x10(o: Int) = p.s16(o).takeIf { it != 0x7FFF }?.let { it / 10.0 }
            fun u16x10(o: Int) = u16n(o)?.let { it / 10.0 }
            fun i8(o: Int) = p.s8(o).takeIf { it != -128 }
            return LiveFrame(
                uptimeMs = p.u32(0), utc = p.u32(4), rpm = u16n(8), speed = u16x10(10),
                coolant = s16x10(12), iat = s16x10(14), map = u16x10(16), throttle = u16x10(18),
                stft = i8(20), ltft = i8(21), advance = i8(22), load = p.u8(23).takeIf { it != 0xFF },
                vbat = p.u16(24).takeIf { it != 0 }?.let { it / 1000.0 },
                fuelRate = u16n(26)?.let { it / 100.0 },
                accelX = p.s16(28).takeIf { it != 0x7FFF }?.let { it / 1000.0 },
                accelY = p.s16(30).takeIf { it != 0x7FFF }?.let { it / 1000.0 },
                state = p.u8(32), sourceFlags = p.u16(33), tripM = p.u32(35), dtcWord = p.u16(39),
                fuelLevel = u16n(41)?.let { it / 100.0 },
            )
        }
    }
}

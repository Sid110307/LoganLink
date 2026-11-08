package com.loganlink.core.protocol

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private fun hmac(key: ByteArray, msg: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(msg) }

data class PairingCard(val deviceId: String, val kDev: ByteArray) {
    companion object {
        fun parse(text: String): PairingCard? {
            val parts = text.trim().split(':')
            if (parts.size != 3 || parts[0] != "LL1" || parts[1].length != 12) return null
            if (!parts[1].all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            val k = base32Decode(parts[2]) ?: return null
            return if (k.size == 32) PairingCard(parts[1].lowercase(), k) else null
        }

        fun base32Decode(s: String): ByteArray? {
            val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            var bits = 0; var value = 0
            val out = ArrayList<Byte>()
            for (ch in s.uppercase().trimEnd('=')) {
                val v = alpha.indexOf(ch); if (v < 0) return null
                value = (value shl 5) or v; bits += 5
                if (bits >= 8) { out.add(((value shr (bits - 8)) and 0xFF).toByte()); bits -= 8 }
            }
            return out.toByteArray()
        }

        fun base32Encode(b: ByteArray): String {
            val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            val sb = StringBuilder(); var bits = 0; var value = 0
            for (x in b) {
                value = (value shl 8) or (x.toInt() and 0xFF); bits += 8
                while (bits >= 5) { sb.append(alpha[(value shr (bits - 5)) and 31]); bits -= 5 }
            }
            if (bits > 0) sb.append(alpha[(value shl (5 - bits)) and 31])
            return sb.toString()
        }
    }

    val macAddress: String get() = deviceId.uppercase().chunked(2).joinToString(":")
}

fun deriveUserKey(kDev: ByteArray, userId: Int): ByteArray = hmac(kDev, "LL-USER".toByteArray() + byteArrayOf(userId.toByte()))

data class DriverInvite(val deviceId: String, val userId: Int, val kUser: ByteArray) {
    fun encode() = "LL1U:$deviceId:$userId:${PairingCard.base32Encode(kUser)}"
    companion object {
        fun parse(text: String): DriverInvite? {
            val p = text.trim().split(':')
            if (p.size != 4 || p[0] != "LL1U" || p[1].length != 12) return null
            val id = p[2].toIntOrNull()?.takeIf { it in 1..7 } ?: return null
            val k = PairingCard.base32Decode(p[3])?.takeIf { it.size == 32 } ?: return null
            return DriverInvite(p[1].lowercase(), id, k)
        }
    }
}

class AuthSession(val userId: Int, private val kUser: ByteArray) {
    private var ks: ByteArray? = null
    private var ctr = 0L
    var role = 1
    val authenticated get() = ks != null
    val lastCounter get() = ctr

    fun respond(challenge: ByteArray): ByteArray {
        val deviceId = challenge.copyOfRange(0, 6); val nd = challenge.copyOfRange(6, 22)
        val np = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val tag = hmac(kUser, "LL-AUTH".toByteArray() + deviceId + nd + np).copyOfRange(0, 16)
        ks = hmac(kUser, "LL-SESS".toByteArray() + nd + np)
        ctr = 0
        return byteArrayOf(userId.toByte()) + np + tag
    }

    fun reset() { ks = null }

    @Synchronized
    fun buildCommand(seq: Int, cmd: Int, args: ByteArray): Pair<Long, ByteArray> {
        val key = ks ?: error("not authenticated")
        ctr++
        val body = le32(ctr) + byteArrayOf(cmd.toByte()) + args
        val hp = Frame.headerAndPayload(FrameType.CMD, 4, seq, body + ByteArray(MAC_LEN))
        val mac = hmac(key, hp.copyOfRange(0, hp.size - MAC_LEN)).copyOfRange(0, MAC_LEN)
        return ctr to Frame.build(FrameType.CMD, 4, seq, body + mac)
    }
}

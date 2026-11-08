package com.loganlink.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.loganlink.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Pairing(val address: String, val deviceId: String, val userId: Int, val kUser: ByteArray, val name: String)

data class VehicleProfile(
    val nickname: String = "My Logan",
    val plate: String = "",
    val odometerKm: Int = 0,
    val fuelPricePaise: Int = 10500,
    val tankLitres: Int = 50,
)

private object KeyVault {
    private const val ALIAS = "loganlink_vault"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build()
            )
        }.generateKey()
    }

    fun seal(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv + c.doFinal(plain), Base64.NO_WRAP)
    }

    fun open(sealed: String): ByteArray? = runCatching {
        val b = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12))
        c.doFinal(b, 12, b.size - 12)
    }.getOrNull()
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("loganlink", Context.MODE_PRIVATE)

    private val _pairing = MutableStateFlow(readPairing())
    val pairing: StateFlow<Pairing?> = _pairing.asStateFlow()
    private val _demo = MutableStateFlow(sp.getBoolean("demo", false))
    val demo: StateFlow<Boolean> = _demo.asStateFlow()
    private val _vehicle = MutableStateFlow(readVehicle())
    val vehicle: StateFlow<VehicleProfile> = _vehicle.asStateFlow()
    private val _theme = MutableStateFlow(ThemeMode.entries[sp.getInt("theme", 0)])
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    private val _alerts = MutableStateFlow(sp.getBoolean("alerts", true))
    val alerts: StateFlow<Boolean> = _alerts.asStateFlow()
    private val _overlay = MutableStateFlow(sp.getBoolean("overlay_maps", true))
    val overlayWithMaps: StateFlow<Boolean> = _overlay.asStateFlow()
    fun setOverlayWithMaps(on: Boolean) { sp.edit().putBoolean("overlay_maps", on).apply(); _overlay.value = on }

    private val _autoFuel = MutableStateFlow(sp.getBoolean("auto_fuel", true))
    val autoFuel: StateFlow<Boolean> = _autoFuel.asStateFlow()
    fun setAutoFuel(on: Boolean) { sp.edit().putBoolean("auto_fuel", on).apply(); _autoFuel.value = on }

    private val _autoDrive = MutableStateFlow(sp.getBoolean("auto_drive", false))
    val autoDriveMode: StateFlow<Boolean> = _autoDrive.asStateFlow()

    val onboarded get() = _pairing.value != null || _demo.value

    private fun readPairing(): Pairing? {
        val addr = sp.getString("p_addr", null) ?: return null
        val key = sp.getString("p_key", null)?.let { KeyVault.open(it) } ?: return null
        return Pairing(addr, sp.getString("p_id", "")!!, sp.getInt("p_user", 0), key, sp.getString("p_name", "LoganLink")!!)
    }

    fun savePairing(p: Pairing) {
        sp.edit().putString("p_addr", p.address).putString("p_id", p.deviceId).putInt("p_user", p.userId)
            .putString("p_key", KeyVault.seal(p.kUser)).putString("p_name", p.name).putBoolean("demo", false).apply()
        _demo.value = false
        _pairing.value = p
    }

    fun forgetPairing() {
        sp.edit().remove("p_addr").remove("p_id").remove("p_user").remove("p_key").remove("p_name").apply()
        _pairing.value = null
    }

    fun setDemo(on: Boolean) { sp.edit().putBoolean("demo", on).apply(); _demo.value = on }

    private fun readVehicle() = VehicleProfile(
        sp.getString("v_nick", "My Logan")!!, sp.getString("v_plate", "")!!, sp.getInt("v_odo", 0),
        sp.getInt("v_price", 10500), sp.getInt("v_tank", 50),
    )

    fun saveVehicle(v: VehicleProfile) {
        sp.edit().putString("v_nick", v.nickname).putString("v_plate", v.plate).putInt("v_odo", v.odometerKm)
            .putInt("v_price", v.fuelPricePaise).putInt("v_tank", v.tankLitres).apply()
        _vehicle.value = v
    }

    fun addOdometer(km: Double) {
        if (km < 0.05) return
        val v = _vehicle.value
        saveVehicle(v.copy(odometerKm = v.odometerKm + km.toInt().coerceAtLeast(0)))
    }

    fun setTheme(m: ThemeMode) { sp.edit().putInt("theme", m.ordinal).apply(); _theme.value = m }
    fun setAlerts(on: Boolean) { sp.edit().putBoolean("alerts", on).apply(); _alerts.value = on }
    fun setAutoDriveMode(on: Boolean) { sp.edit().putBoolean("auto_drive", on).apply(); _autoDrive.value = on }

    var lastSeq: Long
        get() = sp.getLong("last_seq", -1)
        set(v) = sp.edit().putLong("last_seq", v).apply()

    var demoSeeded: Boolean
        get() = sp.getBoolean("demo_seeded", false)
        set(v) = sp.edit().putBoolean("demo_seeded", v).apply()
}

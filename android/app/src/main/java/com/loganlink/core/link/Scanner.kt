package com.loganlink.core.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class FoundModule(val name: String, val address: String, val rssi: Int, val vbat: Double?, val state: Int?)

@SuppressLint("MissingPermission")
fun scanModules(context: Context): Flow<List<FoundModule>> = callbackFlow {
    val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner
    if (scanner == null) { trySend(emptyList()); close(); return@callbackFlow }
    val seen = LinkedHashMap<String, FoundModule>()
    val cb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, r: ScanResult) {
            val mfg = r.scanRecord?.getManufacturerSpecificData(0xFFFF)
            seen[r.device.address] = FoundModule(
                r.scanRecord?.deviceName ?: r.device.name ?: "LoganLink", r.device.address, r.rssi,
                mfg?.takeIf { it.size >= 3 }?.let { (it[2].toInt() and 0xFF) / 10.0 }, mfg?.getOrNull(1)?.toInt(),
            )
            trySend(seen.values.sortedByDescending { it.rssi })
        }
    }
    scanner.startScan(
        listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()),
        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb,
    )
    awaitClose { runCatching { scanner.stopScan(cb) } }
}

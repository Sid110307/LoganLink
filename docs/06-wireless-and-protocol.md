# 06 — Wireless Communication Architecture and Protocol

## 1. Technology selection

| Link | Use | Decision |
|------|-----|----------|
| **BLE 5.0 (LE 2M PHY, DLE 251, MTU 247)** | Primary phone link: live data, commands, log sync, OTA | **Selected.** Native on iOS/Android without MFi, low power, background reconnection supported by both OSes |
| Bluetooth Classic SPP | — | Rejected: iOS forbids non-MFi SPP; ESP32-S3 has no Classic radio anyway |
| Wi-Fi 2.4 GHz (SoftAP, WPA2) | Bulk only: OTA images and large log export when parked | Enabled on demand, ≤ 5 min, never in DRIVE |
| LTE Cat-1 (variant C) | Remote status, alerts, location while away | MQTT 3.1.1 over TLS 1.2 to the owner's broker |
| NFC/UWB | — | Not needed |

## 2. GATT design

Service **LoganLink** UUID `4c4c0001-6c6f-6761-6e6c-696e6b000000`

| Characteristic | UUID | Properties | Direction | Security |
|----------------|------|------------|-----------|----------|
| `TX` | `4c4c0002-…` | Notify | Module → phone (LIVE, EVENT, RSP, LOG_CHUNK, AUTH_*) | Encrypted link required (LESC bond) |
| `RX` | `4c4c0003-…` | Write Without Response, Write | Phone → module (CMD, AUTH_RESPONSE) | Encrypted link required |
| `OTA` | `4c4c0004-…` | Write Without Response | Phone → module (OTA_DATA) | Encrypted + app-auth session |

Standard services: Device Information (0x180A: model, FW rev, HW rev, serial) and Battery Service (0x180F,
reports the **car** battery state of charge, so OS widgets can show it).

Advertising: name `LoganLink-XXXX` (last 2 bytes of the MAC), service UUID, manufacturer data
`[0xFFFF company placeholder][ver][state][vbat/100]`, which lets the app show battery voltage before connecting.
Interval 100 ms for the first 30 s after a state change, then 300 ms (STANDBY/DRIVE). No advertising in SLEEP.

Connection parameters requested by the module: foreground live view CI 15–30 ms, latency 0, timeout 4 s.
Background CI 100–200 ms, latency 4. The app sends `SET_LIVE_RATE` when it moves between foreground and background.

## 3. Frame format (shared by all characteristics, little-endian)

```
 Offset  Size  Field
 0       1     magic   = 0xA5
 1       1     version = 0x01
 2       1     type    (table below)
 3       1     flags   bit0 ACK_REQ, bit1 MORE_FRAGMENTS, bit2 AUTHENTICATED (payload ends with 8-byte MAC)
 4       2     seq     per-direction sequence number, wraps at 65535
 6       2     len     payload length N (≤ 233)
 8       N     payload
 8+N     2     crc16   CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) over bytes 0 … 8+N−1
```

| Type | Name | Payload |
|------|------|---------|
| 0x01 | LIVE | `LiveV1` 62 bytes (§4) |
| 0x02 | EVENT | `u32 log_seq, u32 utc, u8 event_id, u8 severity, u16 len, data` |
| 0x03 | DTC_LIST | `u8 count, u8 source, count × {u16 code, u8 status}` |
| 0x10 | CMD | `u32 counter, u8 cmd, args…` + 8-byte MAC |
| 0x11 | RSP | `u32 counter (echo), u8 cmd, u8 status, data…` |
| 0x20 | LOG_CHUNK | `u32 first_seq, u8 n_records, records…` |
| 0x30 | AUTH_CHALLENGE | `u8 device_id[6], u8 nonce_d[16], u8 fw_major, u8 fw_minor` |
| 0x31 | AUTH_RESPONSE | `u8 user_id, u8 nonce_p[16], u8 tag[16]` |
| 0x32 | AUTH_RESULT | `u8 status, u8 role` |
| 0x40 | OTA_DATA | `u32 offset, data` |

Integrity layering: the BLE link layer (CRC-24 + LESC AES-CCM) protects each radio packet. CRC-16 catches application-
layer framing and reassembly errors. The HMAC authenticates commands.

## 4. `LiveV1` payload (62 bytes, 10 Hz)

| Off | Type | Field | Unit / scaling | Invalid |
|-----|------|-------|----------------|---------|
| 0 | u32 | uptime_ms | ms | — |
| 4 | u32 | utc | Unix s | 0 |
| 8 | u16 | rpm | rpm | 0xFFFF |
| 10 | u16 | speed | km/h × 10 (fused) | 0xFFFF |
| 12 | i16 | coolant | °C × 10 | 0x7FFF |
| 14 | i16 | iat | °C × 10 | 0x7FFF |
| 16 | u16 | map | kPa × 10 | 0xFFFF |
| 18 | u16 | throttle | % × 10 | 0xFFFF |
| 20 | i8 | stft | % | −128 |
| 21 | i8 | ltft | % | −128 |
| 22 | i8 | advance | ° BTDC | −128 |
| 23 | u8 | load | % | 0xFF |
| 24 | u16 | vbat | mV | 0 |
| 26 | u16 | fuel_rate | L/h × 100 | 0xFFFF |
| 28 | i32 | lat | deg × 1e7 | 0x7FFFFFFF |
| 32 | i32 | lon | deg × 1e7 | 0x7FFFFFFF |
| 36 | u16 | gnss_speed | km/h × 10 | 0xFFFF |
| 38 | u16 | heading | ° × 10 | 0xFFFF |
| 40 | u8 | sats | count | — |
| 41 | u8 | fix | 0 none, 2 2D, 3 3D | — |
| 42 | i16 | accel_x | mg (vehicle frame, +forward) | 0x7FFF |
| 44 | i16 | accel_y | mg (+left) | 0x7FFF |
| 46 | u16 | co2 | ppm | 0 |
| 48 | i16 | cabin_temp | °C × 10 | 0x7FFF |
| 50 | u8 | cabin_rh | % | 0xFF |
| 51 | u8 | state | 0 BOOT, 1 STANDBY, 2 DRIVE, 3 SLEEP, 4 SAFE | — |
| 52 | u16 | source_flags | bit0 rpm from ECU, bit1 rpm estimated (ripple), bit2 speed from ECU, bit3 speed GNSS, bit4 ECU online, bit5 fuel from model, bit6 fuel-level sensor, bit7 GNSS valid | — |
| 54 | u32 | trip_m | metres this trip | — |
| 58 | u16 | dtc | bit15 MIL on, bits0–7 count | — |
| 60 | u16 | fuel_level | % × 100 (sender or estimate) | 0xFFFF |

The firmware header `firmware/main/proto.h` and the app codec `app/lib/protocol/live_frame.dart` implement this table.
Each includes a size assertion (62 bytes).

## 5. Commands

| ID | Name | Args | Role | Notes |
|----|------|------|------|-------|
| 0x01 | GET_INFO | — | any | FW/HW version, serial, vehicle profile, probe result |
| 0x02 | SET_TIME | u32 utc, i16 tz_min | any | Used until GNSS time is valid |
| 0x03 | SET_LIVE_RATE | u8 hz (0–10) | any | 0 stops the stream |
| 0x10 | READ_DTC | u8 type (stored/pending) | any | Answers with DTC_LIST |
| 0x11 | CLEAR_DTC | u32 confirm_token | owner | Two-step: first call returns a token; second call within 30 s with the token executes. Refused unless RPM = 0 |
| 0x12 | RUN_PROBE | — | owner | Vehicle protocol probe (doc 02 §5) |
| 0x20 | SYNC_LOG | u32 from_seq, u16 max_records | any | Streams LOG_CHUNKs; resumable |
| 0x21 | GET_CONFIG / 0x22 SET_CONFIG | u16 key, value | owner (set) | Keys in `config.h` |
| 0x23 | SET_ODOMETER | u32 km | owner | — |
| 0x24 | CAL_VBAT | u16 measured_mV, u8 point (0/1) | owner | Two-point calibration |
| 0x25 | CAL_FUEL | u8 percent | owner | Records a fuel-sender calibration point |
| 0x26 | FILL_UP | u16 litres×100, u32 price_paise | any | Feeds fuel-model self-calibration |
| 0x30 | AUX_OUT | u8 on, u16 off_after_s | owner | Accessory output |
| 0x40 | OTA_BEGIN | u32 size, u8 sha256[32] | owner | — |
| 0x41 | OTA_END | — | owner | Verify hash + signature, set boot partition |
| 0x42 | WIFI_OTA | — | owner | Starts SoftAP; returns SSID/PSK (random per session) |
| 0x50 | USER_ADD | u8 user_id, u8 role, u8 key[32] | owner | — |
| 0x51 | USER_REVOKE | u8 user_id | owner | Deletes the key and the BLE bond |
| 0x60 | REBOOT | u8 mode (0 normal, 1 safe) | owner | — |

Status codes: 0 OK, 1 BAD_ARGS, 2 NOT_AUTH, 3 FORBIDDEN_ROLE, 4 VEHICLE_STATE (e.g., engine running), 5 BUSY, 6 IO_ERROR, 7 UNSUPPORTED.

## 6. Log sync

- Every log record has a monotonically increasing 32-bit `seq` (doc 07 §7). The app stores the `last_seq` it received.
- On connect, the app sends `SYNC_LOG(last_seq+1, 512)`. The module streams LOG_CHUNKs at about 40 KB/s and the app writes each
  chunk in a single SQLite transaction. If the link drops, the next connect resumes from the new `last_seq`.
- Data integrity: CRC-32 per stored record (checked on read by the module). Records with a bad CRC are skipped and reported as gaps.
- Timestamps: records carry `uptime_ms` plus `utc`. Before a GNSS or phone time fix, `utc = 0`, and the app back-fills it from the
  next valid (uptime, utc) pair in the same boot session (`boot_id` field).

## 7. Security architecture

### 7.1 Threats addressed
Eavesdropping on location and trip data; an unauthorised phone connecting; command replay; malicious firmware;
a stolen phone; and a relay attacker trying to use the module as a bridge into the car (mitigated mainly by the
firmware's read-only vehicle guard, doc 10 §5).

### 7.2 Mechanisms
1. **Device secret** `K_dev` (32 bytes, generated from the hardware RNG at factory provisioning) is stored in encrypted NVS
   (flash encryption + NVS encryption keys in eFuse). It is printed once as a QR code on a card in the box and on a label
   **inside** the enclosure lid: `LL1:<device_id hex>:<base32(K_dev)>`.
2. **BLE bonding:** LE Secure Connections, Just Works. It is accepted **only while the pairing window is open**: a 120 s window
   started by pressing PAIR with VBAT > 13.2 V (engine running), or the first boot with no bonds. Up to 4 bonds.
   Just Works alone doesn't authenticate the phone, which is why step 3 exists.
3. **App-layer authentication (every connection):**
   ```
   module → AUTH_CHALLENGE(device_id, Nd)
   phone  → AUTH_RESPONSE(user_id, Np, tag = HMAC-SHA256(K_user, "LL-AUTH" | device_id | Nd | Np)[0:16])
   module   verifies (constant-time); K_user = HMAC-SHA256(K_dev, "LL-USER" | user_id)  (user_id 0 = owner)
   both     K_s = HMAC-SHA256(K_user, "LL-SESS" | Nd | Np)
   ```
4. **Command authentication:** every CMD carries a 32-bit counter (strictly increasing per session) and
   `MAC = HMAC-SHA256(K_s, header | payload_without_mac)[0:8]`. Replays and reordering are rejected.
5. **Roles:** owner (user 0) can do everything. A driver (users 1–3) can view, sync, log fill-ups and set the time, but can't
   clear DTCs, update firmware, change configuration or manage users. The owner invites a driver by showing a QR code with
   `user_id`+`K_user`. Revoking adds the user to a blocklist and deletes their bond.
6. **Phone storage:** K_user lives in the iOS Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) or the Android
   Keystore-wrapped EncryptedSharedPreferences. The local database is encrypted with SQLCipher, key in Keychain/Keystore.
7. **Firmware integrity:** ESP32-S3 Secure Boot v2 (RSA-3072 PSS). The signing key is held offline (doc 14 §4). Flash encryption is
   in *release* mode. Anti-rollback uses the `secure_version` eFuse.
8. **Factory reset:** hold PAIR for 10 s with the engine running. This wipes bonds, users, logs and K_user derivations. K_dev is kept,
   so the original QR card stays valid.

## 8. Connection management

| Scenario | Behaviour |
|----------|-----------|
| Car starts, phone in pocket | Module enters STANDBY → advertises at 100 ms. **iOS:** the app has a pending `connect()` to the known peripheral, restored by CoreBluetooth state restoration. The OS connects without waking the app UI and relaunches the app in the background. **Android 12+:** `CompanionDeviceManager.startObservingDevicePresence()` wakes the app's `CompanionDeviceService`, which starts a foreground service (type `connectedDevice`) and connects. Typical time to authenticated: 2–4 s |
| Link loss (tunnel, phone moved) | Module keeps logging and advertising. Phone reconnects automatically; log sync fills the gap |
| Two phones in car | Both may connect: the module allows 2 simultaneous connections, and the live stream goes to both. Commands are serialised by role |
| Interference (Wi-Fi hotspots, other BLE) | BLE adaptive channel hopping over 37 data channels. Module Wi-Fi is off in DRIVE. A GNSS L1 (1575 MHz) and 2.4 GHz conflict doesn't occur |
| Phone OS kills app | iOS state restoration and the Android foreground service cover most cases. Data stays complete on the module regardless |
| Firmware mismatch | AUTH_CHALLENGE carries the FW major version. The app refuses incompatible majors and offers an update |

## 9. Firmware update (OTA)

1. App downloads a release bundle from the release host: `loganlink-fw-x.y.z.bin`, plus `manifest.json` with version,
   sha256, minimum app version and secure_version. The manifest is signed with Ed25519, and the app has the public key pinned.
2. Preconditions checked by the module: engine **not** running (RPM = 0, VBAT < 13.0 V), VBAT > 12.2 V, owner session.
3. Transport: **BLE** (default). 233-byte chunks at offset, written without response, with a CMD `ACK` every 32 chunks for flow
   control. Measured on typical phones: 35–60 KB/s, so a 1.6 MB image takes 30–50 s. **Wi-Fi** (optional): the module starts a
   SoftAP with a random WPA2 PSK sent over the authenticated BLE session, and the phone `PUT`s the image to `http://192.168.4.1/ota`.
   HTTP is acceptable because the image is signed and checked.
4. The module writes the image to the inactive OTA slot. Then `esp_ota_end` runs and Secure Boot verifies the signature. The module
   reboots into it in *pending-verify* state. After 60 s of self-test passing (BLE up, flash OK, vehicle probe OK) it calls
   `esp_ota_mark_app_valid_cancel_rollback()`. Otherwise the bootloader rolls back automatically.

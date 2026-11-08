# 07. Protocol

BLE 5 (LE 2M PHY, data length 251, MTU 247) is the only link between dongle and phone. Wi-Fi is not used. Implemented in
`firmware/main/proto.[ch]`, `ble_link.c`, `auth.c` and in the app under `core/protocol/` and `core/link/`.

## GATT

Service `4c4c0001-6c6f-6761-6e6c-696e6b000000`.

| Characteristic | UUID | Properties | Direction | Security |
|----------------|------|------------|-----------|----------|
| TX | `4c4c0002-...` | Notify | Dongle to phone: LIVE, EVENT, DTC_LIST, RSP, LOG_CHUNK, AUTH_* | Encrypted (LESC bond) |
| RX | `4c4c0003-...` | Write, Write Without Response | Phone to dongle: CMD, AUTH_RESPONSE | Encrypted |
| OTA | `4c4c0004-...` | Write Without Response | Phone to dongle: OTA_DATA | Encrypted, owner session |

Also Device Information (0x180A) and Battery Service (0x180F, reporting the car battery).

Advertising: name `LoganLink-XXXX` (last two MAC bytes), the service UUID, and manufacturer data `[ver][state][vbat/100]` so the
app shows battery voltage before connecting. Interval 100 to 300 ms. No advertising in SLEEP. Two simultaneous connections; the
live stream goes to both, commands are serialised.

Connection interval: 15 to 30 ms while the app shows live data, 100 to 200 ms with latency 4 in the background. The app sends
`SET_LIVE_RATE` when it changes.

## Frame format

Little-endian. Same for all characteristics.

```
Offset  Size  Field
0       1     magic    0xA5
1       1     version  0x01
2       1     type
3       1     flags    bit0 ACK_REQ, bit1 MORE_FRAGMENTS, bit2 AUTHENTICATED (payload ends with 8-byte MAC)
4       2     seq      per direction, wraps at 65535
6       2     len      payload length N (≤ 233)
8       N     payload
8+N     2     crc16    CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) over bytes 0 to 8+N-1
```

| Type | Name | Payload |
|------|------|---------|
| 0x01 | LIVE | LiveV1, 62 bytes |
| 0x02 | EVENT | `u32 log_seq, u32 utc, u8 event_id, u8 severity, u16 len, data` |
| 0x03 | DTC_LIST | `u8 count, u8 source, count x {u16 code, u8 status}` |
| 0x10 | CMD | `u32 counter, u8 cmd, args` + 8-byte MAC |
| 0x11 | RSP | `u32 counter (echo), u8 cmd, u8 status, data` |
| 0x20 | LOG_CHUNK | `u32 first_seq, u8 n_records, records` |
| 0x30 | AUTH_CHALLENGE | `u8 device_id[6], u8 nonce_d[16], u8 fw_major, u8 fw_minor` |
| 0x31 | AUTH_RESPONSE | `u8 user_id, u8 nonce_p[16], u8 tag[16]` |
| 0x32 | AUTH_RESULT | `u8 status, u8 role` |
| 0x40 | OTA_DATA | `u32 offset, data` |

The BLE link layer protects each packet (CRC-24, AES-CCM). CRC-16 catches framing and reassembly errors. The HMAC authenticates
commands.

## LiveV1 (62 bytes, up to 10 Hz)

| Off | Type | Field | Unit | Invalid |
|-----|------|-------|------|---------|
| 0 | u32 | uptime_ms | ms | |
| 4 | u32 | utc | Unix s | 0 |
| 8 | u16 | rpm | rpm | 0xFFFF |
| 10 | u16 | speed | km/h x 10 | 0xFFFF |
| 12 | i16 | coolant | °C x 10 | 0x7FFF |
| 14 | i16 | iat | °C x 10 | 0x7FFF |
| 16 | u16 | map | kPa x 10 | 0xFFFF |
| 18 | u16 | throttle | % x 10 | 0xFFFF |
| 20 | i8 | stft | % | -128 |
| 21 | i8 | ltft | % | -128 |
| 22 | i8 | advance | ° BTDC | -128 |
| 23 | u8 | load | % | 0xFF |
| 24 | u16 | vbat | mV | 0 |
| 26 | u16 | fuel_rate | L/h x 100 | 0xFFFF |
| 28 | i32 | lat | unused in rev B | 0x7FFFFFFF |
| 32 | i32 | lon | unused in rev B | 0x7FFFFFFF |
| 36 | u16 | gnss_speed | unused in rev B | 0xFFFF |
| 38 | u16 | heading | unused in rev B | 0xFFFF |
| 40 | u8 | sats | unused in rev B | 0 |
| 41 | u8 | fix | unused in rev B | 0 |
| 42 | i16 | accel_x | mg, + forward | 0x7FFF |
| 44 | i16 | accel_y | mg, + left | 0x7FFF |
| 46 | u16 | co2 | unused in rev B | 0 |
| 48 | i16 | cabin_temp | unused in rev B | 0x7FFF |
| 50 | u8 | cabin_rh | unused in rev B | 0xFF |
| 51 | u8 | state | 0 BOOT, 1 STANDBY, 2 DRIVE, 3 SLEEP, 4 SAFE | |
| 52 | u16 | source_flags | bit0 rpm ECU, bit1 rpm ripple, bit2 speed ECU, bit4 ECU online, bit5 fuel from model | |
| 54 | u32 | trip_m | m this trip | |
| 58 | u16 | dtc | bit15 MIL, bits 0 to 7 count | |
| 60 | u16 | fuel_level | % x 100 | 0xFFFF |

Location comes from the phone. Speed comes from the ECU; the app substitutes phone GPS speed when the ECU does not report it.
Firmware and app both assert the 62-byte size.

## Commands

| ID | Name | Args | Role | Notes |
|----|------|------|------|-------|
| 0x01 | GET_INFO | | any | See below |
| 0x02 | SET_TIME | `u32 utc, i16 tz_min` | any | Sent on every connect |
| 0x03 | SET_LIVE_RATE | `u8 hz` (0 to 10) | any | 0 stops the stream |
| 0x10 | READ_DTC | `u8 type` (stored, pending) | any | Answers DTC_LIST |
| 0x11 | CLEAR_DTC | `u32 token` | owner | First call returns a token; second call with it within 30 s clears. VEHICLE_STATE if the engine runs |
| 0x12 | RUN_PROBE | | owner | [02](02-car-interface.md) |
| 0x20 | SYNC_LOG | `u32 from_seq, u16 max_records` | any | Streams LOG_CHUNKs |
| 0x21 | GET_CONFIG | `u16 key` | any | Keys in `config.h` |
| 0x22 | SET_CONFIG | `u16 key, value` | owner | |
| 0x23 | SET_ODOMETER | `u32 km` | owner | |
| 0x24 | CAL_VBAT | `u16 measured_mV, u8 point` | owner | Two-point calibration |
| 0x25 | CAL_FUEL | `u8 percent` | owner | Unused in rev B |
| 0x26 | FILL_UP | `u16 litres x 100, u32 price_paise` | any | Feeds fuel-model calibration |
| 0x30 | AUX_OUT | | owner | UNSUPPORTED in rev B |
| 0x40 | OTA_BEGIN | `u32 size, u8 sha256[32]` | owner | |
| 0x41 | OTA_END | | owner | Verify, switch slot |
| 0x42 | WIFI_OTA | | owner | UNSUPPORTED |
| 0x50 | USER_ADD | `u8 user_id` (1 to 3) | owner | Returns that driver's 32-byte K_user |
| 0x51 | USER_REVOKE | `u8 user_id` | owner | Blocklists the user and deletes the bond |
| 0x60 | REBOOT | `u8 mode` (0 normal, 1 safe) | owner | |

Status: 0 OK, 1 BAD_ARGS, 2 NOT_AUTH, 3 FORBIDDEN_ROLE, 4 VEHICLE_STATE, 5 BUSY, 6 IO_ERROR, 7 UNSUPPORTED.

GET_INFO response:

| Field | Size |
|-------|------|
| fw major, minor, patch, hw revision | 4 x u8 |
| protocol, ECU target address, KB1, KB2 | 4 x u8 |
| PID support bitmaps 01-20, 21-40, 41-60, 61-80 | 4 x u32 |
| oldest log seq, head log seq | 2 x u32 |
| vehicle-guard denied count | u32 |
| ECU ID | u8 length + bytes |
| VIN | u8 length + 17 chars (length 0 when the car does not report it) |

## Log sync

- Every record has a strictly increasing 32-bit `seq`. The app stores the last `seq` it has.
- On connect: `SET_TIME`, then `SYNC_LOG(last_seq + 1, n)`. Each chunk is written in one database transaction that also advances
  `last_seq`, so a crash cannot skip or duplicate records.
- A dropped link resumes from the new `last_seq` on the next connect.
- Records with a bad CRC-32 are skipped and reported as gaps.
- Records with `utc = 0` are back-filled from the next valid (uptime, utc) pair with the same `boot_id`.

## Security

| Layer | Mechanism |
|-------|-----------|
| Device key | `K_dev`, 32 bytes from the hardware RNG on first boot, in encrypted NVS. Printed once as a QR pairing card `LL1:<device_id hex>:<base32(K_dev)>`, also on a label inside the shell |
| Bonding | LE Secure Connections, Just Works, accepted only inside the pairing window: 120 s after a PAIR press with the engine running, or the first boot with no bonds. Up to 4 bonds |
| App auth, every connection | Dongle sends `AUTH_CHALLENGE(device_id, Nd)`. Phone answers `tag = HMAC-SHA256(K_user, "LL-AUTH" + device_id + Nd + Np)[0:16]`. `K_user = HMAC-SHA256(K_dev, "LL-USER" + user_id)`, user 0 is the owner. Session key `K_s = HMAC-SHA256(K_user, "LL-SESS" + Nd + Np)`. Constant-time compare |
| Commands | 32-bit counter, strictly increasing per session, and `MAC = HMAC-SHA256(K_s, header + payload)[0:8]`. Replays and reordering rejected |
| Roles | Owner (user 0): everything. Drivers (users 1 to 3): view, sync, fill-ups, set time. Drivers cannot clear codes, update firmware, change config or manage users |
| Invites | Owner sends `USER_ADD`, receives K_user, shows it as a QR code; the driver scans it and bonds in a pairing window the owner opens |
| Phone storage | K_user sealed with the Android Keystore; app data private, backup disabled |
| Firmware | Secure Boot v2 (RSA-3072), flash encryption in release mode, anti-rollback via `secure_version`, OTA only for the owner with the engine off ([06](06-firmware.md)) |
| Car | Read-only vehicle guard ([02](02-car-interface.md)). A compromised phone still cannot send a write service |
| Factory reset | Hold PAIR 10 s with the engine running: all bonds wiped; K_dev kept, so the card stays valid |

Threats covered: eavesdropping on trip and location data, an unauthorised phone, command replay, malicious firmware, a stolen
phone (revoke it), and use of the dongle as a bridge into the car. There is no cloud and no inbound internet path.

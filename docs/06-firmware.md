# 06. Firmware

ESP-IDF v5.3, C11, FreeRTOS SMP, NimBLE, mbedTLS, `espressif/esp-dsp` for the FFT. Target ESP32-S3-MINI-1-N8. Source in
`firmware/main/`. The code has not been compiled yet; expect a round of compile fixes at first build.

## Modules

| File | Responsibility |
|------|----------------|
| `board.h` | Pin map (matches `hardware/pinmap.csv`), firmware version |
| `app_main.c` | Boot sequence, OTA confirmation |
| `vstate.[ch]` | Shared live state, lock-protected snapshot |
| `config.[ch]` | NVS settings, device key, users, vehicle profile, local-ID map |
| `kline.[ch]` | K-line init (fast and 5-baud), framing, echo check, timings, foreign-tester detection |
| `vguard.[ch]` | Default-deny allow-list for every vehicle request ([02](02-car-interface.md)) |
| `obd.[ch]` | Probe, PID scheduler and decoding, VIN, DTC read and clear, local-ID mapping |
| `vbat.[ch]` | ADC continuous sampling, VBAT filter, crank capture, ripple FFT and RPM estimate |
| `fuel.[ch]` | Fuel model and self-calibration |
| `trip.[ch]` | Trip start and end, distance, harsh events, 1 Hz history records |
| `sensors.[ch]` | LIS2DW12 accelerometer, time keeping |
| `logger.[ch]` | 128-byte CRC-protected ring records in the `log` partition |
| `proto.[ch]` | Frame build and parse, CRC-16, LiveV1 packing ([07](07-protocol.md)) |
| `auth.[ch]` | Challenge-response, session key, command MAC and replay check |
| `ble_link.[ch]` | GATT server, pairing window, command worker, live stream, log sync, OTA |
| `power.[ch]` | State machine, sleep and hibernate, ULP, PAIR button, LED, thermal policy |
| `ulp/` | ULP-RISC-V program: parked VBAT supervision |

## Tasks

| Task | Core | Priority | Trigger | Stack |
|------|------|----------|---------|-------|
| `vbat` | 1 | 12 | ADC DMA frame | 6 KB |
| `power` | 1 | 11 | 100 ms | 4 KB |
| `vehicle` | 1 | 10 | K-line bound, 70 to 110 ms per request | 6 KB |
| `link` | 0 | 9 | Queue or 10 ms | 8 KB |
| `probe` | 1 | 9 | On RUN_PROBE, exits when done | 6 KB |
| `sensors` | 1 | 7 | 10 ms | 3 KB |
| `otaok` | any | 5 | Once, after an OTA boot | 3 KB |

BLE and the protocol run on core 0; everything that touches the car runs on core 1.

## Boot

1. Second-stage bootloader: Secure Boot verify, flash decrypt, OTA slot select or rollback.
2. Read wake cause and reset reason, log them.
3. GPIO defaults: `AUX_PWR` off, LED off.
4. `vstate_init`, `config_init` (device key generated on the very first boot).
5. `logger_init`: scan for the ring head. Failure enters SAFE state (BLE and OTA only, vehicle task not started).
6. Write a boot record.
7. Start `vbat`, `sensors`, `kline` and `obd`.
8. Start BLE: advertise; open a 10 min pairing window if no bonds exist.
9. Start `power` in STANDBY.
10. If the image is pending verification, run the 60 s self-test, then confirm or roll back.

Wake to advertising takes about 350 ms; to first live frame on a bonded phone 2 to 4 s.

Time: there is no RTC crystal. The RTC runs on the internal oscillator through deep sleep and is re-synced from the phone
(`SET_TIME`) on every connect. Records written before the first sync carry `utc = 0`; the app back-fills them from the next valid
(uptime, utc) pair in the same boot.

## Power states and sleep

| Transition | Condition | Action |
|------------|-----------|--------|
| STANDBY to DRIVE | RPM > 400 (ECU or ripple), or VBAT > 13.2 V for 3 s | Trip start, fuel model on |
| DRIVE to STANDBY | Trip ended: engine off for 8 min | Trip end record, odometer advanced |
| STANDBY to SLEEP | No BLE link, no trip, idle 5 min (3 min after a motion or voltage-step wake, 10 s after a timer wake) | `AUX_PWR` off, ULP armed at 1 s, accelerometer wake armed, 6 h timer |
| STANDBY to HIBERNATE | Resting VBAT < 12.1 V for 10 min, or parked > 14 days | ULP at 10 s, wake only above 13.0 V, motion wake off |
| SLEEP to STANDBY | ULP (VBAT > 13.2 V, or step > 150 mV), accelerometer, timer | Reboot, cause logged |

Crank: a start that drops VBAT below the brownout level resets the MCU. The next boot logs reset reason BROWNOUT; the app treats a
brownout boot followed by engine running as a weak-crank event.

Thermal policy: the die temperature is read every 10 s; above 105 °C the sensors stop.

PAIR button (C&K PTS810): a short press with the engine running opens a 120 s pairing window; a 10 s hold with the engine running
does a factory reset (all bonds wiped, device key kept).

## Logger

| Item | Value |
|------|-------|
| Storage | `log` partition, 4 MB, internal flash, encrypted |
| Record | 128 bytes: `seq, utc, uptime_ms, boot_id, type, len, data[108], crc32` |
| Capacity | 32,768 records, about 9 h of driving at 1 Hz. The phone syncs every drive |
| Wear | At 2 h a day the ring wraps every 4 to 5 days, about 800 erase cycles in 10 years against 100,000 rated |
| Power loss | Atomic per record (CRC); a torn record is skipped; the head is found by scanning at boot |
| Flush | Every 10 s and at engine-off |

Flash writes stall the instruction cache for about 1 ms per record. K-line and BLE are buffered in hardware, so this is harmless.

Partition table (`partitions.csv`, 8 MB): `nvs` 24 KB, `nvs_keys` 4 KB, `otadata`, `phy_init`, `ota_0` and `ota_1` 1.75 MB each,
`coredump` 64 KB, `log` 4 MB, `cfg` 320 KB.

## Fuel model

Speed-density, because the engines in scope have a MAP sensor and no MAF.

```
m_air  = MAP x Vd x (RPM / 120) x VE / (R x T_IAT)     R = 287.05 J/(kg K), Vd = engine displacement
m_fuel = m_air / 14.7 x (1 + (STFT + LTFT) / 100)      0 during decel fuel cut (TPS < 1.5 %, RPM > 1500)
Q[L/h] = m_fuel x 3600 / 0.745                          petrol density 0.745 kg/L
```

Worked example, reference car at idle (Vd = 1.598 L): MAP 32 kPa, 800 rpm, IAT 40 °C (313.15 K), VE 0.66, trims 0.

```
m_air  = 32,000 x 0.001598 x 6.667 x 0.66 / (287.05 x 313.15) = 2.50 g/s
m_fuel = 2.50 / 14.7 = 0.170 g/s
Q      = 0.170 x 3600 / 745 = 0.82 L/h
```

Self-calibration: on a fill-to-full, pumped litres equal litres burned since the previous fill-to-full.
`VE_corr = VE_corr x (0.7 + 0.3 x pumped / modelled)`, accepted only when the ratio is within 0.7 to 1.3. Accuracy is ±10 % after the
first fill and ±3 to 5 % after three. Fill-ups arrive through `FILL_UP`, including the ones the app logs automatically.

## OTA, rollback and secure boot

OTA runs over BLE only (`WIFI_OTA` answers UNSUPPORTED).

1. The owner selects a signed `.bin` in the app (Device, Firmware update).
2. Preconditions checked on the dongle: engine off (RPM 0, VBAT < 13.0 V), VBAT > 12.2 V, owner session.
3. `OTA_BEGIN` with size and SHA-256, then 233-byte chunks to the inactive slot, acknowledged every 32 chunks. A 1.6 MB image takes
   30 to 50 s.
4. `OTA_END`: hash and signature verified, boot partition switched, reboot.
5. The new image boots pending verification. After 60 s of passing self-test (BLE up, flash OK, vehicle probe OK) it calls
   `esp_ota_mark_app_valid_cancel_rollback()`. Otherwise the bootloader rolls back.

Security build for production units (keep one development unit unfused; eFuse burning is irreversible, rehearse on a spare board):

1. Generate the Secure Boot v2 RSA-3072 signing key offline (`espsecure.py generate_signing_key --version 2`). Store it encrypted
   offline with two copies. Losing it ends firmware updates for fused units.
2. Enable `CONFIG_SECURE_BOOT_V2_ENABLED`, `CONFIG_SECURE_FLASH_ENC_ENABLED` in release mode, `CONFIG_NVS_ENCRYPTION`,
   `CONFIG_SECURE_ENABLE_SECURE_ROM_DL_MODE`, `CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE` and anti-rollback.
3. Disable JTAG by eFuse.
4. Flash once over USB; the first boot burns the keys and encrypts flash.
5. Bump `secure_version` for security fixes so older images cannot be reinstalled.

## Robustness

| Mechanism | Setting |
|-----------|---------|
| Task watchdog | 5 s, panic and reboot |
| Brownout | Clean reset |
| Core dump | `coredump` partition |
| Invalid data | Sentinel values ([07](07-protocol.md)), never zeros; every value carries a source bit |

## Build and flash

```bash
# ESP-IDF v5.3 installed and exported
cd firmware
idf.py set-target esp32s3
idf.py build
idf.py -p COMx flash monitor      # USB-C, native USB-Serial-JTAG
```

Download mode: hold PAIR (GPIO0) while plugging in USB. For the dev-board prototype, remap the pins in `board.h`.

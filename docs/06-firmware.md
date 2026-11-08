# 06. Firmware

ESP-IDF v5.3, C11, FreeRTOS SMP, NimBLE, mbedTLS, `espressif/esp-dsp` (FFT). Target ESP32-S3-MINI-1-N8. Source in
`firmware/main/`. Not compiled yet; expect a round of compile fixes.

## Build and flash

```bash
cd firmware                       # ESP-IDF v5.3 installed and exported
idf.py set-target esp32s3
idf.py build
idf.py -p COMx flash monitor      # USB-C, native USB-Serial-JTAG
```

Download mode: hold PAIR (GPIO0) while plugging in USB. For the dev-board prototype, remap pins in `board.h`.

## Modules

| File | Responsibility |
|------|----------------|
| `board.h` | Pin map (matches `hardware/pinmap.csv`), firmware version |
| `app_main.c` | Boot sequence, OTA confirmation |
| `vstate` | Shared live state, locked snapshot |
| `config` | NVS settings, device key, users, vehicle profile, `lidmap` |
| `kline` | Fast and 5-baud init, framing, echo check, timings, foreign-tester detection |
| `vguard` | Default-deny allow-list ([02](02-car-interface.md)) |
| `obd` | Probe, PID scheduler and decoding, VIN, DTC read and clear, local IDs |
| `vbat` | ADC sampling, VBAT filter, crank capture, ripple FFT and RPM |
| `fuel` | Fuel model and self-calibration |
| `trip` | Trip start and end, distance, harsh events, 1 Hz records |
| `sensors` | LIS2DW12, time keeping |
| `logger` | CRC-protected 128-byte ring records in the `log` partition |
| `proto` | Frames, CRC-16, LiveV1 packing ([07](07-protocol.md)) |
| `auth` | Challenge-response, session key, command MAC, replay check |
| `ble_link` | GATT server, pairing window, command worker, live stream, log sync, OTA |
| `power` | State machine, sleep, ULP, PAIR button, LED, thermal policy |
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

BLE and protocol on core 0; everything that touches the car on core 1.

## Boot

Bootloader (Secure Boot verify, flash decrypt, OTA slot or rollback), then: log wake cause and reset reason; `AUX_PWR` and LED off;
`vstate_init`, `config_init` (device key made on the very first boot); `logger_init` (on failure SAFE state: BLE and OTA only);
start `vbat`, `sensors`, `kline`, `obd`; BLE advertising (10 min pairing window if no bonds); `power` in STANDBY; if the image is
pending verification, 60 s self-test then confirm or roll back. Wake to advertising about 350 ms, first live frame 2 to 4 s.

No RTC crystal: `SET_TIME` re-syncs the clock on every connect; earlier records have `utc = 0` and the app back-fills them.

## Power states

| State | Entered when | Active | Current at 12.6 V |
|-------|--------------|--------|-------------------|
| DRIVE | RPM > 400 (ECU or ripple), or VBAT > 13.2 V for 3 s | Trip start, fuel model, ECU polling, 1 Hz log, 10 Hz live | about 20 mA |
| STANDBY | Trip ended (8 min engine-off; trip end record, odometer advanced), or after wake | BLE advertising, low-rate sensing | about 8 mA |
| SLEEP | No BLE link, no trip, 5 min idle (3 min after a motion or voltage-step wake, 10 s after a timer wake) | `AUX_PWR` off; ULP checks VBAT every 1 s; accelerometer wake; 6 h timer | 61 µA design, 150 µA max |
| HIBERNATE | Resting VBAT < 12.1 V for 10 min, or parked > 14 days | ULP every 10 s, wakes only above 13.0 V, no motion wake | about 60 µA |

SLEEP wakes (reboot, cause logged) on VBAT > 13.2 V, a VBAT step of 150 mV (door, lights), motion, or the 6 h timer.

- Crank: a start that drops VBAT below brownout resets the MCU. A BROWNOUT boot followed by engine running is shown as a weak crank.
- Thermal: die temperature read every 10 s; above 105 °C the sensors stop.
- PAIR button (C&K PTS810), engine running: short press opens a 120 s pairing window; 10 s hold factory-resets (bonds wiped, device
  key kept).
- LED: green steady in DRIVE, short green blink every 3 s in STANDBY, off in SLEEP.

## Logger

| Item | Value |
|------|-------|
| Storage | `log` partition, 4 MB internal flash, encrypted |
| Record | 128 bytes: `seq, utc, uptime_ms, boot_id, type, len, data[108], crc32` |
| Capacity | 32,768 records, about 9 h of driving at 1 Hz |
| Wear | At 2 h a day the ring wraps every 4 to 5 days: about 800 erase cycles in 10 years (100,000 rated) |
| Power loss | Atomic per record (CRC); torn records skipped; head found by scan at boot |
| Flush | Every 10 s and at engine-off |

Partitions (`partitions.csv`, 8 MB): `nvs` 24 KB, `nvs_keys` 4 KB, `otadata`, `phy_init`, `ota_0` and `ota_1` 1.75 MB each,
`coredump` 64 KB, `log` 4 MB, `cfg` 320 KB.

## Fuel model

Speed-density (MAP sensor, no MAF):

```
m_air  = MAP x Vd x (RPM / 120) x VE / (R x T_IAT)     R = 287.05 J/(kg K), Vd = displacement
m_fuel = m_air / 14.7 x (1 + (STFT + LTFT) / 100)      0 during decel fuel cut (TPS < 1.5 %, RPM > 1500)
Q[L/h] = m_fuel x 3600 / 0.745                          petrol 0.745 kg/L
```

Reference car at idle (Vd = 1.598 L): MAP 32 kPa, 800 rpm, IAT 40 °C (313.15 K), VE 0.66, trims 0 gives
m_air = 32,000 x 0.001598 x 6.667 x 0.66 / (287.05 x 313.15) = 2.50 g/s, m_fuel = 0.170 g/s, Q = 0.82 L/h.

Self-calibration assumes fill-to-full: each `FILL_UP` over 5 L, with over 5 L modelled since the previous one, compares pumped to
modelled litres. `VE_corr = VE_corr x (0.7 + 0.3 x pumped / modelled)`, applied only for a ratio of 0.7 to 1.3. Accuracy ±10 %
after the first fill, ±3 to 5 % after three.

## OTA

BLE only (`WIFI_OTA` answers UNSUPPORTED).

1. Owner picks a signed `.bin` in the app (LoganLink module, Firmware update).
2. Dongle checks: engine off (RPM 0, VBAT < 13.0 V), VBAT > 12.2 V, owner session.
3. `OTA_BEGIN` (size, SHA-256), then 233-byte chunks to the inactive slot, acked every 32 chunks. 1.6 MB takes 30 to 50 s.
4. `OTA_END`: hash and signature verified, boot partition switched, reboot.
5. After 60 s of passing self-test (BLE up, flash OK, probe OK) it calls `esp_ota_mark_app_valid_cancel_rollback()`; otherwise the
   bootloader rolls back.

## Secure boot (production units)

eFuse burning is irreversible: keep one development unit unfused and rehearse on a spare board.

1. `espsecure.py generate_signing_key --version 2` (Secure Boot v2, RSA-3072), offline. Store encrypted, two copies. Losing it ends
   updates for fused units.
2. Enable `CONFIG_SECURE_BOOT_V2_ENABLED`, `CONFIG_SECURE_FLASH_ENC_ENABLED` (release mode), `CONFIG_NVS_ENCRYPTION`,
   `CONFIG_SECURE_ENABLE_SECURE_ROM_DL_MODE`, `CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE` and anti-rollback.
3. Disable JTAG by eFuse.
4. Flash once over USB; the first boot burns the keys and encrypts flash.
5. Bump `secure_version` for security fixes so older images cannot be reinstalled.

## Robustness

Task watchdog 5 s (panic, reboot); brownout gives a clean reset; core dump to `coredump`; invalid data uses sentinel values
([07](07-protocol.md)), never zeros, and every value carries a source bit.

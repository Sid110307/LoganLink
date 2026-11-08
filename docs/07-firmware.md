# 07 — Firmware and Embedded Software

## 1. Toolchain and frameworks

| Item | Selection |
|------|-----------|
| SDK | **ESP-IDF v5.3 LTS** (C11, FreeRTOS SMP, NimBLE host, mbedTLS) |
| Managed components | `espressif/esp-dsp` (FFT) |
| Additional (production) | Sensirion `gas-index-algorithm` (SGP41 VOC/NOx index) |
| Language | C11 for the main firmware; ULP program in C on the ULP-RISC-V |
| Build | CMake / `idf.py`; reproducible build with `IDF_TOOLS_PATH` pinned in CI |
| Static analysis | `idf.py clang-check` (clang-tidy), cppcheck; `-Werror=all` (IDF default) |
| Unit tests | Unity (IDF `test_apps`) for `proto`, `kline` framing (loopback mock), `fuel`, `logger` (host-side with `linux` target where possible) |

## 2. Source organisation (`firmware/main/`)

| Module | Files | Responsibility | Core / prio |
|--------|-------|----------------|-------------|
| Board | `board.h` | Pin map, constants (mirrors `hardware/pinmap.csv`) | — |
| State | `vstate.[ch]` | Shared live state, lock-protected snapshot | — |
| Config | `config.[ch]` | NVS-backed settings, K_dev, users, vehicle profile, local-ID map | — |
| K-line | `kline.[ch]` | ISO 9141-2 / ISO 14230 PHY control, fast/5-baud init, framing, echo check, P-timings, foreign-tester detection | C1 |
| Guard | `vguard.[ch]` | **Default-deny allow-list** for every vehicle request | — |
| OBD | `obd.[ch]` | Probe, PID scheduler, decode, DTC read/clear, Renault local-ID mapper | C1 / 10 |
| Battery | `vbat.[ch]` | 2-channel ADC continuous (40 kS/s each), VBAT filter, crank capture, ripple FFT, RPM estimate, K_rip self-cal | C1 / 12 |
| Fuel | `fuel.[ch]` | Speed-density fuel model, VE self-calibration from fill-ups | C1 (from power task) |
| GNSS | `gnss.[ch]` | UBX VALSET config, NAV-PVT parser, speed fallback, parking fix | C1 / 8 |
| Sensors | `sensors.[ch]` | I²C buses, IMU (crash detection), SHT45, SCD41, wake accelerometer, RTC | C1 / 7 |
| Trip | `trip.[ch]` | Trip lifecycle, distance, harsh events, 1 Hz history records, AUX output | C1 (power task) |
| Logger | `logger.[ch]` | 128-byte CRC-protected ring records on the external 32 MB NOR | — |
| Protocol | `proto.[ch]` | Frame build/parse, CRC-16, LiveV1 packing | — |
| Auth | `auth.[ch]` | Challenge/response, session key, command MAC + replay protection | — |
| BLE link | `ble_link.[ch]` | NimBLE GATT, pairing window, worker task: commands, live stream, log sync, OTA | C0 / 9 |
| Power | `power.[ch]` | State machine, sleep/hibernate, ULP setup, PAIR button, LED, thermal policy | C1 / 11 |
| ULP | `ulp/ulp_vbat.c` | Parked VBAT supervision; wakes main CPU on charge or voltage step | ULP |
| Entry | `app_main.c` | Boot sequence, OTA confirmation | — |

## 3. Boot sequence

```
ROM → 2nd-stage bootloader (Secure Boot v2 verify, flash decrypt, OTA slot select / rollback)
 → app_main
   1. read wake cause + reset reason (logged)
   2. GPIO defaults (rails off, AUX off)
   3. vstate_init, config_init (NVS; K_dev generated on very first boot)
   4. I²C AON: RTC → initial UTC; wake accelerometer present?
   5. 3V3_SW on → logger_init (head scan ≈ 0.3 s). Failure → SAFE state
   6. REC_BOOT record
   7. vbat_start (ADC continuous) → sensors_start (IMU/cabin)
   8. kl_init → obd_start (vehicle task; probes on first ignition)
   9. ble_link_start (NimBLE; advertises; opens 10-min pairing window if no bonds exist)
  10. power_start (supervisor) → apply STANDBY (GNSS 1 Hz)
  11. if image is PENDING_VERIFY → 60 s self-test → confirm or roll back
Typical time from wake to advertising: ≈ 350 ms. To first live frame on an already-bonded phone: 2–4 s.
```

## 4. Task model and timing

| Task | Core | Prio | Period / trigger | Stack | Worst-case execution |
|------|------|------|------------------|-------|----------------------|
| `vbat` | 1 | 12 | ADC DMA frame (≈ 3.2 ms) | 6 KB | FFT 2048 pts ≈ 1.5 ms @160 MHz once per s |
| `power` | 1 | 11 | 100 ms | 4 KB | < 1 ms (+ flash write 1 Hz ≈ 0.6 ms) |
| `vehicle` | 1 | 10 | K-line bound: ≈ 70–110 ms per request | 6 KB | blocks on UART |
| `link` (BLE worker) | 0 | 9 | queue or 10 ms | 8 KB | notify ≈ 0.2 ms; HMAC ≈ 0.1 ms |
| NimBLE host | 0 | (IDF) | events | (IDF) | — |
| `gnss` | 1 | 8 | UART bytes | 4 KB | < 0.2 ms per NAV-PVT |
| `sensors` | 1 | 7 | 10 ms | 4 KB | I²C 6-byte read ≈ 0.25 ms |

Effective vehicle refresh: with all 10 J1979 PIDs supported, the scheduler gives RPM/speed ≈ 4–5 Hz, MAP/TPS ≈ 2 Hz, and the rest
0.2–1 Hz. That is the protocol limit of K-line at 10.4 kbit/s with ISO-mandated P2/P3 times. The UI interpolates RPM between updates
with the ripple estimate, which updates at about 1 Hz.

## 5. Power-state machine (implementation of doc 03 §3)

| From → To | Condition (code: `power.c`) | Actions |
|-----------|-----------------------------|---------|
| BOOT → STANDBY | init done | GNSS 1 Hz, BLE advertising |
| STANDBY → DRIVE | `running` = ECU/ripple RPM > 400, **or** VBAT > 13.2 V for 3 s, **or** IGN tap | GNSS 5 Hz, trip start, fuel model |
| DRIVE → STANDBY | engine off for 8 min (long Bangalore signals and jams do not split a trip) | REC_TRIP_END, odometer += distance |
| STANDBY → SLEEP | no BLE link, no trip, idle for the standby timeout: **30 min after a drive** (occupant watch), **3 min** after a motion/voltage-step wake, **10 s** after the 6-hourly timer wake | Rails off, ULP armed (1 s), wake accelerometer armed (0.12 g), IGN ext1, 6 h timer |
| STANDBY → HIBERNATE | resting VBAT < 12.1 V (≈ 30 % SoC) for 10 min, or parked > 14 days | ULP every 10 s, wake only at > 13.0 V. No motion wake |
| SLEEP → (reboot) STANDBY | ULP (charge or step), accel, IGN, timer | Cause logged |

**Crank survival.** The crank capture lives in `vbat.c`. A start that resets the MCU (VBAT < 4.2 V) loses that crank record, but
the next boot logs `REC_BOOT` with reset reason `BROWNOUT`. The app treats a brownout boot followed by engine running as a "weak
crank" event. That gives the same user-facing result as a crank record.

## 6. Vehicle communication

### 6.1 Probe (`obd_run_probe`)
1. Fast init, functional 0x33 → 2. 5-baud KWP 0x33 → 3. ISO 9141-2 0x33 → 4. fast init physical to each address in
   `config_ecu_candidates()`. There is a 3 s pause between attempts so the ECU's P3max session timer clears.
2. On success: PID bitmaps 0x00/0x20/0x40/0x60; `0x1A 0x80` ECU identification; `0x21 01…0F` raw blocks logged as `REC_LID_DUMP`
   for the app's Signal Mapper.
3. The profile persists in NVS. Re-probe happens only on command or after repeated connect failures.

### 6.2 Scheduling
The most-overdue supported PID is requested next (see `s_sched[]`). Local-ID blocks are polled at 4 Hz when a mapping exists.
TesterPresent (`3E 01`) goes out after 2 s of silence, under the P3max limit of 5 s.

### 6.3 Coexistence with workshop tools
`kline.c` detects bytes the module didn't cause, outside the reply window. That signals another tester on the bus, and all
module traffic stops for 10 minutes. The splitter keeps the socket available, and the module never contends with a garage scan tool.

### 6.4 Error handling
Three consecutive timeouts or CRC/echo errors mark the ECU offline. Rules: back off 2 s, then reconnect. After 3 failed
connects, back off 30 s. An echo mismatch means a bus fault or collision, so the request is aborted immediately and never retried
blindly. A negative response `7F xx 78` extends P2 to 5 s (responsePending). Other negative responses mark the PID unsupported for
the session.

## 7. Data logging

- Records are 128 bytes, fixed: `seq, utc, uptime_ms, boot_id, type, len, data[108], crc32`.
- Capacity: 32 MB / 128 B = 262,144 records, which is **≈ 73 h of driving at 1 Hz**, or several months of typical Indian
  urban use (1–2 h/day) when combined with phone sync.
- Wear: each sector is erased once per full ring pass. At 2 h/day the ring wraps about every 36 days, so 10 years is about 100
  erase cycles, against the W25Q256 rating of 100,000.
- Power-loss behaviour: a write is atomic at record level (CRC). On boot the head is found by scanning sector heads. A torn record
  is skipped.
- Code and log data use separate flash chips. Log writes never stall instruction fetch from the internal flash.

## 8. Fuel model (`fuel.c`)

```
m_air  = MAP·Vd·(RPM/120)·VE / (R·T_IAT)                 R = 287.05 J/kg·K, Vd = 1.598 L
m_fuel = m_air / 14.7 · (1 + (STFT+LTFT)/100)            0 during decel fuel cut (TPS < 1.5 %, RPM > 1500)
Q[L/h] = m_fuel · 3600 / 0.745
```
Worked example (idle): MAP 32 kPa, 800 rpm, IAT 40 °C, VE 0.66 → m_air = 32 000 × 0.001598 × 6.667 × 0.66 / (287.05 × 313.15)
= **2.50 g/s** → fuel 0.170 g/s → **0.82 L/h**. That is consistent with a 1.6 L 8-valve idle consumption of 0.8–1.0 L/h.

**Self-calibration.** When the user logs a fill-to-full, the pumped litres equal the litres burned since the previous fill-to-full.
`VE_corr ← VE_corr × (0.7 + 0.3 × pumped/modelled)`, accepted only when the ratio is in 0.7–1.3. Expect accuracy of ±10 % after the
first fill and **±3–5 % after three fills**.

## 9. Robustness mechanisms

| Mechanism | Implementation |
|-----------|----------------|
| Task watchdog | 5 s, panic → reboot. All long loops yield |
| Interrupt watchdog | IDF INT_WDT |
| Brownout | Level 7 (≈ 2.9 V) → clean reset |
| Core dump | To flash partition `coredump`; uploaded through the app for diagnostics |
| Rollback | `BOOTLOADER_APP_ROLLBACK_ENABLE` + 60 s self-test before `mark_app_valid` |
| Anti-rollback | `secure_version` eFuse, bumped for security fixes |
| SAFE mode | If log flash fails: BLE + OTA only, vehicle task not started |
| Stack overflow | FreeRTOS canary + `uxTaskGetStackHighWaterMark` reported in GET_INFO (debug builds) |
| Data validation | Every decoded value carries its source bit. Invalid values use the sentinel codes of doc 06 §4, never zeros |

## 10. Memory budget (estimate)

| Region | Use |
|--------|-----|
| Flash (app) | ≈ 1.3 MB (NimBLE 350 KB, mbedTLS 200 KB, app 150 KB, IDF 600 KB). The 3 MB OTA slots leave headroom |
| Internal SRAM | ≈ 190 KB used of 512 KB (FFT buffers 24 KB, NimBLE 60 KB, stacks 40 KB) |
| PSRAM 2 MB | Reserved: future on-device ML (driving-style classifier), OTA staging buffer |

## 11. Security build configuration (release)

Enable in `sdkconfig` for production units (keep a **development unit unfused**):
`CONFIG_SECURE_BOOT_V2_ENABLED`, `CONFIG_SECURE_FLASH_ENC_ENABLED` + `MODE_RELEASE`, `CONFIG_NVS_ENCRYPTION`,
`CONFIG_SECURE_DISABLE_ROM_DL_MODE` (or `SECURE_ENABLE_SECURE_ROM_DL_MODE`), and disable JTAG via eFuse. Signing key generation and
custody are covered in doc 14 §4. **eFuse burning is irreversible.** Test the full flow on a sacrificial board first.

## 12. Build, flash and monitor

```bash
# one-time: install ESP-IDF v5.3 (https://docs.espressif.com/projects/esp-idf/en/v5.3/esp32s3/get-started/)
cd firmware
idf.py set-target esp32s3
idf.py build
idf.py -p COMx flash monitor          # USB-C (native USB-Serial-JTAG)
```
Production programming over Tag-Connect: `esptool.py --chip esp32s3 -p COMx write_flash @flash_args` (doc 14 §3).

## 13. Implementation status of this repository

| Area | Status |
|------|--------|
| K-line PHY control, fast/5-baud init, KWP/ISO 9141 framing, guard, PID decode, probe, DTCs | Implemented |
| VBAT, crank capture, ripple FFT / RPM estimate, fuel model, trip, logger, power states, ULP | Implemented |
| BLE GATT, auth, commands, live stream, log sync, BLE OTA | Implemented |
| GNSS, IMU, SHT45, SCD41, RTC, wake accelerometer | Implemented (register values per datasheets, to be confirmed on first boards) |
| SGP41 gas index; fuel-sender ADC; coolant NTC; REV input | Hooks present, drivers to finish during bring-up |
| CAN (ISO 15765) | Hardware fitted; driver deferred because the reference car has no CAN on the OBD socket (V1) |
| Wi-Fi SoftAP OTA (`CMD_WIFI_OTA`) and LTE/MQTT (variant C) | Returns `RS_UNSUPPORTED` in v1.0; planned for v1.1 |

**This code has not yet been compiled.** It was written against the ESP-IDF v5.3 APIs. Expect a short round of compile fixes on
first build, then bring-up on real hardware per doc 12.

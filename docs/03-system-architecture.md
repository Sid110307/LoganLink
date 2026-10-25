# 03 — System Architecture

## 1. Architecture options evaluated

| Option | Description | Score (functionality / reliability / power / cost / maintainability, 1–5) | Verdict |
|--------|-------------|-----------------------------------------------------------------------|---------|
| 1. ELM327 dongle + phone app only | Commercial dongle, app does everything | 2 / 2 / 2 / 5 / 3 | Rejected: no logging when phone absent, no sensors, typical clones drain battery and are flaky on K-line init |
| 2. Linux SBC (Raspberry Pi/CM4) | Rich OS | 5 / 2 / 1 / 3 / 2 | Rejected: 2–3 W idle, SD-card corruption on power loss, slow boot, high parked drain |
| 3. MCU + separate BLE module + crypto chip | e.g., STM32 + nRF52 | 4 / 5 / 5 / 3 / 3 | Good but more parts, two firmwares to maintain |
| **4. Single ESP32-S3 SoC + transceivers + sensors** | One SoC with BLE 5, Wi-Fi, TWAI, ULP, secure boot | **5 / 4 / 4 / 5 / 5** | **Selected** |
| 5. Distributed (sensor nodes around car) | Several modules | 5 / 3 / 3 / 2 / 2 | Rejected: more installation points; nothing to gain on this car |

ESP32-S3 drawbacks and how they are handled: ~8 µA deep sleep (fine). Only RSA secure boot on S3 (fine).
The module's PCB antenna needs keep-out (doc 05 §6). Wi-Fi isn't used while driving, to keep the 2.4 GHz
band clear for BLE.

## 2. System block diagram

```
                                    ┌──────────────────────────── LL-VM1 MODULE ─────────────────────────────┐
 OBD J1962 (via splitter)          │                                                                         │
  pin16 +BAT ──F 3A──┐              │  ┌───────── POWER ─────────┐        ┌──────────── ESP32-S3-WROOM-1 ─────┐ │
  pin4/5 GND ────────┼──────────────┼─▶│ TVS SM8S26A             │ 3V3_AON│ Core0: BLE (NimBLE), proto, OTA   │ │
                     │              │  │ Schottky STPS3H100U     │───────▶│ Core1: vehicle, sensors, logger   │ │
                     │              │  │ π-filter, 47µF hybrid   │        │ ULP-RISC-V: parked supervision    │ │
                     │              │  │ LMR36015 buck → 3.3 V   │        │ TWAI ◀──────────▶ SN65HVD230Q ◀──┼─┼── pin 6/14 CAN
                     │              │  │ TPS22918 ×2 load sw.    │        │ UART1 ◀─────────▶ discrete K PHY ◀┼─┼── pin 7 K-line
                     │              │  │ [C] TPS54360B → 3.8 V   │        │ UART2 ◀─────────▶ MAX-M10S GNSS ◀┼─┼── U.FL → active GNSS antenna
                     │              │  └─────────────────────────┘        │ I2C  ◀─▶ ASM330LHHX IMU (sw)     │ │
                     │              │                                      │      ◀─▶ AIS2DW12 wake accel (aon)│ │
 Optional taps ──────┼──────────────┼─▶ IGN_SENSE  (RTC GPIO, wake)       │      ◀─▶ SHT45, SGP41, SCD41    │ │
  IGN / FUEL / REV / │              │   AIN_AUX (fuel)  REV_SENSE          │      ◀─▶ RV-3028 RTC (aon)      │ │
  coolant probe      │              │   VBAT_SENSE (DC) / VBAT_AC (ripple) │ SPI  ◀─▶ W25Q256 32 MB NOR       │ │
                     │              │                                      │ USB  ◀─▶ USB-C (prog/debug)      │ │
 AUX_OUT (1 A) ◀─────┼──────────────┼── low-side driver ◀──────────────────│ LEDC ──▶ RGB LED, PAIR button     │ │
                     │              │                                      │ [C] UART0 ◀─▶ A7672S LTE Cat-1   │ │
                     │              │                                      └──────────────┬────────────────────┘ │
                     │              └─────────────────────────────────────────────────────┼──────────────────────┘
                                                                                          │ BLE 5 (LESC, 2M PHY)
                                                                                          ▼
                                     ┌──────────────── SMARTPHONE APP (Flutter) ─────────────────┐
                                     │ BLE transport → frame codec → auth → repositories          │
                                     │ SQLite (trips, samples, DTCs, events) → analytics engine   │
                                     │ UI (Riverpod state) · notifications · voice · maps         │
                                     └───────────────┬────────────────────────────────────────────┘
                                                     │ optional HTTPS (backup/export, weather, LLM opt-in)
                                                     ▼
                                     ┌──────── OPTIONAL SERVICES ────────┐
                                     │ Owner's MQTT broker (variant C)   │
                                     │ Firmware release host (signed)    │
                                     └───────────────────────────────────┘
```

## 3. Operating states

```
            power applied / reset
                    │
                    ▼
              ┌──────────┐  self-test fail ┌──────────┐
              │  BOOT    │────────────────▶│  SAFE    │ (BLE only, logs, OTA allowed)
              └────┬─────┘                 └──────────┘
                   │ ok
                   ▼
   ┌────────────▶┌──────────┐ VBAT>13.2V 3s │ IGN=1 │ motion+K-line resp
   │             │ STANDBY  │───────────────────────────────┐
   │             │(BLE adv, │                               ▼
   │             │ sensors  │                         ┌──────────┐
   │             │ low rate)│◀────── engine off 120s ─│  DRIVE   │ ECU polling, GNSS 5 Hz, IMU 100 Hz,
   │             └────┬─────┘                         │          │ logging 1 Hz, live stream 10 Hz
   │                  │ 30 min no activity            └──────────┘
   │                  ▼ (post-park watch done)
   │             ┌──────────┐ ULP: VBAT rise >0.4 V or >13.2 V │ IMU motion │ IGN │ RTC tick
   │             │  SLEEP   │──────────────────────────────────────────────────────────▶ STANDBY
   │             │ deep, ULP│
   │             └────┬─────┘
   │                  │ resting VBAT < 12.1 V for 10 min, or parked > 14 days
   │                  ▼
   │             ┌──────────┐ ULP checks only VBAT every 10 s; wake when > 13.0 V
   └─────────────│HIBERNATE │ (motion wake disabled, GNSS backup off)
                 └──────────┘
```

| State | Active blocks | Supply current @ 12.6 V (B variant) |
|-------|---------------|-----------------------------------|
| DRIVE | All (Wi-Fi off) | 40–55 mA (≈ 0.6 W) |
| STANDBY | CPU 80 MHz, BLE advertising 300 ms, cabin sensors 0.2 Hz, GNSS hot | 12–18 mA |
| SLEEP | ULP + RTC + wake accelerometer + GNSS backup | 80–150 µA |
| HIBERNATE | ULP timer only | 40–60 µA |

## 4. Data flow and timing budget

| Stream | Producer → consumer | Rate | Latency budget | Notes |
|--------|---------------------|------|----------------|-------|
| K-line request/response | `obd` task → ECU | 1 PID per ~70–110 ms (P2 + P3 + bytes) ≈ 10 req/s | — | Scheduler: RPM, speed 5 Hz; MAP, TPS 2 Hz; ECT, IAT, trims 0.5 Hz; DTC/MIL 1/30 s |
| VBAT | ADC → `vbat` | 100 Hz DC, 1 kS/s on crank, 40 kS/s ripple bursts every 1 s | 10 ms | Crank capture triggered by dV/dt < −2 V/50 ms |
| GNSS | MAX-M10S → `gnss` | 5 Hz DRIVE, 1 Hz STANDBY | 200 ms | UBX-NAV-PVT only (100 bytes) |
| IMU | ASM330 FIFO → `imu` | 104 Hz, FIFO batch 26 samples | 250 ms | Crash ring buffer 5 s |
| Fusion/derived | `fusion`, `fuel`, `trip` | 10 Hz | 100 ms | — |
| Live stream to phone | `link` → BLE notify | 10 Hz, 64-byte `LIVE` frame | ≤ 150 ms sensor-to-screen | 2M PHY, 15–30 ms connection interval while app is foreground |
| Log record | `logger` → NOR flash | 1 Hz (128 B) + events | flush every 10 s or on engine-off | Wear: 32 MB / 128 B/s ≈ 73 h per full cycle → 100k cycles ≫ 10-year life |
| Log sync | flash → phone | burst, ~40 KB/s effective | — | Resumable by sequence number |

## 5. Software partitioning

| Layer | Module | Runs on |
|-------|--------|---------|
| Vehicle access | `kline`, `twai_obd`, `obd`, `vguard`, `probe`, `vehicle_profile` | Core 1 |
| Measurement | `vbat`, `ripple`, `gnss`, `imu`, `cabin`, `aux` | Core 1 |
| Intelligence (embedded) | `fusion`, `fuel`, `trip`, `events`, `health` | Core 1 |
| Storage | `logger` (LittleFS + raw ring), `config` (NVS, encrypted) | Core 1 |
| Communication | `link` (BLE GATT), `proto`, `auth`, `ota`, `lte` (variant C) | Core 0 |
| Platform | `power`, `board`, `wdt`, `led`, `button` | both |
| Phone | Transport, codec, repositories, analytics, UI | Phone |

**Design rule:** the module is autonomous. Everything required for trip history and diagnostics is computed and
stored on the module, so data is complete even if the phone is absent for weeks. The phone adds heavy analytics,
UI, maps and long-term storage.

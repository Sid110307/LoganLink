# 01. Overview

## Data sources

| Source | Provides |
|--------|----------|
| Engine ECU over K-line | RPM, speed, coolant, intake air, MAP, throttle, load, advance, fuel trims, MIL, fault codes, VIN |
| Dongle sensors | Battery voltage, cranking profile, charging state, alternator-ripple RPM, acceleration (harsh braking and acceleration) |
| Phone | GPS (parking, routes, speed fallback), time, UI, storage, notifications |

The dongle logs on its own; the phone syncs the log on every connect, so trips are complete even without the phone.

## Features

Live data up to 10 Hz with a full-screen Drive Mode; automatic trips (end after 8 min engine-off) with distance, time, fuel, cost
and score; parking spot from phone GPS; speed-density fuel model with fill-ups logged from payment notifications; fault codes with
plain-English meanings and an owner-only clear; battery health from cranking voltage; service schedule by km and time; alerts;
owner plus up to 3 drivers; Google Maps next turn in Drive Mode; CSV export. Details in [08 App](08-app.md).

## Block diagram

```
 OBD-II socket
  pin 16 +12 V ──► fuse ─► TVS ─► reverse diode ─► pi filter ─► buck ─► 3.3 V
  pin 4/5 GND
  pin 7 K-line ◄─► discrete K-line interface (2N7002K driver, LMV331 receiver)
  pin 6/14 CAN ─► footprint only (not fitted)
                                │
                     ┌──────────▼───────────────────────────┐
                     │ ESP32-S3-MINI-1 (BLE 5, ULP-RISC-V)  │
                     │  K-line UART, VBAT ADC + ripple ADC  │
                     │  LIS2DW12 accelerometer (I2C)        │
                     │  4 MB log in internal flash          │
                     └──────────┬───────────────────────────┘
                                │ BLE 5, LESC + app-layer HMAC
                     ┌──────────▼───────────────────────────┐
                     │ Android app                          │
                     │  link, sync, SQLite, trips, alerts   │
                     │  phone GPS, notifications, Maps      │
                     └──────────────────────────────────────┘
```

Power states (DRIVE about 20 mA, STANDBY about 8 mA, SLEEP 61 µA design, HIBERNATE about 60 µA) are in [06 Firmware](06-firmware.md).

## Design limits

| Item | Requirement |
|------|-------------|
| Supply | 6 to 16 V operating; cranking down to 4.5 V (reset allowed below 6 V, no data loss); 24 V jump start for 60 s; reverse polarity -14 V |
| Transients | ISO 7637-2 pulses 1, 2a, 2b, 3a, 3b; ISO 16750-2 suppressed load dump (Test B, 35 V) without TVS conduction. An unsuppressed load dump may destroy the dongle's TVS and fuse; the car is unaffected |
| Temperature | -30 to +85 °C operating; shell in ASA |
| Vibration, shock | ISO 16750-3 passenger body profile; 500 m/s² 6 ms shock |
| ESD | ISO 10605, ±8 kV contact, ±15 kV air |
| EMC | CISPR 25 class 3; no audible interference on FM radio |
| Derating | Ceramic capacitors ≤ 50 % of rated voltage, X7R or better; resistors ≤ 50 % power; MOSFET VDS ≤ 75 %; Tj ≤ 125 °C |

## What it never does

| Function | Reason |
|----------|--------|
| Write to the ECU (coding, adaptation, actuator tests, reflash) | Can stall the engine in traffic or brick the ECU. The firmware guard blocks every non-read service except the guarded fault-code clear |
| Immobiliser or key programming | Theft risk. SecurityAccess (0x27) is blocked permanently |
| Remote start, lock or unlock | Defeats the immobiliser or adds attack surface |
| Any safety function | Cannot influence engine, brakes, steering or airbags |

Bus writes are on the roadmap only as signed, single-use service scripts ([11 Roadmap](11-roadmap.md)).

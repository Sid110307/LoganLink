# 01. Overview

## What it does

LoganLink plugs into the OBD-II socket and gives an older petrol car a live dashboard, trip log, fault-code reader and battery
monitor on an Android phone. Data comes from three places:

| Source | Provides |
|--------|----------|
| Engine ECU over K-line | RPM, speed, coolant, intake air, MAP, throttle, load, advance, fuel trims, MIL, fault codes, VIN |
| Dongle's own sensors | Battery voltage, cranking profile, charging state, alternator-ripple RPM, acceleration (harsh braking and acceleration) |
| Phone | GPS (parking, routes, speed fallback), time, UI, storage, notifications |

The dongle logs on its own, so trips are complete even when the phone is absent; the phone syncs the log on every connect.

## Features

| Feature | Notes |
|---------|-------|
| Live data | Gauges and tiles at up to 10 Hz; Drive Mode full-screen view |
| Trips | Automatic. A trip ends only after 8 min engine-off. Distance, time, fuel, cost, score |
| Parking | Saved 45 s after engine-off from phone GPS |
| Fuel | Speed-density fuel model; fill-ups logged automatically from payment notifications |
| Fault codes | Read stored codes with plain-English meanings; owner-only two-step clear, engine off |
| Battery | Resting voltage, cranking minimum, charging state, health from cranking voltage |
| Maintenance | Service schedule by km and time |
| Alerts | Overheat, charging fault, over-voltage, new fault code |
| Drivers | Owner plus up to 3 invited drivers with restricted rights |
| Navigation | Google Maps next turn in Drive Mode; floating car panel over Maps |
| Export | Trips as CSV |

## System block diagram

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

Hardware details: [03 Hardware](03-hardware.md). Enclosure: [05 Enclosure](05-enclosure.md).

## Operating states

| State | Entered when | Active | Current at 12.6 V |
|-------|--------------|--------|-------------------|
| DRIVE | Engine running: RPM > 400, or VBAT > 13.2 V for 3 s | ECU polling, logging 1 Hz, live stream 10 Hz | about 20 mA |
| STANDBY | Trip ended (8 min engine-off), or after wake | BLE advertising, low-rate sensing | about 8 mA |
| SLEEP | No BLE link, no trip, 5 min idle | ULP checks VBAT every 1 s, accelerometer wake armed, 6 h timer | 65 µA design, 150 µA max |
| HIBERNATE | Resting VBAT < 12.1 V for 10 min, or parked > 14 days | ULP checks VBAT every 10 s, wakes only above 13.0 V | about 60 µA |

Sleep wakes on a VBAT rise above 13.2 V, a VBAT step of 150 mV (door, lights), accelerometer motion, or the 6 h timer. At 65 µA the
dongle takes about 47 mAh a month from the battery.

## Design limits

| Item | Requirement |
|------|-------------|
| Supply | 6 to 16 V operating; cranking down to 4.5 V (reset allowed below 6 V, no data loss); 24 V jump start for 60 s; reverse polarity -14 V |
| Transients | ISO 7637-2 pulses 1, 2a, 2b, 3a, 3b; ISO 16750-2 suppressed load dump (Test B, 35 V): ride through without TVS conduction. An unsuppressed load dump may destroy the dongle's TVS and fuse; the car is unaffected |
| Temperature | -30 to +85 °C operating; shell in ASA, never PLA or PETG |
| Vibration, shock | ISO 16750-3 passenger body profile; 500 m/s² 6 ms shock |
| ESD | ISO 10605, ±8 kV contact, ±15 kV air |
| EMC | CISPR 25 class 3; no audible interference on FM radio |
| Derating | Ceramic capacitors ≤ 50 % of rated voltage continuous, X7R or better; resistors ≤ 50 % power; MOSFET VDS ≤ 75 %; Tj ≤ 125 °C |

## What it never does

| Function | Reason |
|----------|--------|
| Write to the ECU (coding, adaptation writes, actuator tests, reflash) | A bad write can stall the engine in traffic or brick the ECU. The firmware guard blocks every non-read service. The single exception is the guarded fault-code clear |
| Touch the immobiliser or key programming | Theft risk. SecurityAccess (0x27) is blocked permanently |
| Remote start, remote lock or unlock | Defeats the immobiliser or adds attack surface to a car with no security architecture |
| Any safety function | The dongle performs no safety-related function and cannot influence engine, brakes, steering or airbags |

Bus writes are on the roadmap only as signed, single-use service scripts with enforced preconditions ([11 Roadmap](11-roadmap.md)).

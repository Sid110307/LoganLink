# LoganLink — Connected-Vehicle System for older OBD-II petrol cars

LoganLink is a small dongle that plugs straight into the car's OBD socket, plus a native Android app. It turns an
older K-line OBD-II petrol car (developed on a Renault X90-platform sedan with the K7M 1.6) into a connected, self-diagnosing car. The dongle reads the engine ECU over
the K-line. It also measures the 12 V system itself (battery health, cranking, charging, and RPM from alternator
ripple) and records driving events with an accelerometer. It logs everything locally and streams it to the phone
over Bluetooth LE. The phone adds location (parking spot, routes), the UI, analytics and long-term storage.

**Current hardware baseline: rev B, the OBD plug-in dongle ([doc 15](docs/15-revB-obd-dongle.md)).** Rev A, an
under-dash module with GNSS, cabin sensors and LTE option, is kept as reference in docs 04/05/09/13 and parts of doc 11.
Where doc 15 differs, doc 15 wins.

| # | Document | Contents |
|---|----------|----------|
| 01 | [docs/01-product-and-features.md](docs/01-product-and-features.md) | Product definition and feature specification (rev B drops the GNSS/cabin features; see doc 15 §1) |
| 02 | [docs/02-vehicle-architecture.md](docs/02-vehicle-architecture.md) | Reference-car E/E architecture, diagnostic protocols, signals, acquisition strategy |
| 03 | [docs/03-system-architecture.md](docs/03-system-architecture.md) | System block diagram, data flows, timing, operating states |
| 04 | [docs/04-hardware-design.md](docs/04-hardware-design.md) | *Rev A* hardware design (K-line PHY and protection rationale still apply) |
| 05 | [docs/05-pcb-design.md](docs/05-pcb-design.md) | *Rev A* PCB (4-layer); LionCircuits/turnkey notes still apply |
| 06 | [docs/06-wireless-and-protocol.md](docs/06-wireless-and-protocol.md) | BLE GATT, frame format, LiveV1, commands, security, OTA (**unchanged in rev B**) |
| 07 | [docs/07-firmware.md](docs/07-firmware.md) | Firmware architecture, tasks, state machines, timing, update and recovery (rev B deltas in doc 15 §3) |
| 08 | [docs/08-mobile-app.md](docs/08-mobile-app.md) | App product design (implementation is now native Android, see below) |
| 09 | [docs/09-mechanical-enclosure.md](docs/09-mechanical-enclosure.md) | *Rev A* enclosure (superseded by doc 15 §6) |
| 10 | [docs/10-reliability-safety-security.md](docs/10-reliability-safety-security.md) | ISO 7637 / 16750 / CISPR 25, derating, safety and cybersecurity |
| 11 | [docs/11-installation.md](docs/11-installation.md) | Rev A harness install; commissioning §8 still applies (rev B install: doc 15 §7) |
| 12 | [docs/12-verification-acceptance.md](docs/12-verification-acceptance.md) | Test plan and acceptance criteria (rev B deltas: doc 15 §9) |
| 13 | [docs/13-bom-procurement.md](docs/13-bom-procurement.md) | *Rev A* costed BOM (rev B costs: doc 15 §8) |
| 14 | [docs/14-implementation-plan.md](docs/14-implementation-plan.md) | Build sequence, programming, key management, maintenance, references |
| **15** | [docs/15-revB-obd-dongle.md](docs/15-revB-obd-dongle.md) | **Rev B baseline**: dongle architecture, protection calcs, sleep budget, PCB, shell, socket clearance, cost |
| **16** | [docs/16-roadmap.md](docs/16-roadmap.md) | **Roadmap**: bus writes (service routines) with their safety architecture, CAN, add-on pods, Android Auto, ML, etc. |

| Source tree | Contents |
|-------------|----------|
| `firmware/` | ESP-IDF v5.3 project for rev B (ESP32-S3-MINI-1-N8): K-line/KWP2000, OBD, power states + ULP, battery/ripple analytics, fuel model, internal-flash logger, LIS2DW12, BLE link, vehicle-write guard. `firmware/optional/` holds the rev A GNSS driver (not built) |
| `android/` | **Native Android app (Kotlin, Jetpack Compose)**, replacing the earlier Flutter `app/` prototype |
| `hardware/` | `bom-revB.csv`, `pinmap-revB.csv` (baseline); `*-revA.csv` (reference) |
| `mechanical/` | `dongle.scad`: rev B thin-wall snap-fit shell (baseline); `enclosure.scad`: rev A box (reference) |

---

## Key engineering decisions

1. **The car is read-only.** The firmware sends only an allow-list of diagnostic *read* services. The one exception is a guarded
   DTC clear (owner, confirmation, engine stopped). Writing to the bus (actuator tests, relearns) is on the roadmap. It would come
   as signed, single-use service scripts with enforced preconditions, never as a looser guard (doc 16 §1).
2. **Plug-in, no wiring.** The dongle's PCB carries a J1962 male plug and uses the OBD socket as its mechanical anchor and its only
   electrical connection. **Verify where the socket is on your car and that there's ≥ 75 mm clearance in front of it**. You think
   it's in the glovebox; that isn't confirmed. If space is tight, use a right-angle extension (doc 15 §6.3).
3. **Pre-certified radio, cheap board.** An ESP32-S3-MINI-1 module carries all the RF. The rest is a 2-layer 38 × 46 mm board.
   About ₹3,500 for one turnkey unit, about ₹1,650 each at 25 units.
4. **Data comes three ways** (doc 02 §5): the ECU over K-line (generic J1979 PIDs and/or Renault local IDs), the dongle's own
   measurements (VBAT, cranking, ripple-RPM, acceleration), and the phone (GPS, time).
5. **Parked current about 65 µA by design** (acceptance ≤ 150 µA), about 47 mAh a month. Low-battery hibernate at 12.1 V resting.
6. **Built at LionCircuits** turnkey from Mouser/DigiKey/element14, including the OBD plug (Molex 68503-1602 housing +
   57964-9702 pins, doc 15 §5).

## Status

Docs are complete. The firmware (rev B port) is written against ESP-IDF v5.3 but **not yet compiled**, so expect a round of compile
fixes at bring-up. The KiCad schematic and layout are specified (docs 04/05/15) but not drawn. The Android app status is in `android/`.

## Items to verify on your car

| ID | Item | How it is resolved |
|----|------|--------------------|
| V1 | Diagnostic protocol and init (fast vs. 5-baud, ECU address) | Firmware probe |
| V2 | Supported J1979 PIDs (BS-III-era calibrations may support few) | Probe reads support bitmaps |
| V3 | Renault local-ID blocks and scaling | Probe dumps raw blocks; app Signal Mapper |
| V4 | **OBD socket location (glovebox?), front clearance ≥ 75 mm, pin 16 permanent +12 V** | Look, measure, multimeter with the key out |
| V5 | ECU part number/supplier | Label on the ECU |
| V6 | Molex 68503 housing flange dimensions (for `dongle.scad`) | Calipers on the delivered part |

## Quick start
- **Firmware:** `cd firmware && idf.py set-target esp32s3 && idf.py build flash monitor`
- **App:** open `android/` in Android Studio, or `cd android && ./gradlew assembleDebug`
- **Shell:** `openscad -D 'part="bottom"' -o bottom.stl mechanical/dongle.scad` (also `top`, `pipe`)
- **Build order:** [doc 14](docs/14-implementation-plan.md), starting with Phase 0: a vehicle protocol probe with a dev board.

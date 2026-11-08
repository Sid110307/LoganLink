# LoganLink

An OBD-II dongle plus a native Android app for older OBD-II petrol cars. The dongle reads the engine ECU over the K-line
(ISO 9141-2 / ISO 14230 KWP2000), measures the 12 V system (battery, cranking, charging), records driving events with an
accelerometer, logs locally and streams to the phone over Bluetooth LE. The phone supplies GPS, UI, trip history and alerts.
The car is read-only except for a guarded two-step fault-code clear.

Developed on a 2008 Renault X90-platform sedan with the K7M 1.6 ("the reference car" in these docs).

Hardware: ESP32-S3-MINI-1 on a 51 x 40 mm 2-layer PCB, flat behind the OBD plug, in a printed front shell, retainer and
snap-on rear shell, 27.6 mm out from the socket.

## Repository

| Path | Contents |
|------|----------|
| `firmware/` | ESP-IDF v5.3 C project for the dongle. Not compiled yet |
| `android/` | Android app, Kotlin and Jetpack Compose |
| `hardware/` | `bom.csv`, `pinmap.csv`, `pcb/` (KiCad) |
| `mechanical/` | `dongle_freecad.py`, which generates `LoganLink-dongle.FCStd`, `dongle.step` and the print STLs |
| `docs/` | Documentation |

## Documentation

| # | Document | Contents |
|---|----------|----------|
| 01 | [Overview](docs/01-overview.md) | Features, block diagram, design limits, what it never does |
| 02 | [Car interface](docs/02-car-interface.md) | OBD pins, K-line electrical and timing, probe, PIDs, vehicle guard |
| 03 | [Hardware](docs/03-hardware.md) | Schematic blocks, part values, sleep budget |
| 04 | [KiCad](docs/04-kicad.md) | Netlist, board setup, placement, outputs, bring-up |
| 05 | [Enclosure](docs/05-enclosure.md) | FreeCAD shell, printing, assembly, fit |
| 06 | [Firmware](docs/06-firmware.md) | Modules, tasks, power states, logger, fuel model, OTA, recovery, build |
| 07 | [Protocol](docs/07-protocol.md) | BLE GATT, frames, LiveV1, commands, log sync, security |
| 08 | [App](docs/08-app.md) | Screens, automatic features, alerts, permissions, build, pairing |
| 09 | [Install and test](docs/09-install-and-test.md) | Car install, calibration, acceptance tests |
| 10 | [BOM and ordering](docs/10-bom-and-ordering.md) | Cost, PCB and assembly order |
| 11 | [Roadmap](docs/11-roadmap.md) | Bus writes and their safety rules, later phases |

## Build order

1. Prototype in the car: ESP32-S3 DevKitC-1 plus the discrete K-line interface on perfboard, fed from a fused OBD pigtail. Run the
   probe ([02](docs/02-car-interface.md)) to learn your car's protocol, PIDs and VIN.
2. Draw the schematic and PCB ([03](docs/03-hardware.md), [04](docs/04-kicad.md)).
3. Order PCB and parts ([10](docs/10-bom-and-ordering.md)), then assemble and inspect.
4. Flash the firmware ([06](docs/06-firmware.md)) and install the app ([08](docs/08-app.md)).
5. Print the enclosure ([05](docs/05-enclosure.md)).
6. Install, calibrate and run the acceptance tests ([09](docs/09-install-and-test.md)).

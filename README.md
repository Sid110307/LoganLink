# LoganLink

Connected-Vehicle System for OBD-II supported cars.

LoganLink is a small dongle that plugs into the car's OBD-II socket, plus a native Android app. The dongle reads the engine
computer over the K-line (ISO 9141-2 / ISO 14230 KWP2000), measures the 12 V system itself (battery, cranking, charging), records
driving events with an accelerometer, logs locally and streams to the phone over Bluetooth LE. The phone supplies GPS, the UI,
trip history and alerts. The car is read-only, except for a guarded two-step fault-code clear. It targets older OBD-II petrol cars;
it was developed on a 2008 Renault X90-platform sedan with the K7M 1.6, called "the reference car" in these docs.

Hardware: ESP32-S3-MINI-1 module on a 54 x 42 mm 2-layer PCB lying flat directly behind the OBD plug, in a printed
front shell, retainer and snap-on rear shell, 27.6 mm out from the socket.

## Repository layout

| Path | Contents |
|------|----------|
| `firmware/` | ESP-IDF v5.3 C project for the dongle (K-line, OBD, vehicle guard, power states and ULP, battery analytics, fuel model, logger, BLE link, OTA). Not compiled yet |
| `android/` | Native Android app, Kotlin and Jetpack Compose |
| `hardware/` | `bom.csv`, `pinmap.csv`, `pcb/` (KiCad) |
| `mechanical/` | `dongle_freecad.py` generates `LoganLink-dongle.FCStd`, `dongle.step` and the STL files for printing |
| `docs/` | Design and build documentation (below) |

## Documentation

| # | Document | Contents |
|---|----------|----------|
| 01 | [Overview](docs/01-overview.md) | Features, block diagram, operating states, what it never does |
| 02 | [Car interface](docs/02-car-interface.md) | OBD pins, K-line electrical and timing, probe, PIDs, vehicle guard |
| 03 | [Hardware](docs/03-hardware.md) | Schematic design, protection, power, sleep budget |
| 04 | [KiCad (PCB from zero)](docs/04-kicad.md) | Drawing the schematic and layout in KiCad |
| 05 | [Enclosure](docs/05-enclosure.md) | FreeCAD shell, printing, fit |
| 06 | [Firmware](docs/06-firmware.md) | Tasks, boot, power states, logger, fuel model, OTA, secure boot, build |
| 07 | [Protocol](docs/07-protocol.md) | BLE GATT, frames, LiveV1, commands, log sync, security |
| 08 | [App](docs/08-app.md) | Screens, automatic features, permissions, architecture, build |
| 09 | [Install and test](docs/09-install-and-test.md) | Installing in the car, calibration, acceptance tests |
| 10 | [BOM and ordering](docs/10-bom-and-ordering.md) | Parts, PCB fab and assembly order |
| 11 | [Roadmap](docs/11-roadmap.md) | Bus writes and their safety architecture, later phases |

## Build order

1. Prototype on a dev board in the car: ESP32-S3 DevKitC-1 plus the discrete K-line interface on perfboard, fed from a fused OBD
   pigtail. Flash the firmware and run the probe ([02](docs/02-car-interface.md)) to learn the protocol, PIDs and VIN of your car.
2. Draw the schematic and PCB in KiCad ([03](docs/03-hardware.md), [04](docs/04-kicad.md)).
3. Order the PCB and parts ([10](docs/10-bom-and-ordering.md)).
4. Assemble and inspect the boards.
5. Flash and bring up the firmware ([06](docs/06-firmware.md)), install the app ([08](docs/08-app.md)).
6. Print the enclosure ([05](docs/05-enclosure.md)).
7. Install in the car, calibrate and run the acceptance tests ([09](docs/09-install-and-test.md)).

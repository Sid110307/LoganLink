# LoganLink

An OBD-II dongle and an Android app for older K-line petrol cars. The dongle reads the engine ECU over the K-line (ISO 9141-2 /
ISO 14230 KWP2000), measures the 12 V system, detects harsh driving with an accelerometer, keeps its own log and streams to the
phone over Bluetooth LE. The phone adds GPS, the UI, trip history and alerts. The car is read-only, except for a guarded two-step
fault-code clear.

Developed on a 2008 Renault X90-platform sedan with the K7M 1.6 engine ("the reference car").

Part references below match `hardware/pcb/pcb.kicad_sch` and `hardware/bom.csv`. Board coordinates are in mm from the board
centre, +y towards the antenna (KiCad shows that as negative y).

1. [Overview](#1-overview)
2. [Car interface](#2-car-interface)
3. [Circuit](#3-circuit)
4. [PCB](#4-pcb)
5. [Enclosure](#5-enclosure)
6. [Firmware](#6-firmware)
7. [Protocol](#7-protocol)
8. [App](#8-app)
9. [Install and test](#9-install-and-test)
10. [BOM and ordering](#10-bom-and-ordering)
11. [Roadmap](#11-roadmap)

---

## 1. Overview

### Data sources

| Source | Provides |
|--------|----------|
| Engine ECU over K-line | RPM, speed, coolant, intake air, MAP, throttle, load, advance, fuel trims, MIL, fault codes, VIN |
| Dongle sensors | Battery voltage, cranking profile, charging state, alternator-ripple RPM, harsh braking and acceleration |
| Phone | GPS (parking, routes, speed fallback), time, UI, storage, notifications |

The dongle logs on its own and the phone syncs the log on every connect, so trips are complete even when the phone was away.

### Features

Live data up to 10 Hz with a full-screen Drive Mode; automatic trips (end 8 min after engine-off) with distance, time, fuel, cost
and score; parking spot from phone GPS; speed-density fuel model, corrected by fill-ups read from payment notifications; fault codes
in plain English with an owner-only clear; battery health from cranking voltage; service schedule by km and time; alerts; owner plus
up to 3 drivers; Google Maps next turn in Drive Mode; CSV export.

### Block diagram

```
 OBD-II socket
  pin 16 +12 V ─ F1 ─ D2 TVS ─ D3 reverse diode ─ FB1/C1/C7/C8 filter ─ U3 buck ─ 3.3 V
  pins 4, 5 GND
  pin 7 K-line ─ discrete interface (Q1 2N7002K driver, U2 LMV331 receiver)
  pins 6, 14 CAN ─ U4 footprint, not fitted
  USB-C ─ F2 ─ D4 ─ VIN (bench power and flashing)
                    │
       ┌────────────▼───────────────────────┐
       │ U6 ESP32-S3-MINI-1 (BLE 5, ULP)    │
       │ K-line UART, battery + ripple ADC  │
       │ U5 LIS2DW12 accelerometer (I2C)    │
       │ 4 MB log in internal flash         │
       └────────────┬───────────────────────┘
                    │ BLE 5, LESC bond + HMAC per command
       ┌────────────▼───────────────────────┐
       │ Android app: sync, SQLite, trips,  │
       │ alerts, phone GPS, Maps            │
       └────────────────────────────────────┘
```

### Design limits

| Item | Requirement |
|------|-------------|
| Supply | 6 to 16 V operating; cranking to 4.5 V (reset allowed below 6 V, no data loss); 24 V jump start for 60 s; reverse -14 V |
| Transients | ISO 7637-2 pulses 1, 2a, 2b, 3a, 3b; ISO 16750-2 suppressed load dump (Test B, 35 V) without TVS conduction. An unsuppressed load dump may destroy the TVS and fuse; the car is unaffected |
| Temperature | -30 to +85 °C operating; shell in ASA |
| Vibration, shock | ISO 16750-3 passenger body profile; 500 m/s² 6 ms shock |
| ESD | ISO 10605, ±8 kV contact, ±15 kV air |
| EMC | CISPR 25 class 3; no audible FM interference |
| Derating | Ceramics ≤ 50 % rated voltage, X7R or better; resistors ≤ 50 % power; MOSFET VDS ≤ 75 %; Tj ≤ 125 °C |

### What it never does

| Function | Reason |
|----------|--------|
| Write to the ECU (coding, adaptation, actuator tests, reflash) | Can stall the engine in traffic or brick the ECU. The firmware guard blocks every non-read service except the guarded code clear |
| Immobiliser or key programming | Theft risk. SecurityAccess (0x27) is blocked permanently |
| Remote start, lock or unlock | Defeats the immobiliser or adds attack surface |
| Any safety function | Cannot influence engine, brakes, steering or airbags |

---

## 2. Car interface

### OBD pins (SAE J1962) and J1

| OBD pin | Function | J1 pad |
|---------|----------|--------|
| 16 | +12 V permanent | 1 (to F1) |
| 4 | Chassis ground | 2 |
| 5 | Signal ground | 3 |
| 7 | K-line | 4 |
| 6 | CAN-H (only if U4 is fitted) | 5 |
| 14 | CAN-L (only if U4 is fitted) | 6 |

Ground returns only through pins 4 and 5; never add a separate chassis ground.

### K-line electrical (ISO 9141-2 / ISO 14230-1)

| Parameter | Value |
|-----------|-------|
| Topology | Single wire, open drain to ground, pulled up to battery |
| Tester pull-up | R17 510 Ω + D7 to VBAT_P |
| Driver | Q1 2N7002K with R1 47 Ω in series; low level 0.16 x VBAT (limit 0.2 x VBAT) |
| Receiver | U2 LMV331, threshold 0.49 x VBAT, hysteresis about ±3 % VBAT |
| Bit rate | 10,400 bit/s, 8N1, bit time 96.15 µs |
| Echo | Every transmitted byte is read back; a mismatch aborts the request |

Discrete, not a LIN transceiver: those have a 6 to 20 ms TXD-dominant timeout that cuts the 25 ms fast-init pulse short.

### Timings

| Symbol | Meaning | Value |
|--------|---------|-------|
| W5 | Bus idle before init | ≥ 300 ms |
| TiniL | Fast-init low pulse | 25 ms ± 1 ms |
| TWuP | Fast-init wake-up pattern | 50 ms ± 1 ms |
| 5-baud init | Address byte at 5 bit/s | 2.0 s |
| W1 | ECU sync byte 0x55 after address | 60 to 300 ms |
| W4 | Tester inverts KB2 after | 25 to 50 ms |
| P1 | ECU inter-byte | 0 to 20 ms |
| P2 | ECU response start | 25 to 50 ms; 5 s after `7F xx 78` (response pending) |
| P3 | Tester new request after response | 60 ms (min 55 ms) |
| P3max | Session timeout | 5 s; TesterPresent (`3E 01`) after 2 s of silence |
| P4 | Tester inter-byte | 5 ms |

### Message format (ISO 14230-2)

```
[FMT][TGT][SRC][LEN?][SID][DATA...][CS]
FMT  0x80 | len physical addressing, 0xC0 | len functional
TGT  0x33 functional OBD, or the ECU address found by the probe
SRC  0xF1 tester
CS   8-bit sum of all preceding bytes
```

Fast-init StartCommunication: `C1 33 F1 81 66`, reply `83 F1 xx C1 KB1 KB2 CS`. RPM request: `C2 33 F1 01 0C F3`.

### Protocol probe

Runs on the first engine start and on demand (app: Garage, LoganLink module, "Detect again"), 3 s between attempts:

1. ISO 14230 fast init, functional address 0x33.
2. ISO 14230 5-baud init, address 0x33.
3. ISO 9141-2 5-baud init, address 0x33 (key bytes 08 08 or 94 94).
4. ISO 14230 fast init to each candidate physical ECU address, confirmed with `1A 80`.

On success it reads the PID support bitmaps (01 00, 20, 40, 60), VIN (mode 09 PID 02 if supported) and ECU ID (`1A 80`), and dumps
local-ID blocks `21 01` to `21 0F` raw into the log. The profile is kept in NVS. CAN is not probed.

### J1979 PIDs

The scheduler requests the most overdue supported PID. One request takes 70 to 110 ms, so RPM and speed update at 4 to 5 Hz.

| PID | Signal | Decoding | Period |
|-----|--------|----------|--------|
| 0C | RPM | ((A x 256) + B) / 4 | 200 ms |
| 0D | Speed | A km/h | 200 ms |
| 0B | MAP | A kPa | 500 ms |
| 11 | Throttle | A x 100 / 255 % | 500 ms |
| 04 | Load | A x 100 / 255 % | 1 s |
| 0E | Advance | A / 2 - 64 ° BTDC | 1 s |
| 05 | Coolant | A - 40 °C | 2 s |
| 0F | Intake air | A - 40 °C | 2 s |
| 06 | Short-term trim | (A - 128) x 100 / 128 % | 2 s |
| 07 | Long-term trim | (A - 128) x 100 / 128 % | 5 s |
| 01 | MIL and DTC count | bit 7 of A = MIL, bits 0 to 6 = count | 30 s |

Fault codes: mode 03 (stored), 07 (pending) or KWP 0x18. Clear: mode 04 or KWP 0x14, guarded.

### Local IDs (`lidmap`)

Many K-line ECUs of this era answer few J1979 PIDs but expose data blocks through `21 xx`. The config holds a `lidmap`: local ID,
byte offset, size, signed flag, scale, offset, target field. Mapped blocks are polled at 4 Hz and fill the same live fields. To
build one, record probe dumps during known conditions (idle then 2,000 rpm, cold then warm), find the byte that tracks each signal
and scale it against the cluster or a scan tool. No community maps are shipped.

### Vehicle guard

`vguard_allow()` sits between all firmware and the K-line transmitter. Default deny; no command sends arbitrary bytes.

| Service | Status |
|---------|--------|
| J1979 modes 01, 02, 03, 07, 09 | Allowed |
| KWP 0x1A, 0x21, 0x17, 0x18, 0x3E, 0x81, 0x82 | Allowed |
| KWP 0x10 | Default session (0x81) only |
| Mode 04 / KWP 0x14 | Only when armed by the owner two-step command, valid 5 s, engine off (RPM 0, VBAT < 13.0 V) |
| Everything else (0x27, 0x2E, 0x3B, 0x2F, 0x30, 0x31, 0x34 to 0x37, 0x11, mode 08, ...) | Denied, counted, reported in GET_INFO |

### Errors and other testers

- Three consecutive timeouts, checksum or echo errors: ECU offline, back off 2 s; after 3 failed connects back off 30 s.
- A negative response other than 78 marks that PID unsupported for the session.
- K-line bytes the dongle did not cause mean another tester is connected: the dongle stays silent for 10 min.

### Check on each car

| Item | How |
|------|-----|
| Protocol, ECU address, PIDs, VIN | Probe result in the app. Older calibrations support few PIDs and no VIN |
| Local-ID layout | Raw probe dumps, then build the `lidmap` |
| Pin 16 is permanent +12 V | Multimeter, key out ([9](#9-install-and-test)) |
| Socket clearance | Against the enclosure size ([5](#5-enclosure)) |

---

## 3. Circuit

One A3 schematic sheet: `hardware/pcb/pcb.kicad_sch`. GPIO map: [`hardware/pinmap.csv`](../hardware/pinmap.csv).

### Power

| Part | Value | Note |
|------|-------|------|
| F1 | 2 A fast, 1206 | Board draws under 0.2 A; protects the car wiring from a board fault |
| D2 | SMCJ33CA, bidirectional | 33 V standoff, 36.7 V min breakdown: never conducts on the 35 V suppressed load dump; clamps ISO 7637 pulses both ways. Bidirectional, so a reversed battery does not blow F1 |
| D3 | SS210, 100 V 2 A Schottky | Reverse battery; output is VBAT_P |
| C1 | 2.2 µF 100 V X7R | On VBAT_P |
| FB1 | 600 Ω ferrite | VBAT_P to VIN |
| C7, C8 | 2.2 µF 100 V X7R, 22 µF 63 V electrolytic (6.3 x 7.7 mm) | On VIN. C8 damps the input LC on hot-plug |
| U3 | LMR36006, 4.2 to 60 V in, 0.6 A, 400 kHz | 24 µA no-load, most of the sleep current. C6 1 µF on VCC, C9 100 nF boot |
| R20 / R19 | 100k / 43.2k | Vout = 1.0 x (1 + 100 / 43.2) = 3.31 V |
| L1 | 22 µH SRN4018, Isat 0.95 A | |
| C10, C11 | 22 µF 16 V X5R 1206 | Output |
| C13, C14, C15, C16 | 100 nF, 10 µF, 100 nF, 10 µF | 3V3 decoupling at U6 and U5 |
| F2, D4 | 0.75 A PTC, B5819WS | USB VBUS into VIN. D3 and D4 OR the sources, so USB in the car is safe |

### K-line

| Part | Value | Check |
|------|-------|-------|
| R17 + D7 | 510 Ω 0.5 W to VBAT_P | Worst dissipation 14.4² / 510 = 0.41 W |
| Q1 + R1 | 2N7002K, 47 Ω series | Pull-ups total about 255 Ω. Low level 14.4 x 47 / 302 = 2.2 V = 0.16 VB, under 0.2 VB |
| R3, R2 | 330 Ω gate, 100k pull-down | Gate held off when the MCU is off or crashed |
| R7 / R8 | 1M / 150k from K-line | K seen at 0.130 VB on U2+ |
| R18 / R9 | 1M / 68k from VBAT_P | Reference 0.064 VB on U2- (K_REF), switching point 0.49 VB |
| R12 | 4.7M, U2 output to U2+ | About 3 % VB hysteresis |
| U2 | LMV331 on AUX_PWR, C4 100 nF | Open drain into GPIO18 (K_RX), R13 10k pull-up to AUX_PWR. Unpowered in deep sleep |
| D1 | PESD1LIN | ESD and burst |
| TX | UART1 TX, inverted in firmware | Q1 on = line low |

The reference is ratiometric, so the threshold tracks battery voltage from 9 to 16 V without calibration.

### Sensing

| Signal | Path | Range |
|--------|------|-------|
| Battery voltage | R15 1M / R16 150k, C5 10 nF, R14 4.7k, D6 clamp, GPIO1 | 14.4 V reads 1.88 V; full scale about 23 V |
| Alternator ripple | C2 100 nF 100 V, R10 4.7k, bias R6 / R11 100k from AUX_PWR, C3 1 nF, D5 clamp, GPIO2 | AC only, biased at 1.65 V while awake |
| Motion | U5 LIS2DW12 on I2C (0x19), R21 / R22 4.7k pull-ups, INT1 on GPIO7 | Wakes from deep sleep on movement |

### ESP32-S3-MINI-1 (U6)

- EN: R24 10k up, C12 1 µF down, SW1 (reset) to GND. SW1 is not fitted; a power cycle does the same.
- GPIO0: R23 10k up, SW2 (PAIR) to GND. Held at power-up = download mode.
- USB: GPIO19/20 to J2 through U1 (USBLC6-2SC6); CC1, CC2 each 5.1k to GND (R4, R5).
- No 32 kHz crystal; the RTC runs on the internal oscillator and is re-synced from the phone on every connect.
- LEDs: LED1 red on GPIO38 (R25 330 Ω, about 4 mA), LED2 green on GPIO39 (R26 330 Ω, about 4 mA), PWM-dimmed.
- Console UART0 (GPIO43/44) on J3 pads: 1 3V3, 2 GND, 3 TXD0, 4 RXD0.
- GPIO21 is AUX_PWR: feeds U2, the ripple bias and the K_RX pull-up (under 1 mA), held low in deep sleep.
- GPIO6 `IGN_SENSE_N` is reserved for an optional ignition tap, not fitted.
- Test pads: TP1 K_TX, TP2 K_RX, TP3 K_REF, TP4 AUX_PWR.

### CAN (not fitted)

U4 SN65HVD230 on GPIO4/5, Rs to GND, CAN-H and CAN-L to J1 pads 5 and 6. Fit it and the two crimp pins only for a CAN car.

### Sleep budget (engine off, 12.6 V)

| Load | Current |
|------|---------|
| LMR36006 no-load | 24 µA |
| Battery divider R15/R16 | 11 µA |
| Reference divider R18/R9 | 11 µA |
| K-line divider R7/R8 (K idles at battery) | 10 µA |
| ESP32-S3 deep sleep + ULP, reflected to 12 V | about 4 µA |
| LIS2DW12 low power | about 1 µA |
| Total | about 61 µA (max 150 µA) |

1.5 mAh a day, about 0.5 Ah a year. Below 12.1 V resting the firmware hibernates.

---

## 4. PCB

KiCad 10, `hardware/pcb/`. Two layers, 51 x 40 mm, R3 corners, 1.6 mm FR4, 1 oz, all parts on top.

The project library `loganlink` (`hardware/pcb/lib/`) holds the LMR36006 symbol and `TI_RNX0012A` footprint (from TI Ultra
Librarian, checked against the datasheet: 11 pads 0.25 x 0.6 mm, 0.5 mm pitch, SW pad 1.825 x 0.25 with split paste). U6 uses
the stock `ESP32-S2-MINI-1` footprint, which has the same land pattern as the S3-MINI-1.

### Rules

| Item | Value |
|------|-------|
| Track / clearance | 0.2 / 0.2 mm everywhere; 0.15 mm only at the U3, U5, U6 and J2 pads (custom rule in `pcb.kicad_dru`) |
| Via | 0.3 drill / 0.6 pad |
| Copper to edge | 0.3 mm |

| Net class | Track | Nets |
|-----------|-------|------|
| Default | 0.2 | Signals |
| Power | 0.4 | Battery path, VIN, SW, 3V3, VBUS |
| KLine | 0.3 | K-line, pull-up, driver drain |
| USB | 0.25, pair gap 0.25 | USB D+ / D- |

### Layout as built

| Item | Position | Note |
|------|----------|------|
| U6 ESP32 module | centred on x, antenna along the top edge | Must be centred: its antenna keepout is 45.4 mm wide |
| H1 to H4 | (±22.5, 12.5), (±22.5, -17) | M2, 2.2 mm holes |
| J1 wire pads | pad 1 at (-6.5, -5), 2.54 pitch to (6.2, -5) | Wires solder from the bottom |
| Input protection | left of J1 (F1, D2, D3, C1, FB1) | |
| Buck | upper left (U3, L1, C7 to C11) | SW node and L1 away from the antenna |
| K-line | below the module (Q1, U2, dividers, D1, D7) | |
| USB | bottom right, J2 mouth on the bottom edge | Bench only |
| LED1, LED2 | (21, 7), (21, 9) | Light pipe centre (21, 8) |
| SW2 (PAIR) | (22, 2) | Pinhole above it |
| SW1 (reset) | (19, -1.5) | Not fitted (footprint only) |
| J3 UART | pad 1 at (23.5, -4.5), 2.54 pitch downwards | Pads only |

The USB pair is routed coupled (0.25 / 0.25 mm) on the top layer with no vias, length-matched to about 0.01 mm. Both layers carry a
GND pour with stitching vias. The car outline art for the silkscreen is in `hardware/pcb/art/car-outline.svg`.

Known DRC items, all expected: module GND pads 42, 43 and 64 show as unconnected (tied inside the module); two thermal-spoke
warnings (C5.2, J3.2, both tied by tracks); module silkscreen near the edge.

### Outputs

Gerbers, drill and placement are in `hardware/pcb/Gerber/` (drill/place origin at the bottom-left corner). Zip the folder for the
fab. BOM: [`hardware/bom.csv`](../hardware/bom.csv), in LionCircuits format.

### Bring-up

1. Unpowered: VBAT_P to GND and 3V3 to GND are not shorts.
2. Bench supply 12 V, 100 mA limit, into J1 pads 1 and 2: 3.3 V on J3 pad 1, under 30 mA.
3. Hold SW2 while plugging USB-C into the PC: the board enumerates for flashing.
4. Flash ([6](#6-firmware)). Check the LEDs, the battery reading against a meter and the accelerometer wake.
5. Scope the K-line with a 510 Ω pull-up to 12 V: low under 2.9 V, clean edges at 10.4 kbaud. TP1 to TP3 help here.
6. Idle sleep current under 150 µA.

---

## 5. Enclosure

Plug nose into the car, board flat behind it: 54.8 x 43.8 mm, 29.6 mm out from the socket face, so a knee or glovebox lid pushes
it straight in.

`mechanical/dongle_freecad.py` builds everything. Run it in FreeCAD (Macro > Execute, or the MCP bridge); it writes
`LoganLink-dongle.FCStd`, `dongle.step` and the STLs next to itself. Sizes are in the `P` dict at the top.

| Part | File | Print |
|------|------|-------|
| Front shell | `dongle-front.stl` | Face down, no supports |
| Retainer | `dongle-retainer.stl` | Plate down, no supports |
| Rear shell | `dongle-rear.stl` | Roof down (already flipped), no supports |
| Light pipe | `dongle-pipe.stl` | Clear PETG, upright |
| Screws | 4 x M2 x 18 pan head self-tapping for plastic | |

Shells and retainer in ASA (a parked car's glovebox passes 70 °C; never PLA). 0.2 mm layers, 4 walls, 30 % infill.

### Stack (from the car outwards)

| z (mm) | Part |
|--------|------|
| 0 to 2 | Front shell face; the plug nose passes through it |
| 2 to 5 | Plug flange, against the inside of the face |
| 5 to 7 | Retainer plate, clamping the flange |
| 5 to 13 | Plug body, through the retainer opening |
| 7 to 18 | Retainer posts |
| 13 to 18 | Wire space |
| 18 to 19.6 | PCB |
| 19.6 to 28.1 | Parts (8 mm max; C8 is 7.7) |
| 28.1 to 29.6 | Rear shell roof |

Plug-in force goes plug, retainer, posts, screws, front shell; pull-out force goes flange to front face. Neither passes through the
board or the wires. The model has been checked against the board STEP: nothing touches the shells, posts or light pipe.

### Assembly

1. Solder 25 mm lengths of 22 AWG wire into J1 pads 1 to 4 from the bottom (5 and 6 only for CAN).
2. Crimp a Molex 57964-9702 pin on each free end and insert: J1.1 to cavity 16, J1.2 to 4, J1.3 to 5, J1.4 to 7 (J1.5 to 6,
   J1.6 to 14 for CAN).
3. Push the plug nose out through the front shell face from inside, flange against the face.
4. Drop the retainer over the plug body onto the flange.
5. Lay the board on the retainer posts; drive the four screws through board and posts into the front shell bosses. Snug, not tight.
6. Press the light pipe into the rear shell from inside.
7. Snap the rear shell on (a groove on each long side of the lip catches a bump on the front shell).

Pop the rear shell off to flash over USB. Normal updates go over the air.

### Fit check

The Molex 68503-1602 housing dimensions in `P` are estimates. Print the front shell and retainer first, measure the housing and
rerun:

| Param | Default | Measure |
|-------|---------|---------|
| `plug_w`, `plug_w_bot`, `plug_h` | 39, 33, 17 | Nose trapezoid: top width, bottom width, height |
| `flange_t`, `flange_ext` | 3, 1.2 | Flange thickness and overhang past the nose |
| `plug_body` | 8 | Body depth behind the flange |
| `gap` | 0.3 | Increase if the plug is tight in the face opening |

---

## 6. Firmware

ESP-IDF v5.3, C11, FreeRTOS SMP, NimBLE, mbedTLS, `espressif/esp-dsp` (FFT). Target ESP32-S3-MINI-1-N8. Source in
`firmware/main/`. Not compiled yet; expect a round of compile fixes.

### Build and flash

```bash
cd firmware                       # ESP-IDF v5.3 installed and exported
idf.py set-target esp32s3
idf.py build
idf.py -p COMx flash monitor      # USB-C, native USB-Serial-JTAG
```

Download mode: hold SW2 (PAIR) while plugging in USB. For a dev-board prototype, remap pins in `board.h`.

### Modules

| File | Responsibility |
|------|----------------|
| `board.h` | Pin map (matches `hardware/pinmap.csv`), firmware version |
| `app_main.c` | Boot sequence, OTA confirmation |
| `vstate` | Shared live state, locked snapshot |
| `config` | NVS settings, device key, users, vehicle profile, `lidmap` |
| `kline` | Fast and 5-baud init, framing, echo check, timings, foreign-tester detection |
| `vguard` | Default-deny allow-list |
| `obd` | Probe, PID scheduler and decoding, VIN, DTC read and clear, local IDs |
| `vbat` | ADC sampling, filter, crank capture, ripple FFT and RPM |
| `fuel` | Fuel model and self-calibration |
| `trip` | Trip start and end, distance, harsh events, 1 Hz records |
| `sensors` | LIS2DW12, time keeping |
| `logger` | CRC-protected 128-byte ring records in the `log` partition |
| `proto` | Frames, CRC-16, LiveV1 packing |
| `auth` | Challenge-response, session key, command MAC, replay check |
| `ble_link` | GATT server, pairing window, commands, live stream, log sync, OTA |
| `power` | State machine, sleep, ULP, PAIR button, LED, thermal policy |
| `ulp/` | ULP-RISC-V program: parked battery supervision |

### Tasks

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

### Boot

Bootloader (OTA slot or rollback), then: log wake cause and reset reason; AUX_PWR and LEDs off; `vstate_init`, `config_init` (device
key made on the very first boot); `logger_init` (on failure, SAFE state: BLE and OTA only); start `vbat`, `sensors`, `kline`,
`obd`; BLE advertising (10 min pairing window if no bonds); `power` in STANDBY; if the image is pending verification, 60 s self-test
then confirm or roll back. Wake to advertising about 350 ms, first live frame 2 to 4 s.

No RTC crystal: `SET_TIME` re-syncs the clock on every connect; earlier records have `utc = 0` and the app back-fills them.

### Power states

| State | Entered when | Active | Current at 12.6 V |
|-------|--------------|--------|-------------------|
| DRIVE | RPM > 400 (ECU or ripple), or VBAT > 13.2 V for 3 s | Trip, fuel model, ECU polling, 1 Hz log, 10 Hz live | about 20 mA |
| STANDBY | Trip ended (8 min engine-off), or after wake | BLE advertising, low-rate sensing | about 8 mA |
| SLEEP | No link, no trip, 5 min idle (3 min after a motion or voltage-step wake, 10 s after a timer wake) | AUX_PWR off; ULP checks VBAT every 1 s; motion wake; 6 h timer | 61 µA design, 150 µA max |
| HIBERNATE | Resting VBAT < 12.1 V for 10 min, or parked > 14 days | ULP every 10 s, wakes only above 13.0 V, no motion wake | about 60 µA |

SLEEP wakes on VBAT > 13.2 V, a VBAT step of 150 mV (door, lights), motion, or the 6 h timer.

- Crank: a start that drops VBAT below brownout resets the MCU; a BROWNOUT boot followed by engine running is shown as a weak crank.
- Thermal: die temperature every 10 s; above 105 °C the sensors stop.
- SW2 (PAIR), engine running: short press opens a 120 s pairing window; 10 s hold factory-resets (bonds wiped, device key kept).
- LED: green steady in DRIVE, short green blink every 3 s in STANDBY, off in SLEEP.

### Logger

| Item | Value |
|------|-------|
| Storage | `log` partition, 4 MB internal flash |
| Record | 128 bytes: `seq, utc, uptime_ms, boot_id, type, len, data[108], crc32` |
| Capacity | 32,768 records, about 9 h of driving at 1 Hz |
| Wear | At 2 h a day the ring wraps every 4 to 5 days: about 800 erase cycles in 10 years (100,000 rated) |
| Power loss | Atomic per record (CRC); torn records skipped; head found by scan at boot |
| Flush | Every 10 s and at engine-off |

Partitions (`partitions.csv`, 8 MB): `nvs` 24 KB, `nvs_keys` 4 KB, `otadata`, `phy_init`, `ota_0` and `ota_1` 1.75 MB each,
`coredump` 64 KB, `log` 4 MB, `cfg` 320 KB.

### Fuel model

Speed-density (MAP sensor, no MAF):

```
m_air  = MAP x Vd x (RPM / 120) x VE / (R x T_IAT)     R = 287.05 J/(kg K), Vd = displacement
m_fuel = m_air / 14.7 x (1 + (STFT + LTFT) / 100)      0 during decel fuel cut (TPS < 1.5 %, RPM > 1500)
Q[L/h] = m_fuel x 3600 / 0.745                          petrol 0.745 kg/L
```

Reference car at idle (Vd = 1.598 L, MAP 32 kPa, 800 rpm, IAT 40 °C, VE 0.66, trims 0): m_air = 2.50 g/s, m_fuel = 0.170 g/s,
Q = 0.82 L/h.

Self-calibration assumes fill-to-full: each `FILL_UP` over 5 L, with over 5 L modelled since the previous one, compares pumped to
modelled litres: `VE_corr = VE_corr x (0.7 + 0.3 x pumped / modelled)`, applied only for a ratio of 0.7 to 1.3. Accuracy ±10 %
after the first fill, ±3 to 5 % after three.

### OTA

BLE only (`WIFI_OTA` answers UNSUPPORTED).

1. Owner picks a `.bin` in the app (LoganLink module, Firmware update).
2. Dongle checks: engine off (RPM 0, VBAT < 13.0 V), VBAT > 12.2 V, owner session.
3. `OTA_BEGIN` (size, SHA-256), then 233-byte chunks to the inactive slot, acked every 32 chunks. 1.6 MB takes 30 to 50 s.
4. `OTA_END`: SHA-256 verified, boot partition switched, reboot.
5. After 60 s of passing self-test it marks the image valid; otherwise the bootloader rolls back.

### Recovery

Nothing is ever locked: no secure boot, no flash encryption, no eFuses burned. Every unit can always be reflashed over USB.

| Problem | Fix |
|---------|-----|
| Bad OTA image | Bootloader rolls back to the previous slot |
| Both slots bad, boot loop | Download mode (hold SW2, plug in USB; ROM, cannot be overwritten), then `idf.py flash` |
| Corrupt config or log | `idf.py erase-flash`, then flash. A new device key is made, so pair again with the new card |

An erased or crashed board is harmless to the car: R2 holds Q1 off, so the K-line is released and nothing is sent.

Task watchdog 5 s (panic, reboot); brownout gives a clean reset; core dump to `coredump`; invalid data uses sentinel values, never
zeros, and every value carries a source bit.

---

## 7. Protocol

BLE 5 (LE 2M PHY, data length 251, MTU 247) is the only link; no Wi-Fi. Code: `firmware/main/proto.[ch]`, `ble_link.c`,
`auth.c`; app `core/protocol/`, `core/link/`.

### GATT

Service `4c4c0001-6c6f-6761-6e6c-696e6b000000`. All characteristics need an encrypted LESC bond.

| Characteristic | UUID | Properties | Carries |
|----------------|------|------------|---------|
| TX | `4c4c0002-...` | Notify | Dongle to phone: LIVE, EVENT, DTC_LIST, RSP, LOG_CHUNK, AUTH_* |
| RX | `4c4c0003-...` | Write, Write Without Response | Phone to dongle: CMD, AUTH_RESPONSE |
| OTA | `4c4c0004-...` | Write Without Response | Phone to dongle: OTA_DATA (owner only) |

Also Device Information (0x180A) and Battery Service (0x180F, the car battery).

- Advertising: name `LoganLink-XXXX` (last two MAC bytes), service UUID, manufacturer data `[ver][state][vbat/100]`. Interval 100
  to 300 ms. None in SLEEP.
- Two simultaneous connections; both get the live stream, commands are serialised.
- Connection interval 15 to 30 ms while showing live data, 100 to 200 ms with latency 4 in the background (`SET_LIVE_RATE`).

### Frame format

Little-endian, same on all characteristics.

```
Offset  Size  Field
0       1     magic    0xA5
1       1     version  0x01
2       1     type
3       1     flags    bit0 ACK_REQ, bit1 MORE_FRAGMENTS, bit2 AUTHENTICATED (payload ends with an 8-byte MAC)
4       2     seq      per direction, wraps at 65535
6       2     len      payload length N (≤ 233)
8       N     payload
8+N     2     crc16    CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) over bytes 0 to 8+N-1
```

| Type | Name | Payload |
|------|------|---------|
| 0x01 | LIVE | LiveV1, 43 bytes |
| 0x02 | EVENT | `u32 log_seq, u32 utc, u8 event_id, u8 severity, u16 len, data` |
| 0x03 | DTC_LIST | `u8 count, u8 source, count x {u16 code, u8 status}` |
| 0x10 | CMD | `u32 counter, u8 cmd, args` + 8-byte MAC |
| 0x11 | RSP | `u32 counter (echo), u8 cmd, u8 status, data` |
| 0x20 | LOG_CHUNK | `u32 first_seq, u8 n_records, records` |
| 0x30 | AUTH_CHALLENGE | `u8 device_id[6], u8 nonce_d[16], u8 fw_major, u8 fw_minor` |
| 0x31 | AUTH_RESPONSE | `u8 user_id, u8 nonce_p[16], u8 tag[16]` |
| 0x32 | AUTH_RESULT | `u8 status, u8 role` |
| 0x40 | OTA_DATA | `u32 offset, data` |

### LiveV1 (43 bytes, up to 10 Hz)

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
| 28 | i16 | accel_x | mg, + forward | 0x7FFF |
| 30 | i16 | accel_y | mg, + left | 0x7FFF |
| 32 | u8 | state | 0 BOOT, 1 STANDBY, 2 DRIVE, 3 SLEEP, 4 SAFE | |
| 33 | u16 | source_flags | bit0 rpm ECU, bit1 rpm ripple, bit2 speed ECU, bit4 ECU online, bit5 fuel from model | |
| 35 | u32 | trip_m | m this trip | |
| 39 | u16 | dtc | bit15 MIL, bits 0 to 7 count | |
| 41 | u16 | fuel_level | % x 100 | 0xFFFF |

Location comes from the phone; with no ECU speed the app uses GPS speed. Firmware and app both assert the 43-byte size.

### Commands

| ID | Name | Args | Role | Notes |
|----|------|------|------|-------|
| 0x01 | GET_INFO | | any | See below |
| 0x02 | SET_TIME | `u32 utc, i16 tz_min` | any | Sent on every connect |
| 0x03 | SET_LIVE_RATE | `u8 hz` (0 to 10) | any | 0 stops the stream |
| 0x10 | READ_DTC | `u8 type` (stored, pending) | any | Answers DTC_LIST |
| 0x11 | CLEAR_DTC | `u32 token` | owner | First call returns a token; second call with it within 30 s clears. VEHICLE_STATE if the engine runs |
| 0x12 | RUN_PROBE | | owner | |
| 0x20 | SYNC_LOG | `u32 from_seq, u16 max_records` | any | Streams LOG_CHUNKs |
| 0x21 | GET_CONFIG | `u16 key` | any | Keys in `config.h` |
| 0x22 | SET_CONFIG | `u16 key, value` | owner | |
| 0x23 | SET_ODOMETER | `u32 km` | owner | |
| 0x24 | CAL_VBAT | `u16 measured_mV, u8 point` | owner | Two-point calibration |
| 0x25 | CAL_FUEL | `u8 percent` | owner | Unused |
| 0x26 | FILL_UP | `u16 litres x 100, u32 price_paise` | any | Feeds fuel-model calibration |
| 0x30 | AUX_OUT | | owner | Unsupported |
| 0x40 | OTA_BEGIN | `u32 size, u8 sha256[32]` | owner | |
| 0x41 | OTA_END | | owner | Verify, switch slot |
| 0x42 | WIFI_OTA | | owner | UNSUPPORTED |
| 0x50 | USER_ADD | `u8 user_id` (1 to 3) | owner | Returns that driver's 32-byte K_user |
| 0x51 | USER_REVOKE | `u8 user_id` | owner | Blocklists the user and deletes the bond |
| 0x60 | REBOOT | `u8 mode` (0 normal, 1 safe) | owner | |

Status: 0 OK, 1 BAD_ARGS, 2 NOT_AUTH, 3 FORBIDDEN_ROLE, 4 VEHICLE_STATE, 5 BUSY, 6 IO_ERROR, 7 UNSUPPORTED.

GET_INFO response, in order: fw major, minor, patch, hw revision (4 x u8); protocol, ECU address, KB1, KB2 (4 x u8); PID bitmaps
01-20, 21-40, 41-60, 61-80 (4 x u32); oldest and head log seq (2 x u32); vehicle-guard denied count (u32); ECU ID (u8 length +
bytes); VIN (u8 length + 17 chars, 0 if not reported).

### Log sync

- Every record has a strictly increasing 32-bit `seq`; the app stores the last one it has.
- On connect: `SET_TIME`, then `SYNC_LOG(last_seq + 1, n)`. Each chunk is written in one database transaction that also advances
  `last_seq`, so a crash or dropped link cannot skip or duplicate records.
- Records with a bad CRC-32 are skipped and reported as gaps.
- Records with `utc = 0` are back-filled from the next valid (uptime, utc) pair with the same `boot_id`.

### Security

| Layer | Mechanism |
|-------|-----------|
| Device key | `K_dev`, 32 bytes from the hardware RNG on first boot, in NVS. Printed once as a QR pairing card `LL1:<device_id hex>:<base32(K_dev)>`, also on a label inside the shell |
| Bonding | LE Secure Connections, Just Works, only inside a pairing window: 120 s after a PAIR press with the engine running, or 10 min after a boot with no bonds. Up to 4 bonds |
| App auth, every connection | Dongle sends `AUTH_CHALLENGE(device_id, Nd)`. Phone answers `tag = HMAC-SHA256(K_user, "LL-AUTH" + device_id + Nd + Np)[0:16]`. `K_user = HMAC-SHA256(K_dev, "LL-USER" + user_id)`, user 0 is the owner. Session key `K_s = HMAC-SHA256(K_user, "LL-SESS" + Nd + Np)`. Constant-time compare |
| Commands | 32-bit counter, strictly increasing per session, `MAC = HMAC-SHA256(K_s, header + payload)[0:8]`. Replays and reordering rejected |
| Roles | Owner (user 0): everything. Drivers (1 to 3): view, sync, fill-ups, set time; no code clearing, firmware, config or users |
| Invites | Owner sends `USER_ADD`, shows the returned K_user as a QR code (`LL1U:<device_id hex>:<user_id>:<base32(K_user)>`); the driver scans it and bonds in a window the owner opens |
| Phone storage | K_user sealed with the Android Keystore; app data private, backup disabled |
| Firmware | Owner-only OTA with the engine off, SHA-256 checked, rollback on a bad image; always reflashable over USB |
| Car | Read-only vehicle guard; a compromised phone still cannot send a write service |
| Factory reset | Hold PAIR 10 s with the engine running: bonds wiped, K_dev kept, so the card stays valid |

No cloud and no inbound internet path. A stolen phone is handled by revoking its user.

---

## 8. App

Kotlin and Jetpack Compose in `android/`. minSdk 26, targetSdk 36. No account, no cloud.

### Build

```bash
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Or open `android/` in Android Studio. Gradle needs JDK 17; the code targets Java 11.

### Pairing

1. Start the engine.
2. Tap "Pair module" and scan the pairing card (or paste the `LL1:...` code). A driver scans the owner's invite QR instead.
3. Press PAIR on the dongle (window open 120 s; on a new dongle with no bonds it is already open).
4. Select `LoganLink-XXXX` and accept the Android pairing request.
5. The app authenticates, sets the time, syncs the log and runs the probe. Then enter odometer, petrol price, tank size and plate in
   Garage, Vehicle.

"Try without a car" on the first screen runs a demo with a simulated car and seeded history.

### Screens

Tabs: Car (status, warnings, parking, last trip, next service, alerts), Live (gauges, last-minute chart), Trips (list, CSV export,
route map, fuel, cost, harsh events, score), Health (fault codes, battery, engine, maintenance), Garage (Vehicle, LoganLink module,
Parking, Fuel log, Maintenance, Drivers, Settings).

- Drive Mode: full-screen speed, RPM, coolant, battery, alerts, Maps next turn; "Float over Maps"; long-press to exit.
- Diagnostics: codes with meaning and severity; owner-only two-step clear, engine off.
- LoganLink module: connection, firmware, probe results, "Detect again", Battery reading, Firmware update, restart.
- Settings: alerts, Drive Mode on engine start, fuel auto-log, car panel over Maps, export, forget module.
- Maintenance items: oil, plugs, filters, timing belt, coolant, brake fluid, by km and time.

### Automatic behaviour

| Feature | Behaviour |
|---------|-----------|
| Connect | Background connect when the car starts (companion device presence, foreground service) |
| Trips | Start with the engine, end 8 min after engine-off. Score counts harsh braking and acceleration per distance; idling costs points only above 60 % of trip time |
| Drive Mode | Opens on engine start if enabled and the app is open |
| Parking | Saved 45 s after engine-off; without a fresh fix (≤ 25 m, under 1 min old) uses the last fix ≤ 20 m under 3 min old |
| Fuel fill-ups | Payment notifications (GPay, PhonePe, Paytm, BHIM, bank SMS) naming a petrol pump: litres = amount / petrol price. ₹100 to ₹10,000; duplicates within 15 min ignored. Sent to the dongle as `FILL_UP` |
| Google Maps | While navigating: next turn in Drive Mode, floating car panel over Maps |
| Vehicle ID | Plate formatted (for example `KA 01 AB 1234`); VIN gives maker and model year |
| Odometer, service | Advanced by every trip; service items count down by km and days |
| Trip summary | Notification at trip end |

| Alert | Condition | Severity |
|-------|-----------|----------|
| Overheat | Coolant > 105 °C | Stop safely |
| Charging fault | VBAT < 12.8 V with RPM > 1200 for 60 s | Check soon |
| Over-voltage | VBAT > 15.0 V | Stop safely |
| New fault code | Code not seen before | Per code |

Cooldown 10 min per alert, once a day per fault code. Battery health: resting voltage 35 %, cranking minimum 50 % (8.5 V scores 0,
10.5 V full), recovery time 15 %.

### Permissions

| Permission | Why |
|------------|-----|
| Nearby devices (`BLUETOOTH_SCAN` `neverForLocation`, `BLUETOOTH_CONNECT`) | Find and connect to the module |
| Location (fine) | Parking, routes, GPS speed fallback |
| Notifications | Alerts, trip summary, fill-up confirmations, foreground-service notice |
| Companion device (`REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, `REQUEST_COMPANION_RUN_IN_BACKGROUND`) | Wake the app when the module appears |
| Foreground service (connected device, location) | Hold the link and record the route while driving |
| Notification access (in Settings) | Maps turn instructions and payment notifications |
| Display over other apps (in Settings) | Car panel over Maps |
| Camera | Not held; the Google code scanner handles QR scanning |

### Architecture

| Package | Contents |
|---------|----------|
| `core/protocol` | Frame codec, CRC-16, LiveV1, log records, pairing card, HMAC auth |
| `core/link` | `VehicleLink`, `BleVehicleLink`, `SimVehicleLink` (demo), scanner |
| `core` (`VehicleHub`) | Connection, sync, trips, parking, alerts, commands |
| `core/data` | SQLite repository, preferences, Keystore key vault, plate and VIN decoding |
| `core/analytics` | Trip score, battery health, insights, fault-code catalogue |
| `core/service` | `DriveService` (foreground, overlay), `PresenceService` (companion device), notifier |
| `core/nav` | Maps notification listener, fuel auto-log |
| `ui` | Navigation, screens, route map, overlay, theme |

The UI talks only to `VehicleHub` and the repository, never to BLE.

---

## 9. Install and test

Tools: multimeter with µA resolution, soft cable tie, phone with the app; 2-channel oscilloscope for the K-line timing test.

### Install in the car

1. **Find the socket**: 16-pin trapezoid, usually under the dash on the driver's side. Check the space for 54.8 x 43.8 x 29.6 mm.
2. **Check pins, key out.** Pin 16 to pin 4: 12.2 to 12.8 V. 0 V means pin 16 is ignition-switched on this car: driving works, but
   no cranking log or parked battery watch. Pin 4 to body < 0.5 Ω. Key on: pin 7 to pin 4 near battery voltage (K-line idle high).
3. **Plug in** with the key out. Green LED blinks every 3 s (STANDBY). The plug must sit fully home and not rock.
4. **Pair** ([8](#pairing)). The probe runs automatically; Garage, LoganLink module then shows protocol, ECU ID, PIDs and VIN. If no
   ECU answers, battery and accelerometer features still work.
5. **Vehicle data**: Garage, Vehicle: odometer, petrol price, tank size, plate.
6. **Calibrate battery voltage**: engine off, wait 2 min, measure across the battery terminals, enter it in Garage, LoganLink
   module, Battery reading. The app must then agree within ±30 mV.
7. **Sleep current**: find the cabin fuse feeding pin 16 (pulled, pin 16 reads 0 V). Dongle unplugged, meter (mA range) across the
   empty fuse slot: note the car's own draw. Plug the dongle in, phone Bluetooth off or away, wait 10 min for SLEEP (LED off), read
   again. Difference ≤ 150 µA (design 61 µA). Refit the fuse.
8. **Drive 15 min**, switch off, wait 1 min: the app shows the trip and parking spot.

Before passing the dongle on: factory reset (hold PAIR 10 s, engine running).

### Acceptance tests

| Test | Method | Pass |
|------|--------|------|
| Sleep current | Step 7, or bench supply at 12.6 V with a µA meter, 1 h after SLEEP entry | ≤ 150 µA (design 61 µA); HIBERNATE ≤ 80 µA |
| Parked battery impact | 14 days parked, resting voltage drop with and without the dongle, same battery | ≤ 0.05 V from the dongle |
| Battery voltage accuracy | Bench supply at 9, 12, 15 V against the multimeter, after calibration | ±30 mV (±50 mV at temperature extremes) |
| K-line timing | Scope on pin 7 during fast init and a PID request | TiniL 25 ± 1 ms, TWuP 50 ± 1 ms, P3 ≥ 55 ms, bit time 96.15 µs ± 1 % |
| Diagnostic accuracy | Idle and 2,000 rpm, cold and warm, against a known-good scan tool | Same PID values ± 1 LSB; same fault codes |
| Workshop coexistence | Scan tool on a splitter while the dongle runs | Dongle silent within one request; scan tool works normally |
| Reconnect | Phone in pocket, 100 ignition cycles | ≥ 98 % connected and authenticated within 10 s |
| Log sync | Sync 10,000 records | No lost or duplicate `seq` |
| OTA recovery | 50 BLE updates, 10 with the link dropped mid-transfer, 5 with power cut mid-write | All recover; rollback works; no bricked unit |
| Vehicle guard | Scripted sweep of SIDs 0x00 to 0xFF against a K-line simulator | No non-allow-listed SID transmitted |
| Thermal | 85 °C chamber, DRIVE load, 4 h; then 95 °C storage, 48 h | No reset; thermal policy engages; no shell deformation |
| Load dump | ISO 16750-2 Test B, 35 V (test house) | No TVS conduction, dongle keeps running |

Record each result with date, firmware hash, hardware serial and raw data.

---

## 10. BOM and ordering

Assembly BOM: [`hardware/bom.csv`](../hardware/bom.csv) (LionCircuits columns: Sl No., Designator, Description, Qty, MPN, DNP,
Footprint, Value). Prices below are indicative INR; check before ordering.

| Item | 1 unit | Each at 25 |
|------|--------|-----------|
| Board parts (SMT) | LionCircuits BOM quote | LionCircuits BOM quote |
| OBD housing, pins, wire | about ₹300 | about ₹225 |
| PCB, 2-layer HASL | ₹200 | ₹60 |
| Assembly, top side | ₹1,500 | ₹300 |
| Enclosure print + screws | ₹120 | ₹80 |
| Total | Sum of the above | Sum of the above |

Assembly setup dominates at one unit. Order at least 5 boards for price and bring-up spares.

Cost choices: ESP32-S3-MINI-1 module (pre-certified, no RF tuning); 2N7002K + LMV331 instead of a K-line transceiver; no RTC
crystal; Bourns SRN4018 inductor; 1 % feedback resistors; CAN footprint only; wires to pads instead of a board-mount OBD connector.

### LionCircuits order

| PCB setting | Value |
|-------------|-------|
| Layers | 2 |
| Material | FR4, 1.6 mm |
| Copper | 1 oz |
| Finish | Lead-free HASL. Ask them to inspect U3 (QFN) and U5 (LGA), the two parts that like flat pads |
| Mask / silk | Green / white |
| Size | 51 x 40 mm |

| Assembly setting | Value |
|------------------|-------|
| Type | Turnkey, top side only |
| Files | `hardware/pcb/Gerber/` zipped (includes `pcb-all.pos`), `hardware/bom.csv` |
| DNP | U4 (CAN), SW1 (reset), J1, J3 and TP1 to TP4 (pads only) |

Buy yourself: OBD housing Molex 68503-1602, 6 crimp pins Molex 57964-9702, 22 AWG wire, 4 x M2 x 18 screws.

Tools: open-barrel crimp tool for the Molex pins, soldering iron, multimeter, current-limited bench supply, USB-C cable,
oscilloscope for the K-line check.

---

## 11. Roadmap

Phases: **Near** (weeks, firmware and app), **Mid** (months, add-on or board revision), **Long** (major work).

### Writing to the bus

Writes would be a separate, narrow capability on top of the read-only guard; the guard itself is never loosened.

| Function | KWP2000 service (IDs are ECU-specific) | Risk | Phase |
|----------|----------------------------------------|------|-------|
| Freeze frame, extended DTC status | 0x12, 0x17, 0x18 (read) | None | Near |
| Clear DTCs | 0x14 | Low | Done (guarded) |
| Idle or throttle-body adaptation reset | 0x31 or 0x3B | Medium: idle hunts until relearned | Mid |
| Fuel-trim adaptation reset | 0x31 or 0x3B | Low to medium | Mid |
| Actuator tests (fan, fuel pump, A/C clutch, purge valve, injector click, MIL) | 0x30 | Medium: never with the engine running or the car moving | Mid |
| Coding writes | 0x3B to coding IDs | High | Long, if ever |
| Key or immobiliser programming | 0x27 + proprietary | Theft | Forbidden |
| ECU reflash or remap | 0x34 to 0x37, 0x27 | Bricking | Forbidden |

Safety rules:

1. **Signed scripts, not free bytes.** Each routine is a CBOR script (target ECU, exact request bytes, expected response, timeouts,
   preconditions, stop step, undo step), signed with an offline Ed25519 key. The dongle verifies it and loads it as a one-shot
   `vguard` extension that allows exactly those bytes once.
2. **Preconditions on the dongle**, checked just before sending. Actuator tests: RPM 0, speed 0, ECU answering, VBAT 12.0 to 14.8 V.
   A script may tighten limits, never allow anything while the car moves.
3. **Owner and presence**: owner role, PAIR pressed within 60 s, typed confirmation stating what happens and how to stop it.
4. **Dead-man**: actuator tests run only while a button is held. Release, link loss or a failed precondition sends
   ReturnControlToECU and drops to the default session.
5. **Dry run**: CI against an ECU simulator (ESP32 dev board emulating KWP), then on the car sending only the precondition reads.
6. **Audit**: every write request, response and precondition snapshot logged (`REC_SERVICE`) and synced.
7. **Rollback**: adaptation values read and stored before writing; routines without undo are marked irreversible.
8. **Compiled out**: SecurityAccess for immobiliser or flash, download and upload (0x34 to 0x37), ECU reset while driving, any
   message to immobiliser or airbag ECUs.

Effort: 2 to 3 weeks for the framework, 1 to 2 days per routine.

### Vehicle coverage

| Item | Phase | Effort | Cost |
|------|-------|--------|------|
| ISO 15765 CAN (ISO-TP, 11/29-bit, 250/500 kbit/s), fit U4 | Near to Mid | 1 to 2 weeks | +₹110 |
| Multiple cars in the app | Near | 1 week | |
| J1850 PWM/VPW for US-market cars | Mid | 2 weeks + extra interface | +₹150 |

### Hardware add-ons

| Item | Phase | Note | Cost |
|------|-------|------|------|
| Cabin air pod (CO2, VOC, temperature, humidity) | Mid | Drowsiness and occupant-left-behind alerts | ₹3,500 |
| Ignition, reverse and accessory taps | Mid | Dashcam switching, reverse detection | ₹300 |
| TPMS receiver for 433 MHz aftermarket sensors | Mid to Long | Protocols vary by brand | ₹900 + sensors |
| LTE Cat-1 variant | Mid to Long | Remote status, tow and theft alerts; needs SIM, server, bigger board | ₹1,500 + SIM |
| Remote start, central locking | Never | Defeats the immobiliser or adds attack surface | |

### App, platform, firmware

| Item | Phase | Effort |
|------|-------|--------|
| Local-ID mapper screen (build `lidmap` from probe dumps) | Near | 1 week |
| Home-screen widgets (battery, fuel, last trip) | Near | 3 to 4 days |
| Google Assistant App Actions ("where is my car") | Near | 3 days |
| ECU simulator firmware for CI | Near | |
| Adaptive PID scheduler (poll faster what the screen shows) | Near | |
| Android Auto status cards | Mid | 1 to 2 weeks |
| Wear OS tile | Mid | 1 week |
| Self-hosted backup and multi-phone history merge | Mid | 2 to 3 weeks |
| Misfire estimate from crank-speed fluctuation | Mid | |
| On-phone driving-style and battery-life models | Mid to Long | 3 to 6 weeks |
| iOS app on the same protocol | Long | 4 to 6 weeks |

### Order

1. CAN driver, ECU simulator, local-ID mapper, widgets.
2. Service-script framework with read-only freeze frame, then the first low-risk routine (fan relay test, engine off).
3. Cabin pod.
4. Android Auto, Wear OS, on-phone models.
5. LTE variant only if remote tracking is needed.

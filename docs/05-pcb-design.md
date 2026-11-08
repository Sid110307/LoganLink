# 05. Custom PCB Design (LL-VM1 rev A)

## 1. KiCad project architecture (KiCad 8)

```
hardware/kicad/
├── LL-VM1.kicad_pro / .kicad_sch (root) / .kicad_pcb
├── sheets/
│   ├── 01_power_entry.kicad_sch        J1, F-sense, D1 TVS, D2, π-filter, U2 buck, U3/U4 load switches, [C] U17
│   ├── 02_mcu.kicad_sch                U1 ESP32-S3, EN RC, straps, USB-C J2 + D5, Tag-Connect J5, button, LED
│   ├── 03_vehicle_if.kicad_sch         K-line PHY (Q1, U14), CAN (U13), VBAT DC/AC paths, IGN/REV buffers, fuel sense, AUX out U15
│   ├── 04_gnss.kicad_sch               U5 MAX-M10S, RF feed, U.FL J3, backup
│   ├── 05_sensors_storage.kicad_sch    U6 IMU, U7 wake accel, U8 RTC, U9–U11 cabin sensors, U12 NOR flash
│   └── 06_lte_variant.kicad_sch        U16 A7672S, SIM J6, J4 U.FL (DNP on variant B)
├── lib/
│   ├── LL-VM1.kicad_sym                project symbols (see §7)
│   ├── LL-VM1.pretty/                  project footprints
│   └── 3dmodels/                       STEP models (from manufacturers)
├── fab/ (generated)                    Gerbers, drill, IPC-356 netlist, BOM, CPL, STEP
└── LL-VM1.kicad_dru                    custom design rules (§4.4)
```

Use **hierarchical labels** for all inter-sheet nets. Global power symbols: `VBAT_PROT`, `VIN_BUCK`, `3V3_AON`,
`3V3_SW`, `3V3_GNSS`, `3V8_LTE`, `GND`. The variant is managed with KiCad 8 **DNP fields** plus two BOM presets
(`VM1-B`, `VM1-C`).

## 2. Schematic net definitions (key nets)

| Net | From → To | Net class |
|-----|-----------|-----------|
| VBAT_IN | J1.1 → D1, D2 anode | `PWR_HV` |
| VBAT_PROT | D2 cathode → L2, R10, R16, R30, C31, U17 | `PWR_HV` |
| VIN_BUCK | L2 → U2 VIN, C3, C4 | `PWR_HV` |
| 3V3_AON | U2 → U1, U3/U4 VIN, U7, U8, I2C0 pull-ups | `PWR_LV` |
| 3V3_SW | U3 → U6, U9–U13, U14, flash, ripple bias | `PWR_LV` |
| 3V3_GNSS | U4 → U5, antenna feed | `PWR_LV` |
| K_LINE | J1.7 → R13, D3, R14 | `HV_SIG` |
| CANH / CANL | J1.6/J1.8 → U13 | `CAN` (diff 120 Ω, length-matched ±2 mm) |
| GNSS_RF | U5 RF_IN → J3 | `RF50` |
| LTE_ANT | U16 ANT → J4 | `RF50` |
| USB_DP / USB_DN | J2 → D5 → U1 | `USB90` (diff 90 Ω) |

## 3. Connector pin assignments

### J1: Micro-Fit 3.0, 10-circuit (2 × 5), PCB header, viewed from mating face

| Pin | Signal | Wire colour (harness) | Gauge | Source |
|-----|--------|-----------------------|-------|--------|
| 1 | VBAT_IN | Red | 20 AWG (0.5 mm²) | OBD 16 via 3 A fuse |
| 2 | GND (power) | Black | 20 AWG | OBD 4 |
| 3 | GND (signal) | Black/white | 22 AWG | OBD 5 |
| 4 | K_LINE | White | 22 AWG | OBD 7 |
| 5 | IGN_TAP (opt.) | Green | 22 AWG | +APC via 0.5 A PTC |
| 6 | CAN_H | Yellow/green twisted | 22 AWG | OBD 6 |
| 7 | AIN_FUEL (opt.) | Purple | 22 AWG | fuel-sender wire |
| 8 | CAN_L | Yellow/brown twisted | 22 AWG | OBD 14 |
| 9 | REV_TAP (opt.) | Grey | 22 AWG | reverse-lamp feed via 0.5 A PTC |
| 10 | AUX_OUT (low side) | Blue | 20 AWG | to accessory −, accessory + via 2 A fuse |

The coolant NTC probe uses a separate 2-pin **JST GH** (SM02B-GHS-TB) connector, J7 (AIN_SPARE + GND), which is optional.

### J2: USB-C. J3/J4: U.FL. J5: Tag-Connect TC2030-NL

| TC2030 pad | 1 | 2 | 3 | 4 | 5 | 6 |
|------------|---|---|---|---|---|---|
| Signal | 3V3_AON | EN | GND | IO0 | U0RXD | U0TXD |

## 4. PCB engineering

### 4.1 Form factor
- Outline **80.0 × 55.0 mm**, 1.6 mm, corner radius 3 mm.
- Four M3 mounting holes (Ø 3.2 mm plated, 6 mm keep-out pad tied to GND via 4 stitching vias) at
  (4, 4), (76, 4), (4, 51), (76, 51) mm from the lower-left corner.
- The ESP32 antenna overhangs nothing. The module sits at the **top edge, centred at x = 40 mm**, with its antenna at the board edge.
- J1 is on the **bottom (short) edge** at the left. J2 USB-C is on the right edge. J3/J4 U.FL are near the right-top corner, next to U5/U16.

### 4.1.1 Manufacturing route: LionCircuits (Bengaluru), single monolithic board
- **One PCB for both variants.** Everything, including the LTE section, SIM holder and all sensors, is on this single
  80 × 55 mm board. Variant B simply leaves the LTE parts unfitted (DNP). There are no daughter boards, flex links or
  board-to-board connectors.
  The only off-board items are cables and antennas: the OBD harness, the GNSS antenna, the optional LTE antenna and
  the optional coolant probe.
- **Fabrication and assembly:** LionCircuits PCB + PCBA, **turnkey sourcing from Mouser / DigiKey**. The BOM
  (`hardware/bom.csv`) therefore lists a manufacturer part number and a Mouser/DigiKey stock number for every line.
  Every part in this design is a standard catalogue part at both distributors. No LCSC-only parts.
- **Before layout freeze**, ask LionCircuits for: (1) their standard 4-layer 1.6 mm stack-up (prepreg/core thickness
  and εr), so the RF50 and USB90 widths in §4.2 can be recalculated; (2) whether they offer impedance control with a
  test coupon, or only "calculated width" (both are acceptable here: the RF line is under 10 mm long); (3) their
  minimum track/space, drill and via-in-pad (filled/capped) capability; (4) their preferred CPL rotation convention.
- Parts that turnkey assemblers often can't place, or need consigned: **SCD41** (moisture-sensitive, tray), **MAX-M10S**,
  **ESP32-S3-WROOM-1** (MSL 3, bake if opened > 168 h), U.FL. Flag these in the order notes. The J1 Micro-Fit THT header
  is hand-soldered after reflow.

### 4.2 Stack-up: 4 layers, 1.6 mm, FR-4 Tg ≥ 150 (LionCircuits standard 4-layer, values below are typical; replace with their figures)

| Layer | Use | Copper | Dielectric to next |
|-------|-----|--------|--------------------|
| L1 Top | Components, signals, RF CPWG | 1 oz (35 µm) | 7628 prepreg 0.21 mm, εr ≈ 4.4 |
| L2 | **Solid GND** (no splits) | 0.5 oz | core ~1.07 mm |
| L3 | Power pours (VBAT_PROT, 3V3 rails) + low-speed signals | 0.5 oz | 7628 prepreg 0.21 mm |
| L4 Bottom | Signals, test points, GND fill | 1 oz | - |

Controlled impedance (verify with the fab's calculator for its exact stack):
- **50 Ω coplanar waveguide with ground (L1 over L2):** width ≈ 0.30 mm, gap ≈ 0.20 mm. Plain microstrip would need ≈ 0.36 mm.
- **USB 90 Ω differential:** ≈ 0.20 mm / 0.15 mm gap (only 8 mm long, so non-critical).
- **CAN 120 Ω differential:** loosely coupled pair, 0.25 mm / 0.5 mm. Short, low speed, non-critical.

### 4.3 Placement zones (top view)

```
 y=55 ┌─────────────────────────────── ANTENNA KEEP-OUT (all layers, 15 × 6 mm beyond module edge) ────┐
      │   [U5 GNSS]  [J3]      ┌────── U1 ESP32-S3-WROOM-1 ──────┐          [U16 LTE (C)]  [J4]   │
      │   GNSS ZONE            │                                  │          LTE ZONE              │
      │                        └──────────────────────────────────┘                                │
      │  [U6 IMU] [U7]  (near board centroid,                 [U12 flash] [U8 RTC]   [J6 SIM]      │
      │                   rigidly mounted area)                                                    │
      │  [U9 SHT45]  [U10 SGP41]  ── edge "sensor window" ──  [U11 SCD41]                 [J2 USB-C]│
      │  ── thermal slot 1 mm milled around SHT45 island ──                                       │
      │  [U13 CAN] [Q1/U14 K-line PHY] [IGN/REV buffers] [U15 AUX]         [U2 buck] [L1] [C3]      │
      │  [D3][D4]                                                [D1 TVS] [D2] [L2] [C1,C2]        │
 y=0  └──[ J1 Micro-Fit 10 ]────────────────────────────────────────────────────────────────────────┘
      x=0                                                                                       x=80
```

Rules:
1. **Noisy-to-quiet gradient:** power entry and buck in the bottom-right corner, vehicle-interface protection along the bottom
   edge next to J1, RF and digital at the top.
2. TVS D1 and C1 within 5 mm of J1.1/J1.2. Their ground connection uses ≥ 6 vias of 0.4 mm drill straight into L2 at the J1 ground pin.
   The transient current loop must not pass under the MCU.
3. Buck hot loop (VIN cap → U2 VIN/PGND) < 25 mm² area. Input caps on the same layer as U2. Switch node copper is minimised
   and is not routed near the GNSS RF.
4. The SHT45 sits on a **thermally isolated peninsula**: a 1 mm milled slot on 3 sides, no pours on the peninsula, and placement
   ≥ 25 mm from the buck and the ESP32. Otherwise self-heating reads +3–5 °C high.
5. SCD41 and SGP41 sit under an enclosure vent (doc 09). SGP41 has a **no-conformal-coat keep-out**.
6. IMU near the board centroid between two mounting holes, to minimise board-flex vibration modes. Its axes are aligned with the board
   edges and marked on the silkscreen. X+ points toward J1, which faces the car front once installed (doc 11).

### 4.4 Routing rules (`LL-VM1.kicad_dru`)

| Net class | Track | Clearance | Via | Notes |
|-----------|-------|-----------|-----|-------|
| `PWR_HV` | 0.8 mm (1 oz, ≥ 2 A at ΔT 10 °C) | **0.5 mm** (60 V + transient creepage margin) | 0.6/0.3 mm, ≥ 2 per transition | L3 pour preferred |
| `PWR_LV` | 0.5 mm | 0.2 mm | 0.6/0.3 | - |
| `HV_SIG` (K-line, taps) | 0.3 mm | 0.5 mm | 0.6/0.3 | - |
| `RF50` | 0.30 mm CPWG | gap 0.20 mm | GND via fence every 1.5 mm (λ/20 at 1.6 GHz ≈ 9 mm, far below) | No vias in the line, length < 15 mm |
| `CAN` | 0.25 mm diff | 0.2 mm | - | - |
| default | 0.2 mm | 0.15 mm | 0.45/0.2 mm | - |

- **L2 stays unbroken.** Every signal on L1/L4 has a continuous L2 reference. No signal crosses a gap in L3 power pours.
  Where a signal changes layers between L1 and L4, place a ground stitching via within 1 mm, so the return current
  has a path between reference planes.
- Ground stitching: a via grid at 5 mm pitch across the board, and a 2.5 mm pitch fence along the board edge (cuts edge radiation).
- Keep the 3V3 buck output on L3 in a single pour. Each load switch output is its own pour island.

### 4.5 RF layout
- **ESP32-S3 PCB antenna:** follow Espressif's *ESP32-S3-WROOM-1 Datasheet §Antenna Keepout*. The module's antenna end sits at the board
  edge, with no copper on **any** layer under or beside the antenna region (≥ 15 mm × module width beyond the feed when it can't overhang).
  No components within 5 mm. The enclosure wall in front of the antenna is plastic only, ≥ 2 mm away (doc 09).
- **GNSS:** U5 RF_IN to J3 is under 10 mm long, as CPWG with a via fence. The 47 nH feed choke sits at J3, with a DC block on the module
  side. Nothing that switches above 1 MHz goes within 10 mm of the RF line. GNSS is also > 30 mm from the buck switch node.
- **LTE (C):** pad stack and matching per the SIMCom *A7672 Series Hardware Design* manual. Use a π-matching network (0402, DNP
  pattern) at J4. Bulk 3 × 100 µF + 33 pF + 10 pF at VBAT pins for the 2 A burst current.

### 4.6 EMC measures (CISPR 25 class 3 target; protects the car's FM band and the GNSS)
- π-filter L2 + C1/C2/C4 on the input; 100 pF + 100 nF at J1 for HF.
- Buck at 400 kHz. Its harmonics in the FM band (88–108 MHz) are high order and low energy. Spread-spectrum on the
  LMR36015 if the variant supports it (check suffix).
- 33 Ω series resistors on SPI SCK and the GNSS UART TX at the source.
- Bus-line common-mode: footprint for an optional CMC (TDK ACT45B-510-2P) on CAN, DNP by default.
- All cables leave the board through J1 on one edge, so no antenna pairs form across the board.

### 4.7 Thermal management
Worst case (85 °C ambient, DRIVE with OTA on Wi-Fi): dissipation is the buck (0.15 W) + ESP32 (1.1 W peak, 0.3 W avg) + GNSS (0.03 W),
about **0.5 W average**. With 4 layers of 80 × 55 mm copper spreading into still air inside the enclosure, θ ≈ 35 °C/W board-to-ambient,
so the internal rise is ≈ 17 °C. The ESP32 module ambient then reaches 85 + 17 = 102 °C, above its 85 °C rating. The firmware therefore
uses a **thermal policy**: the ESP32 internal temperature sensor is read every 10 s. Above 95 °C (die) it disables Wi-Fi/OTA and drops
the CPU to 80 MHz. Above 105 °C it pauses SCD41 and the live stream (logging continues). Under the dash, 85 °C ambient occurs only with
the car parked in the sun, when the module is asleep anyway. In DRIVE, cabin ventilation keeps the under-dash area below about 55 °C.

### 4.8 Manufacturing data
| Item | Specification |
|------|---------------|
| Finish | ENIG (flat for LGA/WSON, better corrosion life than HASL) |
| Solder mask | Matte black LPI, both sides; silkscreen white |
| Min track/space | 0.15/0.15 mm (fab capability 0.09) |
| Min drill | 0.2 mm (vias), 0.3 mm (PWR) |
| Via treatment | Tented. Vias in the WSON/LGA pads are **filled and capped** (or keep vias out of pads) |
| Board thickness tolerance | ±10 % |
| Outline tolerance | ±0.2 mm (routed) |
| Impedance control | Yes, ±10 % on RF50 (fab coupon) |
| IPC class | IPC-6012 Class 2 fabrication, IPC-A-610 Class 2 assembly (Class 3 workmanship on J1 and power entry) |
| Panel | 2 × 2 with 5 mm rails, mouse bites, 3 fiducials per rail + 2 local fiducials near U1 and U5 |

### 4.9 Fabrication outputs (KiCad *Plot* + *Fabrication Outputs*)
- Gerber X2 for: F.Cu, In1.Cu, In2.Cu, B.Cu, F/B.Mask, F/B.Silkscreen, F/B.Paste, Edge.Cuts, plus User.Drawings (fab notes).
- Excellon drill: PTH and NPTH files separate, mm, decimal format, drill map PDF.
- IPC-D-356 netlist (for the fab's electrical test).
- BOM (`hardware/bom.csv`) with columns Designator, Qty, Manufacturer, MPN, Mouser#, DigiKey#, DNP-variant.
  Export it from KiCad with the `MPN`/`Manufacturer` fields and upload it in LionCircuits' turnkey quote format.
- Pick-and-place (CPL) CSV: Designator, Mid X, Mid Y, Rotation, Layer. **Check rotations against the fab's preview.**
  U1, U5, U11 and LGA parts often need rotation offsets.
- STEP of the assembled board, for the enclosure fit-check (doc 09).

## 5. Assembly instructions

1. Paste stencil 0.12 mm (laser-cut, electropolished). Apply a 60 % window pane reduction on the large thermal pads (U2, U5 ground),
   so the parts don't float.
2. Reflow profile per J-STD-020 SAC305: preheat 150–180 °C 60–90 s, peak 245 ± 5 °C, TAL 45–75 s.
   **SCD41, SGP41, SHT45:** these are reflow-compatible, but follow Sensirion's handling guide. No washing, no conformal coat on the
   sensor opening. Use protective tape (Kapton) over the sensor opening during any rework or coating.
3. THT/manual: J1 (selective or hand solder, 360 °C, 3 s/pin), U.FL (reflow).
4. Clean: no-clean flux. If washing is needed, do it **before** fitting the Sensirion sensors (hand-place them afterwards).
5. Conformal coat (optional, recommended for monsoon climates): acrylic (HumiSeal 1B31). Acrylic is RF-transparent, so the
   antenna area may be coated. **Mask:** J1, J2, J3/J4, SW1, test points, U9/U10/U11 openings, LED, Tag-Connect pads.

## 6. Electrical inspection (each board, before first power)

| Step | Check | Pass |
|------|-------|------|
| 1 | Visual/AOI: polarity of D1, D2, C3, LED; U1 orientation; no bridges on U2 (0.5 mm pitch), U12 WSON | Per IPC-A-610 Class 2 |
| 2 | Resistance J1.1 → GND (DMM, both polarities) | > 10 kΩ (no short) |
| 3 | Resistance 3V3_AON → GND | > 1 kΩ |
| 4 | Current-limited power-up: bench PSU 12.0 V, limit 100 mA | Input current < 60 mA, 3V3_AON = 3.30 ± 0.05 V |
| 5 | Reverse-polarity test (production sample only): −14 V, 100 mA limit | Current ≤ 1 mA (D2 blocks), no damage |
| 6 | Programming via USB-C: `idf.py flash`, run factory self-test (doc 12 §2) | All self-test items PASS |

## 7. Component library requirements

| Part | Symbol source | Footprint | Notes |
|------|---------------|-----------|-------|
| ESP32-S3-WROOM-1 | KiCad std `RF_Module:ESP32-S3-WROOM-1` | std | Check that the keep-out zone is included |
| LMR36015 | TI WEBENCH/Ultra Librarian export | VQFN-HR 12 (RNX) | Check pin-1 and the 2-pad thermal |
| MAX-M10S | u-blox symbol (create) | LCC-18 9.7×10.1 per *MAX-M10S Integration Manual* | - |
| ASM330LHHX, AIS2DW12 | SnapEDA/ST | LGA-14L / LGA-12 | Paste 1:1 |
| SHT45/SGP41/SCD41 | Sensirion KiCad libs | DFN / SCD41 LGA | Respect the recommended keep-outs |
| W25Q256JVEI | KiCad std | WSON-8 6×5 | - |
| SN65HVD230 | KiCad std | SOIC-8 | - |
| SM8S26A | create | DO-218AB | Heavy copper pads, 4 vias |
| VNLD5090-E | create | SO-8 | - |
| Micro-Fit 43045-1000 | Molex model | THT RA 2×5 3.0 mm | Pegs plated |
| A7672S | SIMCom reference footprint | LCC 24×24 | Variant C |

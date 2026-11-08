# 04. KiCad

KiCad 10. Project: `hardware/pcb/` (`pcb.kicad_pro`, `.kicad_sch`, `.kicad_pcb`). Values from
[`hardware/bom.csv`](../hardware/bom.csv), connections below.

## 1. Libraries

All stock except the buck. The project library `loganlink` (`hardware/pcb/lib/`, already in `sym-lib-table` and `fp-lib-table`)
holds the LMR36006 symbol and footprint `loganlink:TI_RNX0012A` (TI Ultra Librarian RNX0012B model, checked against the datasheet:
11 pads 0.25 x 0.6 mm, 0.5 mm pitch, 0.65 mm next to pin 1/11, SW pad 1.825 x 0.25 with split paste). If a BOM footprint name is
missing in your library version, search the chooser by package name.

## 2. Schematic

One A3 sheet, net labels. Set Value, Footprint, `MPN` and `Manufacturer` from the BOM (add the two fields in Schematic Setup >
Field Name Templates). U5: `Exclude from board` off, `DNP` on.

| Net | Connects |
|-----|----------|
| VBAT_IN | J1.1 (OBD 16), F1.1 |
| VBAT_F | F1.2, D1 cathode, D2 anode |
| VBAT_P | D2 cathode, C1, L2.1, R10, R13, R22, C16 |
| VIN | L2.2, C2, C3+, U2 VIN, U2 EN, D8 cathode |
| SW | U2 SW, U2 NC (pin 3, tie to SW per datasheet), L1.1, C6.2 |
| BOOT | U2 BOOT, C6.1 |
| VCC_U2 | U2 VCC, C7 |
| FB | U2 FB, R2.2, R3.1 |
| 3V3 | L1.2, C4, C5, R2.1, U1 3V3, C9, R4, R5, U3 VDD + VDD_IO + SA0 + CS, C13, C14, R11, R12 |
| GND | J1.2 (OBD 4), J1.3 (OBD 5), every return, J2 shell, U2 PGND/AGND, exposed pads |
| VBUS | J2 VBUS, D4 VBUS, D8 anode |
| USB_DN / USB_DP | J2 D- / D+ (both pins each), D4 I/O pair, U1 GPIO19 / GPIO20 |
| CC1 / CC2 | J2 CC1 to R6, CC2 to R7, other ends GND |
| EN | U1 EN, R4, C10, TP |
| BTN_PAIR | U1 GPIO0, R5, SW1 (other side GND), TP |
| VBAT_DIV | R13.2, R14 (to GND), C15 (to GND), R15.1 |
| VBAT_SENSE | R15.2, D5 mid, U1 GPIO1 (D5 ends to GND and 3V3) |
| VBAT_AC | C16.2, R16.1 |
| RIPPLE | R16.2, R17 (to AUX_PWR), R18 (to GND), C17 (to GND), D6 mid, U1 GPIO2 |
| AUX_PWR | U1 GPIO21, U4 VCC, C18, R17, R25 |
| K_LINE | J1.4 (OBD 7), D7 cathode, R19.1, D3, R20 |
| K_PU | R10.2, D7 anode |
| K_DRAIN | R19.2, Q1 drain |
| K_GATE | Q1 gate, R26.2, R27 (to GND) |
| K_TXD | U1 GPIO17, R26.1 |
| K_DIV | R20.2, R21 (to GND), R24, U4 IN+ |
| K_REF | R22.2, R23 (to GND), U4 IN- |
| K_RXD | U4 OUT, R24, R25, U1 GPIO18 |
| SDA / SCL | U1 GPIO8 / GPIO9, U3 SDA / SCL, R11 / R12 |
| ACC_INT | U3 INT1, U1 GPIO7 |
| LED_R | U1 GPIO38, R8, LED1 |
| LED_G | U1 GPIO39, R9, LED2 |
| U0TXD / U0RXD | U1 GPIO43 / GPIO44, TP |
| CAN_TXD / CAN_RXD | U1 GPIO4 / GPIO5, U5 D / R |
| CAN_H / CAN_L | J1.5 (OBD 6) / J1.6 (OBD 14), U5 |
| (U5) | VCC to 3V3 with C19, Rs to GND, Vref open |

Q1 source to GND. U2 PG (pin 8) unconnected. TP1 to TP6: 3V3, GND, EN, GPIO0, U0TXD, U0RXD.

Run ERC and fix every error. Only "pin not driven" warnings on nets fed through resistors are acceptable.

## 3. Board setup

| Setting | Value |
|---------|-------|
| Layers | 2, 1.6 mm FR4, 1 oz |
| Min track / clearance | 0.15 / 0.15 mm (design to 0.2) |
| Min via | 0.3 drill / 0.6 pad |
| Min hole | 0.3 mm |
| Copper to edge | 0.3 mm |
| Silk | 0.15 mm line, 0.8 mm text |

| Net class | Nets | Track |
|-----------|------|-------|
| Default | everything else | 0.2 |
| Power | VBAT_IN, VBAT_F, VBAT_P, VIN, SW, 3V3, GND, VBUS | 0.5 |
| KLine | K_LINE, K_PU, K_DRAIN | 0.4 |
| USB | USB_DN, USB_DP | 0.25, routed as a pair, gap 0.2 |

## 4. Outline and fixed positions

Origin at the board centre (Place > Grid Origin after drawing the outline). +y is up, towards the antenna; KiCad shows up as
negative y, so type y with the sign flipped.

| Item | Position | Notes |
|------|----------|-------|
| Outline (Edge.Cuts) | 51 x 40 mm, corners R3 | |
| H1 to H4 | (±23.9, ±17.1) from the board centre | `MountingHole:MountingHole_2.2mm_M2`, keep 5 mm diameter clear on both sides |
| ESP32 module | centred on x = 0, antenna up, top edge on the board edge | Must be centred: its built-in antenna keepout is 45.4 mm wide and would hit a mounting hole otherwise |
| J1 wire pads | pin 1 at (-6.35, -4), pin 6 at (6.35, -4), 2.54 pitch | Wires solder from the bottom |
| LED1, LED2 | (21.0, 9.9), (21.0, 8.1) | Light pipe centre (21, 9). Nothing above 0.8 mm within 2.5 mm of it |
| SW1 | (22.0, 3.0) | Pinhole above it |
| J2 USB-C | centre x 14, mouth on the bottom edge | Bench only, used with the rear shell off |
| TP1 to TP6 | bottom side, x 23, y -6.25 to 6.25, 2.5 pitch | Pads only |

## 5. Placement zones

All on top. Tallest part 6.0 mm (C3 is 5.8).

| Zone | Area (x, y) | Parts |
|------|-------------|-------|
| Input | -27 to -8, -21 to -3 | F1 near J1.1, D1, D2, C1, L2, C2 |
| Buck | -24 to -6, -3 to 15 | C3, U2, C6, C7, R2, R3, L1, C4, C5 |
| Sense | -6 to 2, 0 to 21 | R13 to R18, C15 to C17, D5, D6, U3, R11, R12, C13, C14 |
| K-line | -8 to 9, -21 to -6 | Q1, R19, R26, R27, R10, D7, D3, U4, R20 to R25, C18, U5 |
| USB | 9 to 21, -21 to -9 | J2, D4, R6, R7, D8 |
| UI | 18 to 27, 0 to 14 | LED1, LED2, R8, R9, SW1, R4, R5, C10 |

C9 at the module 3V3 pin. Keep the SW node and L1 as far from the antenna as the zone allows. Bottom side: only J1 joints and test
pads; keep the area inside the plug housing outline (39 x 17 mm trapezoid centred on the board, wide side up) flat, the housing's
rear face is 5 mm away.

## 6. Routing order

1. Buck: C2/C3 to VIN and GND pins first, shortest loop. SW node small. FB trace short, away from SW and L1.
2. Power path J1.1 to F1, D1, D2, L2 in 0.5 mm.
3. K-line: J1.4 to D3, D7, R19 in 0.4 mm. U4 inputs away from SW.
4. USB pair, then I2C, ADC, LEDs.
5. Ground pour both layers, stitching vias every 5 mm along the edges and around U2 and U1's ground pads.
6. Fill zones (B), run DRC, fix everything.

## 7. Fit check

File > Export > STEP. In FreeCAD open `mechanical/LoganLink-dongle.FCStd`, import the STEP, align its bottom face with `PCB_ref`.
Nothing may touch the rear shell, retainer posts or light pipe.

## 8. Outputs

Set the drill/place origin at the bottom-left corner of the outline first.

| File | Settings |
|------|----------|
| Gerbers | F.Cu, B.Cu, F.Mask, B.Mask, F.Silkscreen, B.Silkscreen, F.Paste, Edge.Cuts. Drill/place origin, Protel extensions |
| Drill | Excellon, mm, PTH and NPTH in one file, same origin |
| BOM | From the schematic: Reference, Value, Footprint, MPN, Manufacturer, DNP excluded |
| Placement | File > Fabrication Outputs > Component Placement: CSV, mm, top only, same origin |

Zip the Gerbers and drill file. Ordering: [10](10-bom-and-ordering.md).

## 9. Bring-up

1. Unpowered: VBAT_P to GND and 3V3 to GND are not shorts.
2. Bench supply 12 V, 100 mA limit, into J1.1/J1.2: 3.3 V on TP 3V3, under 30 mA.
3. USB-C to the PC, hold the button, tap power: the board enumerates for flashing.
4. Flash ([06](06-firmware.md)). Check LEDs, battery reading against a meter, accelerometer wake.
5. Scope the K-line with a dummy 510R pull-up: low under 2.9 V, clean edges at 10.4 kbaud.
6. Idle sleep current under 150 µA.

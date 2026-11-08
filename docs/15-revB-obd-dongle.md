# 15 — Rev B: OBD Plug-in Dongle (current hardware baseline)

**Engineering change order ECO-002.** Rev B replaces the rev A under-dash module (docs 04, 05, 09, 11 §4–5, 13) as the
build baseline. Rev A docs stay as reference: their K-line PHY, protection philosophy, protocol, security and verification
methods still apply unless this document says otherwise. The wire protocol (doc 06) is **unchanged**.

## 1. What changed and why

| Area | Rev A | Rev B | Reason |
|------|-------|-------|--------|
| Mechanical anchor | Box tied under the dash, harness to an OBD splitter | **PCB carries a J1962 male plug and plugs straight into the OBD socket** | No harness, no splitter, no tapping. Installs in 10 s |
| Radio | ESP32-S3-WROOM-1 (PCB antenna, 18 × 25.5 mm) | **ESP32-S3-MINI-1-N8** (pre-certified, 15.4 × 20.5 mm, 8 MB flash) | Smaller, cheaper, same SoC, so the firmware carries over. No custom RF work |
| PCB | 4-layer 80 × 55 mm | **2-layer 38 × 46 mm**, single-sided SMT + 1 THT plug | The module carries all the RF. The board only has a buck and slow interfaces. 2-layer cuts fab cost by about 4× |
| GNSS | MAX-M10S + external antenna | **Removed: the phone supplies location** (parking spot, routes) | GNSS can't see the sky from a glovebox or under the dash anyway. Saves ≈ ₹2,400 |
| Cabin sensors | SCD41, SGP41, SHT45 | **Removed** | Meaningless inside a closed glovebox. Saves ≈ ₹4,000 (roadmap: separate cabin pod) |
| IMU | ASM330LHHX + AIS2DW12 | **One LIS2DW12** (±16 g at 100 Hz for driving events; ~1 µA wake mode for sleep) | One part does both jobs |
| RTC | RV-3028-C7 | **32.768 kHz crystal on the ESP32-S3 XTAL_32K pins (GPIO15/16)**, time set by the phone on every connect | ±20 ppm ≈ 1.7 s/day drift, corrected daily |
| Log storage | External 32 MB NOR | **4 MB `log` partition in the module's internal flash** (≈ 9 h of 1 Hz driving) | The phone syncs every drive. Saves a chip and 4 GPIO |
| Supply chain | LMR36015 + SM8S26A + STPS3H100 | **LMR36006-Q1 + SMCJ33A + STPS2H100A + on-board 2 A NANO2 fuse** | Sized for a 0.6 A board; see §4 for the honest protection trade-off |
| Load switches | 2 × TPS22918 | **None**: the K-line comparator and ripple bias (< 1 mA) are powered directly from GPIO21 (`AUX_PWR`) | Cheaper, simpler, same sleep result |
| CAN | Fitted | **Footprint only (DNP)** | The reference car has no CAN on its OBD socket |
| AUX output, taps, LTE variant | Present | **Removed** (optional IGN pad kept, DNP) | Cost. Moved to the roadmap (doc 16) |
| Enclosure | 86 × 61 × 19.5 mm box with ears | **Thin-wall (1.5 mm) snap-fit dongle shell**, `mechanical/dongle.scad` | Fits the plug body snugly. No screws in the antenna zone |
| Cost (1 unit) | ≈ ₹16,800 | **≈ ₹3,300** | §8 |

## 2. Block diagram

```
 OBD socket (car)
   │ Molex 68503 OBD plug, 6 crimped pins on short leads to the PCB edge (4,5,7,16; 6,14 to DNP CAN)
   ▼
 pin16 ─ F1 2 A NANO2 ─┬─ D1 SMCJ33A ─ GND        ┌───────────── ESP32-S3-MINI-1-N8 ─────────────┐
                       └─ D2 STPS2H100A ─ π (L2, C1/C2, C3 22 µF) ─ U2 LMR36006 ─ 3V3 ──▶ │ BLE 5 / Wi-Fi (on-module antenna, far end) │
 pin4/5 ─ GND                                                                     │ ULP-RISC-V: VBAT wake in deep sleep        │
 pin7 K-line ─ R13 47 Ω ─ Q1 2N7002K (TX) ◀─────────────────── GPIO17 (UART1 TX, inverted)                       │
            └─ 1 M/150 k ─ U4 LMV331 (RX, powered by AUX_PWR) ─▶ GPIO18 (UART1 RX)                               │
 VBAT_PROT ─ 1 M/150 k ─▶ GPIO1 (ADC, ULP)   VBAT ─ 100 nF AC path ─▶ GPIO2 (ripple)                             │
 U3 LIS2DW12 ── I2C GPIO8/9, INT1 ─▶ GPIO7 (ext0 wake)    Y1 32.768 kHz ── GPIO15/16                           │
 USB-C (GPIO19/20) + USBLC6   ·   LED R/G GPIO38/39   ·   PAIR GPIO0   ·   AUX_PWR GPIO21                      │
                                                                                 └────────────────────────────────┘
```

Pin map: `hardware/pinmap-revB.csv`. BOM: `hardware/bom-revB.csv`. Firmware `board.h` matches both.

## 3. Firmware changes (done in `firmware/`)

- `board.h`: rev B pins; `HW_REVISION 'B'`, FW 1.1.0.
- `partitions.csv`: 8 MB layout. Two 1.75 MB OTA slots, a 4 MB encrypted `log` partition (subtype 0x40), 320 KB `cfg`.
- `logger.c`: same 128-byte CRC'd ring records, now on the internal `log` partition through `esp_partition_*`. Flash writes briefly
  stall the instruction cache, about 1 ms per record at 1 Hz. The K-line and BLE are buffered by hardware, so this is harmless.
- `sensors.c`: LIS2DW12 only (100 Hz ±16 g for harsh/crash events; 12.5 Hz low-power wake-up for sleep). RTC uses
  `gettimeofday`/`settimeofday`, which keeps counting through deep sleep on the 32 kHz crystal (`CONFIG_RTC_CLK_SRC_EXT_CRYS`).
  `sensors_power()` now switches `AUX_PWR`.
- GNSS driver moved to `firmware/optional/` and is not built. `REC_PARK` is no longer written. The app records the parking
  location from the phone at engine-off/disconnect.
- `CMD_AUX_OUT` answers `RS_UNSUPPORTED`. The post-park awake window drops from 30 min to 5 min (no CO₂ occupant watch).
- **LiveV1 is unchanged.** `lat/lon`, `gnss_speed`, `heading`, `co2`, `cabin_*` carry their sentinel "invalid" values. Vehicle speed
  comes only from the ECU (PID 0x0D). The app can fill phone GPS speed when the ECU doesn't supply speed.

## 4. Electrical design and calculations

### 4.1 Supply path
2 A NANO2 fuse (F1) → SMCJ33A (D1) → STPS2H100A (D2) → π-filter (600 Ω ferrite L2, 2 × 2.2 µF 100 V, 22 µF 63 V electrolytic)
→ LMR36006-Q1 → 3.3 V.

### 4.2 TVS choice: SMCJ**33**A, not 26 A (flagged change from the brief)
A 1,500 W SMC-package TVS can't absorb a load-dump event, which carries tens to hundreds of joules. So the right strategy for a small
part is **don't conduct during a suppressed load dump at all**:

| Part | VWM | VBR min | VC @ IPP | Conducts at the 35 V suppressed load dump (ISO 16750-2 Test B)? |
|------|-----|---------|----------|------------------------------------------------------|
| SMCJ26A | 26 V | 28.9 V | 42.1 V | **Yes**: it would try to clamp 35 V for up to 400 ms and fail short, then F1 opens |
| **SMCJ33A** | 33 V | 36.7 V | 53.3 V @ 28 A | **No**: the board rides through at 35 V (everything downstream is rated ≥ 60 V) |

Downstream ratings: LMR36006 60 V operating; Q1 2N7002K 60 V; STPS2H100A 100 V; input MLCCs 100 V; C3 63 V. At the 53.3 V
worst-case clamp, the minimum margin is 7 V on the buck. ISO 7637-2 pulse 2a (+112 V, 2 Ω, 50 µs) peaks around 30 A in D1 for
microseconds, which is within the 10/1000 µs rating. Pulse 1 (−150 V) forward-biases D1, D2 blocks, and F1 sees I²t ≈ 0.45 A²s,
below the NANO2 2 A melting I²t. Jump start at 24 V is below VWM.

**Honest trade-off vs rev A.** Rev A's SM8S26A (6.6 kW) could absorb ISO 7637 energy with far more margin. In rev B, an **unsuppressed**
load dump (alternator disconnected under load, Test A) above about 37 V will make D1 conduct beyond its energy rating. Expected
outcome: D1 fails short and F1 opens, so the dongle is dead and has to be repaired. The car is not affected. This is acceptable for a
₹3k plug-in part, and the car's own ECUs see the same event. Rev A's protection remains documented if you want it back. The SM8S
footprint (DO-218) doesn't fit a dongle.

### 4.3 Buck (LMR36006-Q1, 400 kHz variant, VFB = 1.0 V)
| Item | Value |
|------|-------|
| Output | R_T 100 k / R_B 43.2 k → 3.315 V |
| Inductor | ΔI = 3.3 × (14 − 3.3) / (14 × 22 µH × 400 kHz) = 0.29 A at 14 V; 0.35 A at 42 V. Peak 0.6 + 0.18 = 0.78 A → **22 µH, Isat ≥ 1.2 A** |
| Output cap | 2 × 22 µF X7R: ΔV = 0.35 / (8 × 400 k × 44 µ) ≈ 2.5 mV |
| Load | BLE active ≈ 60 mA avg, 130 mA peak; Wi-Fi OTA 340 mA peak → within 0.6 A |
| Quiescent | ≈ 25 µA (datasheet typ., PFM). Confirm on your orderable part |

⚠ Verify the exact orderable suffix (frequency option, package) and VFB on TI's site before layout.

### 4.4 Sleep-current budget (12.6 V battery)
| Contributor | Current |
|-------------|---------|
| LMR36006 Iq | ≈ 25 µA |
| VBAT divider 1 M + 150 k | 11 µA |
| K-line receive dividers (2 × ≈ 1.07 M) | 22 µA |
| ESP32-S3 deep sleep + ULP 1 Hz + 32 kHz crystal + LIS2DW12 LP (≈ 15 µA at 3.3 V, referred to 12.6 V at η ≈ 0.75) | ≈ 5 µA |
| LMV331 + ripple bias | 0 (AUX_PWR low). The comparator inputs back-feed < 2 µA through its 1 M resistors into the unpowered part. Measure this |
| D1 leakage at 12.6 V (spec 1 µA at 33 V) | < 1 µA |
| **Total** | **≈ 65 µA design** (acceptance ≤ 150 µA) → ≈ 47 mAh/month |

### 4.5 RTC crystal
32.768 kHz, 7 pF CL (e.g., Abracon ABS07). Load caps C = 2 × (CL − C_stray) ≈ 2 × (7 − 2) = 10 pF C0G each. Route the crystal
< 5 mm from GPIO15/16 with a ground guard ring and no traces under it.

## 5. PCB (2-layer, 38 × 46 mm, LionCircuits)

- Stack: 1.6 mm FR-4, 1 oz both sides, ENIG, black mask. Bottom layer is almost solid GND, with only short jumpers.
- **Plug edge (x = 0):** a row of 6 solder pads for the leads from the Molex 68503 plug (pitch to match the lead spacing), placed
  right behind the housing. Fuse, TVS, Schottky and K-line protection sit within 10 mm of pads 16/4/5/7. Return current goes straight into the bottom GND pour.
- **Middle:** buck (hot loop < 15 mm²), comparator, dividers, LIS2DW12 near the centroid, USB-C on the +Y edge at x ≈ 20 mm, LED and
  PAIR switch at x ≈ 33 mm (under the shell's light pipe and pinhole).
- **Far end (x ≈ 38–46 mm): ESP32-S3-MINI-1 with its antenna end flush to the board edge**, no copper on either layer in the antenna
  keep-out (per the Espressif MINI-1 datasheet), and no metal (screws, USB shell) within 10 mm.
- **Mechanical features:** 4 edge notches, 5.6 × 1.4 mm, at 13 mm and 31.8 mm from the plug edge on both long sides (snap hooks
  pass through). Two 3 × 3 mm copper-free "RIB" pads for the shell's hold-down posts. No mounting holes needed.
- Test pads (bottom): VBAT_PROT, 3V3, K, K_TXD, K_RXD, VBAT_SENSE, AUX_PWR, U0TXD/RXD, EN, IO0, GND. A TC2030 footprint is optional.

**OBD plug sourcing.** Use the **Molex 68503-1602** OBD-II plug housing (16 positions, 4 mm pitch) with **Molex 57964-9702** crimp pins,
both stocked at element14/Newark/DigiKey, so the whole BOM stays turnkey. Only six cavities are populated (4, 5, 6, 7, 14, 16). Each pin is
crimped onto a 20 mm 22 AWG lead soldered to a pad row on the PCB edge, and the two shell halves clamp the housing flange, so the leads
carry no load. Fallback: a generic straddle-mount PCB plug (LCSC/AliExpress, consigned), which needs a different footprint.

## 6. Mechanical: dongle shell (`mechanical/dongle.scad`)

### 6.1 Geometry (defaults; edit the "MEASURE YOUR PARTS" block first)
| Item | Value |
|------|-------|
| Shell, plug zone | 45 × 23 mm cross-section (flange captured between the halves) |
| Shell, PCB zone | 41.6 × 13.6 mm cross-section, thin-wall 1.5 mm |
| Length | 53 mm shell + 16 mm plug shroud outside → **≈ 69 mm protrusion from the socket face** |
| Split | On the PCB bottom plane. The plug flange is clamped between the halves, so insertion and removal force goes through the plastic, not the solder joints |
| Retention | 4 snap hooks (bottom half, through the PCB notches) into windows in the top half. No screws |
| Features | Ø3.1 light-pipe bore, Ø1.6 PAIR pinhole, USB-C side slot, zip-tie/lanyard eye under the far end |
| Mass (ASA shell ≈ 9 g, PCBA ≈ 12 g, plug ≈ 6 g) | ≈ 27 g |

### 6.2 Load on the socket
The centre of mass sits about 30 mm out from the socket face, giving 0.027 kg × 9.81 × 0.030 m ≈ 8 mN·m static. At 5 g pothole
shocks it is about 40 mN·m. That is well within what OBD sockets take from commercial dongles (which are often heavier, 40–60 g).
Still, for Indian roads: **tie the dongle's eye to the socket bracket or a nearby harness** with a soft cable tie. That stops
fretting of the plug contacts over years, and stops glovebox contents from knocking it out.

### 6.3 Location: verify on your car before ordering
I could not confirm from service data where the diagnostic socket is on the RHD reference car (it varies by model). You think it's in the glovebox.
Before finalising the length parameters:
1. Find the socket and photograph it. Measure the free depth in front of it (need ≥ 75 mm), and whether the glovebox lid closes
   and the box empties without hitting the dongle.
2. Check pin 16 is permanent +12 V with the key out (doc 11 §3).
3. **If clearance is short:** use a low-profile right-angle OBD extension (flat ribbon, 20–30 cm, ≈ ₹300–500). Plug the dongle into it
   and cable-tie the dongle flat against the glovebox side or the dash structure. Same electronics, no redesign.

### 6.4 Thermal
A closed glovebox in a car parked in the sun reaches about 60–70 °C, below the dash top's 85–95 °C. Ratings: ESP32-S3-MINI-1 85 °C,
LIS2DW12 85 °C, electrolytic 105 °C, ASA HDT ≈ 95 °C. Dissipation is 0.2 W driving (no Wi-Fi), so self-heating is below 5 °C. The
firmware's die-temperature policy (doc 05 §4.7) still applies. **Don't print the shell in PLA or PETG.**

### 6.5 RF
The antenna end of the module sits at the far end of the dongle, which is the most open end, pointing into the cabin or glovebox
volume. The glovebox is plastic, so expect −60 to −75 dBm at the driver's seat. If the socket is buried behind steel, the extension
cable option in §6.3 also lets you move the antenna into the open.

### 6.6 3D printing workflow
| Step | Setting |
|------|---------|
| Export | `openscad -D 'part="bottom"' -o bottom.stl mechanical/dongle.scad`, then `top`, `pipe` |
| Material | **ASA** (or PC-blend). For batches: HP MJF PA12, which gives crisp snap hooks |
| Orientation | Bottom: floor down. Top: outer face down (the export already flips it). Pipe: upright, clear PETG or a Bivar light pipe |
| Layers | 0.4 mm nozzle, **0.16 mm layers** (thin walls and snap barbs), 3–4 perimeters, so the 1.5 mm walls print solid |
| Supports | Only under the plug-flange recess and USB slot bridge, if your printer can't bridge 10 mm |
| Snap fit | Print one test pair first. If hooks are too stiff, reduce `hook_t` to 0.8. If they're loose, increase `barb` to 0.8 |
| Fit check | The plug shroud passes the front window with ≤ 0.3 mm play. The flange is clamped without rattle. The PCB doesn't flex when snapped |

## 7. Install (replaces doc 11 §2–5)
1. Program and pair on the bench (USB-C power is fine for BLE testing. The K-line needs 12 V on the plug).
2. Key out: plug the dongle into the socket and tie it off (§6.2).
3. Start the engine: LED goes green, and the phone pairs with PAIR pressed (doc 11 §8 commissioning steps 2–7, 10–12 still apply.
   Skip the GNSS and taps steps).

## 8. Cost roll-up (indicative INR, excl. GST; details in `hardware/bom-revB.csv`)
| Item | 1 unit | per unit @ 25 |
|------|--------|---------------|
| Components (incl. Molex OBD plug + pins) | 1,570 | 1,170 |
| Attrition / MOQ overage | 140 | 50 |
| PCB, 2-layer 46 × 38 mm | 200 (5-pc lot) | 60 |
| Turnkey assembly | 1,500 (setup dominated) | 300 |
| Shell, light pipe, tie | 120 | 80 |
| **Total** | **≈ ₹3,530** | **≈ ₹1,660** |

The biggest lever for single units is assembly setup. If you hand-solder at home (you have the gear), a single unit is about
₹2,090 including the PCB.

## 9. Verification deltas (vs doc 12)
Delete DV-14 (GNSS) and the cabin-sensor checks. Re-run DV-01–06 with the rev B protection: **DV-06 must show no conduction in D1 at
35 V** (Test B). Run DV-08 sleep current against the ≈ 65 µA design and ≤ 150 µA acceptance. Add **DV-31, retention**: 1,000 plug
cycles on a spare socket, and 8 h vibration on a socket fixture with the zip tie fitted. Contacts should show < 20 mΩ change.

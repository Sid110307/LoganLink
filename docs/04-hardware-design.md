# 04 — Electronic Hardware Design

All part numbers are current production parts as of this design. "Q1/AEC" marks automotive-qualified
parts. Before ordering, confirm the **exact orderable suffix** (package, reel, temperature grade) on the
manufacturer's site. Some suffixes are flagged ⚠ below because the family is certain but the suffix
should be checked.

## 1. Component selection summary

| Ref | Function | Part | Key specs | Package | Why this part |
|-----|----------|------|-----------|---------|---------------|
| U1 | SoC + radio | **Espressif ESP32-S3-WROOM-1-N16R2** | Dual Xtensa LX7 240 MHz, 512 KB SRAM, 16 MB flash, 2 MB quad PSRAM, BLE 5 + Wi-Fi 4, TWAI, ULP-RISC-V, Secure Boot v2, flash encryption, **−40…+85 °C** | 18 × 25.5 mm module, PCB antenna | Pre-certified RF (FCC/CE/**WPC-India via ETA as module**); the -N16**R8** octal-PSRAM variant is only rated to 65 °C, so it is avoided |
| U2 | Buck 3.3 V | **TI LMR36015-Q1** (adjustable, 400 kHz) ⚠ suffix | 4.2–60 V in, 1.5 A, ~24 µA Iq PFM, AEC-Q100 | VQFN-HR 2×3 mm | 60 V rating gives margin over the 42 V TVS clamp; low Iq for parking |
| U3, U4 | Load switches | **TI TPS22918-Q1** | 1–5.5 V, 2 A, 52 mΩ, adjustable rise time, QOD | SOT-23-6 | Cut sensor rail and GNSS rail to 0 in sleep |
| U5 | GNSS | **u-blox MAX-M10S** | GPS/GAL/BDS/GLONASS/QZSS (+NavIC-ready L1 constellations per FW), −167 dBm tracking, ~25 mW | 9.7 × 10.1 LCC | Low power, concurrent 4-GNSS for urban India |
| U6 | 6-axis IMU | **ST ASM330LHHX** | ±2…16 g, ±125…4000 dps, AEC-Q100, 105 °C, FIFO 3 KB | LGA-14 2.5×3 | Automotive-grade, low-noise, crash range |
| U7 | Wake accelerometer | **ST AIS2DW12** | Ultra-low-power (~1 µA @ 1.6 Hz LP), wake-up interrupt, AEC-Q100 | LGA-12 2×2 | Parked motion detection on always-on rail |
| U8 | RTC | **Micro Crystal RV-3028-C7** | 45 nA, ±1 ppm (25 °C), I²C, EEPROM, backup switch | 3.2 × 1.5 C7 | Timekeeping in sleep without coin cell |
| U9 | Temp/RH | **Sensirion SHT45** | ±0.1 °C, ±1 %RH | DFN-4 1.5×1.5 | Cabin climate |
| U10 | VOC/NOx | **Sensirion SGP41** | VOC & NOx index | DFN-6 2.44×2.44 | Air quality |
| U11 | CO₂ | **Sensirion SCD41** | 400–5000 ppm, ±(40 ppm + 5 %), photoacoustic | 10.1×10.1×6.5 | Cabin CO₂ / occupant detection |
| U12 | NOR flash | **Winbond W25Q256JVEIQ** (or -JVEIM) | 32 MB, 100 k cycles, −40…+85 °C | WSON-8 6×5 | Dedicated log store, isolated from code flash |
| U13 | CAN PHY | **TI SN65HVD230QDRQ1** | 3.3 V, 1 Mbit/s, AEC-Q100 | SOIC-8 | 3.3 V-only supply, no 5 V rail needed |
| U14 | K-line RX comparator | **TI LMV331-Q1** (LMV331QDCKRQ1 ⚠) | 2.7–5.5 V, open-drain, 1 µs | SC-70-5 | See §4 for why the K-line PHY is discrete |
| Q1 | K-line driver | **Diodes 2N7002KQ-7** | 60 V, 300 mA, ESD-protected gate, AEC-Q101 | SOT-23 | Open-drain K-line driver |
| U15 | AUX low-side switch | **ST VNLD5090-E** (dual OMNIFET III) | 41 V clamp, current limit, thermal shutdown, 90 mΩ | SO-8 | Short-circuit-proof accessory output |
| D1 | Input TVS | **Vishay SM8S26AHE3** (AEC) | VWM 26 V, VBR 28.9 V min, VC 42.1 V @ IPP, 6.6 kW (10/1000 µs), load-dump-rated | DO-218AB | Absorbs ISO 7637-2 pulses and suppressed load dump |
| D2 | Reverse blocking | **ST STPS3H100U** | 100 V, 3 A Schottky, VF ≈ 0.55 V @ 1 A, 175 °C Tj | SMB | Zero quiescent current (better than an ideal-diode controller for parked current) |
| D3,D4 | ESD, lines | **Nexperia PESD1LIN** (K-line), **onsemi NUP2105L** (CAN) | IEC 61000-4-2 ±15/±30 kV | SOD-323 / SOT-23 | Bus-line ESD |
| D5 | USB ESD | **ST USBLC6-2SC6** | ±15 kV | SOT-23-6 | USB-C data lines |
| L1 | Buck inductor | **Coilcraft XAL6060-153MEB** (AEC-Q200) | 15 µH, Isat ≥ 3 A, DCR ~50 mΩ | 6.5×6.5 | §3.2 |
| L2 | Input filter | **Würth 74438357100** WE-MAPI ⚠ (or any 10 µH ≥ 1.5 A shielded AEC-Q200) | 10 µH | 4030 | π-filter conducted EMI |
| C_in bulk | Hybrid polymer | **Panasonic EEH-ZA1J470P** | 47 µF 63 V, 125 °C, AEC-Q200 | SMD 6.3×7.7 | Low ESR, hold-up |
| J1 | Vehicle connector | **Molex Micro-Fit 3.0** RA dual-row 10-ckt header 43045-1000 ⚠ (mating 43025-1000 + 43030-0007 crimps) | 5 A/contact, 105 °C, positive latch | THT | Vibration-proof latch, keyed |
| J2 | USB-C | **GCT USB4105-GF-A** | USB 2.0, 16-pin | SMD/THT | Programming/console |
| J3,J4 | RF | **Hirose U.FL-R-SMT-1(10)** | 50 Ω | — | GNSS and LTE antennas |
| J5 | Debug | **Tag-Connect TC2030-IDC-NL** footprint | UART0 + EN + IO0 + 3V3 + GND | pads only | Production programming, zero BOM cost |
| SW1 | Pair | **C&K PTS526 SM15 SMTR2 LFS** ⚠ | 6.2 mm tact, sealed | SMD | Recessed pinhole access |
| LED1 | Status | **Kingbright APHB1608LZGKSURKC** ⚠ (bi-colour R/G 0603) | — | 0603 | Red/green/amber |
| *Variant C only* ||||||
| U16 | LTE Cat-1 | **SIMCom A7672S** | LTE-FDD B1/3/5/8, TDD B38/40/41, GSM fallback; India bands | LCC 24×24 | Indian bands, AT-command UART, low cost |
| U17 | LTE buck 3.8 V | **TI TPS54360B-Q1** | 4.5–60 V, 3.5 A, EN shutdown 2 µA | SO-8 PowerPAD | Handles 2 A GSM bursts |
| J6 | Nano-SIM | **Amphenol C707 10M006 512 2** ⚠ | Push-push | — | — |

## 2. Power architecture and input protection

### 2.1 Schematic (power entry)

```
 J1.1 VBAT_IN ──[F: 3 A ATO in harness]──┬───────────────┬──── D2 STPS3H100U ─►|── VBAT_PROT ──┬── L2 10µH ──┬── VIN_BUCK ─► U2 LMR36015 ─► 3V3_AON
                                          │               │                                    │             │                 │
                                     D1 SM8S26A      C1 4.7µF/100V                     C2 4.7µF/100V   C3 47µF/63V hybrid   │
                                     (cathode up)      X7S 1210                           X7S 1210      + C4 4.7µF/100V      ├─ U3 TPS22918 ─► 3V3_SW  (IMU, cabin sensors, CAN PHY, flash, K-RX comp, ripple bias)
                                          │               │                                    │             │                 └─ U4 TPS22918 ─► 3V3_GNSS
 J1.2 GND ───────────────────────────────┴───────────────┴────────────────────────────────────┴─────────────┴──── GND plane
                                                                     VBAT_PROT ──► VBAT_SENSE divider, K-line pull-up, VNLD5090 supply sense, [C] U17
```

### 2.2 Transient environment and response

| Disturbance (12 V system) | Standard | Level | Protection behaviour | Functional status target |
|---------------------------|----------|-------|----------------------|--------------------------|
| Pulse 1 (inductive disconnect) | ISO 7637-2 | −150 V, Ri 10 Ω, 2 ms | D1 forward-conducts (≈ −1.5 V), D2 blocks; ≤ 15 A for 2 ms. Fuse I²t 0.45 A²s ≪ 3 A ATO melt I²t | A (no reset; hold-up from C3) |
| Pulse 2a | ISO 7637-2 | +112 V, Ri 2 Ω, 50 µs | D1 clamps ~36 V at ~38 A | A |
| Pulse 2b | ISO 7637-2 | +10 V, 0.2–2 s | Within 60 V buck rating | A |
| Pulses 3a/3b | ISO 7637-2 | ±220 V, 50 Ω, 0.1 µs bursts | D1 + C1/C2 | A |
| Suppressed load dump | ISO 16750-2 Test B | Us* = 35 V | D1 conducts lightly (VBR 28.9 V); buck input ≤ 36 V | A |
| Unsuppressed load dump | ISO 16750-2 Test A | Us 79–101 V, Ri 0.5–4 Ω, td 40–400 ms | Energy (up to several hundred J at Ri = 0.5 Ω) exceeds any single SMD TVS. D1 clamps, and **the 3 A fuse is the designed sacrificial element.** At Ri ≥ 2 Ω, Us = 79 V, td = 40 ms: I ≈ 20 A peak, E ≈ 15 J < SM8S rating → survives without fuse loss | C (Ri ≥ 2 Ω) / E-fuse (worst case) |
| Jump start | ISO 16750-2 | 24 V, 60 s | Below VWM 26 V; buck fine; parts rated ≥ 40 V | A |
| Reverse battery | ISO 16750-2 | −14 V, 60 s | D2 blocks (100 V); D1 forward-conducts → **fuse opens within ms** (by design: fuse + TVS crowbar). Board unharmed | C (replace fuse) |
| Cranking | ISO 16750-2 starting profile | Down to 4.5 V (6 V level II) | Buck dropout ~0.3 V at 150 mA, D2 0.45 V → 3V3 holds to VIN ≈ 4.1 V. ESP32 BOD at 2.8 V. C3 holds 15 ms at 150 mA from 6 V → 4.2 V | A down to 6 V; B (reset OK) below |
| ESD | ISO 10605 | ±8 kV contact / ±15 kV air at connector | D3/D4/D5 + 100 nF line caps | A |

**Hold-up calculation (C3 + ceramics ≈ 56 µF):** the buck draws P_in = 3.3 V × 0.15 A / 0.85 ≈ 0.58 W. The energy
available between 6.0 V and the 4.2 V dropout point is ½·C·(6.0² − 4.2²) = ½ × 56 µF × 18.36 V² ≈ 0.51 mJ.
So t ≈ 0.51 mJ / 0.58 W ≈ **0.9 ms**. That is too short to ride through a 15 ms dropout,
so the firmware design accepts a reset for dips below 4.2 V. A dip to ~6 V (most real cranks on a healthy battery) is
fully ridden through by the buck itself. The crank-capture log entry is written to RTC RAM first and committed after
the dip, so it survives a reset (doc 07 §5).

### 2.3 Buck converter design (U2 LMR36015-Q1, 400 kHz)

| Parameter | Calculation | Value |
|-----------|-------------|-------|
| Output | VOUT = VFB·(1 + R_T/R_B), VFB = 1.0 V | R_T = 100 kΩ, R_B = 43.2 kΩ (0.1 %) → **3.315 V** |
| Inductor ripple @ 14 V | ΔI = VOUT(VIN−VOUT)/(VIN·L·f) = 3.3·10.7/(14·15µ·400k) | 0.42 A (28 % of 1.5 A) |
| Inductor ripple @ 42 V (clamp) | 3.3·38.7/(42·15µ·400k) | 0.51 A |
| Inductor peak | 1.5 A + 0.26 A | 1.76 A → XAL6060-153 Isat ≥ 3 A ✓ (derated 70 %: 2.1 A) |
| Output cap | ΔV = ΔI/(8·f·C) = 0.51/(8·400k·44µ) | 3.6 mV + ESR → 2 × 22 µF 10 V X7R 1206 |
| Input ripple current | I_rms ≈ IOUT·√(D(1−D)), D = 0.24 | 0.64 A → 2 × 4.7 µF X7S + hybrid share |
| Efficiency | Datasheet curves: ~86 % @ 150 mA, ~80 % @ 10 mA (PFM) | Used in power budget |
| Bootstrap | 100 nF 16 V X7R | per datasheet |
| EN | Tie to VIN via 100 k (always on); UVLO internal 3.9 V | — |
| Thermal | P_loss ≈ 0.5 W·(1/0.86 − 1) ≈ 0.08 W | ΔT negligible; 4 thermal vias under pad |

### 2.4 Power budget (B variant)

| Load | DRIVE avg (mA @3.3 V) | DRIVE peak | SLEEP (µA @3.3 V) |
|------|----------------------|------------|-------------------|
| ESP32-S3 (BLE active, 160 MHz, Wi-Fi off) | 55 | 130 (BLE TX) / 340 (Wi-Fi OTA only) | 10 (deep sleep + ULP @ 1 Hz ADC) |
| MAX-M10S + active antenna | 8 + 10 | 30 | 30 (V_BCKP, optional jumper R52) |
| ASM330LHHX (high-perf 104 Hz) | 0.8 | — | 0 (rail off) |
| AIS2DW12 (LP 12.5 Hz wake) | 0.004 | — | 1 |
| SCD41 periodic (5 s) | 15 | 205 | 0 |
| SGP41 | 3.2 | 4 | 0 |
| SHT45 | 0.01 | 1 | 0 |
| W25Q256 | 1 (avg) | 25 (program) | 0 |
| SN65HVD230 recessive | 10 | — | 0 |
| K-line RX comparator + pull-up drive | 0.3 | 24 (line low) | 0 |
| RV-3028 | — | — | 0.05 |
| LED | 2 | 5 | 0 |
| **Total 3.3 V** | **≈ 105 mA** | **≈ 450 mA** | **≈ 41 µA (11 µA without GNSS backup)** |
| Input @ 13.8 V (η 86 %) | **29 mA** (+ dividers) ⇒ **≈ 0.4 W** | — | — |
| Direct VBAT loads in sleep | — | — | VBAT_SENSE 11 µA, K-RX dividers 22 µA, buck Iq 24 µA, D1 leakage ≤ 5 µA |
| **Sleep at battery (12.6 V)** | | | 41 µA × 3.3/12.6/0.8 ≈ 13 µA + 62 µA ⇒ **≈ 75 µA** (≈ 55 µA w/o GNSS backup) |

Battery impact: 75 µA × 720 h = **54 mAh/month**. A 35 Ah battery loses about 0.15 %/month from the module.
Lead-acid self-discharge is about 3–5 %/month.

## 3. Pin map (ESP32-S3-WROOM-1-N16R2)

Strapping pins (0, 3, 45, 46) carry only signals whose default state is safe at reset. GPIO 26–32 are taken by the
internal flash. Full table: `hardware/pinmap.csv`.

| GPIO | Net | Dir | Notes |
|------|-----|-----|-------|
| 0 | BTN_PAIR | In | Strap: 10 k pull-up, button to GND (also enters download mode at reset) |
| 1 | VBAT_SENSE | ADC1_CH0 | ULP-readable; 1 M / 150 k + 10 nF |
| 2 | VBAT_AC | ADC1_CH1 | AC-coupled ripple path |
| 3 | AIN_FUEL | ADC1_CH2 | Strap (JTAG sel, inert by default eFuse); analog only |
| 4 | CAN_TXD | Out | TWAI TX |
| 5 | CAN_RXD | In | TWAI RX |
| 6 | AIN_SPARE (coolant NTC) | ADC1_CH5 | 10 k NTC half-bridge from 3V3_SW |
| 7 | ACC_WAKE_INT | In, RTC | AIS2DW12 INT1 → ext0 wake |
| 8 | REV_SENSE_N | In, RTC | Optional reverse-lamp tap via buffer |
| 9 | IMU_INT1 | In | ASM330 FIFO watermark |
| 10–13 | FLASH_CS/MOSI/SCK/MISO | SPI2 | W25Q256, 40 MHz; CS 10 k pull-up to 3V3_SW |
| 14 | IGN_SENSE_N | In, RTC | Optional ignition tap via buffer → ext1 wake |
| 15 | GNSS_RXD | Out | UART2 TX → MAX-M10S RXD |
| 16 | GNSS_TXD | In | UART2 RX ← MAX-M10S TXD |
| 17 | K_TXD | Out | UART1 TX, **inverted in UART HW** (drives Q1 gate) |
| 18 | K_RXD | In | UART1 RX from U14 |
| 19/20 | USB_D−/D+ | USB | Native USB-Serial-JTAG |
| 21 | GNSS_EN | Out | U4 ON; 100 k pull-down |
| 35 | SPARE | — | (free because R2 quad-PSRAM variant is used) |
| 36 | LTE_PWRKEY | Out | Variant C, via NPN |
| 37 | LTE_STATUS | In | Variant C |
| 38/39 | LED_R / LED_G | Out | 330 Ω / 150 Ω |
| 40 | AUX_OUT_EN | Out | VNLD5090 IN1, 100 k pull-down |
| 41/42 | I2C0_SDA/SCL (always-on) | I²C | RV-3028, AIS2DW12; 4.7 k to 3V3_AON |
| 43/44 | U0TXD/U0RXD | UART0 | Tag-Connect console; Variant C: LTE UART |
| 45 | LTE_EN | Out | Strap (VDD_SPI): **must be 0 at reset**; 100 k pull-down matches |
| 46 | SENS_EN | Out | Strap: **must be 0 at reset**; 100 k pull-down → sensors off at boot ✓ |
| 47/48 | I2C1_SDA/SCL (switched) | I²C | IMU, SHT45, SGP41, SCD41; 2.2 k to 3V3_SW |

I²C is split into an always-on bus and a switched bus. If the bus pull-ups were on the always-on rail,
current would flow into an unpowered sensor's ESD diodes and partly power it. That leaks current and can
latch the device.

## 4. Vehicle interface circuits

### 4.1 K-line PHY (discrete)

LIN transceivers (TJA1021, MCP2003B, etc.) have a **TXD-dominant timeout** (typically 6–20 ms). It ends the
25 ms TiniL fast-init pulse early, so they can't do ISO 14230 fast init reliably. The L9637D is the classic
K-line chip, but it needs 5 V logic. The discrete PHY below is fully characterised and has no hidden timeouts:

```
 VBAT_PROT ──[R10 510 Ω 0.5 W 1206]──►|D10 BAS16──┬────────────[R13 47 Ω]──┬── J1.7  K-LINE ── OBD pin 7
                                                   │                         │
                         K_TXD (GPIO17, inverted) ─┤                    D3 PESD1LIN ── GND
                                    [R11 100 Ω]──G Q1 2N7002KQ             │
                                    [R12 100 k]─GND  S─GND   D─────────────┘(drain to K node)
 K node ──[R14 1 M]──┬── U14 IN+   (ratio 150k/1.15M = 0.1304)
               [R15 150 k]
                    GND
 VBAT_PROT ─[R16 1 M]─┬── U14 IN−  (ratio 68k/1.068M = 0.0637  → threshold = 0.0637/0.1304 = 0.488·VBAT)
               [R17 68 k]
                    GND
 U14 OUT (open-drain) ──[10 k to 3V3_SW]── K_RXD (GPIO18);  hysteresis R18 4.7 M OUT→IN+  (≈ ±3 % VBAT)
 U14 VCC = 3V3_SW;  C 100 nF
```

- Driver: Q1 sinks (VBAT − 0.6)/510 + ECU pull-up current ≈ 25–45 mA. Rds(on) ≤ 3 Ω → VOL < 0.15 V ✓ (ISO 14230 requires < 0.2·VB).
- Edge rate: R11 gate resistor 100 Ω plus line capacitance about 1.3 nF × 510 Ω gives a 0.66 µs rise, ≪ 96 µs bit time ✓.
- Comparator inputs stay below 2.1 V at VB = 16 V. That is within the LMV331 common-mode range at 3.3 V (VCC − 1.0 V) ✓.
- Fault cases: K shorted to VBAT → Q1 current limited by R13 (47 Ω) to ~0.3 A for one bit, and firmware detects echo mismatch and stops.
  K shorted to GND → R10 dissipates 0.29 W continuous (0.5 W part) ✓. Firmware sees a stuck-dominant line and raises an alarm.
- The ECU receives our own transmitted bytes back as an echo. The firmware reads and checks each echo.

### 4.2 CAN (fitted for portability; inactive on the reference car)

SN65HVD230Q with RS → GND (high-speed). Split termination **not fitted** (vehicle bus is already terminated). ESD
NUP2105L. 0 Ω jumpers R20/R21 in CANH/CANL, so they can be removed if the pins are used for something else.

### 4.3 VBAT measurement

- **DC path:** R30 1 M (0.1 %, 25 ppm, 0805 thin-film, 150 V rated) / R31 150 k (0.1 %) + C30 10 nF C0G.
  Ratio 0.13043. Range 0–22.2 V for ADC full-scale 2.9 V (12 dB atten.). 1 LSB ≈ 5.4 mV after averaging 16 samples.
  Source impedance 130 kΩ → τ = 1.3 ms (fine for 1 kS/s crank capture). Sampling into the ADC's ~2 pF sample cap from
  10 nF gives < 0.02 % droop ✓. Clamp: BAT54S to 3V3_AON/GND via 4.7 k series.
- **AC path (ripple):** C31 100 nF 100 V X7R from VBAT_PROT → R32 4.7 k → node biased at 1.65 V by R33/R34 100 k/100 k from
  3V3_SW. High-pass fc = 1/(2π·50 k·100 n) = **32 Hz**. Low-pass by R32 and C32 1 nF = **34 kHz** (anti-alias for 40 kS/s,
  combined with oversampling). Clamp BAT54S.
- Calibration: two-point (12.00 V and 15.00 V from a bench supply, DMM-verified). Gain/offset are stored in NVS. Target
  **±30 mV** over 9–16 V and −20…+70 °C.

### 4.4 Optional tap inputs (IGN, REV)

```
 TAP ─[F 0.5 A PTC in harness]─ J1.x ─[R 47 k]─┬─[R 47 k]─ GND
                                                ├─ D 12 V zener BZX84-C12 ─ GND
                                                └─ G Q (2N7002KQ)   D ─[100 k to 3V3_AON]─ IGN_SENSE_N
                                                                    S ─ GND
```
VGS = VTAP/2: 6 V at 12 V, 4.5 V at 9 V (cranking), clamped 12 V at a 24 V+ jump. Input current is 128 µA at 12 V, and only
while the tap is live. The output is active-low, and the 100 k pull-up draws current only while active.

### 4.5 Fuel sender sense

R40 100 k / R41 22 k + 100 nF, clamp BAT54S. The ratio 0.1803 maps 0–15 V to 0–2.7 V. The input impedance of 122 kΩ is
parallel to a sender of a few hundred ohms at most, so the gauge error is < 0.3 % ✓.

### 4.6 Accessory output

VNLD5090-E channel 1, low-side, input from GPIO40. Its 41 V active clamp handles relay coils and dashcam hardwire kits
without a flyback diode. Current limit is about 13 A typical, with thermal shutdown. The fuse in the harness is 2 A.
An optional 100 k/22 k divider (not fitted by default) on the output reads the drain voltage back on GPIO35 to detect
open-load or short faults.

## 5. GNSS front-end

- MAX-M10S RF_IN → 50 Ω coplanar waveguide (doc 05 §6) → U.FL J3. Feed the active antenna 3.3 V through **R50 22 Ω (1210)**
  + **L50 47 nH** (Murata LQW15AN47NJ00 ⚠) onto the RF line, with **C50 47 pF** DC-block on the module side.
  Short-circuit: 150 mA. Firmware reads `UBX-MON-RF` antenna status and turns GNSS_EN off if it reports SHORT.
- V_BCKP: from 3V3_AON through R51 1 k + jumper R52 (fit for hot start; current about 30 µA).
- Antenna: active GPS/GNSS patch, 3–5 V, ≥ 26 dB gain, magnetic or adhesive, RG174 cable, U.FL or SMA-to-U.FL pigtail.
  Mounted on the dash top under the windscreen (doc 11).
- Configuration (UBX-CFG-VALSET at boot): GPS + Galileo + BeiDou + GLONASS, NAV-PVT 5 Hz (DRIVE) / 1 Hz, dynamic model
  *Automotive* (4), UART kept at the 38,400 default (NAV-PVT 100 B × 5 Hz = 500 B/s, 13 % of link capacity).

## 6. Reset, supervision and programming

- ESP32-S3 EN: RC 10 k / 1 µF (τ = 10 ms after 3V3 stable), plus internal BOD at 2.8 V.
- Hardware watchdogs: RWDT and MWDT enabled. Task watchdog at 5 s (doc 07 §9).
- Programming: USB-C (native USB-Serial-JTAG, no bridge chip) for development and field use. Tag-Connect TC2030 for
  production (UART0 + EN + IO0).
- Test points: TP1 VBAT_PROT, TP2 VIN_BUCK, TP3 3V3_AON, TP4 3V3_SW, TP5 3V3_GNSS, TP6 K-line, TP7 K_TXD, TP8 K_RXD,
  TP9 VBAT_SENSE, TP10 GND (×3), TP11 CANH, TP12 CANL. All 1.0 mm pads on the bottom side for a bed-of-nails fixture.

## 7. Signal compatibility and timing check

| Interface | Requirement | Design | Margin |
|-----------|-------------|--------|--------|
| K-line VOL | < 0.2·VB = 2.4 V @12 V | 0.15 V | ✓ |
| K-line receive threshold | 0.3–0.7 VB window | 0.488·VB ± 3 % | ✓ |
| K-line bit time | 96.15 µs, ±2 % | ESP32 UART fractional divider: 80 MHz/10,400 = 7692.3 → error < 0.01 % | ✓ |
| Fast-init TiniL | 25 ± 1 ms | TX pin detached from UART and driven as GPIO; edges timed with `esp_timer_get_time()` busy-wait on Core 1 (no interrupts disabled); jitter < 50 µs | ✓ |
| I²C | 400 kHz, Cb < 400 pF | 2.2 k on 3V3_SW bus, ~60 pF | rise ≈ 0.85·RC = 0.11 µs < 0.3 µs ✓ |
| GNSS UART | 115.2 k | 1.5 % tolerance | ✓ |
| ADC | 12 dB atten. usable 0.1–2.9 V | VBAT_SENSE 0.8–2.2 V across 6–17 V | ✓ |

# 02. Target Vehicle Electrical/Electronic Architecture and Integration Design

> **Evidence level.** The reference car is a 2007–2010 Indian-market Renault X90 (Phase-1) sedan built at Nashik with the K7M 1.6.
> Other X90/K7M-family cars and most K-line OBD-II petrol cars of that era share this architecture. Facts below are marked: **[D]** documented in Renault/Dacia
> X90 service literature or component datasheets; **[C]** common across the platform but varies by
> market/trim, so confirm on the car; **[M]** must be measured (see README “Items to verify”).

## 1. Vehicle summary

| Item | Value |
|------|-------|
| Engine | Renault K7M, 1598 cc, SOHC 8-valve, multipoint sequential injection, about 90 PS **[D/C]** |
| Engine management | Speed-density (MAP-based, **no MAF sensor**) **[D]**. ECU supplier Siemens/VDO (EMS31xx/Sirius family) or Sagem (S2000/S3000 family) depending on build **[C/M → V5]** |
| Sensors to ECU | MAP + IAT (combined), coolant temperature, throttle position, crankshaft (TDC/flywheel) sensor, knock sensor, upstream O₂ (if catalysed calibration), vehicle-speed sensor **[D/C]** |
| Gearbox | JH3 5-speed manual; electronic vehicle-speed sensor on gearbox **[C]** |
| Ignition | Static ignition, double-ended coil pack (wasted spark) **[D]** |
| Body electronics | UCH (Unité Centrale Habitacle) controls central locking, interior light and immobiliser function on trims so equipped; immobiliser transponder in key **[C]** |
| Instrument cluster | Analog speedometer, fuel and temperature gauges; tachometer on higher trims; electronic speed input **[C]** |
| Networks | Phase-1 X90 engine ECU uses **K-line diagnostics** (no CAN powertrain bus) **[C → V1]** |
| Battery | 12 V lead-acid, typically 35–45 Ah in this segment **[M]** (read the label) |
| Alternator | Internal regulator, nominal 14.0–14.6 V; 75–90 A class **[C]** |
| Earthing | Battery − to body and engine block strap; ECU earths at engine/body studs **[D]** |

## 2. Electrical power distribution

```
 BATTERY + ──┬── Starter (direct, heavy cable)
             ├── Alternator B+ (charging)
             ├── Engine-bay fuse/relay box (maxi fuses → main feeds, ECU main relay, fan)
             └── Cabin fuse box (under dash, driver side)
                  ├── Permanent (+BAT): clock/radio memory, interior lamp, diagnostic socket pin 16 [C → V4]
                  ├── Ignition (+APC, "après contact"): cluster, ECU ignition feed, reverse lamp switch feed
                  └── Accessory (+ACC): radio, cigarette lighter / 12 V socket
 BATTERY − ──── body earth strap, engine-to-body strap
```

Renault labels: **+BAT** = permanent, **+APC** = ignition-switched, **+ACC** = accessory. LoganLink
takes its supply from **+BAT at OBD pin 16**, fused locally. It senses +APC through an optional
high-impedance input. It never takes load current from +APC circuits.

## 3. Diagnostic socket (SAE J1962 / ISO 15031-3)

Location **[C → V4]**: on the reference car the 16-pin socket is in the lower dashboard on the driver's
side, near the cabin fuse box, behind a small cover or open under the fascia. Before you
connect anything, confirm its position and the pin population with a multimeter (doc 11 §3).

| Pin | ISO function | Expected on the reference car | LoganLink use |
|-----|--------------|---------------------------|---------------|
| 4 | Chassis ground | Present | GND (power return) |
| 5 | Signal ground | Present | GND_SIG (joined to GND at module star point) |
| 6 | CAN-H (ISO 15765-4) | Usually **not populated** on Phase-1 **[C]** | CAN-H (harmless if absent) |
| 7 | K-line (ISO 9141-2 / 14230) | **Present**: engine ECU and other ECUs (airbag, ABS if fitted, UCH) share K-line with different addresses **[C]** | K-line |
| 14 | CAN-L | Usually not populated | CAN-L |
| 15 | L-line | Rarely used | Not connected (reserved) |
| 16 | +12 V permanent | Present **[C → V4]** | VBAT_IN |

### 3.1 K-line electrical characteristics (ISO 9141-2 / ISO 14230-1)

| Parameter | Value |
|-----------|-------|
| Topology | Single-wire, bidirectional, open-collector to ground, pulled up to VBAT |
| Tester pull-up | 510 Ω to VBAT (ISO 9141-2 tester) |
| Logic thresholds | Low ≤ 0.2·VBAT (recessive-to-dominant), high ≥ 0.7·VBAT (ISO 14230-1 receiver: VIL < 0.3·VB, VIH > 0.7·VB) |
| Bit rate | 10,400 bit/s, 8N1 |
| Bus capacitance | ≤ 1.3 nF typical installation |

### 3.2 Protocol timings (ISO 14230-2 / ISO 9141-2)

| Symbol | Meaning | Value used |
|--------|---------|------------|
| W5 | Bus idle before init | ≥ 300 ms |
| TiniL | Fast-init wake-up low pulse | 25 ms ± 1 ms |
| TWuP | Wake-up pattern total | 50 ms ± 1 ms |
| 5-baud init | Address byte at 5 bit/s (ISO 9141-2: 0x33) | 2.0 s |
| W1 | ECU sync (0x55) after address | 60–300 ms |
| W4 | Tester inverts KB2 after | 25–50 ms |
| P1 | ECU inter-byte | 0–20 ms |
| P2 | ECU response start | 25–50 ms (default), up to P2max per timing params |
| P3 | Tester new request after response | ≥ 55 ms (we use 60 ms) |
| P3max | Session timeout | 5,000 ms → TesterPresent (0x3E) every 2,000 ms when idle |
| P4 | Tester inter-byte | 5–20 ms (we use 5 ms) |

### 3.3 Message format (ISO 14230-2)

```
[FMT][TGT][SRC][LEN?][SID][DATA…][CS]
FMT = 0b10LL LLLL physical addressing (0x80 | len) or 0b11LL LLLL functional (0xC0 | len)
TGT = 0x33 functional OBD (SAE J1979) | ECU physical address (found by probe)
SRC = 0xF1 (external test equipment)
CS  = 8-bit sum of all preceding bytes, modulo 256
```
Example: fast-init StartCommunication `C1 33 F1 81 66`; positive response `83 F1 xx C1 KB1 KB2 CS`.
Example OBD request RPM: `C2 33 F1 01 0C F3`.

## 4. Signal inventory and acquisition decisions

| Signal | Native location | Characteristics | Acquisition method (selected) | Rationale |
|--------|-----------------|-----------------|-------------------------------|-----------|
| Engine speed | ECU (crank sensor) | - | **A** PID 0x0C / LID; fallback **B** alternator ripple | No wire-tapping of crank sensor (inductive, high-voltage spikes, ECU-critical) |
| Vehicle speed | Gearbox VSS → ECU/cluster | Square-wave pulses | **A** PID 0x0D; **B** GNSS | Avoids loading cluster speed input |
| Coolant temp | NTC to ECU (and separate gauge sender) | 0–5 V ECU-referenced | **A**; optional **C**: own NTC probe | Never parallel-tap ECU NTC: alters ECU reading |
| MAP, IAT, TPS, advance, trims, O₂ | ECU | - | **A** only | - |
| Battery voltage | +BAT | 9–15 V, ripple, transients | **B** OBD pin 16 | Direct, guaranteed |
| Ignition state | +APC | 0/12 V | **C** optional IGN tap (100 k input, fused 1 A) **or** inferred: VBAT > 13.2 V or K-line ECU response | Optional tap gives clean key-on before crank |
| Fuel level | Tank sender → cluster gauge (resistive, about 20–300 Ω class **[M → V6]**) | DC voltage at sender wire varies with level | **C** optional: high-impedance (122 kΩ) voltage measurement at sender wire | Draws < 0.1 mA, does not affect gauge; calibrated by fill points |
| Reverse | Reverse-lamp feed | 0/12 V | **C** optional REV tap | For parking features |
| Position, heading, speed | - | - | **B** GNSS MAX-M10S | - |
| Accelerations, rotation | - | - | **B** IMU | - |

## 5. Data-acquisition strategy (three paths)

### Path A: ECU diagnostics (firmware `probe` → `obd`)

1. **Probe sequence** (run once at commissioning, re-run on demand):
   1. ISO 14230 fast init, functional 0x33 → if positive, KWP2000 generic OBD.
   2. ISO 14230 5-baud init address 0x33.
   3. ISO 9141-2 5-baud init address 0x33 (KB 08 08 / 94 94).
   4. Renault physical: fast init to ECU physical address. The probe tries the candidate
      address list configured in `vehicle_profile.c` and confirms with `ReadEcuIdentification (0x1A 0x80)`.
   5. CAN 500 k / 250 k listen-only check, then 0x7DF request (only if CAN-H/CAN-L show differential activity).
2. **Capability capture:** PID support bitmaps (01 00/20/40/60), Mode 09 VIN if supported, ECU ID strings, and
   raw dumps of `0x21 0x01…0x0F` local-ID blocks. All are stored in `/cfg/vehicle.json` and synced to the app.
3. **Signal Mapper (app):** for Renault LID blocks the app plots each raw byte while you do known
   actions (idle → 2,000 rpm, cold → warm). Then you assign scaling (e.g., byte × 40 = rpm). Mappings are saved
   as a profile and shared within the device. This is the standard reverse-engineering method behind
   the community Renault definitions. They aren't reproduced here because their accuracy for this exact
   ECU and calibration can't be guaranteed.

### Path B: Direct measurement (always available)
VBAT (10 Hz plus burst modes), GNSS (1–5 Hz), IMU (100 Hz for crash/harsh events), cabin sensors.

**Alternator-ripple RPM estimation:** a 3-phase alternator with a 6-diode bridge produces ripple at

```
f_ripple = 6 · p · n_alt / 60 = p · n_alt / 10        [Hz, n in rpm]
n_alt    = k · n_eng                                   (k = pulley ratio)
⇒ f_ripple = (p · k / 10) · n_eng = K_rip · n_eng
```

Typical values are p = 6 claw-pole pairs and k ≈ 2.4–2.8, so **K_rip ≈ 1.45–1.70 Hz/rpm**.
With k = 2.7, K_rip = 1.62, and 800 rpm idle gives 1,296 Hz while 6,000 rpm gives 9,720 Hz. Neither
p nor k is documented for this alternator, so the firmware calibrates K_rip once against OBD RPM. Without OBD,
it calibrates against the cluster tachometer at a steady 2,000 rpm, entered in the app.
Acquisition: ADC continuous mode at **40 kS/s**, 2,048 samples (51.2 ms) on the AC-coupled VBAT path,
Hann window, real FFT (19.5 Hz bins), and peak search over 1,000–10,500 Hz with harmonic check (the 2f peak
must exist). Parabolic interpolation gives ±0.5 % at idle and better above. Valid only while charging
(VBAT > 13.2 V) and ripple > 20 mVpp.

### Path C: Optional taps
Fitted only if the user wants those features. See doc 11 §6 for exact tap points and fusing.

## 6. Grounding architecture

- Module GND returns through OBD pins 4 + 5 (both connected in harness). OBD pin 4 is chassis ground, which is
  low impedance. Pin 5 is signal ground, the ECU reference.
- The PCB has one ground plane. The input protection return (TVS) goes to the **pin-4 ground entry** with the
  shortest path, so transient current never crosses the digital section.
- Path C analog taps reference the same GND. The fuel-sender sense shares the sender's ground (tank earth) only
  via body. A differential measurement isn't needed for the required ±3 % accuracy.
- GNSS antenna cable shield goes to PCB ground at the connector only.

## 7. Integration rules (non-negotiable)

1. No splicing of ECU sensor wiring. No parallel loads on ECU inputs.
2. All taps fused within 150 mm of the tap point (1 A ATO mini-blade or 0.5 A PTC for signals).
3. Tap method: **solder-and-heat-shrink with adhesive-lined sleeve**, or Posi-Tap-type piercing connectors rated for 18–22 AWG. **No** scotch-lock IDC clips: they cut strands and corrode in Indian monsoon humidity.
4. Harness routed away from the steering column, pedal box and airbag connectors (yellow); secured every 150 mm.
5. Module must not prevent workshop access: the OBD splitter keeps a female J1962 for scan tools.
6. When a workshop tool is connected, the module detects foreign K-line traffic (bytes not initiated by
   itself) and goes **bus-silent** for 10 minutes so the two testers don't collide (doc 07 §6.3).

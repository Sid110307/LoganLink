# 10 — Robustness, Reliability, Safety and Cybersecurity

## 1. Environmental profile (passenger compartment, India)

| Parameter | Design value | Basis |
|-----------|--------------|-------|
| Operating temperature | −30 … +85 °C (module ambient) | ISO 16750-4, passenger compartment code "not exposed to sunlight" → upper value raised for Indian summer cabin soak |
| Storage | −40 … +95 °C | Parked in sun, rails off |
| Humidity | 95 % RH, damp-heat cyclic 25/55 °C | ISO 16750-4 §5.6; monsoon |
| Vibration | Random, passenger car body: 10–1,000 Hz, 27.8 m/s² RMS, 8 h/axis | ISO 16750-3 Test IV (sprung mass) |
| Mechanical shock | 500 m/s², 6 ms half-sine, 10 per direction | ISO 16750-3 §4.2 |
| Free fall | 1 m onto concrete (handling) | ISO 16750-3 §4.3 |
| Electrical | doc 04 §2.2 | ISO 16750-2, ISO 7637-2 |
| ESD | ±8 kV contact / ±15 kV air (unpowered and powered) | ISO 10605 |
| Emissions | CISPR 25 class 3 (conducted + radiated); must not disturb FM radio (76–108 MHz) or GNSS L1 | CISPR 25 |
| Immunity | ISO 11452-2 (ALSE) 30 V/m, 200 MHz–2 GHz; ISO 11452-4 BCI 60 mA | — |
| Life | 10 years, 150,000 km, ~6,000 operating hours, ~40,000 sleep/wake cycles | — |

## 2. Derating rules (applied in the BOM)

| Component class | Rule | Example in this design |
|-----------------|------|------------------------|
| Ceramic capacitors | Voltage ≤ 50 % rated at max continuous, ≤ 80 % at transient; X7R/X7S only (no Y5V) | 4.7 µF **100 V** on the 26 V continuous / 42 V clamp input |
| Electrolytic / hybrid | 125 °C rated; ripple ≤ 70 %; lifetime L = L0 · 2^((Tmax−Ta)/10) | EEH-ZA 4,000 h @125 °C → **> 100,000 h at 65 °C** |
| Resistors | Power ≤ 50 %; voltage ≤ 50 % of max working | R10 510 Ω 0.5 W sees 0.29 W only in the stuck-low fault |
| Semiconductors | Tj ≤ 125 °C at worst case; VDS ≤ 75 % | 2N7002K 60 V on a ≤ 42 V line |
| Connectors | Current ≤ 50 % of rating at 85 °C | Micro-Fit 5 A, max 2 A (AUX) |
| Wire | ≤ 50 % of ampacity for the bundle | 0.5 mm² carries ≤ 3 A |

## 3. Reliability prediction (indicative)

Parts-count method (SN 29500 / IEC 61709 style) at 55 °C average: the dominant contributors are the ESP32 module (~30 FIT),
the GNSS module (~20 FIT), the SCD41 (~50 FIT, a sensor with moving-signal photoacoustics and MEMS), the hybrid capacitor (~10 FIT),
solder joints and connectors (~40 FIT). The total is **≈ 200 FIT, giving MTBF ≈ 5 × 10⁶ h**. Over 6,000 operating hours plus
sleep-mode exposure, field failure probability over life is < 2 %. The SCD41 is the main wear item, and the design tolerates its
failure: the CO₂ features just disappear.

## 4. Failure modes and effects (FMEA extract)

| Failure | Effect on module | Effect on vehicle | Mitigation | Severity after mitigation |
|---------|------------------|-------------------|------------|---------------------------|
| K-line driver shorted on (Q1 D-S short) | K-line held low | **ECU diagnostics blocked; engine runs normally** (K-line isn't used for engine control) | Firmware echo/stuck-low detection; harness is pluggable. Engine operation unaffected | Low |
| Module draws excess current (short on 3V3) | Fuse 3 A opens | OBD pin 16 circuit (shared with, e.g., radio memory) protected by **our** fuse first | Local 3 A fuse below the vehicle fuse rating | Low |
| Firmware hang | Watchdog reset < 5 s | None | TWDT, IWDT, RWDT | Low |
| Bad OTA image | Boot fails | None | Rollback, signature verification | Low |
| Parked drain bug (e.g., stuck awake) | 15 mA → 0.36 Ah/day | Unprotected: a 35 Ah battery at 80 % charge would be too weak to crank in ~ 4–7 weeks | Low-battery hibernate at 12.1 V resting (≈ 30 % SoC, still cranks reliably), plus a ULP independent of the main firmware | Low |
| Flash corruption | Logs lost | None | Per-record CRC, SAFE mode | Low |
| Connector disengaged by vibration | Module offline | None | Positive latch, strain relief | Low |
| Harness chafing to body | Short to ground | Fuse opens | Corrugated sleeve, grommets, ties every 150 mm | Low |
| Wrong DTC clear | MIL off before repair | Hides an emissions fault | 2-step confirmation, owner only, engine off, logged | Low |

## 5. Vehicle safety and cybersecurity architecture

The 2008 Logan has no gateway, no secure diagnostics and no intrusion detection. Anything that can talk on the K-line can,
in principle, use any diagnostic service the ECUs implement. So the module is the security boundary, and it's built
around **defence in depth**:

1. **Vehicle guard (default deny).** One function (`vguard_allow`) sits between all code and the K-line transmitter. Allowed are J1979 modes
   01/02/03/07/09, KWP 0x1A/0x21/0x17/0x18/0x3E/0x81/0x82, and 0x10 with default-session only. DTC clear needs a one-shot arm (owner + confirm + engine
   off). **Never allowed:** SecurityAccess (0x27), write data (0x2E/0x3B), IO control (0x30/0x2F), routines (0x31), download/upload (0x34–0x37),
   ECU reset (0x11), J1979 mode 08. Denials are counted and reported. No command path exists for sending arbitrary bytes.
2. **Authenticated control plane.** LESC-encrypted link, plus app-layer HMAC challenge-response with per-session keys, command MACs and
   monotonically increasing counters (doc 06 §7).
3. **Pairing gated by physical presence.** New bonds only within 120 s of a PAIR press with the engine running.
4. **Signed firmware.** Secure Boot v2, flash encryption, anti-rollback, signed release manifest.
5. **No inbound internet path** in variant B. Variant C: outbound-only TLS MQTT with server certificate pinning. Remote commands are
   limited to *status* and *locate*. The vehicle guard applies identically, and no remote command reaches the K-line except reads.
6. **Least data.** Location history stays on the module and phone. There is no vendor cloud.
7. **Threat model review** against ISO/SAE 21434 concepts (asset → threat → risk → control) for: BLE attacker in a car park, stolen
   phone, malicious firmware, compromised broker (C), physical attacker with USB access. Physical USB access allows reflashing only of
   images signed with our key after Secure Boot is enabled. ROM download mode is restricted per doc 07 §11.

**Functional safety.** The module performs no safety-related function and must never be able to influence one. The guard and the
read-only architecture ensure it can't change engine, brake, steering or airbag behaviour. Crash detection (CE-06) is informational. It
offers an SOS call and doesn't replace eCall.

## 6. Long-term operation

- **Power interruptions:** state is persisted at engine-off, and records are atomic. An RTC_DATA crank record survives resets.
- **Clock:** GNSS time, then phone time, then the RV-3028 holdover (±1 ppm ≈ 2.6 s/month).
- **Component ageing:** the hybrid capacitor's life is far beyond 10 years at its operating temperature. SCD41 self-calibration (ASC) is
  enabled and needs weekly exposure to fresh air (normal car use provides this). VBAT calibration is re-checked yearly against a DMM.
- **Maintenance:** yearly inspection: connector latch, harness ties, fuse, GNSS antenna adhesion; firmware updates as released.

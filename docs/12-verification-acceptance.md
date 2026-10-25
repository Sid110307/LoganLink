# 12 — System Verification and Acceptance

## 1. Test equipment

| Equipment | Minimum spec | Indicative cost (INR) | Use |
|-----------|--------------|-----------------------|-----|
| Bench DC supply | 0–30 V, 3 A, with current readback to 1 mA (e.g., Rigol DP711 / OWON SPE3103) | 8,000–25,000 | Power-up, sweep, cranking emulation |
| DMM | 4½ digit, µA range (e.g., UNI-T UT61E+) | 4,000–6,000 | Calibration, sleep current |
| µA logger (recommended) | Nordic PPK2 or Joulescope-class | 8,000 (PPK2) | Sleep profile, wake events |
| Oscilloscope | 2-ch 100 MHz (e.g., Rigol DS1102Z-E) | 25,000–30,000 | K-line timing, ripple, buck |
| USB-UART / USB-C cable | — | 300 | Programming |
| K-line reference tool | Genuine ELM327-based (OBDLink SX) or a known-good scan tool | 3,000–6,000 | Cross-check readings on the car |
| ISO 7637/16750 pulse generator | Lab only (test house) | rented: ~₹25k–60k/day at Indian EMC labs | Transient compliance |
| Thermal chamber / heat gun + thermocouple | — | lab / 1,500 | Thermal tests |

## 2. Factory self-test (firmware `selftest` command, each board)

| Item | Method | Pass criterion |
|------|--------|----------------|
| 3V3_AON / 3V3_SW / 3V3_GNSS | ADC readback via divider test points + DMM | 3.30 V ± 1.5 % |
| Log flash | JEDEC ID 0xEF4019, write/read/erase one sector | ID matches, data matches |
| I²C devices | WHO_AM_I / serial reads | ASM330 0x6B, AIS2DW12 0x44, SHT45 serial, SCD41 serial, RV-3028 responds |
| GNSS | UBX-MON-VER within 2 s of GNSS_EN | Version string received |
| K-line loopback | Fixture ties K to 510 Ω/12 V; echo test of 64 bytes at 10,400 baud | 0 errors |
| VBAT channel | Fixture supplies 12.000 V and 15.000 V | Reads within ±30 mV after calibration |
| BLE | Advertising seen by the fixture scanner, RSSI at 1 m | ≥ −60 dBm |
| LED / button | Operator visual + button press | — |
| Current | 12.0 V STANDBY | 10–25 mA |

## 3. Design verification (prototype units, DV)

| # | Test | Procedure | Acceptance |
|---|------|-----------|------------|
| DV-01 | Supply operating range | Sweep 6–16 V slowly, full functionality | No reset; all functions at 6.5–16 V |
| DV-02 | Cranking | ISO 16750-2 starting profile (level II: 8 V → 6 V → 9.5 V; level IV down to 4.5 V) | Level II: no reset (status A). Level IV: reset allowed, recovery < 2 s, no data loss (status B) |
| DV-03 | Jump start | 24 V for 60 s | Status A |
| DV-04 | Reverse polarity | −14 V for 60 s, fused 3 A | Fuse may open; board functional after fuse replacement (status C) |
| DV-05 | ISO 7637-2 pulses 1, 2a, 2b, 3a, 3b | Test house, level III | 1: C; 2a, 3a/b: A; 2b: B |
| DV-06 | Load dump | ISO 16750-2 Test B Us* = 35 V | Status A–C (no damage) |
| DV-07 | Buck performance | Load step 20 → 400 mA | 3V3 dip < 100 mV; ripple < 30 mVpp |
| DV-08 | Sleep current | 12.6 V, PPK2, 1 h after SLEEP entry | **≤ 150 µA mean target, ≤ 300 µA acceptance**; HIBERNATE ≤ 80 µA |
| DV-09 | Battery impact | 14-day parked test in the car, battery initially full | Resting voltage drop attributable to the module ≤ 0.05 V (compare with the module unplugged on the same battery) |
| DV-10 | K-line timing | Scope on the K-line during fast init | TiniL 25 ± 1 ms, TWuP 50 ± 1 ms; P3 ≥ 55 ms; byte timing 96.15 µs ± 1 % |
| DV-11 | Diagnostic accuracy | Car at idle / 2,000 rpm / 3,000 rpm and cold/warm; compare against the reference scan tool simultaneously (alternate polling) | Identical PID values (± 1 LSB); RPM ± 1 %; DTC list identical |
| DV-12 | Ripple RPM | Compare with ECU RPM, 800–4,000 rpm, charging | Error ≤ 3 % after calibration |
| DV-13 | VBAT accuracy | 9–16 V, −10/25/70 °C | ±30 mV (±50 mV at temperature extremes) |
| DV-14 | GNSS | Open-sky cold start / hot start; urban drive vs. phone track | TTFF cold ≤ 35 s, hot ≤ 3 s; median position error ≤ 2.5 m open sky |
| DV-15 | Fuel model | 3 fill-to-full cycles | Error vs. pumped litres ≤ ±5 % after the 3rd fill |
| DV-16 | BLE range & reconnect | Phone in pocket, driver seat; 100 ignition cycles | RSSI ≥ −75 dBm; auto-reconnect ≥ 98 % within 10 s (Android 12+/iOS) |
| DV-17 | Throughput | Log sync of 10,000 records | ≥ 30 KB/s; zero lost or duplicate records (seq check) |
| DV-18 | Latency | Toggle a test signal; measure to the app tile change (high-speed video) | ≤ 200 ms (95th percentile) |
| DV-19 | OTA | 50 BLE updates incl. 10 with forced link drop and 5 with power cut mid-write | 100 % recover; rollback works; no bricked unit |
| DV-20 | Security | Unbonded phone outside the pairing window; replayed command capture; wrong K_user; fuzzed frames (10⁵ random) | All rejected; no crash; vguard denies all non-allow-listed SIDs (scripted SID sweep 0x00–0xFF against a K-line simulator) |
| DV-21 | Thermal | 85 °C chamber, DRIVE mode load, 4 h; 95 °C storage 48 h | No reset; die temp policy engages correctly; enclosure no deformation (ASA) |
| DV-22 | Cold | −30 °C start-up | Boots, logs, BLE within 10 s |
| DV-23 | Damp heat | 40 °C / 93 % RH, 10 days (powered cycling) | No corrosion, leakage current increase < 20 µA |
| DV-24 | Vibration | ISO 16750-3 Test IV, 8 h/axis, powered, monitoring for resets | No resets, no mechanical damage, J1 latched, no fastener loosening |
| DV-25 | Shock / drop | 500 m/s² 6 ms; 1 m drop (unpowered) | Functional; enclosure may scuff |
| DV-26 | ESD | ISO 10605 ±8 kV contact on J1 pins, USB shell, enclosure seams | Status A for air, B for contact on pins |
| DV-27 | Emissions | CISPR 25 radiated/conducted (pre-scan with near-field probe + SDR, then lab) | Class 3 limits; no audible FM interference at 88–108 MHz on the car radio |
| DV-28 | Workshop coexistence | Connect a scan tool while the module runs | Module goes silent ≤ 1 request; scan tool works normally |
| DV-29 | Long-term soak | 30-day in-car use, daily commute | No resets except intended; data completeness ≥ 99.9 %; battery healthy |
| DV-30 | App | Scripted UI tests on the device matrix (doc 08 §10) + module simulator | All flows pass; no crash in 8 h continuous live view; memory stable |

## 4. Acceptance summary (release gate)

| Spec | Target | Gate |
|------|--------|------|
| Parked current | ≤ 150 µA | ≤ 300 µA |
| VBAT accuracy | ±30 mV | ±50 mV |
| Diagnostics agreement with reference tool | 100 % | 100 % |
| Auto-reconnect success | ≥ 99 % | ≥ 98 % |
| Live latency (p95) | ≤ 150 ms | ≤ 200 ms |
| Data completeness over 30 days | 100 % | ≥ 99.9 % |
| OTA recovery | 100 % | 100 % |
| Vehicle guard SID sweep | 0 forbidden SIDs transmitted | 0 |
| Thermal | No reset at 85 °C | Same |

Record results in `docs/test-reports/DV-xx.md` with date, firmware hash, hardware serial, equipment and raw data.

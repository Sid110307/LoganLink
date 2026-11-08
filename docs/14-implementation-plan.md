# 14 — Implementation Sequence, Maintenance and References

## 1. Build sequence

| Phase | Duration | Work | Exit criterion |
|-------|----------|------|----------------|
| **0. Vehicle probe (de-risk)** | 1 week | Breadboard: ESP32-S3 DevKitC-1 + discrete K-line PHY (2N7002K, LMV331, 510 Ω, dividers from doc 04 §4.1) on perfboard, fed from a fused OBD pigtail. Flash this firmware (pins remapped in `board.h`); run the probe | V1, V2, V5 answered: protocol, PID map and ECU ID known. Optionally, an ELM327-class tool confirms the same |
| 1. Schematic | 1–2 weeks | KiCad schematic per doc 05; ERC clean; peer review against docs 04/05 checklists | Review sign-off |
| 2. Layout | 2 weeks | Placement per doc 05 §4.3, routing rules, DRC clean; LionCircuits stack-up confirmed; 3D fit-check against `enclosure.scad` | DRC clean, fab DFM OK |
| 3. Fab + assembly (proto, 5 pcs) | 2–3 weeks | LionCircuits turnkey, consigned SCD41/MAX-M10S if needed | Boards received |
| 4. Bring-up | 1–2 weeks | Doc 05 §6 inspection → power → programming → self-test → each driver (finish SGP41, fuel ADC, coolant NTC). Compile-fix the firmware against IDF 5.3 | All self-test items pass on 5/5 boards |
| 5. Firmware integration | 2–3 weeks | Vehicle task on the car, ripple calibration, sleep tuning (PPK2), BLE/OTA | DV-08, DV-10, DV-11, DV-16, DV-19 pass |
| 6. App | 4–6 weeks (parallel from Phase 1) | Remaining screens, QR pairing, native background shims, notifications, Signal Mapper | DV-30 on the device matrix |
| 7. Enclosure | 1 week (parallel) | Print, fit, iterate parameters; inserts | Fit checks of doc 09 §6 |
| 8. Installation + commissioning | 1 day | Doc 11 | Doc 11 §8 complete |
| 9. Verification | 3–4 weeks | Doc 12 DV list (EMC/transients at a test lab) | Release gate (doc 12 §4) |
| 10. Release | — | Enable Secure Boot + flash encryption on production units; sign v1.0.0 | Units shipped/installed |

## 2. Device programming and configuration

**Development:** `idf.py -p COMx flash monitor` over USB-C.

**Production (per unit):**
1. Fixture: Tag-Connect TC2030 + pogo pads for the test points. Hold IO0 low and pulse EN to enter download mode.
2. `esptool.py --chip esp32s3 -p COMx write_flash @build/flash_args` (signed bootloader + partition table + app, for Secure Boot builds).
3. First boot generates K_dev. The provisioning script reads `device_id` and K_dev over the factory console (enabled only before
   `secure_version` lock), prints the **pairing card** and the **inner-lid QR label**, then locks the console with an eFuse/NVS flag.
4. Run self-test (doc 12 §2). Record serial, MAC, firmware hash and results in the production log.

## 3. Firmware update procedure (field)

1. Build a release: `idf.py build`, then sign the app (`espsecure.py sign_data --version 2 --keyfile release_key.pem …`), or let
   the build sign it with `CONFIG_SECURE_BOOT_SIGNING_KEY`.
2. Create `manifest.json` {version, sha256, size, min_app, secure_version} and sign it with the Ed25519 release key.
3. Publish to the release host (any static HTTPS host, e.g., a GitHub Releases page).
4. App: Device → Update. It checks the manifest signature and preconditions (engine off, VBAT > 12.2 V), transfers over BLE (~40 s), and the
   module verifies, reboots and self-tests. Rollback is automatic if the self-test fails.

## 4. Key management

| Key | Where it lives | Backup |
|-----|----------------|--------|
| Secure Boot v2 RSA-3072 signing key | Offline: encrypted USB drive (VeraCrypt), never on the build server; optional YubiHSM/Nitrokey HSM | 2 encrypted copies in separate locations. **Losing it means no more firmware updates for fused units** |
| Manifest Ed25519 key | Same as above | Same |
| Flash-encryption key | Generated on-chip per device (release mode); never leaves the chip | Not needed |
| K_dev (per module) | Encrypted NVS on the module + printed pairing card | The card is the user's backup |

## 5. Maintenance schedule

| Interval | Task |
|----------|------|
| Each firmware/app release | Update via the app; read release notes for new vehicle profiles |
| 6 months | Check the J1 latch and harness ties; wipe dust from the vent; check GNSS antenna adhesion (summer heat) |
| 12 months | VBAT calibration check with a DMM; review sleep current (app → Device → Diagnostics shows the estimated parked drain from wake statistics) |
| On battery replacement | Re-run VBAT calibration; reset battery-health baselines in the app |
| On ECU or cluster replacement | Re-run the vehicle probe; re-enter the odometer |

## 6. Final technical specifications (LL-VM1-B rev A)

| Parameter | Value |
|-----------|-------|
| Supply | 6–16 V operating; 4.5 V crank survival with reset; 24 V/60 s jump; −14 V reverse (fuse-protected) |
| Consumption | DRIVE ≈ 0.4 W (≈ 30 mA at 13.8 V); SLEEP ≈ 75 µA design (≤ 300 µA accept); HIBERNATE ≈ 50 µA |
| Vehicle interfaces | K-line ISO 9141-2 / ISO 14230 (fast + 5-baud), CAN ISO 11898-2 (hardware), IGN/REV/FUEL inputs, 1 A low-side AUX output |
| Processor | ESP32-S3, 2 × 240 MHz, 16 MB flash, 2 MB PSRAM, ULP-RISC-V |
| Storage | 32 MB NOR ring log (≈ 73 h at 1 Hz full record) |
| Wireless | BLE 5.0 (2M PHY, LESC), Wi-Fi 2.4 GHz (OTA). Variant C: LTE Cat-1 |
| Positioning | Multi-GNSS (GPS, Galileo, BeiDou, GLONASS), 5 Hz |
| Sensors | 6-axis IMU ±16 g, wake accelerometer, temperature/RH, CO₂, VOC/NOx, RTC ±1 ppm |
| Measured electrical | VBAT ±30 mV, crank capture 1 kS/s, alternator ripple 40 kS/s FFT |
| Enclosure | ASA, 86 × 61 × 19.5 mm, IP40 (vent IP5X with membrane), −30 … +85 °C |
| Security | Secure Boot v2, flash encryption, LESC bonding, HMAC app-layer auth, read-only vehicle guard |

## 7. Engineering references

**Standards**
- ISO 9141-2:1994 *Road vehicles — Diagnostic systems — CARB requirements for interchange of digital information*
- ISO 14230-1/-2/-3 (KWP2000 on K-line): physical layer, data link layer, application layer
- ISO 15031-3 / SAE J1962 (diagnostic connector); SAE J1979 / ISO 15031-5 (emissions diagnostic services, PIDs)
- ISO 15765-2/-4 (diagnostics on CAN); ISO 11898-2 (CAN high-speed physical layer)
- ISO 7637-2:2011 (electrical transient conduction along supply lines)
- ISO 16750-1…-5 (environmental conditions and testing for electrical equipment)
- ISO 10605 (ESD for road vehicles); CISPR 25 (vehicle radio-disturbance limits, on-board receivers)
- ISO 11452-2/-4 (component immunity); ISO/SAE 21434 (road-vehicle cybersecurity engineering)
- IPC-2221, IPC-6012 Class 2, IPC-A-610, J-STD-020

**Datasheets and manuals** (use the latest revision from the manufacturer)
- Espressif: *ESP32-S3 Technical Reference Manual*; *ESP32-S3-WROOM-1/1U Datasheet* (antenna keep-out, module temperature grades);
  ESP-IDF v5.3 Programming Guide (ADC continuous, ULP-RISC-V, NimBLE, Secure Boot v2, OTA, Flash Encryption)
- TI: LMR36015-Q1, TPS22918-Q1, SN65HVD230Q, LMV331-Q1, TPS54360B-Q1 datasheets
- u-blox: *MAX-M10S Data Sheet*, *MAX-M10S Integration Manual*, *u-blox M10 SPG Interface Description* (UBX-CFG-VALSET keys, NAV-PVT)
- ST: ASM330LHHX, AIS2DW12, VNLD5090-E, STPS3H100 datasheets
- Sensirion: SHT4x, SGP41, SCD4x datasheets and handling/assembly guides
- Micro Crystal RV-3028-C7 application manual; Winbond W25Q256JV datasheet; Vishay SM8S series (load-dump TVS)
- SIMCom A7672 series hardware design and AT command manuals (variant C)
- Molex Micro-Fit 3.0 product specification (PS-43045, crimp specification for 43030)

**Vehicle**
- Renault/Dacia X90 workshop manual sections for injection (K7M), wiring diagrams (cabin fuse box, diagnostic socket) and
  the UCH. Use the version for your build year and market; your dealer's service documentation for market-specific details.

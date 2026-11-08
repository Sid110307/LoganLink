# 03. Hardware

One 2-layer board, 54 x 42 mm, all parts on the top side. It sits flat behind the OBD-II plug housing (Molex 68503-1602).
Six short wires join the housing's crimp pins to pads on the board. Parts and pins are in
[`hardware/bom.csv`](../hardware/bom.csv) and [`hardware/pinmap.csv`](../hardware/pinmap.csv). The layout is in [04](04-kicad.md).

## Blocks

```
OBD16 ─ F1 2A ─┬─ D1 SMCJ33A ─ GND
               └─ D2 STPS2H100A ─ VBAT_P ─ C1 ─ L2 ferrite ─ VIN ─ C2, C3 ─ U2 LMR36006 ─ L1 22uH ─ 3V3 ─ U1, U3
USB VBUS ─ D8 ─ VIN (bench only)
VBAT_P ─ R13/R14 ─ GPIO1        VBAT_P ─ C16 ─ R16 ─ GPIO2 (ripple)
K_LINE ─ D3 ESD; pull-up VBAT_P ─ R10 ─ D7; driver Q1 + R19; receiver U4 comparator ─ GPIO18
```

## Power

| Item | Value | Why |
|------|-------|-----|
| F1 | 2 A fast, 1206 | Board draws under 0.2 A; fuse only protects the car wiring from a board fault |
| D1 | SMCJ33A | Standoff 33 V, breakdown 36.7 V min. Above the 35 V suppressed load dump so it never conducts on a healthy car. Clamps ISO 7637 pulses |
| D2 | STPS2H100A, 100 V Schottky | Reverse battery. Drop about 0.5 V at 100 mA |
| C1, C2 | 2.2 µF 100 V X7S | Rated above any transient D1 lets through |
| C3 | 22 µF 63 V electrolytic | Damps the input LC (L2 + ceramics) so it does not ring on hot-plug |
| U2 | LMR36006, 4.2 to 60 V in, 0.6 A, 400 kHz | 24 µA no-load quiescent, which sets most of the sleep current |
| FB | R2 100k / R3 43.2k | Vout = 1.0 x (1 + 100/43.2) = 3.31 V |
| L1 | 22 µH, Isat 0.95 A | TI's value for 3.3 V at 400 kHz |
| D8 | PMEG4010CEJ | Lets USB power the board on the bench. D2 and D8 OR the two sources, so USB plugged in while in the car is safe |

## K-line

| Item | Value | Check |
|------|-------|-------|
| R10 + D7 | 510R 0.5 W to VBAT_P | ISO 9141-2 tester pull-up. Worst dissipation while held low: 14.4² / 510 = 0.41 W, only during low bits |
| Q1 + R19 | 2N7002K, 47R series | Pull-ups total about 255R (ours and ECU's). Low level = 14.4 x 47 / 302 = 2.2 V = 0.16 VB, under the 0.2 VB limit |
| R20/R21 | 1M / 150k from K_LINE | K seen at 0.130 VB |
| R22/R23 | 1M / 68k from VBAT_P | Reference 0.064 VB, so the switching point is 0.49 VB: centred between 0.3 VB low and 0.7 VB high |
| R24 | 4.7M | About 3 % VB hysteresis |
| U4 | LMV331, powered from AUX_PWR | Open drain into GPIO18 with R25 10k pull-up. Off in deep sleep |
| D3 | PESD1LIN | ESD and burst on the line that leaves the board |
| TX | UART1 TX inverted in firmware | Q1 on = line low |

The ratiometric reference makes the receiver track battery voltage from 9 V to 16 V without calibration.

## Sensing

| Signal | Path | Range |
|--------|------|-------|
| Battery voltage | R13 1M / R14 150k, C15 10 nF, R15 4.7k, D5 clamp, GPIO1 | 14.4 V reads 1.88 V. Full scale about 23 V at 11 dB attenuation |
| Alternator ripple | C16 100 nF 100 V, R16 4.7k, bias R17/R18 100k from AUX_PWR, C17 1 nF, D6 clamp, GPIO2 | AC only, biased at 1.65 V while awake |
| Motion | LIS2DW12 on I2C (0x19), INT1 on GPIO7 | Wake on movement in deep sleep |

## ESP32-S3-MINI-1

- EN: R4 10k up, C10 1 µF down.
- GPIO0: R5 10k up, SW1 to GND. Hold at power-up for download mode; short press in app for pairing.
- USB: GPIO19/20 to the USB-C through D4 (USBLC6). CC1 and CC2 each 5.1k to GND.
- No 32 kHz crystal. The RTC runs on the internal oscillator and is re-synced from the phone on every connect.
- LEDs: red on GPIO38 (330R, about 4 mA), green on GPIO39 (150R, about 7 mA). Firmware PWMs them down.
- Console UART0 on test pads with EN, GPIO0, 3V3 and GND.

## Sleep budget (engine off, 12.6 V)

| Load | Current |
|------|---------|
| LMR36006 no-load | 24 µA |
| Battery divider | 11 µA |
| Reference divider | 11 µA |
| K-line divider (K idles at battery) | 10 µA |
| ESP32-S3 deep sleep + ULP, reflected to 12 V | about 4 µA |
| LIS2DW12 low power | about 1 µA |
| Total | about 61 µA (design max 150 µA) |

That is 1.5 mAh a day, about 0.5 Ah a year. Below 12.1 V resting the firmware drops to hibernate.

## CAN (not fitted)

U5 SN65HVD230 on GPIO4/5 with Rs to GND, OBD pins 6 and 14 wired to J1. Fit it, plus the two crimp pins, only for a CAN car.

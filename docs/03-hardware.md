# 03. Hardware

One 2-layer board, 51 x 40 mm, all parts on top, flat behind the OBD-II plug housing (Molex 68503-1602). Six short wires join the
housing's crimp pins to pads on the board. Parts: [`hardware/bom.csv`](../hardware/bom.csv). Pins:
[`hardware/pinmap.csv`](../hardware/pinmap.csv). Netlist and layout: [04](04-kicad.md).

```
OBD16 ─ F1 2A ─┬─ D1 SMCJ33A ─ GND
               └─ D2 STPS2H100A ─ VBAT_P ─ C1 ─ L2 ferrite ─ VIN ─ C2, C3 ─ U2 LMR36006 ─ L1 22uH ─ 3V3 ─ U1, U3
USB VBUS ─ D8 ─ VIN (bench only)
VBAT_P ─ R13/R14 ─ GPIO1        VBAT_P ─ C16 ─ R16 ─ GPIO2 (ripple)
K_LINE ─ D3 ESD; pull-up VBAT_P ─ R10 ─ D7; driver Q1 + R19; receiver U4 comparator ─ GPIO18
```

## Power

| Item | Value | Note |
|------|-------|------|
| F1 | 2 A fast, 1206 | Board draws under 0.2 A; protects the car wiring from a board fault |
| D1 | SMCJ33A | Standoff 33 V, breakdown 36.7 V min: never conducts on the 35 V suppressed load dump. Clamps ISO 7637 pulses |
| D2 | STPS2H100A, 100 V Schottky | Reverse battery. About 0.5 V drop at 100 mA |
| C1, C2 | 2.2 µF 100 V X7S | |
| C3 | 22 µF 63 V electrolytic | Damps the input LC (L2 + ceramics) on hot-plug |
| U2 | LMR36006, 4.2 to 60 V in, 0.6 A, 400 kHz | 24 µA no-load quiescent, most of the sleep current |
| FB | R2 100k / R3 43.2k | Vout = 1.0 x (1 + 100/43.2) = 3.31 V |
| L1 | 22 µH, Isat 0.95 A | |
| D8 | PMEG4010CEJ | USB powers the board on the bench. D2 and D8 OR the sources, so USB in the car is safe |

## K-line

| Item | Value | Check |
|------|-------|-------|
| R10 + D7 | 510R 0.5 W to VBAT_P | Tester pull-up. Worst dissipation held low: 14.4² / 510 = 0.41 W |
| Q1 + R19 | 2N7002K, 47R series; gate via R26 100R, R27 100k pull-down | Pull-ups total about 255R. Low level = 14.4 x 47 / 302 = 2.2 V = 0.16 VB, under the 0.2 VB limit |
| R20/R21 | 1M / 150k from K_LINE | K seen at 0.130 VB |
| R22/R23 | 1M / 68k from VBAT_P | Reference 0.064 VB, switching point 0.49 VB (between 0.3 VB low and 0.7 VB high) |
| R24 | 4.7M | About 3 % VB hysteresis |
| U4 | LMV331, powered from AUX_PWR | Open drain into GPIO18, R25 10k pull-up. Off in deep sleep |
| D3 | PESD1LIN | ESD and burst |
| TX | UART1 TX inverted in firmware | Q1 on = line low |

The ratiometric reference tracks battery voltage from 9 to 16 V without calibration.

## Sensing

| Signal | Path | Range |
|--------|------|-------|
| Battery voltage | R13 1M / R14 150k, C15 10 nF, R15 4.7k, D5 clamp, GPIO1 | 14.4 V reads 1.88 V; full scale about 23 V at 11 dB |
| Alternator ripple | C16 100 nF 100 V, R16 4.7k, bias R17/R18 100k from AUX_PWR, C17 1 nF, D6 clamp, GPIO2 | AC only, biased at 1.65 V while awake |
| Motion | LIS2DW12 on I2C (0x19), 4.7k pull-ups R11/R12, INT1 on GPIO7 | Wakes from deep sleep on movement |

## ESP32-S3-MINI-1

- EN: R4 10k up, C10 1 µF down.
- GPIO0: R5 10k up, SW1 (PAIR) to GND. Held at power-up = download mode.
- USB: GPIO19/20 to USB-C via D4 (USBLC6); CC1, CC2 each 5.1k to GND.
- No 32 kHz crystal; the RTC runs on the internal oscillator and is re-synced from the phone on every connect.
- LEDs: red GPIO38 (330R, about 4 mA), green GPIO39 (150R, about 7 mA), PWM-dimmed.
- Console UART0 (GPIO43/44) on test pads with EN, GPIO0, 3V3, GND.
- GPIO6 `IGN_SENSE_N` is reserved for an optional ignition tap, not fitted.

## Sleep budget (engine off, 12.6 V)

| Load | Current |
|------|---------|
| LMR36006 no-load | 24 µA |
| Battery divider | 11 µA |
| Reference divider | 11 µA |
| K-line divider (K idles at battery) | 10 µA |
| ESP32-S3 deep sleep + ULP, reflected to 12 V | about 4 µA |
| LIS2DW12 low power | about 1 µA |
| Total | about 61 µA (max 150 µA) |

1.5 mAh a day, about 0.5 Ah a year. Below 12.1 V resting the firmware hibernates.

## CAN (not fitted)

U5 SN65HVD230 on GPIO4/5, Rs to GND, OBD pins 6 and 14 to J1. Fit it and the two crimp pins only for a CAN car.

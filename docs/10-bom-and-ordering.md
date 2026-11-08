# 10. BOM and Ordering

Full list with MPNs and footprints: [`hardware/bom.csv`](../hardware/bom.csv). Prices are indicative INR from Mouser, DigiKey and
LionCircuits; check before ordering.

## Cost

| Item | 1 unit | Each at 25 |
|------|--------|-----------|
| Board parts (SMT) | about ₹1,155 | about ₹820 |
| OBD housing, pins, wire | about ₹300 | about ₹225 |
| PCB, 2-layer HASL | ₹200 | ₹60 |
| Assembly, top side | ₹1,500 | ₹300 |
| Enclosure print + screws | ₹120 | ₹80 |
| Total | about ₹3,275 | about ₹1,485 |

Assembly setup dominates at one unit. Order at least 5 boards for price and bring-up spares.

Cost choices: ESP32-S3-MINI-1 module (pre-certified, no RF tuning); 2N7002K + LMV331 instead of a K-line transceiver; no RTC crystal;
C&K PTS810 button; Bourns SRN4018 inductor; 1 % feedback resistors; CAN footprint only (DNP); wires to pads instead of a board-mount
OBD connector.

## LionCircuits order

| PCB setting | Value |
|-------------|-------|
| Layers | 2 |
| Material | FR4, 1.6 mm |
| Copper | 1 oz |
| Finish | Lead-free HASL. Cheaper than ENIG; ask LionCircuits to inspect U3 (LGA) and U4 (QFN) joints, the two parts that like flat pads |
| Mask / silk | Green / white |
| Size | 51 x 40 mm |

| Assembly setting | Value |
|------------------|-------|
| Type | Turnkey, top side only |
| Files | Gerber zip, BOM CSV, placement CSV ([04](04-kicad.md) section 8) |
| DNP | U5 (and any CAN-only parts you add) |
| Not assembled | J1 and TP1 to TP6 are pads only |

Buy the OBD housing (Molex 68503-1602), crimp pins (57964-9702), wire and screws yourself.

Tools: open-barrel crimp tool for the Molex pins, soldering iron, multimeter, current-limited bench supply, USB-C cable,
oscilloscope for the K-line check.

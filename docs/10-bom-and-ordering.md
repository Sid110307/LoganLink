# 10. BOM and ordering

The full list with MPNs and footprints is [`hardware/bom.csv`](../hardware/bom.csv). Prices are indicative INR from Mouser,
DigiKey and LionCircuits listings; check before ordering.

## Cost

| Item | 1 unit | Each at 25 |
|------|--------|-----------|
| Board parts (SMT) | about ₹1,155 | about ₹820 |
| OBD housing, pins, wire | about ₹300 | about ₹225 |
| PCB, 2-layer ENIG | ₹200 | ₹60 |
| Assembly, top side | ₹1,500 | ₹300 |
| Enclosure print + screws | ₹120 | ₹80 |
| Total | about ₹3,275 | about ₹1,485 |

At one unit the assembly setup cost dominates. Order at least 5 boards: the per-board price falls fast and you get spares for
bring-up.

## Choices made for cost

| Part | Choice | Saved against the obvious pick |
|------|--------|-------------------------------|
| Radio | ESP32-S3-MINI-1 module | Pre-certified, no RF layout or antenna tuning |
| K-line | 2N7002K + LMV331 | A dedicated LIN/K-line transceiver costs more and still needs the pull-up |
| RTC crystal | None, internal oscillator | Time comes from the phone |
| Button | C&K PTS810 | About half the price of an E-Switch TL3342 |
| Buck inductor | Bourns SRN4018 | About a third of a Coilcraft XGL4030 |
| Buck feedback | 1 % resistors | 0.1 % is not needed for a 3.3 V rail |
| CAN | Footprint only (DNP) | Fit only for a CAN car |
| OBD connection | Wires from the housing to pads | No board-mount OBD connector |

## LionCircuits order

PCB:

| Setting | Value |
|---------|-------|
| Layers | 2 |
| Material | FR4, 1.6 mm |
| Copper | 1 oz |
| Finish | ENIG (flat pads for the LGA accelerometer and the buck's QFN) |
| Mask / silk | Green / white |
| Size | 54 x 42 mm |

Assembly:

| Setting | Value |
|---------|-------|
| Type | Turnkey, top side only |
| Files | Gerber zip, BOM CSV, placement CSV ([04](04-kicad.md) section 8) |
| DNP | U5 (and CAN-only parts if you add any) |
| Not assembled | J1 and TP1 to TP6 are pads only |

Order the OBD housing, crimp pins and screws yourself; they are not on the board.

## Tools

Open-barrel crimp tool for the Molex pins, soldering iron, multimeter, bench supply with
current limit, USB-C cable, oscilloscope for the K-line check.

# 13. Bill of Materials and Procurement

The line-item BOM is in **`hardware/bom.csv`**: designators, manufacturer, MPN, package, variant and indicative unit prices at
qty 1 and qty 25. This document gives the roll-up, off-board items, tools, and the sourcing route.

> **Prices are indicative estimates in INR (late-2025 / 2026 levels, at about ₹85–88 per USD), excluding 18 % GST.** They vary with
> the exchange rate and stock. Get a live turnkey quote from LionCircuits, plus Mouser India / DigiKey India carts, before
> committing. Mouser and DigiKey India normally ship duty-paid (DDP) above their free-shipping threshold. Check the current terms.
> The Mouser# / DigiKey# columns in the CSV are intentionally left blank: LionCircuits' turnkey service matches by
> manufacturer part number, and distributor stock numbers should come from the live cart rather than be guessed.

## 1. Sourcing route

| Item | Route |
|------|-------|
| PCB fabrication + SMT assembly | **LionCircuits** (Bengaluru), turnkey. Upload Gerbers + BOM (MPN) + CPL. They source from Mouser/DigiKey |
| Parts LionCircuits may ask you to consign | SCD41, MAX-M10S, ESP32-S3 module (sometimes short at distributors). Buy directly from Mouser/DigiKey India and ship to LionCircuits |
| THT J1 | Hand-solder after reflow, or ask for the selective/manual THT option |
| Off-board harness parts | Mouser/DigiKey (Molex, Littelfuse), or Indian retailers (Robu.in, Evelta, ElectronicsComp) for wire, loom and fuse holders |
| GNSS antenna, OBD splitter | Indian e-commerce / Robu.in / Amazon.in (choose an active 3–5 V GNSS antenna ≥ 26 dB; a splitter with pins 4,5,6,7,14,16) |
| Enclosure | Self-print (ASA), or MJF PA12 via an Indian 3D-print bureau |

## 2. Cost roll-up: single finished unit (variant B)

| Group | Content | INR |
|-------|---------|-----|
| Active ICs and modules | U1–U15 | 8,370 |
| Protection and discretes | D1–D12, Q1–Q3 | 400 |
| Passives (incl. precision dividers, inductors, hybrid cap) | L1, L2, L50, C*, R* | 825 |
| Connectors, LED, switch | J1, J2, J3, J7, LED1, SW1 | 310 |
| **Electronic components subtotal** | | **≈ 9,900** |
| Attrition / MOQ overage on passives (turnkey) | +10 % | 990 |
| PCB (4-layer ENIG, per board from a 5-pc prototype lot) | LionCircuits | 1,100 |
| Assembly (setup + stencil spread over a small prototype order) | LionCircuits | 2,500 |
| GNSS active antenna (3 m, U.FL/SMA pigtail) | - | 700 |
| OBD Y-splitter, pins 4/5/6/7/14/16 | - | 450 |
| Micro-Fit receptacle 43025-1000 + 10 × 43030-0007 crimps | Molex | 150 |
| Inline fuse holders (3 A, 2 A) + fuses + 2 × 0.5 A PTC | Littelfuse / generic | 200 |
| Wire TXL 0.5/0.35 mm² (5 m), corrugated loom, fleece tape, ties, tie mounts | - | 400 |
| Enclosure: ASA (≈ 60 g), 8 heat-set inserts, 8 screws, PTFE vent, light pipe, labels | - | 450 |
| **Total, single unit (B)** | | **≈ ₹16,800** |
| Option: omit SCD41 (no CO₂ / occupant features) | | −2,900 → ≈ ₹13,900 |
| Variant C extras: A7672S, TPS54360B-Q1, L3, Q4, J4, nano-SIM holder, caps, LTE antenna | | +2,300 (plus SIM plan ≈ ₹150–300/month) |

## 3. Small batch (25 units, variant B)

| Group | Per unit (INR) |
|-------|----------------|
| Components at qty-25 pricing | ≈ 7,700 |
| PCB (25-off panelised) | ≈ 260 |
| Turnkey assembly (setup amortised) | ≈ 450 |
| Off-board kit at volume | ≈ 1,600 |
| Enclosure (MJF PA12 or batch ASA prints) | ≈ 400–800 |
| **Per-unit total** | **≈ ₹10,400–10,800** |
| One-time (batch) | Stencil + setup in the PCBA quote; EMC pre-scan at a test lab ≈ ₹30,000–60,000/day; programming fixture (pogo-pin bed, 3D-printed) ≈ ₹3,000 |

## 4. Programming, test and installation equipment (one-time)

| Item | INR |
|------|-----|
| Multimeter (UT61E+ class) | 5,000 |
| Bench supply 0–30 V 3 A | 8,000–25,000 |
| Micro-Fit ratchet crimper (generic) / genuine Molex 63819-0000 | 2,500 / ≈ 20,000 |
| Nordic PPK2 (µA profiling) | 8,000 |
| Reference OBD tool (OBDLink SX or equivalent) | 4,000–6,000 |
| Tag-Connect TC2030-IDC-NL cable (production programming) | 4,000 |
| Oscilloscope 100 MHz (optional but recommended for bring-up) | 28,000 |
| Soldering station + hot-air (rework, inserts) | 6,000–12,000 |
| Trim tools, heat-shrink, misc. | 1,500 |
| **Total without / with oscilloscope** | **≈ ₹39,000–84,000 / ≈ ₹67,000–112,000** |

## 5. Procurement notes
- **Lead-time risk:** SCD41 and MAX-M10S have had allocation periods. Order them first. The approved alternate for the GNSS is the
  u-blox MAX-M10M, which differs in variant suffix only. Re-check the footprint.
- **Counterfeit risk:** buy ICs only from franchised distributors (Mouser, DigiKey, element14, Arrow) or the manufacturer. Never source
  the ESP32 module, GNSS or Sensirion sensors from marketplace sellers.
- **MSL handling:** ESP32-S3-WROOM-1, MAX-M10S and SCD41 are moisture-sensitive. Keep them in dry packs and tell the assembler.
- **Compliance (India):** the ESP32-S3-WROOM-1 module carries an ETA (WPC) certificate. A product built on it for personal use
  needs no separate approval. **Selling** units needs WPC ETA registration, and for LTE variant C, TEC/MTCTE considerations. Check
  current DoT rules before any commercial distribution.

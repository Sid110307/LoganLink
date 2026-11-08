# 09. Mechanical Engineering and Enclosure

Source CAD: `mechanical/enclosure.scad` (parametric OpenSCAD). Every dimension below derives from its parameters.
Change the parameters, not the numbers here.

## 1. Installation location

**Selected:** behind the lower dashboard trim on the driver's side, above the OBD socket and near the cabin fuse box.
It is fixed to the steel dash cross-member bracket or an existing harness bracket with an M4 self-tapper and 2 cable ties.

| Criterion | Assessment |
|-----------|------------|
| Temperature | Shaded from direct sun; parked peak ≈ 60–70 °C in Indian summer (vs. 85–95 °C on the dash top) |
| Wiring | Under 400 mm to the OBD socket, fuse box and ignition circuits |
| Radio | BLE from under the plastic fascia reaches the cabin easily (−55 to −70 dBm at the driver's seat). Keep the antenna end ≥ 20 mm from the steel cross-member |
| GNSS | Not suitable for a patch antenna, so an external active antenna goes on the dash top (§7) |
| Safety | Clear of the steering column, pedal box, knee-airbag zone (none on this car), and yellow SRS connectors |
| Service | The lower trim panel unclips; module removable in < 5 min |

Orientation: **connector J1 facing the car's front** (X+ of the IMU points forward, as `sensors.c` expects). The main face is
vertical or horizontal. The firmware auto-levels gravity, but the X axis must point forward or backward. If it's mounted
reversed, set `imu_orientation` in the app.

## 2. Overall dimensions (from the SCAD parameters)

| Item | Value |
|------|-------|
| PCB | 80 × 55 × 1.6 mm, 4 × M2.5 holes at 4 mm insets |
| Body (base + lid) | **86.0 × 61.0 × 19.5 mm** |
| With mounting ears | 110 × 61 mm footprint |
| Wall / floor / lid | 2.4 / 2.2 / 2.2 mm |
| PCB-to-wall gap | 0.6 mm |
| Clearance above PCB | 10.0 mm (tallest part: Micro-Fit RA header / SCD41 6.5 mm) |
| Clearance below PCB | 3.5 mm (THT leads, probe pads) |
| Mass (ASA, 30 % infill) | ≈ 38 g enclosure, ≈ 70 g complete |

## 3. Construction

```
        LID (prints face-down)                     ┌ vent slots (6 × 2.2 mm) over SCD41/SGP41, PTFE mesh behind
   ┌──────────────────────────────────────────┐    │  light pipe Ø3.0 · PAIR pinhole Ø1.6
   │  ▒▒ ▒▒ ▒▒ ▒▒ ▒▒ ▒▒            ○  ·       │◀───┘
   └─┬──────┬───────────────────────────┬──┬──┘  hold-down ribs (2 × 15 mm, 0.2 mm preload)
     │ rib  │                           │rib│
   ══╪══════╪═════════ PCB ═════════════╪══╪══   on 4 standoffs, M2.5 × 6 screws into heat-set inserts
   ┌─┴──┐                                  ┌┴─┐
   │ boss│  standoffs Ø6.5 × 3.5           │  │  corner bosses Ø7 with M2.5 inserts for the lid
   └────┴──────────────────────────────────┴──┘
   [ear]  BASE: J1 opening (bottom edge), USB-C (right), SHT45 louvres (left), Ø2 drain hole   [ear]
```

- **Fasteners:** 8 × M2.5 brass heat-set inserts (Ø3.6 mm bore, 5 mm deep). 4 × M2.5 × 6 pan-head screws (PCB),
  4 × M2.5 × 12 (lid). All stainless A2. **No metal within the antenna zone** (x 26–54 mm along the top edge).
- **Retention:** PCB screwed at 4 points + 2 lid ribs. First board-mode resonance is estimated > 400 Hz, above the ISO 16750-3
  passenger-car body random-vibration band (10–1,000 Hz, with most energy below 200 Hz).
- **J1 latch:** the Micro-Fit positive latch takes harness pull-out loads. The housing opening is 0.5 mm oversize, so the
  enclosure doesn't preload the header. The header pegs carry no load.
- **Strain relief:** the harness is cable-tied to the enclosure's ear slot within 40 mm of J1. The antenna cable exits through a
  6 mm slot between base and lid with a 5 mm service loop inside, so the U.FL never carries pull loads (U.FL is rated for only
  a few mating cycles and very low pull force).

## 4. Environmental design

| Concern | Measure |
|---------|---------|
| Heat | ASA (Tg ≈ 100 °C) or PC-ABS; no PETG/PLA (softens at 70–80 °C). Board dissipation 0.5 W, so no heat sink is needed |
| Humidity / condensation | Sensor vents give natural exchange. A Ø2 mm drain at the lowest point when mounted connector-down. Conformal coat optional (doc 05 §5) |
| Dust | 6-slot vent backed by **PTFE vent membrane** (e.g., GORE-style adhesive vent, or acoustic mesh), IP5X-class dust ingress on the vent. Overall enclosure rating IP40 |
| Sensor accuracy | SHT45 sits on the PCB thermal peninsula next to the left-wall louvres. The SCD41 needs free air exchange: total vent open area ≈ 185 mm² |
| UV | Under-dash location, so no UV exposure. If dash-top mounted, use ASA (UV-stable) |

## 5. Materials and manufacturing routes

| Route | Material | When | Notes |
|-------|----------|------|-------|
| **FDM 3D print (recommended for 1–25 units)** | **ASA** (e.g., Polymaker ASA, eSun ASA) | Prototype and small batch | Heat- and UV-resistant, low warp vs. ABS with an enclosure |
| FDM alternative | PC-ABS, PC-CF | Higher temperature margin | Needs a heated chamber |
| SLS / MJF | PA12 (HP MJF) | 25–500 units, best fit/finish | Indian bureaus (e.g., in Bengaluru and Pune) quote ≈ ₹600–900 per set |
| Injection moulding | PC/ABS UL94-V0 | > 1,000 units | Tooling ≈ ₹2.5–4 lakh for a 2-cavity aluminium tool |
| CNC | Aluminium 6061 | Not recommended | Kills BLE; would need a plastic RF window |

## 6. 3D-printing workflow (FDM, ASA)

| Step | Setting |
|------|---------|
| Export | `openscad -D 'part="base"' -o base.stl mechanical/enclosure.scad` (also `lid`, `pipe`) |
| Orientation | **Base:** floor down. **Lid:** face down (as exported), so vent slots and pipe bore print clean. **Pipe:** upright, in clear PETG/PC (or buy a Bivar light pipe) |
| Supports | Base: support only under the J1 and USB openings (bridges ≤ 32 mm can bridge without support on a tuned printer). Lid: none |
| Nozzle / layer | 0.4 mm / 0.2 mm; walls 4 perimeters (≈ 1.7 mm), top/bottom 5 layers |
| Infill | 30 % gyroid |
| Temperatures | Nozzle 250 °C, bed 100 °C, **enclosed printer**, part cooling 10–20 % |
| Adhesion | PEI sheet + brim 5 mm on the base (ASA corners lift) |
| Shrink compensation | 0.5 % XY scaling for ASA (check with the 80 × 55 PCB fit) |
| Post-process | Remove brim, deburr openings, press **heat-set inserts** with a soldering iron at 240 °C (insert tip), let cool 2 min. Optional: wet-sand 400/800 + acetone vapour polish for a satin finish (ASA polishes like ABS) |
| Fit checks | PCB drops onto standoffs without force. USB-C plug engages fully. Micro-Fit receptacle latches. Lid closes with ribs touching the PCB (0.2 mm preload) |

Tolerances: FDM ±0.2 mm typical. Openings are designed +0.5 mm oversize. Insert bores: verify against the insert vendor's
recommended hole (Ø3.6 mm is typical for M2.5 × 5.7).

## 7. GNSS antenna placement

- An active patch antenna (e.g., 28 × 24 mm, adhesive, 3 m RG174) sits **on the dash top, centred, ≥ 50 mm behind the
  windscreen base**, under the dark frit band if possible, so it's invisible from outside.
- The cable is routed down the A-pillar trim edge or the dash-centre gap, away from airbag deployment paths (the reference car
  has a driver airbag only, in the steering wheel. Still keep the A-pillar route behind trim, not over it).
- Athermal (metallised heat-reflective) windscreen films attenuate GNSS by 10–20 dB. If one is fitted, test the fix time before
  finalising the location.

## 8. Assembly and disassembly

1. Press 8 inserts into the base. 2. Fit the light pipe into the lid. 3. Apply the vent membrane inside the lid. 4. Stick the
pairing-QR label inside the lid. 5. Connect the GNSS antenna pigtail U.FL to J3 and route it out of the slot with a service loop. 6. Place
the PCB and fit 4 × M2.5 × 6. 7. Close the lid and fit 4 × M2.5 × 12 (0.3 N·m, plastic, don't over-torque). Disassembly is the reverse.
The inserts tolerate > 10 cycles.

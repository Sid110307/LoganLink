# 11 — Installation and Vehicle Integration (reference car: Renault X90, K7M 1.6)

> **Safety first.** Work with the ignition off and the key out. For the optional taps (§6), disconnect the battery negative terminal
> first. Before you do, make sure you have the radio security code if your head unit needs one. Stay clear of yellow airbag
> connectors and the steering column. Allow 1.5–2 h for a first installation.

## 1. Installation architecture

```
   DASH TOP:  [GNSS active antenna] ──RG174 (behind A-pillar trim edge / dash gap)──┐
                                                                                    │
   UNDER DASH (driver side, behind lower trim):                                     │
                                                                                    ▼
   OBD socket ◄── female end of splitter (workshop access kept)        ┌────────────────────┐
        ▲                                                              │  LoganLink module  │ ← tied to dash bracket
        └── male J1962 plug of splitter ── 400 mm harness ─[3 A fuse]─►│  J1 Micro-Fit 10   │
                                                                       └────────────────────┘
   Optional taps (fuse box / harness):  IGN (+APC) ─[0.5 A PTC]─┐            ▲
                                        REV lamp feed ─[0.5 A PTC]─┼─────────┘ (pins 5, 9, 7 of J1)
                                        Fuel-sender wire ───────────┘
```

## 2. Tools and materials

| Item | Spec |
|------|------|
| Trim tools | Plastic pry set |
| Multimeter | CAT II/III, 0.1 % DC accuracy, for commissioning and VBAT calibration |
| Crimp tool | Molex 63819-0000 (genuine) or an equivalent Micro-Fit ratchet crimper (e.g., Engineer PA-09 for small runs) |
| Soldering iron + adhesive heat-shrink | For the taps |
| OBD splitter | J1962 male → J1962 female + 400 mm pigtail breakout with **pins 4, 5, 6, 7, 14, 16 populated** (flat-ribbon "Y" type) |
| Wire | 0.5 mm² (20 AWG) and 0.35 mm² (22 AWG), **TXL or GXL** automotive cross-linked insulation (125 °C), or FLRY-B |
| Fusing | Inline ATO mini-blade holder (waterproof) + **3 A** fuse (VBAT); 2 A for AUX; 0.5 A resettable PTC (e.g., Littelfuse 1206L050 in-line on a small board, or 0.5 A ATO) for taps |
| Protection | 7 mm corrugated split loom, Tesa 51036 fleece harness tape, 2.5 × 100 mm cable ties, adhesive tie mounts |

## 3. Pre-installation checks (V4)

1. Remove the lower dash trim on the driver's side and locate the 16-pin diagnostic socket.
2. With the key **out**, measure pin 16 → pin 4: **12.2–12.8 V** means permanent supply is confirmed. If it reads 0 V, pin 16 is
   ignition-switched on this car. That's acceptable, but parking features will be limited, so record it.
3. Pin 4 → body ground: < 0.5 Ω. Pin 5 → pin 4: < 0.5 Ω.
4. Pin 7 → pin 4 with the key **on**: ≈ battery voltage (K-line idle high). This confirms the K-line is present.
5. Pins 6/14: usually not populated on this car. Note what you find.
6. Read and photograph the engine ECU label (engine bay) for V5.

## 4. Harness construction (module side)

| J1 pin | From | Wire | Notes |
|--------|------|------|-------|
| 1 VBAT | Splitter pin 16 | 0.5 mm² red | Inline **3 A** fuse within 100 mm of the splitter |
| 2 GND | Splitter pin 4 | 0.5 mm² black | — |
| 3 GND_SIG | Splitter pin 5 | 0.35 mm² black/white | — |
| 4 K-LINE | Splitter pin 7 | 0.35 mm² white | Twist loosely with pin-3 wire (≈ 30 twists/m) |
| 6 / 8 CAN H/L | Splitter 6 / 14 | 0.35 mm² twisted pair | Fit even if unused: zero cost, and future-proof |
| 5 IGN, 7 FUEL, 9 REV | Taps (§6) | 0.35 mm² | Leave the cavities empty if not used |
| 10 AUX_OUT | Accessory negative | 0.5 mm² blue | Accessory positive from a fused +12 V source (2 A) |

Crimping: strip 3.0 mm, crimp **43030-0007** terminals (20–24 AWG) and pull-test each (≥ 30 N for 20 AWG). Insert until the latch
clicks, then tug-test. Sleeve the whole harness in corrugated loom, with tape at the ends. Label both ends.

## 5. Mechanical installation

1. Temporarily connect everything and run commissioning (§8) **before** final mounting.
2. Mount the module above the OBD socket on the steel dash bracket: one M4 self-tapper through an ear, plus 2 cable ties through the
   ear slots. **J1 faces the front of the car.** The antenna end of the PCB (opposite J1) faces the cabin/footwell, not the steel.
3. Harness: tie it to the existing loom every 150 mm. Keep it ≥ 50 mm from the steering column and pedals. Add a drip loop so condensation
   doesn't run into J1.
4. GNSS antenna: clean the dash top with isopropyl alcohol, then stick the antenna centrally ≥ 50 mm behind the windscreen base, cable
   toward the dash-centre gap. Route the cable under the dash, secure it, and plug the U.FL pigtail in (with the 5 mm service loop inside the enclosure).
5. Plug the splitter into the vehicle socket and secure the splitter's female socket where a mechanic can reach it (tie it near the original
   position). Refit the trim.

## 6. Optional taps

All taps use **solder + adhesive-lined heat-shrink** or a Posi-Tap-type connector. Never use scotch-lock IDC clips.

| Tap | Where (reference car) | How to identify | Protection |
|-----|----------------------|------------------|------------|
| IGN (+APC) | Cabin fuse box: an ignition-switched fuse output (e.g., the radio's switched feed or cluster feed) | Multimeter: 0 V key off, 12 V key on, stays at ≥ 9 V while cranking | 0.5 A PTC at the tap point |
| REV | Reverse-lamp feed: at the reverse switch harness on the gearbox, or the rear lamp connector | 12 V only in reverse with ignition on | 0.5 A PTC |
| FUEL | Fuel-gauge sender signal at the cluster connector or the tank-sender connector (under the rear seat) | Voltage varies with fuel level, key on (record full/reserve readings, V6) | Series 10 kΩ at the tap (module input is 122 kΩ) |
| Coolant probe (alternative to ECU data) | 10 kΩ NTC, clamped to the upper radiator hose near the thermostat housing with a stainless clamp + thermal pad | — | JST-GH lead routed through the bulkhead grommet, away from the exhaust manifold |

Never tap ECU sensor wires (doc 02 §7).

## 7. Electrical precautions

- Fuses go as close to the source as possible. The module fuse (3 A) is below the vehicle fuse for the OBD circuit, so a fault opens ours first.
- Don't route the harness alongside the ignition coil HT leads or the starter cable.
- Ground through OBD pins 4/5 only. **Do not add a separate chassis ground.** A second ground would form a ground loop and invalidate
  the protection-return design.

## 8. Commissioning

| Step | Action | Expected |
|------|--------|----------|
| 1 | Plug in J1 with ignition off | LED: red flash, then green blink every 3 s (STANDBY). Supply current via a DC clamp meter ≤ 20 mA |
| 2 | Install the app and scan the pairing card; start the engine; press PAIR | Phone pairs, app shows *Connected* |
| 3 | Vehicle probe (automatic) | App reports the protocol, ECU ID and supported PIDs, which resolves V1/V2/V5. If none: Tier-1 mode + Signal Mapper |
| 4 | VBAT calibration | Engine off, wait 2 min, measure at OBD pins 16–4 with the DMM, enter the value in the app → reading within ±30 mV |
| 5 | Odometer and fuel | Enter the cluster odometer reading, fuel price, and tank fill state ("Fill to full" at the next fill-up) |
| 6 | Ripple calibration | Idle with ECU RPM available: automatic within 10 min. Without ECU: hold a steady 2,000 rpm on the tachometer, then tap "Calibrate" |
| 7 | IMU orientation check | Drive straight, brake gently: app shows negative longitudinal g. If positive, set "mounted reversed" |
| 8 | GNSS | Fix (3D, ≥ 8 satellites) within 60 s cold, < 5 s hot |
| 9 | Taps (if fitted) | IGN toggles with the key; REV in reverse; fuel calibration points saved |
| 10 | Sleep current | Ignition off, phone away, wait 35 min: current at the fuse ≤ 300 µA (target ≤ 150 µA). Use a DMM in the µA/mA range in place of the fuse, with a 3 A bypass during wake-up |
| 11 | Firmware | Update to the latest release from the app; module reboots and confirms within 60 s |
| 12 | Final | Fix trim; drive 15 min; verify the trip is recorded and the parking location saved |

## 9. Removal (e.g., for sale of vehicle)
Unplug the splitter, cut the tie mounts, remove the taps and insulate the tap points with heat-shrink, then factory-reset the module
(PAIR held 10 s, engine running) before giving it away.

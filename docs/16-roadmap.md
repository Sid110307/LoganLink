# 16 — Roadmap: What Can Be Added Next

Phases: **Near** (weeks, firmware/app only, no new hardware) · **Mid** (months, small hardware add-ons or a board rev) ·
**Long** (major work or new platforms). Effort is for one engineer who already knows this codebase. Costs are hardware per unit (INR).

## 1. Writing to the bus (service functions)

The car's ECUs support **diagnostic routines** beyond reading data: actuator tests, adaptation resets, relearns, and on some ECUs
configuration ("coding"). Workshop tools such as Renault CLIP do this over the same K-line. LoganLink can do it too, but **a bad
write can stall an engine in traffic, leave the idle unstable, or brick an ECU**. So writing is added as a separate, deliberately
narrow capability on top of the read-only guard, never by loosening the guard.

### 1.1 Candidate functions for a K7M Logan (verify each against the ECU's actual service set; IDs are ECU-specific)

| Function | Typical KWP2000 service | Use | Risk | Phase |
|----------|-------------------------|-----|------|-------|
| Freeze-frame and extended DTC status | 0x12 / 0x17 / 0x18 (read) | Better diagnosis | None (read) | Near |
| Clear DTCs | 0x14 | Already implemented (guarded) | Low | Done |
| Idle-speed / throttle-body adaptation reset ("relearn") | 0x31 StartRoutineByLocalId or 0x3B WriteDataByLocalId | After cleaning the throttle body or idle valve, which is common on K7M | Medium: idle hunts until relearned | Mid |
| Fuel-trim (adaptive) reset | 0x31 / 0x3B | After fixing a vacuum leak or injector | Low–medium | Mid |
| Actuator tests: radiator fan relay, fuel pump relay, A/C clutch, purge (canister) valve, idle actuator sweep, injector click test, MIL lamp | 0x30 InputOutputControlByLocalId | Find a dead fan, pump or injector without a workshop | Medium: must never run with the engine running or the car moving | Mid |
| Service-interval reset (if the cluster or UCH supports it on this trim) | ECU-specific | Convenience | Low | Mid |
| Key/remote programming, immobiliser functions | 0x27 SecurityAccess + proprietary | — | **Never** (theft risk) | Forbidden |
| ECU reflash / calibration change ("remap") | 0x34–0x37, 0x27 | — | **Never via LoganLink** | Forbidden |
| Coding / configuration writes (variant coding) | 0x3B to coding IDs | Retrofit options | High: wrong coding can disable functions | Long, if ever |

### 1.2 Safety architecture for writes

1. **Signed service scripts, not free-form bytes.** Each routine is a small declarative script (CBOR) shipped with the app:
   target ECU, exact request bytes, expected positive response, timeouts, preconditions, post-actions, and the *undo* step if one
   exists. Scripts are signed with an offline Ed25519 "service key" (separate from the firmware key). The module verifies the
   signature and loads that one routine into a **one-shot extension** of `vguard`. The extension allows exactly those byte
   sequences, once, then disappears. The base allow-list never grows.
2. **Preconditions enforced on the module** (not just in the app), evaluated immediately before transmission:
   - Actuator tests: RPM = 0, vehicle speed = 0, ignition on (ECU answering), VBAT 12.0–14.8 V.
   - Relearns: whatever the routine requires (often engine *running* at idle, warm ECT > 80 °C, speed = 0, no throttle).
   - A script may only tighten these limits, never relax the module's global rule that **nothing runs while the car is moving**.
3. **Owner plus physical presence.** Owner role, *and* the PAIR button pressed within the last 60 s (proves someone is in the car),
   *and* a typed confirmation in the app showing what will happen and how to stop it.
4. **Dead-man and abort.** Actuator tests run only while the user holds a button in the app. Releasing it, losing the BLE link, or
   a precondition becoming false sends the routine's *stop* request (ReturnControlToECU, 0x30 xx 00) and drops to a default session.
5. **Dry-run first.** Each script runs against the bench **ECU simulator** (an ESP32 dev board emulating KWP responses) in CI, and
   in a "dry-run" mode on the car that sends only the precondition reads.
6. **Full audit log.** Every write request and response, with its preconditions snapshot, goes into the module log (`REC_SERVICE`)
   and syncs to the phone, so you can always see what was changed and when.
7. **Rollback.** Before any adaptation write, the script reads and stores the current value (`ReadDataByLocalId`) so the app can offer
   "restore previous". Routines without a defined undo are marked *irreversible* in the UI.
8. **Permanently forbidden in firmware:** SecurityAccess (0x27) for anything immobiliser- or flash-related, download/upload
   (0x34–0x37), ECU reset during driving, and any message to the immobiliser or airbag ECUs. These are compiled out, not configurable.

**Prerequisite work:** identify the ECU (V5) and obtain its routine IDs. Sources: the Renault service manual for injection
diagnostics, DDT-style community databases (validate every ID on the simulator, then on the car with the engine off). Effort:
2–3 weeks for the framework, then about 1–2 days per routine including validation.

## 2. Vehicle coverage

| Item | Phase | Effort | HW cost |
|------|-------|--------|---------|
| ISO 15765 CAN driver (ISO-TP, 11/29-bit, 250/500 k) for newer cars: same PCB, fit the DNP SN65HVD230Q | Near–Mid | 1–2 weeks | +₹110 |
| Multi-vehicle profiles in app (switch cars, per-car history) | Near | 1 week | — |
| Generic OBD-II "any car" mode (auto-probe all protocols; J1850 PWM/VPW would need an extra PHY) | Mid | 2 weeks (+HW for J1850) | +₹150 |
| Renault/Dacia platform library (Duster, Sandero, Kwid K-line/CAN variants) | Long | ongoing | — |

## 3. Hardware add-ons (rev C / plug-on pods)

| Item | Phase | Notes | Cost |
|------|-------|-------|------|
| **GNSS pod**: Quectel L76K or u-blox MAX-M10 with ceramic patch, on a 4-pin cable to the dash top | Mid | Logs routes without the phone, plus parking location; tow/theft tracking needs LTE too | ₹600–1,800 |
| **LTE Cat-1 variant** (SIMCom A7672S) for remote status, theft/tow alerts, geofence | Mid–Long | Needs a larger PCB or a second board, a SIM plan, and server or MQTT; regulatory work if sold | ₹1,500 + SIM |
| **Cabin air pod** (SCD41 CO₂, SGP41 VOC, SHT45) in the cabin, BLE-linked or wired | Mid | Restores CO₂/drowsiness and occupant-left-behind alerts | ₹3,500 |
| **TPMS**: read 433 MHz aftermarket TPMS valve sensors with a CC1101/SX1276 receiver | Mid–Long | Tyre pressures in the app. Protocols vary by sensor brand | ₹900 + sensors |
| **Coolant/oil temperature probe** inputs | Mid | For data the ECU doesn't expose | ₹200 |
| **Ignition/reverse/accessory taps** with a short harness (rev A features) | Mid | Accessory switching for a dashcam, reverse detection | ₹300 |
| **HUD**: small BLE e-paper/OLED or HUD reflector unit on the dash showing speed/RPM/alerts | Long | Phone-independent display | ₹1,500–3,000 |
| **Dashcam integration**: trigger clip save on harsh/crash events (via the dashcam's Wi-Fi API, or a GPIO on hardwire kits) | Mid | Depends on the dashcam model | — |
| **Remote start / central locking** | — | Not planned: defeats the immobiliser or adds attack surface (doc 10 §5) | — |

## 4. App and platform

| Item | Phase | Effort |
|------|-------|--------|
| Home-screen widgets (battery, fuel, last trip) via Glance | Near | 3–4 days |
| Quick Settings tile + notification controls (start Drive Mode) | Near | 2 days |
| **Android Auto** via the Car App Library. The *IoT* category template (Android Auto 14+/Car App API 7+) shows vehicle status cards. Check the current category policy before publishing; sideloaded/dev builds work for personal use | Mid | 1–2 weeks |
| Wear OS tile: battery, parking location, alerts | Mid | 1 week |
| Voice: Google Assistant App Actions ("Hey Google, where is my car") | Near | 3 days |
| Wi-Fi SoftAP OTA (`CMD_WIFI_OTA`) for 10× faster updates | Near | 1 week |
| Optional cloud sync and backup (self-hosted Supabase/Firebase or your own server), multi-phone history merge | Mid | 2–3 weeks |
| ML insights: on-phone driving-style classifier (TFLite), fuel-economy coaching, battery remaining-life model trained on crank history | Mid–Long | 3–6 weeks |
| AI assistant over local data (opt-in): "why did my mileage drop?" with an LLM API, sending aggregates only | Mid | 1–2 weeks |
| Trip sharing and expense export (fuel/cost CSV/PDF for reimbursements) | Near | 3 days |
| Fleet/family dashboard (several cars, several drivers) | Long | 1–2 months |
| iOS app (SwiftUI) using the same protocol | Long | 4–6 weeks |

## 5. Firmware

| Item | Phase |
|------|-------|
| SGP/CAN/fuel-sender drivers (if those add-ons are fitted) | Near–Mid |
| Adaptive PID scheduler (poll faster what the screen shows) | Near |
| Misfire estimate from crank-speed fluctuation via ripple/IMU fusion | Mid |
| Secure Boot / flash-encryption production provisioning script | Near |
| ECU simulator firmware for CI and bus-write validation (§1.2 step 5) | Near (prerequisite for §1) |

## 6. Suggested order
1. Near: CAN driver + ECU simulator + Wi-Fi OTA + widgets/App Actions (no new hardware).
2. Then service-script framework with **read-only** freeze-frame, then the first low-risk routine (fan relay test, engine off).
3. GNSS pod and cabin pod as plug-on accessories.
4. Android Auto IoT, Wear OS, ML insights.
5. LTE variant only if remote tracking is truly needed.

# 11. Roadmap

Phases: **Near** (weeks, firmware and app only), **Mid** (months, small hardware add-on or board revision), **Long** (major work).
Effort is for one engineer who knows this codebase. Costs are hardware per unit.

## 1. Writing to the bus

ECUs offer routines beyond reading: actuator tests, adaptation resets, relearns, coding. A bad write can stall an engine in traffic,
leave the idle unstable or brick an ECU. Writes are added as a separate, narrow capability on top of the read-only guard. The guard
itself is never loosened.

### Candidate functions (IDs are ECU-specific; verify each)

| Function | KWP2000 service | Risk | Phase |
|----------|-----------------|------|-------|
| Freeze frame, extended DTC status | 0x12, 0x17, 0x18 (read) | None | Near |
| Clear DTCs | 0x14 | Low | Done (guarded) |
| Idle or throttle-body adaptation reset | 0x31 or 0x3B | Medium: idle hunts until relearned | Mid |
| Fuel-trim adaptation reset | 0x31 or 0x3B | Low to medium | Mid |
| Actuator tests: fan relay, fuel pump relay, A/C clutch, purge valve, injector click, MIL | 0x30 | Medium: never with the engine running or the car moving | Mid |
| Coding writes | 0x3B to coding IDs | High | Long, if ever |
| Key or immobiliser programming | 0x27 + proprietary | Theft | Forbidden |
| ECU reflash or remap | 0x34 to 0x37, 0x27 | Bricking | Forbidden |

### Safety architecture

1. **Signed service scripts, not free bytes.** Each routine is a declarative CBOR script: target ECU, exact request bytes, expected
   response, timeouts, preconditions, stop step, undo step. Signed with an offline Ed25519 service key, separate from the firmware
   key. The dongle verifies it and loads it as a one-shot extension of `vguard` that allows exactly those bytes once, then disappears.
2. **Preconditions enforced on the dongle**, checked immediately before transmission. Actuator tests: RPM 0, speed 0, ECU
   answering, VBAT 12.0 to 14.8 V. Relearns: as the routine requires (often warm idle, speed 0). A script may tighten limits, never
   relax the global rule that nothing runs while the car moves.
3. **Owner and physical presence.** Owner role, PAIR pressed within the last 60 s, and a typed confirmation that states what will
   happen and how to stop it.
4. **Dead-man.** Actuator tests run only while the user holds a button. Release, link loss or a failed precondition sends the stop
   request (ReturnControlToECU) and drops to the default session.
5. **Dry run first.** Every script runs in CI against an ECU simulator (ESP32 dev board emulating KWP responses), then on the car in a
   dry-run mode that sends only the precondition reads.
6. **Audit log.** Every write request and response with its precondition snapshot is logged (`REC_SERVICE`) and synced.
7. **Rollback.** Before an adaptation write the script reads and stores the current value so the app can restore it. Routines with no
   undo are marked irreversible.
8. **Compiled out permanently:** SecurityAccess for immobiliser or flash, download and upload (0x34 to 0x37), ECU reset while
   driving, any message to immobiliser or airbag ECUs.

Prerequisite: identify the ECU and its routine IDs from service documentation, validate each on the simulator, then on the car with
the engine off. Effort: 2 to 3 weeks for the framework, then 1 to 2 days per routine.

## 2. Vehicle coverage

| Item | Phase | Effort | Cost |
|------|-------|--------|------|
| ISO 15765 CAN driver (ISO-TP, 11/29-bit, 250/500 kbit/s): fit the CAN transceiver on the existing footprint | Near to Mid | 1 to 2 weeks | +₹110 |
| Multiple cars in the app | Near | 1 week | |
| J1850 PWM/VPW for US-market cars | Mid | 2 weeks + extra interface | +₹150 |

## 3. Hardware add-ons

| Item | Phase | Notes | Cost |
|------|-------|-------|------|
| GNSS pod on a short cable | Mid | Routes and parking without the phone | ₹600 to 1,800 |
| Cabin air pod (CO2, VOC, temperature, humidity) | Mid | CO2 drowsiness and occupant-left-behind alerts | ₹3,500 |
| Ignition, reverse and accessory taps | Mid | Dashcam switching, reverse detection | ₹300 |
| TPMS receiver for 433 MHz aftermarket sensors | Mid to Long | Protocols vary by brand | ₹900 + sensors |
| LTE Cat-1 variant | Mid to Long | Remote status, tow and theft alerts; needs SIM, server, bigger board | ₹1,500 + SIM |
| Remote start, central locking | Never | Defeats the immobiliser or adds attack surface | |

## 4. App and platform

| Item | Phase | Effort |
|------|-------|--------|
| Local-ID mapper screen (build `lidmap` from probe dumps) | Near | 1 week |
| Home-screen widgets (battery, fuel, last trip) | Near | 3 to 4 days |
| Google Assistant App Actions ("where is my car") | Near | 3 days |
| Android Auto status cards (Car App Library, IoT category) | Mid | 1 to 2 weeks |
| Wear OS tile | Mid | 1 week |
| Self-hosted backup and multi-phone history merge | Mid | 2 to 3 weeks |
| On-phone driving-style and battery-life models | Mid to Long | 3 to 6 weeks |
| iOS app on the same protocol | Long | 4 to 6 weeks |

## 5. Firmware

| Item | Phase |
|------|-------|
| ECU simulator firmware for CI (prerequisite for section 1) | Near |
| Production provisioning script for Secure Boot and flash encryption | Near |
| Adaptive PID scheduler (poll faster what the screen shows) | Near |
| Misfire estimate from crank-speed fluctuation | Mid |

## 6. Order

1. CAN driver, ECU simulator, provisioning script, local-ID mapper, widgets.
2. Service-script framework with read-only freeze frame, then the first low-risk routine (fan relay test, engine off).
3. GNSS and cabin pods.
4. Android Auto, Wear OS, on-phone models.
5. LTE variant only if remote tracking is required.

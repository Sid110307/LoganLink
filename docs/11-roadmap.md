# 11. Roadmap

Phases: **Near** (weeks, firmware and app), **Mid** (months, small add-on or board revision), **Long** (major work). Effort is for one
engineer who knows this codebase; costs are hardware per unit.

## 1. Writing to the bus

Writes are a separate, narrow capability on top of the read-only guard; the guard itself is never loosened.

| Function | KWP2000 service (IDs are ECU-specific) | Risk | Phase |
|----------|-----------------|------|-------|
| Freeze frame, extended DTC status | 0x12, 0x17, 0x18 (read) | None | Near |
| Clear DTCs | 0x14 | Low | Done (guarded) |
| Idle or throttle-body adaptation reset | 0x31 or 0x3B | Medium: idle hunts until relearned | Mid |
| Fuel-trim adaptation reset | 0x31 or 0x3B | Low to medium | Mid |
| Actuator tests: fan relay, fuel pump relay, A/C clutch, purge valve, injector click, MIL | 0x30 | Medium: never with the engine running or the car moving | Mid |
| Coding writes | 0x3B to coding IDs | High | Long, if ever |
| Key or immobiliser programming | 0x27 + proprietary | Theft | Forbidden |
| ECU reflash or remap | 0x34 to 0x37, 0x27 | Bricking | Forbidden |

Safety rules:

1. **Signed scripts, not free bytes.** Each routine is a CBOR script (target ECU, exact request bytes, expected response, timeouts,
   preconditions, stop step, undo step), signed with an offline Ed25519 service key separate from the firmware key. The dongle
   verifies it and loads it as a one-shot `vguard` extension that allows exactly those bytes once.
2. **Preconditions on the dongle**, checked just before transmission. Actuator tests: RPM 0, speed 0, ECU answering, VBAT 12.0 to
   14.8 V. Relearns: as the routine needs. A script may tighten limits, never allow anything while the car moves.
3. **Owner and presence**: owner role, PAIR pressed within 60 s, typed confirmation stating what happens and how to stop it.
4. **Dead-man**: actuator tests run only while a button is held. Release, link loss or a failed precondition sends
   ReturnControlToECU and drops to the default session.
5. **Dry run**: CI against an ECU simulator (ESP32 dev board emulating KWP), then on the car sending only the precondition reads.
6. **Audit**: every write request, response and precondition snapshot logged (`REC_SERVICE`) and synced.
7. **Rollback**: adaptation values read and stored before writing; routines without undo are marked irreversible.
8. **Compiled out**: SecurityAccess for immobiliser or flash, download and upload (0x34 to 0x37), ECU reset while driving, any
   message to immobiliser or airbag ECUs.

Each routine: identify IDs from service documentation, validate on the simulator, then on the car engine off. Effort: 2 to 3 weeks
for the framework, 1 to 2 days per routine.

## 2. Vehicle coverage

| Item | Phase | Effort | Cost |
|------|-------|--------|------|
| ISO 15765 CAN (ISO-TP, 11/29-bit, 250/500 kbit/s), fit the CAN transceiver | Near to Mid | 1 to 2 weeks | +₹110 |
| Multiple cars in the app | Near | 1 week | |
| J1850 PWM/VPW for US-market cars | Mid | 2 weeks + extra interface | +₹150 |

## 3. Hardware add-ons

| Item | Phase | Notes | Cost |
|------|-------|-------|------|
| Cabin air pod (CO2, VOC, temperature, humidity) | Mid | Drowsiness and occupant-left-behind alerts | ₹3,500 |
| Ignition, reverse and accessory taps | Mid | Dashcam switching, reverse detection | ₹300 |
| TPMS receiver for 433 MHz aftermarket sensors | Mid to Long | Protocols vary by brand | ₹900 + sensors |
| LTE Cat-1 variant | Mid to Long | Remote status, tow and theft alerts; needs SIM, server, bigger board | ₹1,500 + SIM |
| Remote start, central locking | Never | Defeats the immobiliser or adds attack surface | |

## 4. App, platform, firmware

| Item | Phase | Effort |
|------|-------|--------|
| Local-ID mapper screen (build `lidmap` from probe dumps) | Near | 1 week |
| Home-screen widgets (battery, fuel, last trip) | Near | 3 to 4 days |
| Google Assistant App Actions ("where is my car") | Near | 3 days |
| ECU simulator firmware for CI (needed for section 1) | Near | |
| Production provisioning script for Secure Boot and flash encryption | Near | |
| Adaptive PID scheduler (poll faster what the screen shows) | Near | |
| Android Auto status cards (Car App Library, IoT category) | Mid | 1 to 2 weeks |
| Wear OS tile | Mid | 1 week |
| Self-hosted backup and multi-phone history merge | Mid | 2 to 3 weeks |
| Misfire estimate from crank-speed fluctuation | Mid | |
| On-phone driving-style and battery-life models | Mid to Long | 3 to 6 weeks |
| iOS app on the same protocol | Long | 4 to 6 weeks |

## 5. Order

1. CAN driver, ECU simulator, provisioning script, local-ID mapper, widgets.
2. Service-script framework with read-only freeze frame, then the first low-risk routine (fan relay test, engine off).
3. Cabin pod.
4. Android Auto, Wear OS, on-phone models.
5. LTE variant only if remote tracking is required.

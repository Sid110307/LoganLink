# 09. Install and Test

Tools: multimeter with µA resolution, soft cable tie, phone with the app; 2-channel oscilloscope for the K-line timing test.

## Install in the car

1. **Find the socket**: 16-pin trapezoid, usually under the dash on the driver's side. Check the space against
   [05 Enclosure](05-enclosure.md).
2. **Check pins, key out.** Pin 16 to pin 4: 12.2 to 12.8 V. 0 V means pin 16 is ignition-switched on this car: driving works, but
   no cranking log or parked battery watch. Pin 4 to body < 0.5 Ω. Key on: pin 7 to pin 4 near battery voltage (K-line idle high).
3. **Plug in** with the key out. Green LED blinks every 3 s (STANDBY). The plug must sit fully home and not rock.
4. **Pair**: [08 App, Pairing](08-app.md#pairing). The probe runs automatically; Garage, LoganLink module then shows protocol, ECU
   ID, PIDs and VIN. If no ECU answers, battery and accelerometer features still work.
5. **Vehicle data**: Garage, Vehicle: odometer, petrol price, tank size, plate.
6. **Calibrate battery voltage**: engine off, wait 2 min, measure across the battery terminals, enter it in Garage, LoganLink module,
   Battery reading. The app must then agree within ±30 mV.
7. **Sleep current**: find the cabin fuse feeding pin 16 (pulled, pin 16 reads 0 V). Dongle unplugged, meter (mA range) across the
   empty fuse slot: note the car's own draw. Plug the dongle in, phone Bluetooth off or away, wait 10 min for SLEEP (LED off), read
   again. Difference ≤ 150 µA (design 61 µA). Refit the fuse.
8. **Drive 15 min**, switch off, wait 1 min: the app shows the trip and parking spot.

Before passing the dongle on: factory reset (hold PAIR 10 s, engine running).

## Acceptance tests

| Test | Method | Pass |
|------|--------|------|
| Sleep current | Step 7, or bench supply at 12.6 V with a µA meter, 1 h after SLEEP entry | ≤ 150 µA (design 61 µA); HIBERNATE ≤ 80 µA |
| Parked battery impact | 14 days parked, resting voltage drop with and without the dongle, same battery | ≤ 0.05 V attributable to the dongle |
| Battery voltage accuracy | Bench supply at 9, 12, 15 V against the multimeter, after calibration | ±30 mV (±50 mV at temperature extremes) |
| K-line timing | Scope on pin 7 during fast init and a PID request | TiniL 25 ± 1 ms, TWuP 50 ± 1 ms, P3 ≥ 55 ms, bit time 96.15 µs ± 1 % |
| Diagnostic accuracy | Idle and 2,000 rpm, cold and warm, against a known-good scan tool | Same PID values ± 1 LSB; same fault-code list |
| Workshop coexistence | Scan tool on a splitter while the dongle runs | Dongle silent within one request; scan tool works normally |
| Reconnect | Phone in pocket, 100 ignition cycles | ≥ 98 % connected and authenticated within 10 s |
| Log sync | Sync 10,000 records | No lost or duplicate `seq` |
| OTA recovery | 50 BLE updates, 10 with the link dropped mid-transfer, 5 with power cut mid-write | 100 % recover; rollback works; no bricked unit |
| Vehicle guard | Scripted sweep of SIDs 0x00 to 0xFF against a K-line simulator | No non-allow-listed SID transmitted |
| Thermal | 85 °C chamber, DRIVE load, 4 h; then 95 °C storage, 48 h | No reset; thermal policy engages; no shell deformation |
| Load dump | ISO 16750-2 Test B, 35 V (test house) | No TVS conduction, dongle keeps running |

Record each result with date, firmware hash, hardware serial and raw data.

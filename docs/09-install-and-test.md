# 09. Install and Test

## Tools

Multimeter with a µA or 1 µA-resolution mA range, a soft cable tie, the phone with the app installed. For the K-line timing test, a
2-channel oscilloscope.

## Install in the car

1. **Find the socket.** It is a 16-pin trapezoid connector, usually under the dash on the driver's side, near the fuse box or in the
   glovebox area. Check the free space in front of it against the shell dimensions in [05 Enclosure](05-enclosure.md).
2. **Check pin 16 with the key out.** Pin 16 to pin 4 must read 12.2 to 12.8 V. 0 V means pin 16 is ignition-switched on this car:
   the dongle still works while driving, but it cannot log cranking or watch the battery while parked. Pin 4 to body: < 0.5 Ω.
   With the key on, pin 7 to pin 4 reads close to battery voltage (K-line idle high).
3. **Plug in** with the key out. The LED blinks green every 3 s (STANDBY). Check it sits fully home and square; the plug
   should not rock in the socket.
4. **Pair.** Start the engine and follow [08 App, Pairing](08-app.md#pairing). The probe runs automatically; the Module screen then
   shows protocol, ECU ID, supported PIDs and VIN. If no ECU answers, the battery and accelerometer features still work.
5. **Enter vehicle data.** Garage, Vehicle: odometer from the cluster, petrol price, tank size, plate.
6. **Calibrate battery voltage.** Engine off, wait 2 min, measure across the battery terminals with the multimeter, enter the reading
   in Garage, LoganLink module, Battery reading. The app reading must then agree within ±30 mV.
7. **Check sleep current.** Find the cabin fuse that feeds pin 16 (pulling it makes pin 16 read 0 V). With the dongle unplugged,
   connect the multimeter (mA range) across the empty fuse slot and note the car's own draw. Plug the dongle in, switch Bluetooth off
   on the phone or walk away, wait 10 min for SLEEP (LED off), read again. The difference is the dongle's sleep current: ≤ 150 µA,
   design value 61 µA. Refit the fuse.
8. **Drive 15 min**, switch off, wait 1 min. The app must show the trip and the parking spot.

Removal: unplug, then factory-reset (hold PAIR 10 s with the engine running) before passing the dongle on.

## Acceptance tests

| Test | Method | Pass |
|------|--------|------|
| Sleep current | Step 7 above, or a bench supply at 12.6 V with a µA meter, 1 h after SLEEP entry | ≤ 150 µA (design 61 µA); HIBERNATE ≤ 80 µA |
| Parked battery impact | 14 days parked, compare resting voltage drop with and without the dongle on the same battery | ≤ 0.05 V attributable to the dongle |
| Battery voltage accuracy | Bench supply at 9, 12, 15 V against the multimeter, after calibration | ±30 mV (±50 mV at temperature extremes) |
| K-line timing | Scope on pin 7 during fast init and a PID request | TiniL 25 ± 1 ms, TWuP 50 ± 1 ms, P3 ≥ 55 ms, bit time 96.15 µs ± 1 % |
| Diagnostic accuracy | Idle and 2,000 rpm, cold and warm, against a known-good scan tool | Same PID values ± 1 LSB; same fault-code list |
| Workshop coexistence | Connect a scan tool on a splitter while the dongle runs | Dongle silent within one request; scan tool works normally |
| Reconnect | Phone in pocket, 100 ignition cycles | ≥ 98 % connected and authenticated within 10 s |
| Log sync | Sync 10,000 records | No lost or duplicate `seq` |
| OTA recovery | 50 BLE updates, 10 with the link dropped mid-transfer, 5 with power cut mid-write | 100 % recover; rollback works; no bricked unit |
| Vehicle guard | Scripted sweep of SIDs 0x00 to 0xFF against a K-line simulator | No non-allow-listed SID transmitted |
| Thermal | 85 °C chamber, DRIVE load, 4 h; then 95 °C storage, 48 h | No reset; thermal policy engages; no shell deformation |
| Load dump | ISO 16750-2 Test B, 35 V (test house) | No TVS conduction, dongle keeps running |

Record each result with date, firmware hash, hardware serial and the raw data.

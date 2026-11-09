# LoganLink

> Connected-Vehicle System for OBD-II supported cars

An OBD-II dongle plus a native Android app for older OBD-II petrol cars. The dongle reads the engine ECU over the K-line
(ISO 9141-2 / ISO 14230 KWP2000), measures the 12 V system (battery, cranking, charging), records driving events with an
accelerometer, logs locally and streams to the phone over Bluetooth LE. The phone supplies GPS, UI, trip history and alerts.

Developed on a 2008 Renault X90-platform sedan with the K7M 1.6.

## Repository

| Path | Contents |
| ------ | ---------- |
| `firmware/` | ESP-IDF v5.3 C firmware |
| `android/` | Android app |
| `hardware/` | PCB and BOM |
| `mechanical/` | Enclosure and print stuff |

## Features

- [x] QR card pairing with the dongle
- [x] Owner + up to 3 drivers with roles
- [x] Live RPM, speed, coolant, MAP, throttle, load, trims, battery, etc.
- [x] Full-screen Drive Mode
- [x] Floating car panel over Maps
- [x] Route map for every trip
- [x] Driving score from harsh braking, acceleration and idling
- [x] Trip summary notifications
- [x] Fuel consumption that self-calibrates with each fill-up
- [x] Fuel log and cost history
- [x] Alerts for overheating, charging faults, overvoltage, etc.
- [x] Charging system check
- [x] Battery health from cranking voltage
- [x] Service reminders by km and time
- [x] Odometer kept up to date by trips
- [x] VIN read and decoded (maker, model year)
- [x] Automatic protocol detection for K-line cars
- [x] Battery voltage calibration
- [x] Offline dongle logging
- [x] GPS speed when the car doesn't report it
- [x] RPM from alternator ripple fallback
- [x] Firmware updates over BT
- [x] Status LED for standby and driving
- [x] Low-power parked mode with hibernation
- [ ] Manufacturer data blocks for older ECUs
- [ ] Fuel level
- [ ] Ignition sensing
- [ ] Multiple cars in one app
- [ ] Home-screen widgets for battery, fuel and last trip
- [ ] "Where is my car" with Google Assistant
- [ ] Freeze frame data for fault codes
- [ ] Idle and fuel-trim adaptation resets
- [ ] Actuator tests: fan, fuel pump, A/C clutch, injectors
- [ ] Android Auto status cards
- [ ] CAN support

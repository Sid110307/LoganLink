# 08. Android App

Kotlin and Jetpack Compose in `android/`. minSdk 26, targetSdk 36. No account, no cloud.

## Build

```bash
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Or open `android/` in Android Studio. Gradle needs JDK 17; the code targets Java 11.

## Pairing

1. Start the engine.
2. Tap "Pair module" and scan the pairing card (or paste the `LL1:...` code). A driver scans the owner's invite QR instead.
3. Press PAIR on the dongle (window open 120 s; on a new dongle with no bonds it is already open).
4. Select `LoganLink-XXXX` and accept the Android pairing request.
5. The app authenticates, sets the time, syncs the log and runs the probe. Then enter odometer, petrol price, tank size and plate in
   Garage, Vehicle.

"Try without a car" on the first screen runs a demo with a simulated car and seeded history.

## Screens

Tabs: Car (status, warnings, parking, last trip, next service, alerts), Live (gauges, last-minute chart), Trips (list, CSV export,
route map, fuel, cost, harsh events, score), Health (fault codes, battery, engine, maintenance), Garage (Vehicle, LoganLink module,
Parking, Fuel log, Maintenance, Drivers, Settings).

- Drive Mode: full-screen speed, RPM, coolant, battery, alerts, Maps next turn; "Float over Maps"; long-press to exit.
- Diagnostics: codes with meaning and severity; owner-only two-step clear, engine off.
- LoganLink module: connection, firmware, probe results, "Detect again", Battery reading, Firmware update, restart.
- Settings: alerts, Drive Mode on engine start, fuel auto-log, car panel over Maps, export, forget module.
- Maintenance items: oil, plugs, filters, timing belt, coolant, brake fluid, by km and time.

## Automatic behaviour

| Feature | Behaviour |
|---------|-----------|
| Connect | Background connect when the car starts (companion device presence, foreground service) |
| Trips | Start with the engine, end after 8 min engine-off. Score counts harsh braking and acceleration per distance; idling costs points only above 60 % of trip time |
| Drive Mode | Opens on engine start if enabled and the app is open |
| Parking | Saved 45 s after engine-off; without a fresh fix (≤ 25 m, under 1 min old) uses the last fix ≤ 20 m under 3 min old |
| Fuel fill-ups | Payment notifications (GPay, PhonePe, Paytm, BHIM, bank SMS) mentioning a petrol pump: litres = amount / petrol price. ₹100 to ₹10,000; duplicates within 15 min ignored. Sent to the dongle as `FILL_UP` |
| Google Maps | While navigating: next turn in Drive Mode, floating car panel (speed, engine, battery, next turn) over Maps |
| Vehicle ID | Plate formatted (for example `KA 01 AB 1234`); VIN gives maker and model year |
| Odometer, service | Advanced by every trip; service items count down by km and days |
| Trip summary | Notification at trip end |

| Alert | Condition | Severity |
|-------|-----------|----------|
| Overheat | Coolant > 105 °C | Stop safely |
| Charging fault | VBAT < 12.8 V with RPM > 1200 for 60 s | Check soon |
| Over-voltage | VBAT > 15.0 V | Stop safely |
| New fault code | Code not seen before | Per code |

Cooldown 10 min per alert, once a day per fault code.

Battery health: resting voltage 35 %, cranking minimum 50 % (8.5 V scores 0, 10.5 V full), recovery time 15 %.

## Permissions

| Permission | Why |
|------------|-----|
| Nearby devices (`BLUETOOTH_SCAN` `neverForLocation`, `BLUETOOTH_CONNECT`) | Find and connect to the module |
| Location (fine) | Parking, routes, GPS speed fallback |
| Notifications | Alerts, trip summary, fill-up confirmations, foreground-service notice |
| Companion device (`REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, `REQUEST_COMPANION_RUN_IN_BACKGROUND`) | Wake the app when the module appears |
| Foreground service (connected device, location) | Hold the link and record the route while driving |
| Notification access (granted in Settings) | Maps turn instructions and payment notifications |
| Display over other apps (granted in Settings) | Car panel over Maps |
| Camera | Not held; the Google code scanner handles QR scanning |

## Architecture

| Package | Contents |
|---------|----------|
| `core/protocol` | Frame codec, CRC-16, LiveV1, log records, pairing card, HMAC auth |
| `core/link` | `VehicleLink`, `BleVehicleLink`, `SimVehicleLink` (demo), scanner |
| `core` (`VehicleHub`) | Connection, sync, trips, parking, alerts, commands |
| `core/data` | SQLite repository, preferences, Keystore key vault, plate and VIN decoding |
| `core/analytics` | Trip score, battery health, insights, fault-code catalogue |
| `core/service` | `DriveService` (foreground, overlay), `PresenceService` (companion device), notifier |
| `core/nav` | Maps notification listener, fuel auto-log |
| `ui` | Navigation, screens, route map, overlay, theme |

UI talks only to `VehicleHub` and the repository, never to BLE.

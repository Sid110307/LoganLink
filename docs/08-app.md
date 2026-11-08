# 08. Android App

Native Android, Kotlin and Jetpack Compose, in `android/`. minSdk 26, targetSdk 36. No account, no cloud.

## Screens

| Screen | Contents |
|--------|----------|
| Onboarding | Pair module (scan the pairing card or a driver invite, then find the module), or "Try without a car" (demo mode with a simulated car and seeded history) |
| Car (tab) | Car status, check-engine warning, driving now, parked location, last trip, next service, alerts |
| Live (tab) | Gauges and tiles, last-minute chart of speed, RPM and battery, button to Drive Mode |
| Trips (tab) | Trip list, CSV export; trip detail with route map, distance, time, fuel, cost, harsh events, score |
| Health (tab) | Fault codes, battery, engine, maintenance summaries |
| Garage (tab) | Vehicle, LoganLink module, Parking, Fuel log, Maintenance, Drivers, Settings, About |
| Drive Mode | Full-screen, large speed, RPM, coolant, battery, alert banner, Google Maps next turn; "Float over Maps"; long-press to exit |
| Diagnostics | Stored codes with plain-English meaning and severity; owner-only two-step clear, engine off |
| Battery | Resting voltage, cranking voltage of recent starts, charging state, health score |
| Engine | Coolant, trims, intake air, load |
| Fuel log | Fill-ups (automatic and manual), spend, real economy |
| Maintenance | Service items by km and time (oil, plugs, filters, timing belt, coolant, brake fluid), countdown |
| Parking | Map of where the car was left, walk there |
| Vehicle | Nickname, registration (auto-formatted, RTO and state shown), make and year from the VIN, odometer, petrol price, tank size |
| Module | Connection, firmware and probe results, "Detect again", battery calibration, firmware update, restart |
| Drivers | Invite a driver by QR, revoke |
| Settings | Appearance, alerts, Drive Mode on engine start, fuel auto-log, car panel over Maps, data export, forget module |

## Automatic features

| Feature | Behaviour |
|---------|-----------|
| Connect | The phone connects in the background when the car starts (companion device presence, foreground service) |
| Trips | Start with the engine. End only after 8 min engine-off, so long signals and quick stops stay one trip. Score counts harsh braking and acceleration per distance; normal idling is not penalised (only trips idling more than 60 % of their time lose a few points) |
| Drive Mode | Opens on engine start when enabled and the app is open |
| Parking | Saved 45 s after engine-off. If GPS is lost (basements), the last accurate fix (≤ 20 m, under 3 min old) is used |
| Fuel fill-ups | Payment notifications from GPay, PhonePe, Paytm, BHIM or bank SMS that mention a petrol pump are logged as fill-ups: litres = amount / petrol price. ₹100 to ₹10,000, duplicates within 15 min ignored. Sent to the dongle for fuel-model calibration |
| Google Maps | While navigating, the next turn shows in Drive Mode, and a floating car panel (speed, engine, battery, next turn) sits over Maps |
| Vehicle ID | Plate typed in any form is formatted (for example `KA 01 AB 1234`) and the RTO and state are shown. The VIN read during the probe gives maker and model year |
| Odometer and service | Advanced by every trip; service items count down by km and days |
| Trip summary | Notification at trip end |

Alerts (10 min cooldown each; fault codes once a day):

| Alert | Condition | Severity |
|-------|-----------|----------|
| Overheat | Coolant > 105 °C | Stop safely |
| Charging fault | VBAT < 12.8 V with RPM > 1200, sustained 60 s | Check soon |
| Over-voltage | VBAT > 15.0 V | Stop safely |
| New fault code | Code not seen before | Per code |

Battery health score: resting voltage 35 %, cranking minimum 50 % (8.5 V scores 0, 10.5 V scores full), recovery time 15 %.

## Permissions

| Permission | Why |
|------------|-----|
| Nearby devices (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) | Find and connect to the module. Scan declared `neverForLocation` |
| Location (fine) | Parking spot, routes, GPS speed fallback |
| Notifications | Alerts, trip summary, fill-up confirmations, the foreground-service notice |
| Companion device (`REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, `REQUEST_COMPANION_RUN_IN_BACKGROUND`) | Wake the app when the car's module appears |
| Foreground service (connected device, location) | Hold the BLE link and record the route while driving |
| Notification access | Read Google Maps turn instructions and payment notifications for fuel auto-log. Granted once in Settings |
| Display over other apps | Floating car panel over Maps. Granted once in Settings |
| Camera (through Google code scanner) | Scan the pairing card and invites; no camera permission is held by the app |

## Architecture

| Package | Contents |
|---------|----------|
| `core/protocol` | Frame codec, CRC-16, LiveV1 decoder, log records, pairing card and HMAC auth |
| `core/link` | `VehicleLink` interface, `BleVehicleLink`, `SimVehicleLink` (demo), scanner |
| `core` (`VehicleHub`) | Connection lifecycle, sync, trip tracking, parking capture, alerts, commands |
| `core/data` | SQLite repository (trips, samples, events, fills, parks, services), preferences, Keystore key vault, plate and VIN decoding |
| `core/analytics` | Trip score, battery health, insights, fault-code catalogue |
| `core/service` | `DriveService` (foreground, overlay), `PresenceService` (companion device), notifier |
| `core/nav` | Maps notification listener, fuel auto-log |
| `ui` | Navigation, screens, components, route map, overlay, theme |

Rule: UI talks only to `VehicleHub` and the repository, never to BLE directly.

## Build

```bash
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Or open `android/` in Android Studio and run on a phone. Requires JDK 17 for Gradle; the code targets Java 11.

## Pairing

1. Start the engine.
2. In the app tap "Pair module" and scan the pairing card from the box (or paste the `LL1:...` code).
3. Press PAIR on the dongle; the pairing window stays open 120 s.
4. Select `LoganLink-XXXX` and accept the Android pairing request.
5. The app authenticates, sets the time, syncs the log and starts the probe. Open Garage, Vehicle to enter odometer, petrol price,
   tank size and plate.

A driver scans the owner's invite QR instead of the card, then pairs the same way while the owner has the window open.

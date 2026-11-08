# 08. Smartphone Application: Product Design and Engineering

> **Implementation note (rev B):** the app is now a native Android app in Kotlin with Jetpack Compose, in `android/`. It replaces
> the Flutter prototype described in §1, §6 and §11 below. The product design, flows, protocol use and data model in this document
> still apply. With rev B hardware, location (parking, routes, speed fallback) comes from the phone's GPS (doc 15 §3).

## 1. Platform and framework

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Framework | **Flutter 3.22+ (Dart 3.4)**, single codebase | One team ships Android + iOS together. Custom gauges and 60 fps animation are Flutter's strength. Mature BLE plugin |
| BLE | `flutter_blue_plus` + thin native shims (§9) | Covers scan/connect/GATT on both OSes. Background features need native OS hooks, which the shims add |
| State | `flutter_riverpod` 2 | Testable, compile-safe dependency graph. Streams from BLE map naturally onto providers |
| Storage | `sqflite_sqlcipher` (AES-256 SQLCipher), key in Keychain/Keystore via `flutter_secure_storage` | Trip and location history is personal data |
| Charts | `fl_chart` | Line, bar and scatter charts with good performance |
| Maps | `google_maps_flutter` (Google Maps SDK) | Best Indian road and POI coverage. Directions via a deep link to the Maps app |
| Notifications | `flutter_local_notifications` | All alerts are generated locally. No server is needed |
| Voice | Android App Actions (`shortcuts.xml` capabilities) + iOS App Intents (Siri), `flutter_tts` for spoken alerts | - |
| Minimum OS | Android 9 (API 28), with full background auto-connect on Android 12+. iOS 15 | Covers > 95 % of active Indian smartphones |

## 2. Information architecture

```
 ┌───────────── Bottom navigation ─────────────┐
 │  Car      Live      Trips     Health   More │
 └────┬────────┬─────────┬─────────┬───────┬───┘
      │        │         │         │       ├── Settings (units, fuel price, alerts, automations)
      │        │         │         │       ├── Drivers & devices (invite, revoke, roles)
      │        │         │         │       ├── LoganLink device (firmware, diagnostics, probe, calibration)
      │        │         │         │       ├── Data (export CSV/GPX, backup, delete)
      │        │         │         │       └── Signal Mapper (advanced: Renault local IDs)
      │        │         │         ├── Diagnostics (DTCs, freeze frame, clear)
      │        │         │         ├── Battery & charging (crank history, health score)
      │        │         │         ├── Engine health (trims, warm-up, idle smoothness)
      │        │         │         └── Maintenance (service items, reminders, log fill-up)
      │        │         ├── Trip list → Trip detail (map, speed/RPM profile, events, score, fuel, cost)
      │        │         └── Analytics (weekly/monthly distance, economy, cost, score trends)
      │        ├── Live dashboard (gauges + tiles)
      │        └── Drive mode (full-screen, 3 values, landscape)
      ├── Overview: hero car card, status chips, parking card, insights, last trip
      └── Parking: map, walk-to-car, share location, parked duration
```

## 3. Key user flows

### 3.1 First-time setup (≈ 90 s)
1. Install the app → welcome (3 panels: what it does, privacy, permissions).
2. Permissions: Bluetooth (Nearby devices on Android 12+), Location "While using" (iOS) / fine location (Android), Notifications.
   Background location is **not** requested. The parking position comes from the module's GNSS.
3. Scan the pairing card QR (`LL1:…`) → the app validates it and derives K_owner.
4. "Start the engine and press PAIR on the module" (an animated illustration shows where).
5. Scan, filtered by service UUID → match the `device_id` from the card against the device's advertised MAC suffix → connect →
   OS pairing dialog (Android "Pair?" / iOS "Bluetooth Pairing Request") → AUTH succeeds.
6. Vehicle setup: confirm the car (petrol preset), enter the odometer reading from the cluster and the fuel price.
   Optional: say which taps are fitted.
7. The module runs the **vehicle probe**, with live progress ("Talking to engine computer… found KWP2000 fast-init, 11 data
   items"). If no ECU answers, the app explains Tier-1 mode clearly and offers the Signal Mapper later.
8. Done → Overview. Android: `CompanionDeviceManager.associate()` runs during step 5 so background presence works.

### 3.2 Daily use (zero touch)
Get in → start the engine → the phone auto-connects in the background (2–4 s) → optional automation (open Drive Mode, start
music) → drive → spoken alerts only if something is wrong → engine off → parking location saved, trip summary notification
("12.4 km · 28 min · 14.8 km/L · ₹78 · score 86").

### 3.3 Problem detected
New DTC or anomaly → notification with severity colour → tap → Diagnostics detail: code, plain-English meaning, likely causes on
the K7M, urgency, "share with mechanic" (PDF/WhatsApp), and a clear (owner, engine off, 2-step) button.

### 3.4 Invite another driver
More → Drivers → Invite → the owner app sends `USER_ADD(id)` → receives K_user → shows an invite QR (expires 10 min) → the
driver's app scans it, then bonds during a pairing window the owner opens.

## 4. Design system: "Graphite & Ember"

| Token | Value | Use |
|-------|-------|-----|
| Background | `#0B0D10` | App background (dark-first: night driving, OLED-friendly) |
| Surface / High | `#14171C` / `#1C2027` | Cards, sheets |
| Hairline | `#262B33` | 1 px borders instead of shadows |
| Text / Dim / Faint | `#E9EDF2` / `#9AA3AE` / `#5E6772` | Hierarchy |
| **Ember** (accent) | `#FF7A3D` | Gauges, primary buttons, selection. One accent only |
| Teal | `#3DD6C6` | Healthy, charging, connected |
| Amber | `#FFC145` | Check soon |
| Red | `#FF4D5E` | Stop safely / critical |
| Blue | `#5AA9FF` | Info, cold engine |
| Type | **Inter** (UI) with **tabular figures** for all live numbers. Display numerals 300 weight, 64–96 pt | Calm, premium, legible at a glance |
| Grid | 4-pt spacing; card radius 20; chip radius 10 | - |
| Motion | 120 ms (taps), 240 ms (transitions), 180 ms ease-out needle smoothing | No bouncy motion. Data animates, chrome doesn't |
| Iconography | Material Symbols Outlined, 1.5 px weight | - |
| Accessibility | Contrast ≥ 4.5:1 for text (WCAG AA), Dynamic Type up to 200 %, colour never the only signal (icons + words) | - |

Light theme exists for daytime parked use. It follows the system setting, except Drive Mode, which is always dark.
Implemented in `app/lib/theme/tokens.dart`.

## 5. Screen specifications

### S1 Overview ("Car")
```
┌───────────────────────────────────────┐
│ Good evening                   ⚙︎      │
│ ┌───────────────────────────────────┐ │
│ │   [ car line illustration ]       │ │  hero card: car silhouette (vector), plate, odometer
│ │   MH 12 AB 1234 · 84,212 km       │ │
│ │  ● Connected   ● Battery 12.6 V   │ │  status chips
│ └───────────────────────────────────┘ │
│ ┌ Parked ───────────────────────────┐ │
│ │ [mini map]  Koregaon Park · 2 h   │ │  tap → Parking
│ │             Walk 4 min  ›         │ │
│ └───────────────────────────────────┘ │
│ ┌ Insight ──────────────────── ⚠ ┐   │  max 2 insight cards (severity-sorted)
│ │ Battery is not fully charging   │   │
│ └─────────────────────────────────┘   │
│ Last trip  12.4 km · 28 min · 86 ★    │
│ Fuel  ≈ 42 %  · range ≈ 310 km        │
└───────────────────────────────────────┘
```

### S2 Drive Mode (landscape, full screen, keep-awake)
Large speed (96 pt) in the centre, RPM arc around it, coolant and battery as two small values. Alert banner on top (red/amber).
Nothing to tap except Exit (long-press). Auto-opens when an engine start is detected, if enabled.

### S3 Live dashboard
Two arc gauges (speed, RPM with redline 5,750) and tile grids for Engine, Electrical & economy, and Cabin. Implemented:
`app/lib/ui/live_dashboard.dart`. The connection pill shows *Connected · ECU / Connected / Connecting / Offline*. A ≈ prefix marks estimated values.

### S4 Trips
List grouped by day: distance, duration, economy, cost, score chip. Trip detail: route map coloured by speed; speed and RPM chart
(scrubbable, synchronised with the map marker); events pinned on the route; fuel and cost; score breakdown.

### S5 Health hub
Four cards: **Diagnostics** (MIL, DTC count), **Battery** (health 0–100, last crank min V, trend sparkline), **Engine** (LTFT
trend, warm-up time, idle smoothness), **Maintenance** (next due item, km/days remaining).

### S6 Parking
Full map, car pin (module GNSS fix with accuracy circle), phone location, "Walk there" (Google Maps walking deep link), "Share",
parked-since timer, optional meter reminder.

### S7 Device
Firmware version and update (with progress and the "engine off, keep phone near" requirement), module state, link quality (RSSI),
probe results (protocol, supported PIDs), calibration (VBAT against a multimeter, odometer, fuel sender), core-dump upload,
factory-reset instructions.

### S8 Signal Mapper (advanced)
For Renault local IDs: pick a LID block → live bytes table (hex + decimal, highlight changes) → guided actions ("rev to
2,000 rpm") → assign field + scaling → validate against cluster readings → save to the module (`lidmap`).

## 6. Application architecture

```
lib/
├── main.dart                     shell, navigation, theme
├── theme/tokens.dart             design system
├── protocol/frame.dart           frame codec, CRC, command/status ids   (mirrors firmware proto.[ch])
├── protocol/live_frame.dart      LiveV1 decoder with sentinels → null
├── protocol/auth.dart            pairing card, K_user, challenge/response, command MAC
├── ble/vehicle_link.dart         transport + session state machine, request/response correlation
├── data/database.dart            SQLCipher schema, idempotent log-record ingestion
├── state/providers.dart          Riverpod graph: pairing → link → live/sync/history
├── analytics/insights.dart       baselines (EWMA z-score), battery health, trim watch, trip score
└── ui/                           screens + widgets (arc_gauge.dart, live_dashboard.dart, …)
```

Layering rule: UI → providers → repositories/link → protocol. Nothing in `ui/` touches BLE or SQL directly.

### 6.1 Link state machine
`idle → connecting (autoConnect pending) → authenticating (bond, notify, challenge) → ready → (disconnect) → connecting`.
`error` occurs only on an authentication failure, and it offers re-pairing. Commands time out after 5 s (configurable per command).
Pending commands fail fast on disconnect.

### 6.2 Data synchronisation
On every `ready`: `SET_TIME` (phone UTC + IST offset) → `SYNC_LOG(last_seq+1, 5000)` → chunks are ingested in transactions, and
`last_seq` advances inside the same transaction. A crash mid-sync can't skip or duplicate records (records are keyed by seq;
`INSERT OR IGNORE`). Raw samples are kept for 180 days, then downsampled to 1/min. Trips, events, DTCs and cranks are kept indefinitely.

### 6.3 Offline behaviour
Everything except maps tiles, weather and the optional AI chat works offline. The Overview shows the last-known snapshot with
an "as of" timestamp when disconnected.

### 6.4 Error handling
Each user-visible error states the cause and a recovery step (e.g., *"Couldn't clear codes: engine is running. Switch the engine
off and try again."*). Status codes map through `Status.describe`.

## 7. Data model (SQLite)
Tables: `sample`, `trip`, `event`, `crank`, `dtc`, `fill`, `park`, `service_item`, `kv`. See `app/lib/data/database.dart`. Exports:
CSV (samples, trips), GPX (routes), PDF (diagnostic report).

## 8. Security and privacy
- K_user lives in the Keychain/Keystore. The DB is encrypted. Screen capture is blocked on the pairing and invite screens.
- No account and no cloud by default. Optional backup is an encrypted export file the user stores themselves.
- The optional AI assistant sends only aggregated statistics and DTC codes, never raw location. It is off by default, with explicit consent.

## 9. Background operation (native shims)

| OS | Mechanism | Notes |
|----|-----------|-------|
| Android 12+ | `CompanionDeviceManager.associate()` at pairing → `startObservingDevicePresence()` → `CompanionDeviceService.onDeviceAppeared` → start a foreground service (`foregroundServiceType="connectedDevice"`) holding the GATT connection while the car is on | Permissions: `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (neverForLocation), `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `REQUEST_COMPANION_RUN_IN_BACKGROUND`, `POST_NOTIFICATIONS` |
| Android 9–11 | Foreground service with persistent `autoConnect` GATT | Users must exempt the app from battery optimisation (guided) |
| iOS | `CBCentralManager` with `CBCentralManagerOptionRestoreIdentifierKey`, background mode `bluetooth-central`, a pending `connect()` to the known peripheral (iOS keeps it pending indefinitely and reconnects when in range) | The app is relaunched in the background on connect. Its ~10 s execution window is enough to auth and start sync. Live UI needs the foreground |

The shim is about 150 lines of Kotlin and 150 of Swift, exposed through a `MethodChannel('loganlink/background')`.

## 10. Testing
- Unit: protocol codec (`test/protocol_test.dart`, including the CRC check value 0x29B1), auth vectors shared with firmware, insights.
- Integration: a **module simulator** (a desktop Dart or Python script on an ESP32 dev board running the firmware with a fake vehicle
  that generates synthetic K-line responses) for end-to-end UI testing without the car.
- Device matrix: Pixel (Android 14), Samsung A-series (One UI aggressive battery management), Xiaomi/Redmi (MIUI autostart
  permission), Vivo/Oppo, iPhone 12+ (iOS 16–18).

## 11. Build and deployment

```bash
cd app
flutter pub get
flutter test
flutter run                                   # debug on a connected phone
flutter build appbundle --release             # Play Store (.aab), sign with upload key
flutter build ipa --release                   # App Store / TestFlight (Xcode signing)
```
Required platform setup:
- **Android:** `minSdkVersion 28`; permissions as in §9; Google Maps API key in `AndroidManifest.xml` (restrict to package + SHA-1).
- **iOS:** `Info.plist`: `NSBluetoothAlwaysUsageDescription`, `NSLocationWhenInUseUsageDescription`, `NSCameraUsageDescription`
  (QR), `UIBackgroundModes = [bluetooth-central]`; Maps API key in `AppDelegate`.
- For personal use, sideloading (Android APK) or a free Apple developer account (7-day provisioning) works. TestFlight is better for family use.

## 12. Implementation status
Implemented: protocol codec, auth, BLE link, encrypted database with ingestion, providers and sync, insights, design tokens,
arc gauge, Live Dashboard, shell navigation, protocol unit tests. Specified but still to build: Overview, Drive Mode,
Trips, Health, Parking, Device, Signal Mapper screens, QR pairing flow, native background shims and notifications.
**The Dart code has not been run.** Flutter wasn't available in the environment where it was written.

## 13. Automatic features (native app)

| Feature | How it works for the driver |
|---------|-----------------------------|
| Trips | Start with the engine. They only end after 8 minutes off, so long signals, jams and quick stops stay in one trip. Idling in traffic isn't scored against you |
| Parking | Saved 45 s after the engine stops. In basements, the last accurate fix from the drive in is used |
| Fuel log | Payments to petrol pumps in GPay, PhonePe, Paytm, BHIM or bank SMS are logged as fill-ups (amount ÷ your petrol price). Needs notification access once |
| Google Maps | While navigating, the next turn shows in Drive Mode, and a small car panel (speed, engine, battery, next turn) can float over Maps. Needs notification access and "display over other apps" once |
| Odometer and service due | Advanced by every trip; service reminders count down on their own |

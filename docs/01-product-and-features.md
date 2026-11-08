# 01. Product Definition and Feature Specification

## 1. Product definition

| Item | Specification |
|------|---------------|
| Product | LoganLink Vehicle Module (LL-VM1) and LoganLink app |
| Variants | **LL-VM1-B** Base (BLE + Wi-Fi, GNSS, sensors). **LL-VM1-C** Connected (adds LTE Cat-1 for remote functions) |
| Vehicle | Older K-line/OBD-II petrol cars. Reference car: Renault X90-platform sedan, K7M 1.6 8-valve MPFI (1598 cc), RHD India |
| Interface to car | J1962 OBD socket via pass-through splitter; optional 4 analog/digital taps |
| Interface to user | iOS 15+ / Android 9+ app over BLE 5; RGB status LED; recessed PAIR button |
| Supply | 12 V nominal, operating 6–16 V, survives 4.5 V cranking dip (reset allowed below 6 V), 24 V jump-start for 60 s, −14 V reverse, suppressed and unsuppressed load dump with clamp |
| Consumption | Driving ≈ 45 mA @ 13.8 V (B). Parked ≤ 150 µA design / ≤ 300 µA acceptance |
| Environment | −30…+85 °C operating (cabin, under dash), 95 % RH non-condensing, vibration to ISO 16750-3 passenger-car body profile |
| Size | Enclosure body 86 × 61 × 19.5 mm (110 mm across mounting ears), about 70 g complete |
| Service life | 10 years / 150,000 km design life; field-updatable firmware |

## 2. Data-source legend

Every feature lists which data paths it depends on (see doc 02 §5):

- **A**: engine ECU over the diagnostic link (K-line; CAN if present)
- **B**: on-board module sensors: VBAT, GNSS, IMU, cabin sensors, RTC
- **C**: optional vehicle taps: IGN, FUEL_SNS, REV, coolant probe
- **P**: phone: location, notifications, voice, network
- **L**: LTE (variant C only)

Each feature also has a **Tier**. **T1** works with B alone, so it is guaranteed on any car.
**T2** needs A or C. **T3** needs L.

## 3. Feature specification

### 3.1 Vehicle telemetry and monitoring

| ID | Feature | Source | Hardware | Firmware | App UX | Tier |
|----|---------|--------|----------|----------|--------|------|
| TM-01 | Vehicle speed (live, 10 Hz) | A: PID 0x0D; **B: GNSS Doppler speed (fallback / fusion)** | MAX-M10S, K-line | `obd`, `gnss`, `fusion` (complementary filter, GNSS for absolute, IMU for transients) | Large numeric on Live Dashboard; colour band by limit | T1 |
| TM-02 | Engine RPM | A: PID 0x0C; **B: alternator-ripple frequency × calibrated ratio** | AC-coupled VBAT path, ADC 40 kS/s burst | `vbat_ripple` FFT 2048 pts, peak search 1.0–10.5 kHz, K_rip calibrated (doc 02 §5) | Arc gauge, shift-light setting | T1 (B est.) / T2 |
| TM-03 | Coolant temperature | A: PID 0x05 or Renault LID; C: NTC probe on thermostat housing | ADC AIN_AUX or 1-Wire | `obd`, `aux` | Gauge with warm-up curve; “normal” band 85–100 °C | T2 |
| TM-04 | Intake air temp, MAP, throttle, load, ignition advance, STFT/LTFT, O₂ voltage | A: PIDs 0x0F, 0x0B, 0x11, 0x04, 0x0E, 0x06/0x07, 0x14 | K-line | `obd` scheduler at priority rates (§4 doc 03) | “Engine” screen tiles plus sparkline | T2 |
| TM-05 | Battery voltage, live and resting | B | VBAT divider, 12-bit ADC, 2-point calibrated | `vbat` 10 Hz filtered | Battery card | T1 |
| TM-06 | Charging-system status | B: VBAT while engine running | as above | `vbat`: charge state machine (Not-charging < 13.0 V, Normal 13.6–14.7 V, Over > 14.9 V) | Status chip | T1 |
| TM-07 | Fuel used and instantaneous consumption | A: MAP+RPM+IAT+trims (speed-density) | - | `fuel` model (doc 07 §8) self-calibrated with fill-ups | L/100 km & km/L live; trip totals | T2 |
| TM-08 | Fuel level | C: fuel-sender voltage tap; else estimate (last fill − used) | AIN_AUX 100 k/22 k divider | `aux` with 60 s median filter (fuel slosh) | Fuel bar plus range estimate | T2 |
| TM-09 | Odometer mirror | B: integrated GNSS/OBD distance, set once from cluster reading | - | `trip` | Odometer field; service due calc | T1 |
| TM-10 | Operating history | B | 32 MB NOR flash | `logger` ring buffer (≈140 h at 1 Hz full record) | History browser | T1 |

### 3.2 Intelligent diagnostics

| ID | Feature | Source | Implementation | App UX | Tier |
|----|---------|--------|----------------|--------|------|
| DG-01 | Read stored and pending DTCs | A: Mode 03/07 or KWP 0x18 | `diag` service; raw code plus status byte stored with GNSS/time freeze-frame | DTC list, severity, plain-language meaning, likely causes for K7M | T2 |
| DG-02 | Freeze frame | A: Mode 02 | stored per DTC | Snapshot card | T2 |
| DG-03 | Clear DTCs (guarded) | A: Mode 04 / KWP 0x14 | Allowed only if RPM = 0, owner role, explicit confirmation | Two-step confirmation dialog | T2 |
| DG-04 | MIL status and DTC count | A: PID 0x01 | polled every 30 s | Badge on Vehicle Overview | T2 |
| DG-05 | Battery health | B | Resting voltage after 4 h; crank minimum; crank recovery time; trend over 90 days | Health score 0–100, “replace soon” forecast | T1 |
| DG-06 | Alternator health | B | Charging voltage vs. load and temperature; ripple amplitude (> 0.5 Vpp suggests a diode fault) | Health card | T1 |
| DG-07 | Starter and cranking analysis | B | 1 kS/s capture of VBAT during crank: min V, duration, number of attempts | Each start logged; trend chart | T1 |
| DG-08 | Fuel-trim drift detector | A | LTFT EWMA (τ = 10 h); alert if \|LTFT\| > 10 % (vacuum leak, injector, MAP) | Early warning | T2 |
| DG-09 | Thermal behaviour | A/C | Warm-up time to 80 °C vs. ambient; overshoot; fan-cycle detection; over-temp > 105 °C alert | Thermostat-health indicator | T2 |
| DG-10 | Misfire / roughness indicator | B (+A) | IMU vertical-acceleration variance at idle (engine-order band) vs. baseline; RPM ripple | “Idle smoothness” trend | T1 |
| DG-11 | Condition-based maintenance | B + user | Service items with km/time/condition rules (oil 10,000 km or 12 months; spark plugs 20,000 km; air filter 20,000 km, sooner if dusty-route ratio high; timing belt 60,000 km / 4 yrs, **critical on K7M**) | Maintenance timeline, reminders | T1 |
| DG-12 | Module self-diagnostics | B | Supply, flash, sensor and comms health; reset-reason log | Device screen | T1 |

### 3.3 Driving intelligence

| ID | Feature | Source | Implementation | Tier |
|----|---------|--------|----------------|------|
| DI-01 | Automatic trip recording | B | Trip starts when engine running is detected (VBAT > 13.2 V for 3 s, or IGN, or RPM > 400) and ends after 8 min off, so stops at long signals stay in one trip | T1 |
| DI-02 | Journey statistics | B (+A) | Distance, duration, moving/idle time, average/max speed, fuel, cost (₹, user fuel price) | T1 |
| DI-03 | Acceleration and braking analysis | B | IMU longitudinal axis (auto-aligned with GNSS heading during straight acceleration); harsh event if a_x < −0.35 g (brake) or > 0.30 g (accel) for > 300 ms | T1 |
| DI-04 | Cornering | B | Lateral g > 0.35 g for > 500 ms | T1 |
| DI-05 | Speed profile | B | Histogram 0–140 km/h in 10 km/h bins, per trip and over time | T1 |
| DI-06 | Route history | B | GNSS 1 Hz tracks, Douglas-Peucker simplified (ε = 5 m) on phone | T1 |
| DI-07 | Driving efficiency score | A/B | Weighted: harsh events, idle %, over-rev time (> 4,000 rpm), speed over 100 km/h, coasting share | T1/T2 |
| DI-08 | Fuel-consumption analysis | A + user fill-ups | km/L per trip, per road type (city < 40 km/h avg, highway > 60) | T2 |
| DI-09 | Usage trends | B | Weekly/monthly km, trips, cost, engine hours | T1 |

### 3.4 Connected-vehicle functions

| ID | Feature | Source | Implementation | Tier |
|----|---------|--------|----------------|------|
| CV-01 | Automatic phone connection | B+P | Bonded BLE; Android CompanionDeviceManager presence; iOS CoreBluetooth state restoration | T1 |
| CV-02 | Vehicle status at a glance | B | Last-known snapshot cached on phone; refreshed on connect | T1 |
| CV-03 | Parking location | B+P | Module's last good GNSS fix (HDOP < 2.5) at engine-off, plus phone location at disconnect; best of both | T1 |
| CV-04 | Navigate to car / share location | P | Deep link to Google Maps walking directions; share sheet | T1 |
| CV-05 | Intelligent notifications | B+A+P | Local notifications: low battery, charging fault, overheat, new DTC, service due, parking reminder, cabin CO₂ high | T1/T2 |
| CV-06 | Wireless data sync | B | Incremental log sync by sequence number (doc 06 §6), resumable | T1 |
| CV-07 | Configurable automations | B+P | Rule engine on phone (“when engine starts → open Live Dashboard, start Spotify”; “when parked → save location, notify fuel if < 15 %”) | T1 |
| CV-08 | Driver profiles | P | Up to 4 phones; per-driver trips and scores; owner/driver roles | T1 |
| CV-09 | Voice | P | Siri Shortcuts / Android App Actions: “Where is my car”, “Car battery status”, “Last trip”. TTS alerts while driving (overheat, charging fault) | T1 |
| CV-10 | Remote status and location | L | MQTT over TLS to user’s own broker (or cloud function); position every 15 min parked, on motion | T3 |
| CV-11 | Tow / tamper / intrusion alert | B+L | Motion while ignition off plus GNSS displacement > 50 m → push alert | T3 (logged only on T1) |
| CV-12 | Geofence and curfew | L / P | Enter/exit events | T3 |

### 3.5 Cabin, environment and additional innovations

| ID | Feature | Hardware | Logic | Tier |
|----|---------|----------|-------|------|
| CE-01 | Cabin temperature and humidity | SHT45 | 1 Hz; dew-point (fogging risk when windscreen temp < dew point; estimated from outside temp from weather API on phone) | T1 |
| CE-02 | Cabin CO₂ / drowsiness ventilation alert | SCD41 | Alert at > 1,500 ppm (“switch off recirculation”); critical at > 2,500 ppm | T1 |
| CE-03 | Air quality (VOC / NOx index) | SGP41 | Sensirion gas index algorithm; suggests recirculation in polluted traffic | T1 |
| CE-04 | Occupant-left-behind warning | SCD41 + IMU | For 30 min after engine-off: rising CO₂ (> +150 ppm / 10 min) or cabin motion while car locked → BLE alert; LTE push on variant C | T1/T3 |
| CE-05 | Hot-cabin warning | SHT45 | Parked cabin > 50 °C plus recent occupant activity | T1 |
| CE-06 | Crash / impact detection | IMU (±16 g) | \|a\| > 4 g for > 20 ms → event record with 5 s pre/post buffer; phone offers SOS call (112) with countdown | T1 |
| CE-07 | Pothole and road-quality map | IMU + GNSS | Vertical jerk events geotagged; personal road-quality layer | T1 |
| CE-08 | Reverse-gear detection for parking aid | C (REV tap) | Triggers app parking view; integrates with an aftermarket ultrasonic kit's buzzer line (optional input) | T2 |
| CE-09 | Accessory power control | AUX_OUT (low-side 1 A) | Dashcam / ambient light / USB charger switched by rules (e.g., stay on 10 min after park) | T1 |
| CE-10 | AI insights | On phone | Per-signal baselines (EWMA ± 3σ) on warm-idle RPM, LTFT, charge V, crank min, warm-up time; anomaly cards with explanations; optional LLM chat over local data (opt-in, sends only aggregates) | T1/T2 |

## 4. Deliberately excluded

| Excluded function | Reason |
|-------------------|--------|
| Remote engine start / immobiliser bypass | Defeats the Renault transponder immobiliser. Theft and safety risk. Not possible without modifying the immobiliser circuit |
| Remote lock/unlock | Needs a tap into the central-locking UCH outputs. Raises the cyber-attack surface on a vehicle with no security architecture |
| Any ECU write, coding, reflash, actuator test | Safety: could disable engine or stall the car in traffic |
| Head-up display / cluster replacement | Out of scope for “discreet module”; the phone holder is the display |
| Android Auto / CarPlay projection | Platform policies limit third-party categories. The app works with the phone on a holder instead |

## 5. User experience principles

1. **Zero-touch:** pairing happens once. After that the phone connects in the background when the car starts.
2. **Glanceable while driving:** the Drive Mode screen shows at most 3 numbers plus alerts, with large type
   and no interaction needed. Indian Motor Vehicles Act rules on phone use while driving apply: use a holder.
3. **Explain, don't just show:** every warning says what it means, how urgent it is (*Info / Check soon /
   Stop safely*) and what to do.
4. **Honest data:** every value carries its source and quality (ECU, measured, estimated). Estimated values
   are shown with a “≈” prefix.

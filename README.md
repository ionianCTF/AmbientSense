# Ambient Sense

**Multi-band passive RF sensing on an unmodified commodity smartphone.**

Ambient Sense runs entirely on a stock Android phone and reads the radio environment around it — without connecting to a single beacon or access point, and without a camera or microphone feed for the sensing core. Every observable BLE advertising beacon and dual-band (2.4 GHz / 5 GHz) Wi-Fi access point becomes a *measurement ray* whose attenuation state is monitored in real time. That turns the phone from a communication terminal into a **device-free, multi-modal environment sensor** that answers questions you can check on a map:

- **How many people are nearby?**
- **How many vehicles are passing?**
- **How loud is it** (A-weighted dB(A))?
- **How good is the mobile connection** (cell generation, RSRP, ping, jitter, throughput)?
- **Did the RF field change** between two moments in time (scan epochs)?

Written in **Kotlin + Jetpack Compose**, maps via **OpenStreetMap (osmdroid — no API key)**.

> **Validated, not guessed.** The estimation pipeline is tested against a physics-based
> RF simulator (`validation/rfscene.py`); results that held up changed the tracker, and
> results that lost on complete scenes were rejected. The app ships an **Accuracy** tab
> that measures its own error against your labels.
> Full write-up: [`validation/results/REPORT.md`](ambient-sense/validation/results/REPORT.md)
> and [`validation/results/RADIUS.md`](ambient-sense/validation/results/RADIUS.md).

| people-count error (truth = people within 30 m) | v0.9 | now |
|---|---|---|
| no calibration | 6.3 people | **1.5 people** |
| 25 labels in this place | 1.7 | **0.7** |
| 25 labels in *another* place | 4.1 | **1.8** |
| vehicle recall (cars passing within 10 m) | 0% | **40%** |

A companion manuscript describing the sensing model, scan-epoch change detection,
two-node transit counting, mesh-accrued accuracy and gamified community reporting
accompanies this repository.

---

## Features

| Channel | What is measured | What it becomes |
|---|---|---|
| **Wi‑Fi (device-free)** | Passive scan table: RSSI per BSSID, sampled continuously | Fluctuation (coefficient of variation of linear power), rate of shadowing *dip* events, AP churn → a device-free occupancy signal that responds to bodies carrying nothing |
| **BLE (device-based)** | Advertisements: address, RSSI, TX power, name, manufacturer | Range from the log-distance path-loss model → range-rate regression → **standing / walking / vehicle**, with clustering so a car's phone + watch + hands-free kit counts once |
| **Acoustics** | Microphone PCM | Hann-windowed FFT → IEC 61672-1 A-weighting → **Leq dB(A)**, peak and noise floor |
| **Connectivity** | Telephony + ConnectivityManager, ICMP, HTTP | Generation (4G / 5G NSA / SA / mmWave), band & ARFCN, RSRP / RSRQ / SINR, timing advance, Wi‑Fi standard & link rate, **ping min/avg/max, jitter, loss**, download & upload throughput |
| **Location** | GPS + network + passive fixes | Every measurement is geotagged and dropped on the map |

- Everything lands on a **unified map** with switchable heat layers (people, vehicles,
  noise, throughput, latency), range rings, and BLE device markers coloured by motion class.
- **Scan epochs** store a timed snapshot of the field, reset the engine, rescan, and
  compare *stable levels* (median of the last 20 samples) so a genuine change is detected
  without inventing movement.
- **Two-node transit counter** — two phones form a Bluetooth barrier (Classic RFCOMM +
  BLE L2CAP) whose link presence/loss is fused into a typed people/vehicle crossing count.
- **Sensing mesh** — nearby instances exchange crossing records; a crossing is published
  only when confirmed by a threshold of nodes, down-weighting disagreeing rays and
  extending the useful radius.
- **Gamified club layer** — XP per crossing, fusion bonuses for mesh-confirmed events,
  and a reliability bonus for devices nearest the fused estimate.
- **Choose-your-radius sensing** — pick the sensing radius (5–90 m) and the app tells you
  what each setting costs in accuracy.
- **Self-measured accuracy** — submit ground-truth labels, and the app reports its own
  MAE / RMSE / bias / within-±1 from genuine one-step-ahead errors.

---

## The sensing → calibration loop

1. **Rules first.** With zero labels, a physics-flavoured heuristic runs:
   - *device-based term* — clustered BLE entities inside the proximity radius, scaled by `devices → people`;
   - *device-free term* — Wi‑Fi power fluctuation × √links + blocking-event rate, which is what responds to people and vehicles carrying nothing at all.
2. **You label.** On the Calibrate tab you count what you can actually see. The app stores the ground truth *and* the feature vector that produced its estimate.
3. **It realigns.** A recursive-least-squares model with a forgetting factor (λ = 0.985) learns the **correction to the rules**, not the absolute count. A handful of labels is already useful; a mistaken label cannot throw the estimate far from physics; recent corrections matter more than old ones; and feature weights, MAE, R² and drift are all visible on screen.
4. **It explains itself.** The weight table shows which features the model leans on, so you can see *why* the number moved.

Configuration lives in **Settings**: path-loss exponent, fallback TX power, proximity radius, speed thresholds, heuristic coefficients, microphone calibration offset, probe targets and intervals.

---

## How far can it actually sense?

Measured over 14 simulated scenes (§4 of the report):

| question | answer |
|---|---|
| Something is there (a device is heard) | pedestrians to ~15–20 m, vehicles to ~25–30 m |
| It is classified correctly | walkers reliably only within ~5–8 m, vehicles out to ~25 m |
| Counted reasonably | usable to ~20–25 m, degrades past 30 m |

The asymmetry is signal-to-noise, not radio range. Over a fit window *T*, a target passing
at closest approach *b* with speed *v* changes its range by about
`Δr ≈ sqrt(b² + (vT/2)²) − b` while range noise is `σ_r ≈ 0.23 · σ_RSSI/n · r`.
Speed squared is what makes vehicles easy and distant pedestrians hard.

Also worth knowing: **BLE range is right to about a factor of two** (biased +30…+100% by
body loss, ±60–100% scatter). Every velocity and distance number downstream inherits that.

### Choosing the sensing radius

The default radius is **30 m**. The full picker:

| radius | name | people really there | error, no labels | error, ~25 labels | within ±1 | crowd sampled directly | vehicles caught |
|---|---|---|---|---|---|---|---|
| 5 m | Arm's length | 0.1 | ±0.50 | ±0.16 | 100% | 7% | 0% |
| 10 m | Room | 0.4 | ±0.67 | ±0.29 | 98% | 10% | 29% |
| 15 m | Pavement | 1.0 | ±0.99 | ±0.51 | 90% | 13% | **40%** |
| 20 m | Frontage | 1.9 | ±1.18 | ±0.70 | 76% | 17% | 32% |
| **30 m (default)** | Street | 2.8 | ±1.31 | ±0.82 | 65% | **18%** | 30% |
| 45 m | Crossroads | 3.4 | ±1.42 | ±0.92 | 60% | 17% | 30% |
| 60 m | Square | 3.9 | ±1.58 | ±0.91 | 65% | 15% | 30% |
| 90 m | Horizon | 4.9 | ±2.14 | ±1.16 | 52% | 12% | 30% |

Changing the radius deliberately resets the calibration's confidence (training at one
radius and evaluating at another is worse than no model at all). Weights are kept as a
warm start, trust drops to zero, and the app re-earns trust as you label at the new radius.
Your labels are never deleted.

---

## Validation

The harness is not a simulator beside the app — scenes are replayed through the app's own
algorithms (`validation/` is a 1:1 Python port of the production Kotlin estimators).
A candidate change is adopted only if it wins on *complete scenes*, not row-level aggregates.

Key measured results (4,161 Monte-Carlo scenes):

- **Fusion accuracy** — BLE achieves 57.7% per-crossing entity typing in the research-facing
  build, against 25.7% (Wi-Fi 5 GHz) and 16.1% (Wi-Fi 2.4 GHz); multi-band fusion is asymmetric by design.
- **Ranging honesty gate** — log-distance dBm→metre inversion is gated at −75 dBm and refused
  at the −90 dBm noise floor, suppressing 14–23 m illusions on weak links; the output is a radial band, not a false point.
- **Scan-epoch change detection** — the 20-sample stable-level rule reaches 100% movement
  detection at 2 m with **0% false positives at every tested distance** (naive last-sample
  comparison gave 25–62% false positives).
- **Two-node transit counter** — 100% fused detection and typing at 3–10 m separation,
  94% at 15 m, with the 25 m decay published rather than hidden (61%).
- **Mesh** — zero false confirmations across 160 empty scenes; useful radius extends from
  4–12 m (single phone) toward 20–40 m.
- **Calibration** — the online RLS correction cuts people-count error from 6.3 → 1.5
  (no labels) and 1.7 → 0.7 (25 labels); transfer to a new place costs ~1 person.

Reproduce offline:

```bash
cd ambient-sense/validation
python3 final_suite.py     # ~3 min: shipping vs upgraded, all experiments
python3 report.py          # charts + results/REPORT.md
python3 radius.py          # ~6 min: the sensing-radius sweep (E8)
python3 radius_report.py   # charts + results/RADIUS.md
```

---

## Honest limitations

- **Range, not bearing.** One phone cannot measure angle of arrival. Real positions need two or more observers (or AoA hardware).
- **Radial velocity only.** Tangential passes can be under-called unless the track is kept long enough to see the turn-around.
- **MAC randomisation.** Rotating BLE addresses inflate naive device counts — flagged tracks handle this.
- **OS throttling.** Android caps Wi‑Fi scans and BLE duty cycle in the background; sensing runs in a foreground service, the only reliable workaround.
- **Microphone capture** is blocked while the app is in the background.
- **Absolute accuracy** is best after you calibrate in each environment — that is the whole point of the Calibrate tab.
- **All accuracy figures quoted come from simulation**, not a phone. They are upper bounds.

**Privacy:** nothing leaves the phone. Scan results are processed on-device, MACs are only
shown to you, and measurements are stored in a local Room database you can clear or export
as CSV at any time.

---

## Building

```bash
# 1. Point the Android SDK at your install (or set ANDROID_HOME)
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# 2. Debug build
./gradlew :app:assembleDebug

# 3. Install on a connected device
./gradlew :app:installDebug
```

- **minSdk 26** (Android 8.0), **targetSdk / compileSdk 36**
- Kotlin 2.3.10 · AGP 8.13.2 · Gradle 8.13 · Compose BOM 2026.06.00 · Room 2.8.4 · osmdroid 6.1.20
- Open the project in a current Android Studio and press Run; nothing else is required
  (no API keys — the map uses OpenStreetMap tiles).

## Permissions

Granted on first launch: location (geotagging), Nearby devices / Bluetooth scan, Wi‑Fi scanning,
microphone (optional), phone state (cell measurements), notifications (the foreground service).

Everything degrades gracefully: deny the microphone and you lose the acoustic channel only;
deny phone state and RSRP/band fields simply show a dash.

---

## Project layout

```
ambient-sense/
├── app/
│   └── src/main/java/com/ambientsense/app/
│       ├── MainActivity.kt              # permissions, window, Compose entry
│       ├── AmbientApp.kt                # settings + Room + models singletons
│       ├── data/
│       │   ├── SettingsStore.kt         # typed SharedPreferences exposed as StateFlow
│       │   ├── SensorBus.kt             # process-wide sensor state (StateFlows)
│       │   ├── Entities.kt / AppDatabase.kt
│       ├── sensing/
│       │   ├── SensingService.kt        # foreground service owning the measurement loops
│       │   ├── WifiScanner.kt           # device-free RF statistics (fluctuation, dips, churn)
│       │   ├── BleScanner.kt            # BLE observer + MAC randomisation detection
│       │   ├── DeviceTracker.kt         # range, range-rate, motion class, clustering
│       │   ├── NoiseMeter.kt            # A-weighted sound level meter (FFT)
│       │   ├── NetworkProbe.kt          # cell/Wi-Fi state, ICMP ping, TCP throughput
│       │   ├── FusionEngine.kt          # rules estimator + feature vector + fusion
│       │   └── CalibrationModel.kt      # online RLS with forgetting factor
│       ├── ui/screens/ValidationScreen.kt   # self-measured accuracy report
│       └── ui/                          # Compose screens, theme and charts
├── validation/                          # offline validation harness (Python, not shipped)
│   ├── rfscene.py                       # path loss, body shadowing, bistatic scattering, BLE ads
│   ├── algos.py                         # 1:1 Python port of the app's estimators + variants
│   ├── experiments.py / final_suite.py
│   ├── report.py / radius.py / radius_report.py
│   └── results/REPORT.md, RADIUS.md, results.json, plots/
└── setup_toolchain.sh                   # reproducible CI Android toolchain bootstrap
```

---

## Research this leans on

Device-free RF crowd sensing is a well-studied problem; the parts used here:

- **Depatla & Mostofi, "Passive Crowd Speed Estimation and Head Counting Using WiFi"** (SECON 2018, IEEE TMC 2019) — cross-correlation of two Wi‑Fi links and the probability of crossing a link carry crowd speed and count using only RSSI. The fluctuation + dip-rate statistics in `WifiScanner.kt` follow this line.
- **Depatla & Mostofi, "Crowd Counting Through Walls Using WiFi"** — *inter-event times* of signal dips are far more robust to unknown attenuation than dip depth, which is why this app counts dip events per minute rather than measuring their depth.
- **A. Pankaj & K. S. Raju, "Device-free crowd counting with Wi‑Fi CSI and deep neural networks"** — ~1 person error for small crowds; CSI is not exposed on stock Android, so this app uses RSSI-level statistics and user calibration instead.
- **Indoor people density sensing using Wi‑Fi and CSI** — survey of the RSSI vs. CSI trade-off and classic estimators.

Related open-source work worth reading:

- [rpi-people-counter](https://github.com/AlexNaga/rpi-people-counter) — Bluetooth + Wi‑Fi occupancy estimation on a Pi.
- [Device-Counter](https://github.com/inflac/Device-Counter) — MAC-address counting and the MAC-randomisation discussion that motivated the `randomizedMac` flag.
- [find3-android-scanner](https://github.com/schollz/find3-android-scanner) — continuous Android Wi‑Fi/BLE scanning for indoor positioning.
- [CountingPeople](https://github.com/jacoboqc/CountingPeople) — distributed sniffers for crowd size.
- [wisture](https://github.com/mohaseeb/wisture) — Wi‑Fi RSSI capture and ML on stock phones, and the reason this app exposes calibration knobs rather than shipping fixed constants.

---

## License

See the project files. The offline validation harness is intentionally not shipped in the
application package.
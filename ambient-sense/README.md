# Ambient Sense

An Android app that reads the radio environment around you and turns it into answers you
can check: **how many people are nearby, how many vehicles are passing, how loud it is,
and how good the mobile connection is** — all drawn on one map, and all re-calibrated
from your own labels.

Written in Kotlin + Jetpack Compose, maps via OpenStreetMap (osmdroid, no API key).

> **Validated, not guessed.** The estimation pipeline was tested against a physics-based
> RF simulator, the results changed six things in the tracker, and the app now ships an
> **Accuracy** tab that measures its own error against your labels.
> Full write-up: [`validation/results/REPORT.md`](validation/results/REPORT.md).

| people-count error (truth = people within 30 m) | v0.9 | now |
|---|---|---|
| no calibration | 6.3 people | **1.5 people** |
| 25 labels in this place | 1.7 | **0.7** |
| 25 labels in *another* place | 4.1 | **1.8** |
| vehicle recall (cars passing within 10 m) | 0% | **40%** |

---

## What it actually does

| Channel | What is measured | What it becomes |
|---|---|---|
| **Wi‑Fi (device-free)** | Passive scan table: RSSI per BSSID, sampled continuously | Fluctuation (coefficient of variation of linear power), rate of shadowing *dip* events, AP churn → a device-free occupancy signal that responds to bodies carrying nothing |
| **BLE (device-based)** | Advertisements: address, RSSI, TX power, name, manufacturer | Range from the log-distance path-loss model → range-rate regression → **standing / walking / vehicle**, with clustering so a car's phone + watch + hands-free kit counts once |
| **Acoustics** | Microphone PCM | Hann-windowed FFT → IEC 61672-1 A-weighting → **Leq dB(A)**, peak and noise floor |
| **Connectivity** | Telephony + ConnectivityManager, ICMP, HTTP | Generation (4G / 5G NSA / SA / mmWave), band & ARFCN, RSRP / RSRQ / SINR, timing advance, Wi‑Fi standard & link rate, **ping min/avg/max, jitter, loss**, download & upload throughput |
| **Location** | GPS + network + passive fixes | Every measurement is geotagged and dropped on the map |

Everything lands on a **unified map** with a heat layer you can switch between people,
vehicles, noise, throughput and latency, plus range rings and device markers.

---

## The sensing→calibration loop

1. **Rules first.** With zero labels the app runs a physics-flavoured heuristic:
   - *device-based term* — clustered BLE entities inside the proximity radius, scaled by
     `devices → people` (not everyone carries a discoverable device);
   - *device-free term* — Wi‑Fi power fluctuation × √links + blocking-event rate, which is
     what responds to people and vehicles carrying nothing at all.
2. **You label.** On the Calibrate tab you count what you can actually see and submit it.
   The app stores the ground truth *and* the feature vector that produced its estimate.
3. **It realigns.** A recursive-least-squares model with a forgetting factor (λ = 0.985)
   learns the **correction to the rules**, not the absolute count. Consequences:
   - a handful of labels is already useful (6 labels starts blending the model in,
     30 reaches full weight);
   - a mistaken label cannot throw the estimate far from physical reality;
   - recent corrections matter more than old ones, so the model follows you when you
     move to a new environment;
   - feature weights, MAE, R² and current drift are all visible on the Calibrate screen.
4. **It explains itself.** The weight table shows which features the model leans on, so
   you can see *why* the number moved.

Configuration lives in **Settings**: path-loss exponent, fallback TX power, proximity
radius, the three speed thresholds, the heuristic coefficients, the microphone
calibration offset, probe targets and intervals.

---

## Research this leans on

Device-free RF crowd sensing is a well studied problem; the parts used here are:

- **Depatla & Mostofi, “Passive Crowd Speed Estimation and Head Counting Using WiFi”**
  (SECON 2018, IEEE TMC 2019) — shows that the cross-correlation of two Wi‑Fi links and
  the probability of crossing a link carry crowd speed and count, using only RSSI and
  without anyone carrying a device. The fluctuation + dip-rate statistics in
  `WifiScanner.kt` follow this line of work. [3](https://web.ece.ucsb.edu/~ymostofi/papers/Secon18_DepatlaMostofi.pdf) [4](https://ieeexplore.ieee.org/document/8397119/)
- **Depatla & Mostofi, “Crowd Counting Through Walls Using WiFi”** — the key result we
  exploit: *inter-event times* of signal dips are far more robust to unknown attenuation
  than dip depth, which is why the app counts dip **events per minute** rather than
  measuring how deep they are. [5](https://www.researchgate.net/publication/321124603_Crowd_Counting_Through_Walls_Using_WiFi)
- **Device-free crowd counting with Wi‑Fi CSI and deep neural networks** — reports ~1
  person error for small crowds; CSI is not exposed on stock Android, so this app uses
  RSSI-level statistics instead and leans on user calibration to close the gap.
  [4](https://link.springer.com/article/10.1007/s11276-020-02274-7)
- **Indoor people density sensing using Wi‑Fi and CSI** — survey of the RSSI vs. CSI
  trade-off and of the classic estimators. [1](https://www.iieta.org/download/file/fid/40739)
- **Crowd counting via transfer learning on CSI** [2](https://www.researchgate.net/publication/361293243_Crowd_Counting_Based_on_WiFi_Channel_State_Information_and_Transfer_Learning)

Related open-source work worth reading:

- [rpi-people-counter](https://github.com/AlexNaga/rpi-people-counter) — Bluetooth + Wi‑Fi
  occupancy estimation on a Pi, with the honest caveat that a person with both radios on
  gets counted twice.
- [Device-Counter](https://github.com/inflac/Device-Counter) — MAC-address counting and
  the MAC-randomisation discussion that motivated the `randomizedMac` flag.
- [find3-android-scanner](https://github.com/schollz/find3-android-scanner) — continuous
  Android Wi‑Fi/BLE scanning for indoor positioning.
- [CountingPeople](https://github.com/jacoboqc/CountingPeople) — distributed sniffers for
  crowd size.
- [wisture](https://github.com/mohaseeb/wisture) — Wi‑Fi RSSI capture and ML on stock
  phones, and the reason this app exposes calibration knobs rather than shipping fixed
  constants.

---

## How far can it actually sense?

Measured over 14 simulated scenes (§4 of the report):

| question | answer |
|---|---|
| Something is there (a device is heard) | pedestrians to ~15-20 m, vehicles to ~25-30 m |
| It is classified correctly | walkers reliably only within **~5-8 m**, vehicles out to ~25 m |
| Counted reasonably | usable to ~20-25 m, degrades past 30 m |

(If you want the range question answered as a *choice* rather than a fact, see
[Choosing the sensing radius](#choosing-the-sensing-radius) — the app lets you set the
radius and tells you what each setting costs.)

The asymmetry is signal-to-noise, not radio range. Over a fit window *T*, a target
passing at closest approach *b* with speed *v* changes its range by about
`Δr ≈ sqrt(b² + (vT/2)²) − b`, while the range noise is `σ_r ≈ 0.23 · σ_RSSI/n · r`.
A walker 12 m away moves the range by 1.9 m against 4.2 m of noise — invisible.
A car at 12 m/s moves it by 49 m against the same noise — obvious. Speed squared
is what makes vehicles easy and distant pedestrians hard.

Also worth knowing: **BLE range is right to about a factor of two** (biased +30…+100%
by body loss, ±60-100% scatter). Every velocity and distance number downstream inherits
that.

## Choosing the sensing radius

The app answers *"how much is within R metres?"*, and R is yours to set — a chip row
on the **Field** tab, a full comparison in **Settings**, or the **Accuracy** tab, where
the predicted error sits next to the error this phone has actually achieved. Every
option in the picker carries the accuracy measured for it, so widening the question
comes with a price tag attached. Full write-up:
[`validation/results/RADIUS.md`](validation/results/RADIUS.md).

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

Every extra metre costs accuracy, and past ~30 m it stops buying coverage: at 2.4 GHz
a phone cannot hear another phone much beyond 15 m, so beyond 30 m the count is the
model extrapolating from the slice it can see, not a measurement. Two details that do
not move monotonically — vehicle detection peaks at **15 m**, because a car passing
close gives a clean fast range curve; and below 10 m most of what the radio hears is
infrastructure (beacons, laptops, parked-car sensors) rather than people.

Two laws fell out of the same experiment and are now in the code:

- **RF prior scale** `α(R) = (R/25)^0.505` — the device-free channel sees a volume set
  by the access points, not by your radius, so its prior is rescaled to the region of
  interest. A square-root law, not an area law (rms log residual 0.14 over 8 radii).
- **Residual scale** `s(R) = (R/25)^0.481` — the learned correction is an absolute
  headcount, so it is divided by `s(R)` when training and multiplied by it when
  predicting. That makes labels portable between radii instead of stranded in the
  units of the radius they were taken at.

**Changing the radius resets the calibration's confidence, deliberately.** Training at
one radius and evaluating at another is worse than using no model at all in every pair
tested (e.g. 15 m → 45 m: 1.57 transferred, 2.08 rescaled, 1.34 with no model). So the
app keeps the weights as a warm start, drops trust to zero, falls back to its physics
baseline and re-earns trust as you label at the new radius. Your labels are never
deleted — they stay in the database and in the Accuracy tab.

## Validating it yourself

The **Accuracy** tab turns your labels into a report — MAE, RMSE, bias, within-±1, a
predicted-vs-counted scatter and a "is it still improving?" trend. Because every label
stores what the app was showing *before* it was learned, those are genuine one-step-ahead
errors, not a post-hoc fit.

Protocol that works: pick a spot, run sensing for 5-10 minutes, submit a count every
~30 s (zeros included), get 10-25 labels spanning quiet and busy moments, and repeat per
place.

Reproduce the offline experiments with:

```bash
cd validation
python3 final_suite.py     # ~3 min: shipping vs upgraded, all experiments
python3 report.py          # charts + results/REPORT.md
python3 radius.py          # ~6 min: the sensing-radius sweep (E8)
python3 radius_report.py   # charts + results/RADIUS.md
```

## Honest limitations

- **Range, not bearing.** One phone cannot measure angle of arrival. A device marker sits
  on its measured range ring at a stable pseudo-bearing derived from its address. Real
  positions would need two or more observers (or AoA hardware).
- **Radial velocity only.** A car passing tangentially shows a small |d(range)/dt| near
  the point of closest approach; the fit keeps tracks long enough to see the turn-around,
  but fast tangential passes can be under-called.
- **MAC randomisation.** Most phones rotate their BLE address every ~15 minutes, which
  inflates naive device counts — the app flags those tracks.
- **OS throttling.** Android caps Wi‑Fi scans for foreground apps (roughly one per 30 s)
  and BLE duty cycle in the background; the sensing loop runs in a foreground service,
  which is the only reliable workaround.
- **Microphone** capture is blocked while the app is in the background.
- **Absolute accuracy** is best after you calibrate in each environment — that is the
  whole point of the Calibrate tab.
- **All accuracy figures quoted above come from simulation**, not from a phone. They are
  upper bounds: real bodies, antennas and multipath are messier than the models in
  `validation/rfscene.py`.

Privacy: nothing leaves the phone. Scan results are processed on-device, MACs are only
shown to you, and measurements are stored in a local Room database you can clear or
export as CSV at any time.

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
- Kotlin 2.3.10 · AGP 8.13.2 · Gradle 8.13 · Compose BOM 2026.06.00 · Room 2.8.4 ·
  osmdroid 6.1.20
- Open the project in a current Android Studio and press Run; nothing else is required
  (no API keys — the map uses OpenStreetMap tiles).

## Permissions

Granted on first launch: location (geotagging), Nearby devices / Bluetooth scan, Wi‑Fi
scanning, microphone (optional), phone state (cell measurements), notifications
(the foreground service).

Everything degrades gracefully: deny the microphone and you lose the acoustic channel
only; deny phone state and RSRP/band fields simply show a dash.

---

## Project layout

```
app/src/main/java/com/ambientsense/app/
├── MainActivity.kt              # permissions, window, Compose entry
├── AmbientApp.kt                # settings + Room + models singletons
├── data/
│   ├── SettingsStore.kt         # typed SharedPreferences exposed as StateFlow
│   ├── SensorBus.kt             # process-wide sensor state (StateFlows)
│   ├── Entities.kt / AppDatabase.kt
├── sensing/
│   ├── SensingService.kt        # foreground service owning the measurement loops
│   ├── WifiScanner.kt           # device-free RF statistics (fluctuation, dips, churn)
│   ├── BleScanner.kt            # BLE observer + MAC randomisation detection
│   ├── DeviceTracker.kt         # range, range-rate, motion class, clustering
│   ├── NoiseMeter.kt            # A-weighted sound level meter (FFT)
│   ├── NetworkProbe.kt          # cell/Wi-Fi state, ICMP ping, TCP throughput
│   ├── FusionEngine.kt          # rules estimator + feature vector + fusion
│   └── CalibrationModel.kt      # online RLS with forgetting factor
├── ui/screens/ValidationScreen.kt   # self-measured accuracy report
└── ui/                          # Compose screens, theme (Anthropic-inspired) and charts
```

```
validation/                      # offline validation harness (Python, not shipped)
├── rfscene.py                   # path loss, body shadowing, bistatic scattering, BLE ads
├── algos.py                     # 1:1 Python port of the app's estimators + variants
├── experiments.py / final_suite.py
├── report.py                    # charts + markdown report
└── results/REPORT.md, results.json, plots/
```

## The map layers

- **People / Vehicles** — heat blobs sized and tinted by the fused estimate at that point.
- **Noise / Throughput / Latency** — the same trace recoloured by the acoustic and
  connectivity channels.
- Range rings at 10 / 25 / 50 / 100 m plus the proximity ring; BLE devices are drawn on
  their measured range with a colour per motion class
  (grey standing, clay walking, blue vehicle).

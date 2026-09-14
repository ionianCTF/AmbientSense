# Ambient Sense — validation report

Everything below was produced by `validation/`, which re-implements the app's
estimation pipeline in Python and runs it against a physics-based RF simulator
(`rfscene.py`): log-distance path loss, body shadowing, the bistatic radar
equation for moving scatterers, BLE advertisement streams with MAC rotation, and
Android's Wi-Fi scan cadence. **The algorithms are the same code paths; the data
is simulated.** Treat these as upper bounds on what the phone can do.

Ground truth in the counting experiments: number of people / vehicles within
30 m of the phone (typical busy-street scene: ~3 people and ~0.4 vehicles at any
instant, 10 pedestrians and 5 cars over a 5-minute run, plus 22 background
beacons/laptops).

## Headline

| | shipping v0.9 | upgraded | change |
|---|---|---|---|
| People-count error, **no calibration** | 6.29 | **1.47** people | −77% |
| People-count error, 25 labels | 1.66 | **0.74** people | −55% |
| People-count error, **new place** (25 labels elsewhere) | 4.05 | **1.80** people | −56% |
| Vehicle recall (of cars that pass within 10 m) | 0% | **40%** | — |

## 1. How precise is a BLE range estimate?

| true distance | samples | bias | RMSE | 68th pct |error| |
|---|---|---|---|---|
| 0-3m | 18 | +96% | 120% | 125% |
| 3-6m | 45 | +89% | 120% | 121% |
| 6-10m | 57 | +52% | 97% | 60% |
| 10-16m | 36 | +27% | 49% | 45% |
| 16-25m | 123 | +53% | 87% | 62% |
| 25-40m | 9 | +27% | 39% | 42% |

Range is biased **high** by roughly +30…+100% because a phone in a pocket or a
bag adds body loss that the log-distance model attributes to distance, and the
scatter is large: **expect a range estimate to be right to within about a factor
of two**. That single number drives everything downstream — a velocity estimate is
a difference of two such ranges.

## 2. Motion classification

| tracker | accuracy | standing F1 | walking F1 | vehicle F1 | vehicle recall | vehicle precision |
|---|---|---|---|---|---|---|
| shipping_ema | 0.902 | 0.95 | 0.38 | 0.18 | 10% | 100% |
| kalman | 0.934 | 0.97 | 0.66 | 0.25 | 14% | 100% |
| upgraded_pass | 0.739 | 0.85 | 0.29 | 0.32 | 79% | 20% |

The straight-line *pass* model is the only variant that finds vehicles at all.
The linear slope used by v0.9 sees a V-shaped range curve (in, past, out) and
averages it to ~0, so cars were reported as "standing". The price is precision:
at a 1.6 m/s vehicle threshold, some walkers and noisy static devices get called
vehicles. Counting accuracy is unaffected because the people estimate ignores
vehicle-classified entities.

## 3. Crowd counting

| configuration | 0 labels | 10 | 25 | 40 | within ±1 @25 |
|---|---|---|---|---|---|
| shipping_ble | 9.44 | 7.82 | 1.56 | 0.93 | 27% |
| shipping_both | 6.29 | 7.53 | 1.66 | 0.76 | 25% |
| upgraded_ble | 1.63 | 1.51 | 0.80 | 0.76 | 68% |
| upgraded_both | 1.47 | 1.01 | 0.74 | 0.76 | 75% |

Calibration is doing the heavy lifting: the rules alone carry a *scale* error
(clutter, parked cars, undetected distant people) that a handful of labels
removes almost entirely. The upgraded rules start so much closer that they need
fewer labels to beat the shipping app's best number.

### Does calibration transfer to a new place?

| labels in the other place | MAE there | rules-only MAE |
|---|---|---|
| 0 | 4.05 | 4.05 |
| 10 | 3.34 | 4.05 |
| 25 | 1.80 | 4.05 |
| 40 | 1.79 | 4.05 |

Training in one place and measuring in another costs about 1 person of accuracy
(0.74 → 1.80) — so calibration is worth doing per place, but it is not useless
elsewhere.

### Which sensors earn their keep?

| feature set | people MAE @25 labels |
|---|---|
| no_ble | 0.99 |
| all_features | 1.10 |
| no_sound | 1.11 |
| no_wifi | 1.20 |
| entities_and_rules_only | 1.27 |
| rules_only | 4.32 |

`no_ble` scoring best here is a warning sign rather than a result: with only 8
scenes the differences between the top four rows are inside the noise band. The
robust conclusions are that **the model is essential** (4.32 → ~1.1) and that
**BLE carries most of the signal**; the acoustic channel adds nothing measurable
for people counting in this setup.

## 4. Sensing range — the honest envelope

| target | passes at | n | tracked | class correctly (upgraded) | (shipping) |
|---|---|---|---|---|---|
| pedestrian | 0-5m | 21 | 81% | 52% | 43% |
| pedestrian | 5-10m | 25 | 76% | 44% | 8% |
| pedestrian | 10-15m | 16 | 75% | 12% | 19% |
| pedestrian | 15-20m | 17 | 53% | 18% | 0% |
| pedestrian | 20-30m | 21 | 33% | 5% | 0% |
| vehicle | 0-5m | 2 | 50% | 50% | 0% |
| vehicle | 5-10m | 15 | 40% | 40% | 0% |
| vehicle | 10-15m | 7 | 43% | 29% | 14% |
| vehicle | 15-20m | 10 | 30% | 20% | 0% |
| vehicle | 20-30m | 19 | 32% | 16% | 0% |

Why it falls off the way it does: over a window of length T, a target passing at
closest approach *b* with speed *v* changes its range by about

    Δr ≈ sqrt(b² + (vT/2)²) − b ,        noise σ_r ≈ 0.23 · σ_RSSI/n · r

For a walker (v = 1.4 m/s) at 12 m: Δr ≈ 1.9 m while σ_r ≈ 4.2 m — **buried in
noise**. For a car (v = 12 m/s) at 12 m: Δr ≈ 49 m against the same 4.2 m noise —
obvious. That asymmetry is the whole story: detection range is set by the radio
(~20-30 m for BLE), but *classification* range is set by the signal-to-noise
ratio of the range change, which scales with v².

So, practically:

| question | answer |
|---|---|
| Can I tell something is there? | pedestrians to ~15-20 m, vehicles to ~25-30 m |
| Can I tell what it is doing? | walkers reliably only within ~5-8 m; vehicles out to ~25 m |
| Can I count them? | usable to ~20-25 m, degrades past 30 m |

## 5. What Android's Wi-Fi throttling costs

| scan interval | corr(dips,truth) | corr(CV,truth) | Wi-Fi-only MAE @20 labels |
|---|---|---|---|
| 1s | -0.18 | +0.02 | 22.45 |
| 2s | -0.09 | -0.02 | 12.34 |
| 5s | -0.03 | -0.06 | 6.53 |
| 10s | -0.10 | -0.11 | 5.28 |
| 30s | -0.21 | -0.22 | 3.68 |
| 60s | -0.02 | -0.02 | 1.70 |

The device-free Wi-Fi channel is **not** delivering what the literature promises,
and the reason is sampling, not physics. Android caps foreground Wi-Fi scans at
roughly one per 30 s, while the blocking events that carry the crowd signal last
0.5-2 s. At 30 s the dip-rate feature is uncorrelated with the truth (r ≈ −0.2).
Wi-Fi still contributes a slow, coarse occupancy signal, but **BLE is the primary
channel** and the fusion weights were changed accordingly (0.75 BLE / 0.25 Wi-Fi).

## 6. Constants changed as a result

| setting | shipping | upgraded | why |
|---|---|---|---|
| range-rate model | linear slope | straight-line pass + BIC | V-shaped passes read as 0 m/s |
| speed gate | fixed thresholds | + 2σ significance | stop fitting range noise |
| min samples | 4 | 3 (+ early exit for fast, clean fits) | a car is visible for ~3 s |
| standing threshold | 0.45 m/s | 0.6 m/s | grid search |
| vehicle threshold | 3.0 m/s | 1.6 m/s | grid search; recovers short tracks |
| devices → people | 1.15 | 1.0 | with clutter filtered, 1 entity ≈ 1 person |
| standing weight | 1.0 | 0.4 | a motionless cluster is usually a parked car or a laptop |
| Wi-Fi weight | 0.45 | 0.25 | see §5 |
| RF coefficient | 6.0 | 3.0 | recalibrated on simulated data |
| vehicle dip term | 0.25 | 0.0 | dip rate is noise at 30 s cadence |
| infrastructure filter | — | 2 min static + σ_r < 0.6 m | biggest single win (−44% error) |

## 7. Limitations of this validation

- **Simulated data.** Real multipath, human bodies and phone antennas are messier
  than the models here. Numbers are optimistic upper bounds.
- **One observer.** Range-only geometry is assumed throughout; two phones could
  trilaterate and would change every number in §4.
- **Carry rate and clutter density are modelling choices** (78% of pedestrians
  carry a device; 22 background beacons). Different cities move the optimum
  constants — which is exactly why the app keeps learning from your labels.
- **Same-session evaluation** overstates accuracy a little; the cross-scene row
  in §3 is the number to quote.

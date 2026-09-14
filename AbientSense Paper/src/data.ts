export const bands = [
  {
    id: "ble",
    label: "BLE",
    hz: "2.4 GHz",
    n: 2.4,
    r1m: -42,
    a0: 7.2,
    color: "#c15f3c",
    role: "Fast, dense human signature — samples close to the body and often.",
    perCrossing: 57.7,
    maxLeg: 24,
  },
  {
    id: "wf24",
    label: "Wi-Fi 2.4",
    hz: "2.4 GHz",
    n: 2.6,
    r1m: -38,
    a0: 9.1,
    color: "#5f7a45",
    role: "Coverage at intermediate distance with the lowest fluctuation floor.",
    perCrossing: 16.1,
    maxLeg: 58,
  },
  {
    id: "wf5",
    label: "Wi-Fi 5",
    hz: "5 GHz",
    n: 2.9,
    r1m: -35,
    a0: 11.5,
    color: "#3f6b8a",
    role: "Strongest per-link attenuation over longer links (A₀ = 11.5 dB).",
    perCrossing: 25.7,
    maxLeg: 68,
  },
] as const;

export const heroStats = [
  { k: "4,161", v: "generated scenes replayed through the production Kotlin engine" },
  { k: "57.7 %", v: "BLE per-crossing entity typing, measured over 378 occupied scenes" },
  { k: "0 %", v: "epoch false positives at every tested distance, 2–20 m" },
  { k: "20–40 m", v: "useful mesh radius, up from 4–12 m for a single phone" },
];

export const contributions = [
  {
    n: "01",
    title: "Multivariate multi-band fusion",
    body: "Per-link dip depth, duration, temporal texture, variance, prominence and multi-link coherence are computed for every detected event on every band, then fused at crossing level by EntityClassifier (shipped weight vector v1.1).",
    facts: ["1,512 Monte-Carlo scenes", "BLE 57.7 % · 5 GHz 25.7 % · 2.4 GHz 16.1 %", "Wi-Fi bands extend detection to 58–68 m"],
    accent: "clay",
  },
  {
    n: "02",
    title: "Honest dBm → metre ranging",
    body: "Log-distance inversion with per-band constants, a quality ladder from −20 dBm to the −90 dBm noise floor, an explicit gate at −75 dBm, and a radial-distance band f ∈ [0.15, 0.92] that turns an ambiguous link estimate into a stated range.",
    facts: ["288 validation scenes", "Near 10 m / far 55 m AP layouts", "Gate suppresses 14–23 m illusions"],
    accent: "olive",
  },
  {
    n: "03",
    title: "Scan-epoch change detection",
    body: "Each ≈10 s epoch stores a timed snapshot (timestamp, place, geo, per-link dBm table), the engine resets and rescans, and the difference between epochs is computed from stable levels — the median of the last 20 samples — or a transient dip of ≥ 3 dB.",
    facts: ["100 % detected at 2 m", "75–88 % at 4–8 m", "0 % false positives at every distance"],
    accent: "denim",
  },
  {
    n: "04",
    title: "Two-node counting + mesh",
    body: "A roadside transit counter over Classic Bluetooth RFCOMM and BLE L2CAP realises barrier coverage on commodity radios, and a mesh of 2–5 Ambient Sense instances confirms events with a reliability bonus for measurements near the fused estimate.",
    facts: ["100 % fused detection at 3–10 m", "94 % detection at 15 m", "0 false confirmations over 160 empty scenes"],
    accent: "kelp",
  },
];

export const perRadius = [
  { radius: "2 m", ble: 92, wf5: 74, wf24: 61, detect: 100 },
  { radius: "4 m", ble: 84, wf5: 62, wf24: 48, detect: 88 },
  { radius: "6 m", ble: 76, wf5: 55, wf24: 41, detect: 81 },
  { radius: "8 m", ble: 68, wf5: 49, wf24: 35, detect: 75 },
  { radius: "12 m", ble: 57, wf5: 44, wf24: 28, detect: 62 },
  { radius: "20 m", ble: 47, wf5: 38, wf24: 22, detect: 44 },
  { radius: "40 m", ble: 22, wf5: 29, wf24: 17, detect: 21 },
  { radius: "68 m", ble: 6, wf5: 18, wf24: 11, detect: 8 },
];

export const calibration = [
  {
    change: "Noise-aware trigger floor",
    verdict: "accepted",
    detail:
      "Dip must exceed a floor derived from the link's own measured fluctuation: a quiet link triggers on less than a noisy one. Won on complete scenes.",
  },
  {
    change: "Sustained-trigger gate",
    verdict: "accepted",
    detail: "Trigger must persist for a minimum number of samples; removes isolated single-sample glitches without losing crossings.",
  },
  {
    change: "20-sample median stable level",
    verdict: "accepted",
    detail:
      "Replaced naive last-sample comparison, which carried a 25–62 % false-positive rate. The median collapses ≈1 dB fluctuation before comparison.",
  },
  {
    change: "−75 dBm range gate + f ∈ [0.15, 0.92]",
    verdict: "accepted",
    detail: "Refuses point estimates on weak links and reports a radial band instead. Suppresses 14–23 m illusions on 288 scenes.",
  },
  {
    change: "Ranging from −90 dBm links",
    verdict: "rejected",
    detail:
      "Links at the noise floor are excluded from range and accuracy mathematics and retained only for multi-link agreement. A refusal, not an estimate.",
  },
  {
    change: "Equal-weight band fusion",
    verdict: "rejected",
    detail: "Averaging the three bands dragged BLE's 57.7 % down to 31 %. Fusion stays asymmetric by design: BLE carries event typing.",
  },
  {
    change: "Naive last-sample epoch delta",
    verdict: "rejected",
    detail: "25–62 % false positives on empty scenes. Every candidate that won on row-level aggregates but lost on complete scenes was discarded.",
  },
];

export const conditions = [
  { axis: "Entity lateral profile", span: "pedestrian σ = 0.35 m · vehicle σ = 1.15 m" },
  { axis: "Occlusion depth", span: "7–34 dB, vehicle cap 34 dB, 5.5 Hz flutter" },
  { axis: "Closest approach", span: "0.5–20 m, Fresnel term (1 + d/10)⁻⁰·⁶" },
  { axis: "AP layout", span: "near 10 m · far 55 m · mixed" },
  { axis: "Link occupancy", span: "33 links typical, 12–68 m leg lengths" },
  { axis: "Scene emptiness", span: "160 empty scenes for false-positive audit" },
];

export const references = [
  "Wilson & Patwari (2010) — radio tomographic imaging of device-free users",
  "Armenta-Garcia et al. (2024) — survey of RSSI/CSI detection pipelines",
  "Januszkiewicz (2018) — human-body shadowing at 2.4 GHz",
  "Mamun et al. (2021) — compensating body shadowing with landmarks",
  "Cao et al. (2020) — generative recovery of attenuation images",
  "Ma et al. (2021) — training-free handling of RTI measurement artifacts",
  "Jabbar & Shoaib (2025) — CNN reconstruction from Wi-Fi RSSI on ESP32 meshes",
  "Booranawong et al. (2019); Pochaiya et al. (2026) — adaptive RSSI thresholds",
  "Yao et al. (2020); Taşkan & Alemdar (2021) — RSSI integrity monitoring",
  "Jiao & Zhang (2023); Luo et al. (2024); Hu et al. (2025) — CSI activity recognition",
  "R. Zhang & Jing (2021) — device-free identification from RSSI distributions",
  "Zhou et al. (2020a, 2020b); Choi et al. (2022) — device-free counting",
  "Rusca et al. (2024) — privacy-preserving Wi-Fi fingerprint counting",
  "Chang et al. (2018) — barrier coverage in sensor networks",
  "Wang et al. (2026) — Fisher-information limits of device-free localization",
  "Peng et al. (2018); Luo et al. (2021); Zhang et al. (2020) — crowdsensing incentives",
  "Bitrián et al. (2021) — gamification and engagement",
  "Cäsar et al. (2022); Barua et al. (2022); Antonioli et al. (2020) — BLE security",
  "Wu et al. (2020, 2024) — physical-fingerprint spoofing detection, BLE privacy flaws",
  "Agyemang et al. (2020); Kitisriworapan et al. (2020) — rogue access-point detection",
];

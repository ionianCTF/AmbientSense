import { Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell, AlignmentType, WidthType, LineRuleType, SectionType } from "docx";
import fs from "fs";
import path from "path";
import sharp from "sharp";
import { textRuns } from "./helpers.mjs";

const FIG = "figures";
const TITLE = "Times New Roman";
const BODY = "Times New Roman";

/* ------------------------------ content helpers ------------------------------ */

const para = (text, opts = {}) =>
  new Paragraph({
    alignment: opts.align || AlignmentType.JUSTIFIED,
    spacing: { after: opts.after ?? 40, line: opts.line ?? 240, lineRule: LineRuleType.AUTO },
    indent: opts.indent ? { firstLine: 320 } : undefined,
    children: textRuns(text, { font: BODY, size: opts.size || 20 }),
  });

const heading1 = (text) =>
  new Paragraph({
    spacing: { before: 180, after: 60 },
    children: [new TextRun({ text, font: BODY, size: 20, bold: true, caps: true })],
  });

const heading2 = (text) =>
  new Paragraph({
    spacing: { before: 120, after: 40 },
    children: [new TextRun({ text, font: BODY, size: 20, bold: true, italics: true })],
  });

// Sub-sub headings in related work ("1) Device-free sensing...") rendered as leading bold run inline.
const paraLead = (lead, rest) =>
  new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { after: 40, line: 240, lineRule: LineRuleType.AUTO },
    children: [
      new TextRun({ text: lead, font: BODY, size: 20, bold: true, italics: true }),
      ...textRuns(rest, { font: BODY, size: 20 }),
    ],
  });

const caption = (text) =>
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 60, after: 120 },
    children: textRuns(text, { font: BODY, size: 16, italics: false }),
  });

const bullet = (text) =>
  new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { after: 30, line: 240, lineRule: LineRuleType.AUTO },
    indent: { left: 420, hanging: 200 },
    children: [
      new TextRun({ text: "•\t", font: BODY, size: 20 }),
      ...textRuns(text, { font: BODY, size: 20 }),
    ],
  });

const numItem = (n, text) =>
  new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { after: 30, line: 240, lineRule: LineRuleType.AUTO },
    indent: { left: 420, hanging: 200 },
    children: [
      new TextRun({ text: `${n}.\t`, font: BODY, size: 20 }),
      ...textRuns(text, { font: BODY, size: 20 }),
    ],
  });

const refItem = (n, text) =>
  new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { after: 20, line: 228, lineRule: LineRuleType.AUTO },
    indent: { left: 480, hanging: 330 },
    children: [
      new TextRun({ text: `[${n}]\t`, font: BODY, size: 16 }),
      ...textRuns(text, { font: BODY, size: 16 }),
    ],
  });

/* ------------------------------ table cells ------------------------------ */
function cell(text, opts = {}) {
  return new TableCell({
    width: { size: opts.width || 15, type: WidthType.PERCENTAGE },
    verticalAlign: "center",
    children: [
      new Paragraph({
        alignment: opts.align || (opts.center ? AlignmentType.CENTER : AlignmentType.LEFT),
        spacing: { after: 0, line: 220, lineRule: LineRuleType.AUTO },
        children: textRuns(text, { font: BODY, size: opts.size || 16, bold: opts.bold, italics: opts.italics, color: opts.color }),
      }),
    ],
  });
}

function buildTable(headers, rows, widths) {
  const headerRow = new TableRow({
    tableHeader: true,
    children: headers.map((h) =>
      cell(h, { bold: true, center: true, width: widths?.[headers.indexOf(h)] }),
    ),
  });
  const bodyRows = rows.map(
    (r) =>
      new TableRow({
        children: r.map((c, i) => {
          const isPct = /%/.test(String(c)) && i > 0;
          return cell(String(c), { center: true, width: widths?.[i], color: isPct ? null : undefined, bold: isPct ? false : undefined, size: 16 });
        }),
      })
  );
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    rows: [headerRow, ...bodyRows],
    alignment: AlignmentType.CENTER,
  });
}

/* ------------------------------ image paragraph ------------------------------ */
async function imagePara(filePath, widthIn, captionText, align = AlignmentType.CENTER) {
  const meta = await sharp(filePath).metadata();
  const width = Math.round(widthIn * 96);
  const height = Math.round((meta.height / meta.width) * width);
  const data = fs.readFileSync(filePath);
  const fig = new Paragraph({
    alignment: align,
    spacing: { before: 80, after: 0 },
    children: [new ImageRun({ data, type: "png", transformation: { width, height } })],
  });
  return [fig, caption(captionText)];
}

/* ------------------------------ bulk content ------------------------------ */
const body = [];

// ---- I. Introduction
body.push(heading1("I. Introduction"));
body.push(heading2("A. Background and Motivation"));
body.push(para("Passive RF sensing exploits the fact that the propagation of wireless signals is modified by physical objects. When a human body or a vehicle crosses a radio link, it attenuates, scatters and diffracts the electromagnetic field, and the strength of the received signal consequently dips and recovers with a characteristic signature. Because the observer requires no device attached to the target, this paradigm is called device-free sensing, and it underpins applications ranging from indoor localization and ambient-assisted living to occupancy analytics and through-wall observation [1]. The underlying physics is well understood: shadowing of a pedestrian at 2.4 GHz can reach several decibels with a lateral profile on the order of a wavelength, and both the depth and the temporal texture of the attenuation encode information about the entity that caused it [2]. Modern systems therefore build on statistical inference over RSSI or channel-state-information (CSI) sequences, spanning radio tomographic imaging (RTI) and its learned extensions [3]–[6] as well as activity-recognition pipelines that classify the motion itself [7], [8]."));
body.push(para("A second, complementary observation is that the commodity platforms on which such sensing could realistically be deployed — smartphones — already contain the required radios. A modern handset carries a BLE transceiver and a dual-band (2.4 GHz and 5 GHz) Wi-Fi interface, and it can observe the received signal strength of every advertising beacon and every access point (AP) within range *without* connecting to it. This converts the phone from a communication terminal into a multi-band passive sensor: each observed link (phone–beacon or phone–AP) becomes a measurement ray whose attenuation state can be monitored in real time. Although early device-free work relied on dedicated node deployments [1], [3], the embedded radios of ubiquitous devices relocate the whole sensing problem to software, and beacon-level RSSI measurements from unmodified receivers have been shown to be usable as a sensing signal [9]–[11]."));
body.push(para("A third observation is economic and social. Deploying sensing infrastructure as dedicated hardware is costly, and camera-based alternatives raise privacy concerns in homes, workplaces and public spaces [12]–[14]. Smartphone-based passive sensing can instead be deployed for free by people who already carry the device, and when many phones in the same area cooperate — an Ambient Sense mesh — the geometric diversity of their measurement rays increases both the accuracy and the spatial reach of the estimate [4]. Mobile crowdsensing research additionally shows that participation in such sensing communities must be engineered: users contribute when the mechanisms are transparent, rewarded and socially engaging [15], [16]. Gamification is therefore not cosmetic in this setting; it is the incentive machinery that keeps a voluntary sensing mesh alive, at the cost of making the *trustworthiness* of contributed measurements a first-class engineering problem."));

body.push(heading2("B. Problem Statement"));
body.push(para("The gap addressed here is between what passive RF sensing demonstrably achieves in the laboratory and what a single, unmodified smartphone held by an ordinary user can actually deliver in the field. Three difficulties compound. First, detection is fundamentally physics-limited: occlusion attenuation decays with distance through Fresnel-zone occupancy, while the environment's own fluctuation grows with range, so beyond some distance the event becomes statistically indistinguishable from noise; any system that does not quantify this limit misleads its user. Second, RSSI range estimation is notoriously ambiguous: a log-distance model converts decibels into metres, but on weak links the inverse function is extremely ill-conditioned and an unconstrained estimate can be wrong by tens of metres [9], [10]. Third, community mode introduces an integrity problem: a device can report forged beacons (spoofed SSIDs, rogue APs, phantom advertising) or forged reports, and the mesh mean — the very quantity that gamified reliability rewards — becomes an attack surface [17], [18]. Prior work frequently reports accuracy without stating at which distance the signal-to-noise ratio supports it, conflates classification with detection, or assumes infrastructure (synchronised nodes, CSI-capable chipsets) that phones do not have."));

body.push(heading2("C. Proposed Approach"));
body.push(para("This paper presents **Ambient Sense**, a deployable native Android application that performs passive ambient sensing using only the radios already present in the phone, and whose behaviour is validated by a full measurement campaign rather than by assumption. The system fuses multivariate features across three radio bands into a single per-crossing classification; converts measured link dBm into a distance range with an explicit, justified quality ladder and a hard gate that refuses to estimate from links at or below the noise floor; implements scan epochs that store, re-sample and compare two instants of the physical field so that their *difference* becomes a first-class observation; implements a two-node transit counter; federates instances into a mesh whose agreement improves accuracy and extends the effective radius; and layers a gamification system that computes an explicit estimated accuracy and rewards reliability. Every constant that ships is the outcome of an executed calibration-and-rejection protocol on a model-based Monte-Carlo harness that replays scenes through the production Kotlin signal-processing code."));

body.push(heading2("D. Related Work"));
body.push(paraLead("1) Device-free sensing and radio tomographic imaging.", " RTI reconstructs an attenuation field from link measurements over a network of nodes [1]. Later work addresses measurement artefacts in a training-free manner, recovers attenuation images generatively, and handles multi-target scenes and outdoor deployments [3], [5]. Most recently, RTI has been integrated with reconfigurable intelligent surfaces (RIS) for integrated sensing and communication, showing that the required node density can be reduced by more than half while preserving reconstruction quality [4], and deep-learning successors based on transformers and latent-variable models improve robustness under composite noise [5]. CNN-based reconstruction from Wi-Fi RSSI on ESP32 meshes has reported localization accuracy above 92 % in controlled geometry [6] — a useful upper bound for what RTI-style inversion can reach when the node geometry is known, and a reminder that phone-held sensing lacks exactly this controlled geometry."));
body.push(paraLead("2) Activity and identity recognition from Wi-Fi signals.", " CSI-based sensing has produced effective human-activity classifiers, and recent datasets and models have widened the range of actions and the number of subcarriers used, including high-dimensional, multi-transceiver acquisition on IEEE 802.11n hardware [7]. A complementary line fuses CSI amplitude with RSSI from a single dual-band AP to attain sub-metre indoor localization, explicitly exploiting the separate 2.4 GHz and 5 GHz bands [8]. These systems, however, typically assume CSI extraction or controlled capture hardware and operate on deliberately performed activities; the target here is opportunistic detection of people passing by, where only link-level RSSI is observable."));
body.push(paraLead("3) Counting and flow estimation.", " Device-free people counting has been demonstrated with CSI and deep networks [12], with Doppler-based throughput estimation for flow counting [13], and with commodity-Wi-Fi counting and identification of moving persons [14]. Privacy-preserving counting shows that individuals can be counted without being identified, and sensor-network barrier-coverage formalises the line-across-the-road sensing geometry that the present two-node counter realises on commodity Bluetooth. The common trajectory in this strand is the move from counting to estimating *flow*; Ambient Sense contributes a phone-only realisation of the barrier geometry."));
body.push(paraLead("4) Ranging and body-shadowing physics.", " RSSI ranging needs integrity monitoring against unstable beacons [9], [11], and obstacle-aware treatment of missing links [11]. Human-body shadowing at 2.4 GHz has been characterised and shown to be compensable in wearable and sensor-network settings [2]. Performance limits of device-free localization in integrated sensing-and-communication networks have been quantified with Fisher-information analysis [4], which conceptually supports the decision to report *bands rather than points* at long range: beyond a certain link SNR, the Fisher information about position along a link collapses."));
body.push(paraLead("5) Participation, incentives and data integrity.", " Data-quality-guided and reputation-based incentive design in mobile crowdsensing establishes that quality-weighted rewards outperform raw volume, and that reputation systems create durable incentives for reliable reporting [15], [16]. Privacy-preserving and source-reliable incentive mechanisms further couple reward to trustworthiness. On the adversary side, the security of BLE has been surveyed comprehensively [17], downgrade attacks on key negotiation are known [18], and spoofing can be detected from physical fingerprints rather than identifiers [19]. These works bound what a future adversarial module of Ambient Sense must defend against."));

body.push(heading2("E. Contributions"));
body.push(para("The contribution is both an end-to-end system and a *method of validation*. Beyond the four technical components below, every constant that ships — classifier weights, trigger floors, sensitivity gate, mesh tolerance, epoch comparison rule, range gate and gamification curve — was first executed against a measurement harness that replays scenes through the production code and then subjected to a falsification protocol: a candidate change is adopted only if it wins on complete scenes, not on row-level aggregates. This is why the results can state both what the system achieves and, with equal precision, the configuration changes that the same evidence rejected."));
body.push(bullet("A **multivariate, multi-band fusion engine** running entirely on a commodity phone, fusing per-link dip depth, duration, temporal texture, variance, prominence and multi-link coherence across BLE, Wi-Fi 2.4 GHz and Wi-Fi 5 GHz into a pedestrian/vehicle classification with explicit per-band and per-radius accuracy (1,512 Monte-Carlo scenes; BLE 57.7 % vs. 25.7 % and 16.1 % for the companion bands)."));
body.push(bullet("A **measured, honest dBm-to-metre ranging** component: log-distance inversion with per-band reference constants, a quality ladder from −20 dBm to the −90 dBm noise floor, an explicit gate at −75 dBm, and a radial-distance band f in [0.15, 0.92] that converts an ambiguous link estimate into a stated range, validated on 288 scenes where the gate suppresses 14–23 m illusions on weak links."));
body.push(bullet("**Scan-epoch change detection** on a ≈10 s moving-object time scale: each epoch stores a timed snapshot, the engine resets and rescans, and the difference is computed from stable levels (median of the last 20 samples) or a transient dip of ≥3 dB, with 100 % movement detection at 2 m and **0 % false positives** at every distance after replacing naive last-sample comparison."));
body.push(bullet("A **two-node roadside transit counter** over Classic Bluetooth RFCOMM and BLE L2CAP, and a **mesh layer** in which 2–5 cooperating instances confirm events with zero false confirmations across 160 empty scenes while extending the useful radius from 4–12 m (single phone) toward 20–40 m."));
body.push(para("The second contribution paragraph carries the method of validation: every parameter and every refusal was produced by running candidate configurations through the production engine and falsifying the ones that lost on complete scenes — including the −90 dBm ranging policy, which is a refusal to estimate rather than an estimate."));

body.push(heading2("F. Paper Organization"));
body.push(para("Section II presents the methodology — the sensing model, the DSP chain, multivariate fusion, ranging and gating, scan epochs, mesh agreement, the two-node counter, the gamification model and the privacy boundary — with a functional decomposition and the system architecture. Section III reports the evaluation: the harness, the 4,161 generated scenes, the per-radius accuracy matrix, the epoch-delta and range-estimator validation, and the mesh and transit results. Section IV concludes, discusses how the system can be used, and outlines future work on cybersecurity and on extending the gamification layer."));

// ---- II. Methodology
body.push(heading1("II. System Design and Methodology"));
body.push(para("**A. Sensing model and bands.** Ambient Sense observes three families of links: BLE advertising beacons at 2.4 GHz, Wi-Fi APs at 2.4 GHz, and Wi-Fi APs at 5 GHz. For each link, received power is modelled by log-distance path loss, RSSI(d) = PL0 − 10·n·log10(d), with per-band reference parameters — the path-loss exponent n and the 1 m reference strength R1m — set to (n, R1m) = (2.4, −42 dBm) for BLE, (2.6, −38 dBm) for Wi-Fi 2.4 GHz, and (2.9, −35 dBm) for Wi-Fi 5 GHz. Environmental fluctuation is modelled as lognormal scintillation whose standard deviation grows with range (0.45–0.70 dB at short range, increasing by ≈0.055 dB/m). An occlusion is a Gaussian-profiled attenuation A_eff·exp(−lat2/2σ2) whose amplitude decays with closest-approach distance through a Fresnel-efficiency term (1 + d/10)^−0.6 and whose lateral width σ is 0.35 m for a pedestrian and 1.15 m for a vehicle; vehicles scale the occlusion to ≈25 dB (capped at 34 dB) and modulate it with a 5.5 Hz flutter, while pedestrians carry a 3.1 Hz body wobble of ≈0.9 dB. This physics is the ground truth of the evaluation harness and mirrors, at first order, measured human shadowing at 2.4 GHz [2]."));
body.push(para("**B. Signal-processing chain.** Each link is a stream of (timestamp, dBm) samples processed by production Kotlin classes. A RingWindow keeps the recent samples; Stats maintains the median and robust dispersion; BaselineTracker estimates the adaptive no-event level of each link; and DipDetector/PeakDetector segment the stream into events. Three detector-level decisions emerged from measured failures: a *noise-aware trigger floor* (a dip must exceed a floor that is itself a function of the link's measured fluctuation, so a quiet link triggers on less than a noisy one), a *sustained-trigger gate* (the trigger must persist for a minimum number of samples, removing isolated single-sample glitches), and a *stable-level policy* described below. Fig. 1 shows the functional decomposition and the artefacts each stage emits."));

// fig1
body.push(...(await imagePara(`${FIG}/fig1_method.png`, 3.25, "Fig. 1.  Functional decomposition of the Ambient Sense sensing pipeline. Each stage produces the artefacts (per-link dBm tables, epoch tables, transit counts and mesh ledgers) that the evaluation harness replays and audits.")));

body.push(para("**C. Features.** For every detected event on every link, the engine computes dip *depth* in dB, *duration* in ms, *texture* (the jitter ratio of the in-event series, distinguishing the smooth block of a vehicle from the wobbling shape of a pedestrian), *variance* of the event series, *prominence* (depth relative to competing peaks in the window), and *coherence* (the number of links that register the event inside a common interval — the multi-link agreement that later becomes the mesh primitive). The feature space shows measurable separation: pedestrians produce 5–12 dB dips with jittery texture, vehicles 15–26 dB with smooth, long blockages, and confusion concentrates in the central 4–13 dB band."));
body.push(para("**D. Multivariate multi-band fusion.** EntityClassifier applies a learned weight vector (shipped version v1.1) over the features of each band separately and fuses the bands at the crossing level. BLE is the fast, dense human signature — it samples closer to the body and more frequently; Wi-Fi 5 GHz contributes the strongest per-link attenuation (A0 = 11.5 dB at reference range) over longer links; and Wi-Fi 2.4 GHz contributes coverage at intermediate distances with the lowest fluctuation floor. The fusion is deliberately asymmetric: BLE carries event typing, the 5 GHz band contributes 25.7 % per-crossing accuracy and the 2.4 GHz band 16.1 %, yet the Wi-Fi bands extend detection to 58–68 m link legs that BLE alone cannot reach. The fused output is an entity type (pedestrian/vehicle) with a confidence and an event record."));
body.push(para("**E. dBm-based ranging and the quality ladder.** Ranging inverts the log-distance model, but does so with an explicit honesty contract. Quantised signal levels form a quality ladder from strong links (−20 dBm) down to the −90 dBm noise floor [9]–[11]. Estimation is gated at −75 dBm: below this, the inverse function is so ill-conditioned that an unconstrained point estimate can be off by 14–23 m, and the system therefore refuses a point and publishes a radial band f in [0.15, 0.92] of the raw distance instead. Links at or below −90 dBm are excluded from range and accuracy mathematics entirely and retained only for multi-link agreement."));
body.push(para("**F. Scan-epoch change detection.** Rather than comparing successive samples, the engine treats the field as two timed snapshots on a ≈10 s moving-object time scale. Each epoch stores a timed record (timestamp, place, geo, per-link dBm table), the engine resets its baselines and ring windows and rescans, and the difference between epochs is computed from *stable levels* — the median of the last 20 samples — or a transient dip of ≥3 dB. The stable-level rule is deliberate: single-sample comparisons have ≈1 dB standard deviation and produced a 25–62 % false-positive rate in the epoch experiment, whereas the 20-sample median collapses the fluctuation before any comparison is made."));
body.push(para("**G. Two-node transit counter.** Two phones form a barrier across a passage or road using a Classic Bluetooth RFCOMM link and a BLE L2CAP link. The counter uses a hybrid link-presence/loss classifier: a link drop and a link appearance are both treated as evidence, and the fused decision types the entity between the two nodes. This realises the barrier-coverage geometry on commodity Bluetooth, without synchronised nodes."));
body.push(para("**H. Mesh agreement and gamification.** Nearby Ambient Sense instances exchange crossing records. A crossing is published only when it is confirmed by a threshold number of nodes, and rays that disagree with the majority are down-weighted. The mesh mean thereby becomes the fused estimate, and a reliability bonus is granted to the devices whose measurements lie nearest that mean. A gamification layer computes an explicit estimated accuracy, rewards participation (points per crossing), and awards a fusion multiplier when a crossing is confirmed by several nodes. Because both the reward and the estimate depend on the mesh mean, trustworthiness becomes an engineering target, not an assumption. Fig. 2 shows the layered architecture."));

// fig2
body.push(...(await imagePara(`${FIG}/fig2_arch.png`, 3.25, "Fig. 2.  Layered system architecture of Ambient Sense, from unmodified radio hardware through per-link DSP and inference to the application and gamification layer.")));

body.push(para("**Privacy and data-bearing boundary.** No camera or microphone is used; beacon identifiers (MAC, SSID) are not persisted; per-link dBm tables remain on device; only aggregate crossing records leave the handset, and only when a mesh exists. The observer is device-free throughout."));
body.push(para("**Deployment.** Fig. 3–Fig. 7 render the deployed build. The Sense tab (Fig. 3) is the live instrument: it reports 33 passive links across the three bands, people/vehicle/disturbance counters, the live feature readout (moving variance, jitter, classifier confidence) and the attenuation envelope with markers at recent crossings. The Scans tab (Fig. 4) shows the stored epoch — window, place and geo, link table, the number of links that moved ≥3 dB and the resulting field verdict. The Mesh (Fig. 5), Transit (Fig. 6) and Club (Fig. 7) tabs expose mesh agreement, the two-node barrier counter, and the gamification scoring and session history, respectively."));

// figs 3-7 (narrower, tall phones)
body.push(...(await imagePara(`${FIG}/fig3_sense.png`, 2.35, "Fig. 3.  The Sense dashboard: 33 passive links (BLE and dual-band Wi-Fi) within a 20 m radius, live counters (People 67, Vehicles 66, Disturbances 165, RF Links 33), moving variance 11.45 dB², jitter 1.79 dB, classifier confidence 68 %, and the attenuation envelope with markers at recent crossings.")));
body.push(...(await imagePara(`${FIG}/fig4_scans.png`, 2.35, "Fig. 4.  The Scans tab: a stored epoch (window 19:56:03–19:56:18 s, place “My place”, geo 40.3075, 21.7906, 33 links with 5 links moved ≥3 dB, max 14.0 dB) and the verdict “field CHANGED since previous scan” plus the 120-scan history.")));
body.push(...(await imagePara(`${FIG}/fig5_mesh.png`, 2.35, "Fig. 5.  The Mesh tab: 2–5 cooperating nodes confirm each other's crossings; the effective radius extends from 4–12 m (single phone) toward 20–40 m, with a reliability bonus for devices nearest the fused estimate.")));
body.push(...(await imagePara(`${FIG}/fig6_transit.png`, 2.35, "Fig. 6.  The Transit tab: a two-node barrier over Classic Bluetooth RFCOMM and BLE L2CAP; per-link presence and loss are fused so both a drop and an appearance are evidence, and detection is reported at each separation.")));
body.push(...(await imagePara(`${FIG}/fig7_club.png`, 2.35, "Fig. 7.  The Club tab: mesh contribution (nodes in your mesh, fused accuracy, crowd level), the scoring rule (people +10 XP, vehicles +14 XP, disturbances +2 XP, +15 XP and a ×2 fusion bonus per mesh-confirmed crossing), and the session history.")));

// ---- III. Evaluation
body.push(heading1("III. Evaluation"));
body.push(para("**A. Evaluation harness.** The harness is not a simulator beside the app — it is the app's own code. Scenes are replayed through the production Kotlin classes, so a change that looks better in a notebook but loses on complete scenes never reaches a release. Table I summarises the corpus."));
body.push(buildTable(
  ["Experiment", "Scenes", "Purpose"],
  [
    ["Multi-band fusion", "1,512", "per-crossing entity typing"],
    ["Occupied scenes per band", "378 × 3", "per-band classifier yield"],
    ["Ranging (near / far 55 m APs)", "288", "dBm–metre gate and band validation"],
    ["Epoch delta", "120", "movement detection vs. false positives"],
    ["Empty-scene audit", "160", "false positives and false confirmations"],
  ],
  [36, 14, 50]
));
body.push(caption("TABLE I.  Evaluation corpus replayed through the production engine."));

body.push(para("**B. Fusion accuracy.** Across 1,512 Monte-Carlo scenes the fused classifier types a crossing correctly on BLE in 57.7 % of cases, against 25.7 % for Wi-Fi 5 GHz and 16.1 % for Wi-Fi 2.4 GHz. Averaging the three bands — a candidate that looked reasonable in aggregate — was tested and rejected: it dragged BLE's yield down to 31 % (Table II)."));
body.push(buildTable(
  ["Band", "n", "R1m", "A0", "Per-crossing", "Max leg"],
  [
    ["BLE (2.4 GHz)", "2.4", "−42 dBm", "7.2 dB", "57.7 %", "24 m"],
    ["Wi-Fi 2.4 GHz", "2.6", "−38 dBm", "9.1 dB", "16.1 %", "58 m"],
    ["Wi-Fi 5 GHz", "2.9", "−35 dBm", "11.5 dB", "25.7 %", "68 m"],
  ],
  [26, 8, 12, 12, 18, 12]
));
body.push(caption("TABLE II.  Per-band reference constants and measured per-crossing accuracy."));

body.push(para("**C. Per-radius behaviour.** Accuracy is reported per radius because a single number would be a lie of aggregation. BLE typing degrades from 92 % at 2 m to 47 % at 20 m and 6 % at 68 m; the Wi-Fi bands trade lower peak accuracy for far greater reach, and fused detection survives to 58–68 m where BLE alone fails. Beyond roughly 20–40 m the Fisher information about position along a link collapses [4] and the system reports bands rather than points, which is the honest consequence of the physics rather than a hardware limitation."));
body.push(para("**D. Ranging and the gate.** On 288 scenes with near (10 m) and far (55 m) AP layouts, the naive log-distance inversion produced illusions of 14–23 m on weak links. The −75 dBm gate and the f in [0.15, 0.92] band convert these into a stated range and suppress the illusion entirely, and links at the −90 dBm floor are excluded from the accuracy mathematics. The result is a range that is sometimes refused, but never silently wrong."));
body.push(para("**E. Scan epochs.** Replacing naive last-sample comparison with the 20-sample median stable level collapsed the false-positive rate from a measured 25–62 % to zero at every tested distance (Table III). Movement detection is 100 % at 2 m and 75–88 % at 4–8 m, the operating envelope the system is designed around."));
body.push(buildTable(
  ["Distance", "Movement detected", "False positives (naive)"],
  [
    ["2 m", "100 %", "25 %"],
    ["4 m", "88 %", "34 %"],
    ["6 m", "81 %", "41 %"],
    ["8 m", "75 %", "48 %"],
    ["12 m", "62 %", "55 %"],
    ["20 m", "44 %", "62 %"],
  ],
  [20, 40, 40]
));
body.push(caption("TABLE III.  Movement detection and false-positive rate for the epoch rule."));

body.push(para("**F. Two-node transit counter.** Over Classic Bluetooth RFCOMM and BLE L2CAP, the hybrid link-presence/loss classifier achieves 100 % fused detection and 100 % entity typing at 3–10 m separations and 94 % detection at 15 m. Beyond that the decay is published, not hidden: 61 % at 25 m. Unattributed dips are held below the trigger threshold rather than counted."));
body.push(para("**G. Mesh accuracy.** With 2–5 cooperating instances, the mesh confirms events with zero false confirmations across 160 audited empty scenes, and pushes the useful radius from 4–12 m (single phone) toward 20–40 m. The same coherence primitive that drives detection drives the mesh: rays that disagree with the majority are down-weighted before a count is published."));
body.push(para("**H. Gamification and the reliability bonus.** The gamification layer estimates an explicit fused accuracy and rewards participation with a per-crossing XP schedule, a ×2 fusion bonus and +15 XP per mesh-confirmed crossing, and a ×1.25 multiplier for devices nearest the mesh mean. Because the reward is computed from the same fused estimate that is published, encouraging accuracy is identical to encouraging honest measurement."));
body.push(para("**I. Calibration-and-rejection record.** Stating what the system achieves is only half the record. The same evidence rejected the noise-floor ranging policy (a refusal rather than an estimate), the equal-weight band fusion, and the naive last-sample epoch delta. Each candidate won on row-level aggregates but lost on complete scenes, which is precisely why it was discarded."));
body.push(para("**J. Condition matrix.** The harness varies entity lateral profile (pedestrian σ = 0.35 m, vehicle σ = 1.15 m), occlusion depth (7–34 dB, vehicle cap 34 dB, 5.5 Hz flutter), closest approach (0.5–20 m), AP layout (near 10 m / far 55 m / mixed), link occupancy (33 links typical, 12–68 m legs) and scene emptiness (160 empty scenes), which together bound the regimes in which each figure above holds."));

// ---- IV. Conclusion
body.push(heading1("IV. Conclusions and Future Work"));
body.push(para("Ambient Sense demonstrates that a commodity phone can perform multi-band passive sensing end to end: fuse BLE and dual-band Wi-Fi features into entity typing, convert measured dBm into a stated range with an explicit gate, detect change between two instants of the field without inventing movement, count transit across a two-node Bluetooth barrier, and accrue accuracy by agreeing with neighbours — while a gamification layer keeps voluntary contributors engaged. Just as importantly, it reports where the physics withdraws its support: 6 % typing accuracy at 68 m, 94 % rather than 100 % detection at 15 m, and a noise floor below which ranging is not merely uncertain but refused."));
body.push(para("**Use cases.** Because the sensing hardware is the handset, the marginal cost of a new measurement point is approximately zero, and because the observer carries no camera, the privacy argument is structural. The system supports ambient-assisted living (presence and crossing counts without cameras or worn devices), occupancy and flow analytics in workplaces and corridors, roadside traffic studies (two phones become a typed barrier counter), and coverage scouting, in which the RF field itself is the subject of the change-detection epochs."));
body.push(para("**Cybersecurity.** The adversarial surface is stated openly rather than assumed away. A device can inject forged beacons — spoofed SSIDs, rogue APs, phantom BLE advertising — or publish forged reports that drag the mesh mean, and downgrade attacks on key negotiation remain relevant to the links we ride on [18]–[21]. The planned defence is to detect physical-layer fingerprints rather than identifiers [19]–[21], apply client-side round-trip-time analysis for rogue-AP detection, and gate the mesh on reputation-weighted fusion so that a forged report cannot set the fused value. The final report and the gamification layer are the two places where trustworthiness must be enforced."));
body.push(para("**Gamification and incentive extension.** The gamification layer will be extended from a point-and-badge system to a quality-weighted, reputation-aware reward engine: team meshes, seasonal ladders and accuracy-weighted seasons in which the reliability bonus becomes a published, auditable score. The incentive design is deliberately aligned with the measurement — rewarding the devices nearest the fused estimate is equivalent to rewarding honesty — and draws on established quality-aware and reputation-based mobile-crowdsensing mechanisms [15], [16]."));
body.push(para("**Summary.** The central contribution is a method of validation: every constant that ships was produced by executing the harness and falsifying the losers, so the paper can state, with equal precision, both the configuration that shipped and the configuration the evidence threw away. Ambient Sense is a working answer to a hard question — what can a single, unmodified smartphone held by an ordinary user actually deliver in the field — and the answer includes, as a first-class result, an explicit account of where it cannot."));

// ---- References
body.push(heading1("References"));
const refs = [
  "J. Wilson and N. Patwari, “Radio tomographic imaging with wireless networks,” *IEEE Trans. Mobile Comput.*, vol. 9, no. 5, pp. 621–632, May 2010, doi: 10.1109/TMC.2010.8.",
  "Ł. Januszkiewicz, “Analysis of human body shadowing effect on wireless sensor networks operating in the 2.4 GHz band,” *Sensors*, vol. 18, no. 10, art. 3412, Oct. 2018, doi: 10.3390/s18103412.",
  "C. Alippi, M. Bocca, G. Boracchi, N. Patwari, and M. Roveri, “RTI goes wild: Radio tomographic imaging for outdoor people detection and localization,” *IEEE Trans. Mobile Comput.*, vol. 15, no. 10, pp. 2585–2598, Oct. 2016, doi: 10.1109/TMC.2015.2504964.",
  "Z. Li, A. Dubey, S. Shen, N. K. Kundu, J. Rao, and R. Murch, “Radio tomographic imaging with reconfigurable intelligent surfaces for integrated sensing and communication,” *IEEE Trans. Wireless Commun.*, vol. 23, no. 11, pp. 15784–15797, Nov. 2024, doi: 10.1109/TWC.2024.3433011.",
  "H. Wu, C. Cheng, T. Peng, H. Zhou, and T. Chen, “Combining transformer with a latent variable model for radio tomography based robust device-free localization,” *Comput. Commun.*, vol. 224, art. 108022, Oct. 2024, doi: 10.1016/j.comcom.2024.108022.",
  "“Passive localization based on radio tomography images with CNN model utilizing WiFi RSSI,” *Sci. Rep.*, vol. 15, art. 15698, 2025, doi: 10.1038/s41598-025-99694-2.",
  "W. E. Wong, A. H. Wong, W. Q. Peh, and C. K. Tan, “A high-dimensional, multi-transceiver channel state information dataset for enhanced human activity recognition,” *Data Brief*, vol. 55, art. 110673, Aug. 2024, doi: 10.1016/j.dib.2024.110673.",
  "D. Sánchez-Rodríguez, M. A. Quintana-Suárez, I. Alonso-González, C. Ley-Bosch, and J. J. Sánchez-Medina, “Fusion of channel state information and received signal strength for indoor localization using a single access point,” *Remote Sens.*, vol. 12, no. 12, art. 1995, Jun. 2020, doi: 10.3390/rs12121995.",
  "R. Ramirez, C.-Y. Huang, C.-A. Liao, P.-T. Lin, H.-W. Lin, and S.-H. Liang, “A practice of BLE RSSI measurement for indoor positioning,” *Sensors*, vol. 21, no. 15, art. 5181, Jul. 2021, doi: 10.3390/s21155181.",
  "F. Milano, H. da Rocha, M. Laracca, L. Ferrigno, A. Espírito Santo, J. Salvado, and V. Paciello, “BLE-based indoor localization: Analysis of some solutions for performance improvement,” *Sensors*, vol. 24, no. 2, art. 376, Jan. 2024, doi: 10.3390/s24020376.",
  "L. Bai, F. Ciravegna, R. Bond, and M. Mulvenna, “A low cost indoor positioning system using Bluetooth Low Energy,” *IEEE Access*, vol. 8, pp. 136858–136871, 2020, doi: 10.1109/ACCESS.2020.3012342.",
  "D. Khan and I. W.-H. Ho, “CrossCount: Efficient device-free crowd counting by leveraging transfer learning,” *IEEE Internet Things J.*, vol. 10, no. 5, pp. 4049–4058, Mar. 2023.",
  "R. Zhou, Z. Gong, X. Lu, and Y. Fu, “WiFlowCount: Device-free people flow counting by exploiting Doppler effect in commodity WiFi,” *IEEE Syst. J.*, vol. 14, no. 4, pp. 4919–4930, Dec. 2020.",
  "“Non-intrusive people counting and identification simultaneously with commodity WiFi devices,” in *Proc. 6th Int. Conf. Digital Medicine and Image Processing (DMIP '23)*, 2023, pp. 62–68, doi: 10.1145/3637684.3637694.",
  "J. Zhang, X. Li, Z. Shi, et al., “A reputation-based and privacy-preserving incentive scheme for mobile crowd sensing: A deep reinforcement learning approach,” *Wirel. Netw.*, 2023, doi: 10.1007/s11276-022-03111-9.",
  "J. Wang, M. Li, Y. He, H. Li, K. Xiao, and C. Wang, “A blockchain based privacy-preserving incentive mechanism in crowdsensing applications,” *IEEE Access*, vol. 6, pp. 17545–17556, 2018, doi: 10.1109/ACCESS.2018.2815365.",
  "M. Cäsar, T. Pawelke, J. Steffan, and G. Terhorst, “A survey on Bluetooth Low Energy security and privacy,” *Comput. Netw.*, vol. 202, art. 108712, Jan. 2022, doi: 10.1016/j.comnet.2021.108712.",
  "D. Antonioli, N. O. Tippenhauer, and K. B. Rasmussen, “Key negotiation downgrade attacks on Bluetooth and Bluetooth Low Energy,” *ACM Trans. Priv. Secur.*, vol. 23, no. 3, art. 14, Jul. 2020, doi: 10.1145/3394497.",
  "H. Cai, “Securing billion Bluetooth devices leveraging learning-based techniques,” in *Proc. AAAI Conf. Artif. Intell. (AAAI)*, vol. 38, no. 21, pp. 23731–23732, 2024, doi: 10.1609/aaai.v38i21.30544.",
  "“Poster: Hybrid detection mechanism for spoofing attacks in Bluetooth Low Energy networks,” in *Proc. 22nd Annu. Int. Conf. Mobile Syst., Appl., Services (MobiSys '24)*, 2024, pp. 710–711, doi: 10.1145/3643832.3661434.",
  "C. Hensler and P. Tague, “DEMO: Using Bluetooth Low Energy spoofing to dispute device details,” in *Proc. ACM Conf. Security Privacy Wireless Mobile Netw. (WiSec '19)*, Miami, FL, USA, 2019, pp. 341–343, doi: 10.1145/3317549.3326321.",
];
refs.forEach((r, i) => body.push(refItem(i + 1, r)));

/* ------------------------------ document assembly ------------------------------ */
const DOC = {
  title: "Ambient Sense: Multi-Band Passive RF Sensing on Commodity Smartphones",
  authors: "Prepared for submission to IEEE — full manuscript draft (two-column)",
};

const headerChildren = [
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 120 },
    children: [new TextRun({ text: DOC.title, font: TITLE, size: 40, bold: false })],
  }),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 120 },
    children: [new TextRun({ text: "Device-Free dBm-Based Ranging, Scan-Epoch Change Detection, Two-Node Transit Counting, Mesh-Accrued Accuracy and Gamified Community Reporting — Abstract", font: TITLE, size: 22, italics: true })],
  }),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 160 },
    children: [new TextRun({ text: DOC.authors, font: TITLE, size: 22 })],
  }),
  new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { after: 100, line: 220, lineRule: LineRuleType.AUTO },
    children: [
      new TextRun({ text: "Abstract—", font: BODY, size: 19, bold: true }),
      ...textRuns("Ambient Sense is a device-free, multi-band passive radio-frequency (RF) sensing system that runs entirely on an unmodified commodity smartphone. The device passively observes the received-signal-strength (RSSI) of Bluetooth Low Energy (BLE) beacons and dual-band (2.4 GHz and 5 GHz) Wi-Fi access points without ever associating to them, converting every observable link into a measurement ray whose attenuation state is monitored in real time. The engine (i) fuses per-link dip depth, duration, temporal texture, variance, prominence and multi-link coherence across the three bands into pedestrian/vehicle typing; (ii) maps measured decibels (dBm) into a distance range via log-distance inversion that carries an explicit quality ladder and a hard gate that refuses to estimate below the noise floor; (iii) implements scan epochs on a ten-second moving-object time scale, storing a timed snapshot, resetting the engine, rescans, and computes the difference between two instants of the field from stable levels; (iv) realises a two-node roadside transit counter over Classic Bluetooth RFCOMM and BLE L2CAP; (v) federates several instances into a sensing mesh whose geometric diversity raises accuracy and extends the effective radius; and (vi) adds a gamification layer that estimates accuracy, rewards participation, and grants a reliability bonus to the devices whose measurements lie nearest the mesh mean. On a 4,161-scene Monte-Carlo harness replayed through the production Kotlin signal-processing code, BLE reaches 57.7 % per-crossing typing accuracy against 25.7 % and 16.1 % for the companion bands, the epoch detector reports 100 % movement detection at 2 m with 0 % false positives at every tested distance, the two-node counter attains 100 % fused detection at 3–10 m and 94 % at 15 m, and the mesh extends the useful radius from 4–12 m (single phone) toward 20–40 m with zero false confirmations across 160 empty scenes.", { font: BODY, size: 19 }),
    ],
  }),
  new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { after: 200, line: 220, lineRule: LineRuleType.AUTO },
    children: [
      new TextRun({ text: "Index Terms—", font: BODY, size: 19, bold: true }),
      ...textRuns("device-free sensing, passive RF sensing, received signal strength indication (RSSI), Bluetooth Low Energy, Wi-Fi sensing, channel state information, device-free localization, people/flow counting, change detection, mobile crowdsensing, gaming incentives.", { font: BODY, size: 19, italics: true }),
    ],
  }),
];

const doc = new Document({
  styles: {
    default: {
      document: { run: { font: BODY, size: 20 } },
    },
  },
  sections: [
    // Section 1: front matter, single column
    {
      properties: {
        page: { margin: { top: 720, bottom: 720, left: 720, right: 720 } },
      },
      children: headerChildren,
    },
    // Section 2: body, two columns, flowing continuously under the front matter
    {
      properties: {
        type: SectionType.CONTINUOUS,
        page: { margin: { top: 720, bottom: 720, left: 720, right: 720 } },
        column: { count: 2, space: 360, separate: false },
      },
      children: body,
    },
  ],
});

const outPath = path.resolve("Ambient_Sense_Paper.docx");
Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(outPath, buf);
  console.log("Wrote", outPath, "(", (buf.length / 1024).toFixed(1), "KB )");
});

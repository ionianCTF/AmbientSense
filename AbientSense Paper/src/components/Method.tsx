import { bands } from "../data";
import { Section, SectionHead, DarkCard } from "./ui";

/* --------------------------- architecture diagram --------------------------- */

function Box({
  x,
  y,
  w,
  h,
  title,
  sub,
  stroke,
  fill = "#232220",
  titleFill = "#faf9f5",
}: {
  x: number;
  y: number;
  w: number;
  h: number;
  title: string;
  sub?: string;
  stroke: string;
  fill?: string;
  titleFill?: string;
}) {
  return (
    <g>
      <rect x={x} y={y} width={w} height={h} rx={13} fill={fill} stroke={stroke} strokeWidth="1.3" />
      <text
        x={x + 14}
        y={y + (sub ? h / 2 - 4 : h / 2 + 5)}
        fontSize="13.5"
        fontWeight="600"
        fill={titleFill}
        fontFamily="Instrument Sans, sans-serif"
      >
        {title}
      </text>
      {sub ? (
        <text x={x + 14} y={y + h / 2 + 15} fontSize="11.5" fill="#a3a29a" fontFamily="Instrument Sans, sans-serif">
          {sub}
        </text>
      ) : null}
    </g>
  );
}

function Arrow({ x1, y1, x2, y2 }: { x1: number; y1: number; x2: number; y2: number }) {
  return (
    <g stroke="#57564f" strokeWidth="1.3" fill="none">
      <line x1={x1} y1={y1} x2={x2} y2={y2} />
      <path d={`M${x2 - 4} ${y2 - 5} L${x2} ${y2} L${x2 - 4} ${y2 + 5}`} strokeLinejoin="round" />
    </g>
  );
}

function ArchitectureFigure() {
  const W = 980;
  const groups = [
    { x: 8, w: 224, title: "BLE scan · beacons", sub: "2.4 GHz", color: "#c15f3c" },
    { x: 244, w: 224, title: "Wi-Fi scan · APs", sub: "2.4 GHz", color: "#5f7a45" },
    { x: 480, w: 224, title: "Wi-Fi scan · APs", sub: "5 GHz", color: "#3f6b8a" },
    { x: 716, w: 256, title: "Classic BT + BLE L2CAP", sub: "two-node counter link", color: "#629a90" },
  ];
  return (
    <div className="overflow-hidden rounded-[var(--radius-card)] border border-ivory/12 bg-ivory/[0.03] p-4 sm:p-6">
      <svg viewBox={`0 0 ${W} 430`} className="w-full">
        {groups.map((g) => (
          <g key={g.title}>
            <Box x={g.x} y={12} w={g.w} h={58} title={g.title} sub={g.sub} stroke={g.color} />
            <Arrow x1={g.x + g.w / 2} y1={72} x2={g.x + g.w / 2} y2={104} />
          </g>
        ))}

        <rect x={8} y={106} width={964} height={74} rx={14} fill="#2c2b28" stroke="#4a4942" strokeWidth="1.3" />
        <text x={26} y={136} fontSize="14" fontWeight="600" fill="#faf9f5" fontFamily="Instrument Sans, sans-serif">
          Per-link DSP — production Kotlin classes
        </text>
        <text x={26} y={158} fontSize="12" fill="#a3a29a" fontFamily="Instrument Sans, sans-serif">
          RingWindow · Stats (median, robust dispersion) · BaselineTracker · DipDetector / PeakDetector
        </text>
        <text x={742} y={158} fontSize="11.5" fill="#d4a27f" fontFamily="JetBrains Mono, monospace">
          noise-aware floor · sustained gate
        </text>

        <line x1={490} y1={182} x2={490} y2={214} stroke="#57564f" strokeWidth="1.3" />
        <path d="M486 209 L490 214 L494 209" fill="none" stroke="#57564f" strokeWidth="1.3" />

        {[
          { x: 8, title: "Feature extraction", sub: "depth · duration · texture", sub2: "variance · prominence · coherence", color: "#c15f3c" },
          { x: 334, title: "dBm → metres", sub: "log-distance per band", sub2: "gate −75 dBm · f ∈ [0.15, 0.92]", color: "#5f7a45" },
          { x: 660, title: "Scan-epoch delta", sub: "store → reset → rescan", sub2: "stable level = median of last 20", color: "#3f6b8a" },
        ].map((c) => (
          <g key={c.title}>
            <Box x={c.x} y={218} w={312} h={82} title={c.title} sub={c.sub} stroke={c.color} />
            <text x={c.x + 16} y={284} fontSize="11.5" fill="#a3a29a" fontFamily="Instrument Sans, sans-serif">
              {c.sub2}
            </text>
            <line x1={c.x + 156} y1={300} x2={c.x + 156} y2={330} stroke="#57564f" strokeWidth="1.3" />
            <path d={`M${c.x + 152} 325 L${c.x + 156} 330 L${c.x + 160} 325`} fill="none" stroke="#57564f" strokeWidth="1.3" />
          </g>
        ))}

        <rect x={8} y={334} width={964} height={82} rx={14} fill="#faf9f5" />
        <text x={26} y={364} fontSize="14" fontWeight="600" fill="#141413" fontFamily="Instrument Sans, sans-serif">
          EntityClassifier v1.1 → entity · confidence · event record
        </text>
        <text x={26} y={386} fontSize="12" fill="#6b6a61" fontFamily="Instrument Sans, sans-serif">
          Counters &nbsp;·&nbsp; range band &nbsp;·&nbsp; field verdict &nbsp;·&nbsp; transit count &nbsp;·&nbsp; mesh confirmation &nbsp;·&nbsp; XP
        </text>
        <text x={742} y={386} fontSize="11.5" fill="#c15f3c" fontFamily="JetBrains Mono, monospace">
          artefacts: tables + logs
        </text>
      </svg>
      <p className="mt-3 text-[0.82rem] text-ivory/40">
        Figure 1 — functional decomposition. Each stage produces the artefacts (per-link dBm tables, epoch tables, transit counts,
        mesh ledgers) that the evaluation replays and audits.
      </p>
    </div>
  );
}

/* --------------------------------- section --------------------------------- */

const dsp = [
  { name: "RingWindow", role: "Keeps the recent (timestamp, dBm) samples of every link in a bounded ring buffer." },
  { name: "Stats", role: "Maintains the median and a robust dispersion estimate, insensitive to single-sample spikes." },
  { name: "BaselineTracker", role: "Estimates the adaptive no-event level of each link, so a quiet link is judged by a quiet yardstick." },
  { name: "DipDetector / PeakDetector", role: "Segments the stream into events: depth, duration, and the competing peaks that set prominence." },
];

const features = [
  ["Depth", "dB of the dip below the adaptive baseline"],
  ["Duration", "ms of the occlusion"],
  ["Texture", "jitter ratio in-event: smooth block vs. wobbling shape"],
  ["Variance", "of the event series"],
  ["Prominence", "depth relative to competing peaks in the window"],
  ["Coherence", "number of links registering the event in a common interval"],
];

export function Method() {
  return (
    <Section id="method" tone="ink" className="py-24 md:py-32">
      <SectionHead
        dark
        eyebrow="Section 2 · Methodology"
        title={
          <>
            Physics first, then
            <br />
            the refusal to over-claim.
          </>
        }
        lede="Ambient Sense observes three families of links. Received power follows log-distance path loss, an occlusion is a Gaussian-profiled attenuation whose amplitude decays with closest-approach distance through a Fresnel-efficiency term, and every constant downstream of that model was either adopted or rejected on complete scenes."
      />

      <div className="mt-14 grid gap-6 lg:grid-cols-3">
        <DarkCard className="bg-clay/[0.09]">
          <div className="eyebrow text-kraft">Sensing model</div>
          <p className="font-mono mt-4 text-[0.98rem] text-ivory">RSSI(d) = PL₀ − 10·n·log₁₀(d)</p>
          <p className="mt-4 text-[0.93rem] leading-relaxed text-ivory/60">
            Environmental fluctuation is lognormal scintillation whose standard deviation grows with range (0.45–0.70 dB at short
            range, +0.055 dB/m). Occlusion amplitude scales as (1 + d/10)<sup>−0.6</sup>; lateral width σ is 0.35 m for a
            pedestrian and 1.15 m for a vehicle.
          </p>
        </DarkCard>
        <DarkCard>
          <div className="eyebrow text-clay">Entity signatures</div>
          <ul className="mt-4 space-y-3 text-[0.95rem]">
            <li className="border-t border-ivory/12 pt-3">
              <span className="font-semibold text-ivory">Pedestrian</span>
              <p className="mt-1 text-[0.9rem] text-ivory/55">5–12 dB dips, jittery texture, 3.1 Hz body wobble of ≈0.9 dB.</p>
            </li>
            <li className="border-t border-ivory/12 pt-3">
              <span className="font-semibold text-ivory">Vehicle</span>
              <p className="mt-1 text-[0.9rem] text-ivory/55">
                15–26 dB smooth, long blockages; occlusion ≈25 dB capped at 34 dB with 5.5 Hz flutter.
              </p>
            </li>
            <li className="border-t border-ivory/12 pt-3 text-[0.9rem] leading-relaxed text-ivory/55">
              Confusion concentrates in the central 4–13 dB band — which is exactly where the classifier declines to be confident.
            </li>
          </ul>
        </DarkCard>
        <DarkCard>
          <div className="eyebrow text-kelp">Three measured detector decisions</div>
          <ul className="mt-4 space-y-3 text-[0.92rem]">
            <li className="border-t border-ivory/12 pt-3">
              <span className="font-semibold text-ivory">Noise-aware trigger floor</span>
              <p className="mt-1 text-ivory/55">A dip must exceed a floor derived from the link's own fluctuation.</p>
            </li>
            <li className="border-t border-ivory/12 pt-3">
              <span className="font-semibold text-ivory">Sustained-trigger gate</span>
              <p className="mt-1 text-ivory/55">The trigger must persist across a minimum number of samples.</p>
            </li>
            <li className="border-t border-ivory/12 pt-3">
              <span className="font-semibold text-ivory">Stable-level policy</span>
              <p className="mt-1 text-ivory/55">Compare medians of the last 20 samples, never single samples.</p>
            </li>
          </ul>
        </DarkCard>
      </div>

      {/* band table */}
      <div className="mt-20">
        <h3 className="head text-[1.6rem] text-ivory sm:text-[1.9rem]">Per-band reference constants and measured yield</h3>
        <p className="font-serif-body mt-3 max-w-2xl text-[1.05rem] leading-relaxed text-ivory/60">
          Fusion is asymmetric by design. BLE carries event typing; the Wi-Fi bands contribute reach. Averaging the three bands was
          tested and rejected — it dragged BLE's 57.7 % down to 31 %.
        </p>
        <div className="mt-7 overflow-x-auto rounded-[var(--radius-card)] border border-ivory/12 bg-ivory/[0.03] p-4 sm:p-6">
          <table className="w-full min-w-[760px] border-collapse text-left">
            <thead>
              <tr className="border-b border-ivory/20 text-[0.72rem] uppercase tracking-[0.12em] text-ivory/40">
                <th className="py-3 pr-4 font-semibold">Band</th>
                <th className="py-3 pr-4 font-semibold">Exponent n</th>
                <th className="py-3 pr-4 font-semibold">R at 1 m</th>
                <th className="py-3 pr-4 font-semibold">A₀ at reference</th>
                <th className="py-3 pr-4 font-semibold">Per-crossing</th>
                <th className="py-3 pr-4 font-semibold">Max leg</th>
                <th className="py-3 font-semibold">Role in the fusion</th>
              </tr>
            </thead>
            <tbody className="text-[0.95rem]">
              {bands.map((b) => (
                <tr key={b.id} className="border-b border-ivory/10 align-top transition-colors hover:bg-ivory/[0.03]">
                  <td className="py-4 pr-4">
                    <span className="flex items-center gap-2 font-semibold text-ivory">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ background: b.color }} />
                      {b.label}
                    </span>
                    <span className="mt-1 block text-[0.8rem] text-ivory/40">{b.hz}</span>
                  </td>
                  <td className="num py-4 pr-4 text-ivory/60">{b.n}</td>
                  <td className="num py-4 pr-4 text-ivory/60">{b.r1m} dBm</td>
                  <td className="num py-4 pr-4 text-ivory/60">{b.a0} dB</td>
                  <td className="num py-4 pr-4 font-semibold" style={{ color: b.color }}>
                    {b.perCrossing} %
                  </td>
                  <td className="num py-4 pr-4 text-ivory/60">{b.maxLeg} m</td>
                  <td className="py-4 text-[0.9rem] leading-snug text-ivory/55">{b.role}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* DSP + features */}
      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <DarkCard>
          <div className="eyebrow text-clay">Signal-processing chain</div>
          <ul className="mt-4 space-y-4">
            {dsp.map((d) => (
              <li key={d.name} className="border-t border-ivory/12 pt-3">
                <span className="font-mono text-[0.86rem] font-medium text-ivory">{d.name}</span>
                <p className="mt-1 text-[0.9rem] leading-relaxed text-ivory/55">{d.role}</p>
              </li>
            ))}
          </ul>
        </DarkCard>
        <DarkCard>
          <div className="eyebrow text-denim">Feature space per event</div>
          <ul className="mt-4 grid gap-x-6 gap-y-3 sm:grid-cols-2">
            {features.map(([k, v]) => (
              <li key={k} className="border-t border-ivory/12 pt-2.5">
                <span className="text-[0.92rem] font-semibold text-ivory">{k}</span>
                <p className="mt-0.5 text-[0.85rem] leading-snug text-ivory/55">{v}</p>
              </li>
            ))}
          </ul>
          <p className="mt-5 text-[0.9rem] leading-relaxed text-ivory/55">
            Coherence is the primitive that everything else is built on: the same number of links agreeing inside a common interval
            becomes the mesh confirmation rule, the transit barrier evidence, and the trigger for awarding a fusion bonus.
          </p>
        </DarkCard>
      </div>

      <div className="mt-14">
        <ArchitectureFigure />
      </div>
    </Section>
  );
}

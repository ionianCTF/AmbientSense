import {
  EnvelopeChart,
  FeatureRow,
  HistoryRow,
  KeyRow,
  PhoneCard,
  ScreenTitle,
  type TabId,
} from "./parts";
import { cn } from "../../utils/cn";

const CLAY = "#c15f3c";
const OLIVE = "#5f7a45";
const DENIM = "#3f6b8a";
const INKS = "#5f5e57";

/* ------------------------------- 1 · Sense ------------------------------- */

function Counter({ label, value, color, tint }: { label: string; value: string; color: string; tint: string }) {
  return (
    <div
      className="rounded-[20px] border bg-white/70 p-3.5"
      style={{ borderColor: `${color}55`, background: `linear-gradient(160deg, ${tint}, #ffffff 85%)` }}
    >
      <div className="eyebrow text-[9.5px]" style={{ color, letterSpacing: "0.1em" }}>
        {label}
      </div>
      <div className="head num mt-4 text-[30px]" style={{ color }}>
        {value}
      </div>
    </div>
  );
}

export function SenseScreen() {
  return (
    <>
      <ScreenTitle title="Ambient Sense" sub="Passive RF sensing — people & vehicles leave electromagnetic wakes" />

      <PhoneCard className="mb-3">
        <div className="flex items-start gap-2.5">
          <span className="pulse-dot mt-[7px] h-[11px] w-[11px] shrink-0 rounded-full" style={{ background: CLAY }} />
          <div className="min-w-0 flex-1">
            <div className="head text-[17.5px] leading-tight">Sensing the RF field</div>
            <div className="mt-0.5 text-[12px] text-muted">33 passive links · BLE + Wi-Fi · 20 m radius</div>
          </div>
          <span className="text-[14px] font-medium text-muted">Stop</span>
        </div>
      </PhoneCard>

      <div className="mb-3 grid grid-cols-2 gap-3">
        <Counter label="People" value="67" color={CLAY} tint="#f7e2d8" />
        <Counter label="Vehicles" value="66" color={OLIVE} tint="#e6ecdb" />
        <Counter label="Disturbances" value="165" color={INKS} tint="#eae9e2" />
        <Counter label="RF links" value="33" color={DENIM} tint="#dfe7ee" />
      </div>

      <PhoneCard title="Live signal features" className="mb-3">
        <FeatureRow label="Moving variance" value="11.45 dB²" pct={34} color={CLAY} />
        <FeatureRow label="Jitter" value="1.79 dB" pct={46} color={DENIM} />
        <FeatureRow label="Classifier confidence" value="68%" pct={68} color={OLIVE} />
        <div className="mt-4 flex items-center gap-2">
          <span
            className="rounded-full px-3 py-1.5 text-[12px] font-medium"
            style={{ background: "#f7e2d8", color: CLAY }}
          >
            Radius 20 m
          </span>
          <span className="rounded-full bg-pampas px-3 py-1.5 text-[12px] text-ink/75">Range 0–20 m</span>
          <svg viewBox="0 0 24 24" className="h-[15px] w-[15px] text-denim" fill="none" stroke="currentColor" strokeWidth="1.7">
            <circle cx="12" cy="12" r="8.6" />
            <path d="M12 11v5M12 8.2v.6" strokeLinecap="round" />
          </svg>
        </div>
      </PhoneCard>

      <PhoneCard title="Attenuation envelope" sub="Max dip below adaptive baseline · markers = recent crossings" className="mb-3">
        <EnvelopeChart />
      </PhoneCard>

      <PhoneCard title="Last crossing" className="mb-3">
        <KeyRow k="Entity" v="pedestrian · 68 %" accent={CLAY} />
        <KeyRow k="Dominant band" v="BLE · −58 dBm dip" />
        <KeyRow k="Range band" v="f 0.41 → 4.4–8.2 m" accent={DENIM} />
        <KeyRow k="Coherent links" v="3 of 33" />
        <KeyRow k="Depth · duration" v="8.4 dB · 1.9 s" />
      </PhoneCard>

      <PhoneCard title="Scan epoch" sub="≈10 s moving-object time scale">
        <KeyRow k="Next epoch in" v="6 s" accent={CLAY} />
        <KeyRow k="Stable level" v="median · last 20 samples" />
        <KeyRow k="Field since previous" v="CHANGED · 5 links ≥ 3 dB" />
      </PhoneCard>
    </>
  );
}

/* ------------------------------- 2 · Scans ------------------------------- */

const history: { time: string; dur: string; detail: string; changed?: boolean }[] = [
  { time: "19:56:03", dur: "15s", detail: "p 0 · v 0 · o 1 · 33 links · field CHANGED (5 links moved)", changed: true },
  { time: "19:55:48", dur: "15s", detail: "p 0 · v 0 · o 1 · 33 links · field CHANGED (7 links moved)", changed: true },
  { time: "19:55:40", dur: "5s", detail: "p 0 · v 0 · o 0 · 33 links · unchanged" },
  { time: "19:55:35", dur: "5s", detail: "p 0 · v 0 · o 0 · 33 links · unchanged" },
  { time: "19:55:30", dur: "5s", detail: "p 0 · v 0 · o 0 · 33 links · unchanged" },
  { time: "19:55:25", dur: "5s", detail: "p 0 · v 0 · o 0 · 33 links · unchanged" },
  { time: "19:55:19", dur: "5s", detail: "p 0 · v 0 · o 0 · 33 links · unchanged" },
];

export function ScansScreen() {
  return (
    <>
      <PhoneCard className="mb-3">
        <div className="head text-[17.5px]">Latest stored scan</div>
        <div className="mt-0.5 text-[12px] text-muted">stored, then the engine reset and rescanned</div>
        <div className="mt-1">
          <KeyRow k="Window" v="19:56:03 – 19:56:18 (15s)" />
          <KeyRow k="Place" v="My place" />
          <KeyRow k="Geo" v="40.3075, 21.7906" />
          <KeyRow k="Counts (this window)" v="p 0 · v 0 · o 1" />
          <KeyRow k="Links / changed" v="33 · 5 moved ≥3 dB" />
        </div>
        <p className="mt-3 text-[13px] leading-[1.35]" style={{ color: CLAY }}>
          5 links moved ≥3 dB (max 14.0 dB) → field CHANGED since previous scan
        </p>
      </PhoneCard>

      <PhoneCard>
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="head text-[17.5px]">History</div>
            <div className="mt-0.5 max-w-[24ch] text-[12px] leading-[1.35] text-muted">
              120 scans stored (cap 120) — tap a row for the per-link dBm table
            </div>
          </div>
          <span className="text-[13.5px] text-mist">Clear</span>
        </div>
        <div className="mt-2">
          {history.map((h, i) => (
            <HistoryRow
              key={h.time}
              time={h.time}
              place="My place"
              dur={h.dur}
              detail={h.detail}
              changed={h.changed}
              last={i === history.length - 1}
            />
          ))}
        </div>
      </PhoneCard>
    </>
  );
}

/* -------------------------------- 3 · Mesh -------------------------------- */

export function MeshScreen() {
  const nodes = [
    { name: "This phone · Pixel 8", acc: 72, you: true },
    { name: "node-2 · Galaxy A54", acc: 69, you: false },
    { name: "node-3 · Xiaomi 12", acc: 64, you: false },
  ];
  return (
    <>
      <ScreenTitle title="Mesh" sub="Agreement between nearby Ambient Sense instances" />

      <PhoneCard className="mb-3">
        <div className="head text-[17.5px]">Nodes in your mesh</div>
        <div className="mt-0.5 text-[12px] text-muted">2–5 instances confirm each other's crossings</div>
        <div className="mt-3 space-y-2">
          {nodes.map((n) => (
            <div key={n.name} className="flex items-center gap-2.5">
              <span className={cn("h-2 w-2 rounded-full", n.you ? "bg-clay" : "bg-kelp")} />
              <span className="flex-1 truncate text-[12.5px] text-ink/80">{n.name}</span>
              <span className="num text-[12.5px] font-semibold text-muted">{n.acc}%</span>
            </div>
          ))}
        </div>
        <div className="mt-3 border-t border-black/[0.06] pt-3">
          <KeyRow k="Fused accuracy" v="81 %" accent={OLIVE} />
          <KeyRow k="Agreement tolerance" v="±1 crossing / ±4 m" />
          <KeyRow k="Effective radius" v="20–40 m (single: 4–12 m)" accent={DENIM} />
        </div>
      </PhoneCard>

      <PhoneCard title="Radius extension" sub="Geometric diversity of measurement rays" className="mb-3">
        <div className="relative mt-4 h-[92px]">
          <div className="absolute left-1/2 top-1/2 h-[86px] w-[86px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-dashed border-clay/45" />
          <div className="absolute left-1/2 top-1/2 h-[46px] w-[46px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-dashed border-mist/60" />
          <span className="absolute left-1/2 top-1/2 h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-ink" />
          <span className="absolute left-[68%] top-[26%] text-[10px] text-mist">mesh 20–40 m</span>
          <span className="absolute left-[68%] top-[64%] text-[10px] text-mist">single 4–12 m</span>
        </div>
        <p className="mt-2 text-[11.5px] leading-[1.4] text-muted">
          Three phones see one crossing from three angles; rays that disagree are down-weighted before the count is published.
        </p>
      </PhoneCard>

      <PhoneCard title="Confirmation ledger" sub="160 empty scenes audited" className="mb-3">
        {[
          ["Crossings confirmed (1 h)", "14", OLIVE],
          ["False confirmations", "0", OLIVE],
          ["Rogue beacons flagged", "2", CLAY],
          ["−90 dBm links held for agreement only", "6", DENIM],
        ].map(([k, v, c]) => (
          <KeyRow key={k} k={k} v={v} accent={c} />
        ))}
      </PhoneCard>

      <PhoneCard title="Reliability bonus" sub="Distance from the mesh mean">
        <p className="mt-2 text-[12.5px] leading-[1.45] text-muted">
          Devices whose measurements lie closest to the fused estimate earn a ×1.25 reward multiplier; outliers keep earning but
          never set the mesh value.
        </p>
        <div className="mt-3 flex items-center gap-2">
          <span className="rounded-full bg-pampas px-3 py-1.5 text-[12px]">you ×1.25</span>
          <span className="rounded-full bg-pampas px-3 py-1.5 text-[12px]">node-2 ×1.0</span>
          <span className="rounded-full bg-pampas px-3 py-1.5 text-[12px]">node-3 ×0.9</span>
        </div>
      </PhoneCard>
    </>
  );
}

/* ------------------------------- 4 · Transit ------------------------------- */

function BarrierDiagram() {
  return (
    <svg viewBox="0 0 300 108" className="mt-3 w-full">
      <line x1="46" y1="16" x2="46" y2="92" stroke="#c15f3c" strokeWidth="2" />
      <line x1="254" y1="16" x2="254" y2="92" stroke="#c15f3c" strokeWidth="2" />
      <circle cx="46" cy="16" r="4.5" fill="#c15f3c" />
      <circle cx="254" cy="16" r="4.5" fill="#c15f3c" />
      <text x="30" y="34" fontSize="9" fill="#6b6a61">
        node A
      </text>
      <text x="238" y="34" fontSize="9" fill="#6b6a61">
        node B
      </text>
      <text x="132" y="104" fontSize="9" fill="#6b6a61">
        barrier · 8 m separation
      </text>
      <ellipse cx="150" cy="70" rx="46" ry="10" fill="#5f7a45" opacity="0.18" />
      <rect x="120" y="52" width="60" height="18" rx="6" fill="#5f7a45" />
      <circle cx="134" cy="72" r="5" fill="#5f7a45" />
      <circle cx="166" cy="72" r="5" fill="#5f7a45" />
      <path d="M60 44 A 100 100 0 0 1 240 44" fill="none" stroke="#3f6b8a" strokeWidth="1.4" strokeDasharray="4 3" />
      <path d="m236 39 6 5-8 3" fill="none" stroke="#3f6b8a" strokeWidth="1.4" />
    </svg>
  );
}

export function TransitScreen() {
  return (
    <>
      <ScreenTitle title="Transit" sub="Two-node roadside counter · barrier coverage on commodity Bluetooth" />

      <PhoneCard className="mb-3">
        <div className="head text-[17.5px]">Barrier link</div>
        <div className="mt-0.5 text-[12px] text-muted">Classic BT RFCOMM + BLE L2CAP</div>
        <BarrierDiagram />
      </PhoneCard>

      <div className="mb-3 grid grid-cols-2 gap-3">
        <Counter label="Pedestrians" value="12" color={CLAY} tint="#f7e2d8" />
        <Counter label="Vehicles" value="8" color={OLIVE} tint="#e6ecdb" />
      </div>

      <PhoneCard title="Link state" className="mb-3">
        <KeyRow k="RFCOMM (Classic)" v="connected · 8.1 pps" accent={OLIVE} />
        <KeyRow k="BLE L2CAP" v="connected · 4.0 pps" accent={OLIVE} />
        <KeyRow k="Separation" v="8 m" />
        <KeyRow k="Hybrid classifier" v="presence ∧ loss" />
        <KeyRow k="Entity typing" v="100 % at 3–10 m" accent={CLAY} />
      </PhoneCard>

      <PhoneCard title="Detection by separation" sub="Fused two-node detection" className="mb-3">
        {[
          ["3 m", "100 %", OLIVE],
          ["6 m", "100 %", OLIVE],
          ["10 m", "100 %", OLIVE],
          ["15 m", "94 %", DENIM],
          ["25 m", "61 %", "#b0402a"],
        ].map(([k, v, c]) => (
          <div key={k} className="mt-2 flex items-center gap-3">
            <span className="num w-12 text-[12.5px] text-muted">{k}</span>
            <div className="h-[6px] flex-1 overflow-hidden rounded-full bg-pampas-deep">
              <div className="h-full rounded-full" style={{ width: v, background: c }} />
            </div>
            <span className="num w-11 text-right text-[12.5px] font-semibold" style={{ color: c }}>
              {v}
            </span>
          </div>
        ))}
      </PhoneCard>

      <PhoneCard title="Session" sub="Roadside · 41 min">
        <KeyRow k="Flow estimate" v="17 entities / 41 min" accent={DENIM} />
        <KeyRow k="Peak" v="18:52 · 4 in 5 min" />
        <KeyRow k="Unattributed dips" v="2 · held below threshold" />
      </PhoneCard>
    </>
  );
}

/* -------------------------------- 5 · Club -------------------------------- */

const sessions = [
  ["Sep 6, 19:21", 43, 43],
  ["Sep 6, 19:20", 43, 43],
  ["Sep 6, 19:02", 24, 35],
  ["Sep 6, 18:45", 18, 30],
  ["Sep 6, 18:45", 18, 30],
  ["Sep 6, 18:27", 4, 3],
  ["Sep 6, 17:19", 50, 64],
  ["Sep 6, 17:19", 50, 63],
  ["Sep 6, 17:18", 43, 63],
  ["Sep 6, 17:18", 41, 62],
];

export function ClubScreen() {
  return (
    <>
      <PhoneCard className="mb-3">
        <div className="head text-[17.5px]">Mesh contribution</div>
        <div className="mt-0.5 text-[12px] text-muted">What the fusion network reports through you</div>
        <div className="mt-1">
          <KeyRow k="Nodes in your mesh" v="1" />
          <KeyRow k="Fused accuracy" v="72%" accent={OLIVE} />
          <KeyRow k="Crowd level" v="Calm" />
          <KeyRow k="Confirmed crossings (1 h)" v="0" accent={CLAY} />
        </div>
      </PhoneCard>

      <PhoneCard title="Scoring" className="mb-3">
        <div className="rounded-[14px] bg-pampas p-3.5">
          <p className="text-[12.5px] leading-[1.5] text-ink/85">
            People +10 XP · Vehicles +14 XP · Disturbances +2 XP. When a crossing is confirmed by ≥2 mesh nodes you earn a ×2
            fusion bonus +15 XP per confirmation — so running the app on several phones in the same area not only raises counting
            accuracy (multi-angle cross-filtering), it literally multiplies your score. Day streaks and badges reward keeping
            sensors on.
          </p>
        </div>
      </PhoneCard>

      <PhoneCard title="Session history" className="mb-3">
        <div className="mt-1 space-y-2.5">
          {sessions.map(([t, p, v], i) => (
            <div key={`${t}-${i}`} className="flex items-center justify-between gap-3">
              <span className="num text-[12.5px] text-ink/80">{t}</span>
              <span className="num text-[13px]">
                <span className="text-[13px]">🦶</span> {p} · <span>🚗</span> {v} · <span className="text-muted">ambient</span>
              </span>
            </div>
          ))}
        </div>
      </PhoneCard>

      <PhoneCard title="Badges" sub="Keeping sensors on is the whole game">
        <div className="mt-2 flex flex-wrap gap-2">
          {[
            ["🥉", "First 10 crossings", true],
            ["🌙", "Night watch · 4 h", true],
            ["🕸️", "Mesh of 3", true],
            ["🎯", "50 % fused accuracy", true],
            ["🛡️", "Rogue beacon reporter", false],
            ["🔥", "30-day streak", false],
          ].map(([e, label, on]) => (
            <span
              key={label as string}
              className={cn(
                "flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[11.5px]",
                on ? "bg-sand text-ink" : "bg-pampas text-mist",
              )}
            >
              <span>{e as string}</span>
              {label as string}
            </span>
          ))}
        </div>
      </PhoneCard>
    </>
  );
}

/* ------------------------------- 6 · Settings ------------------------------- */

function Toggle({ on }: { on: boolean }) {
  return (
    <span
      className={cn("relative inline-flex h-[20px] w-[34px] shrink-0 items-center rounded-full transition-colors", on ? "bg-olive" : "bg-line-strong")}
    >
      <span className={cn("absolute h-[16px] w-[16px] rounded-full bg-white transition-all", on ? "left-[16px]" : "left-[2px]")} />
    </span>
  );
}

export function SettingsScreen() {
  return (
    <>
      <ScreenTitle title="Settings" sub="Every shipped constant came from an executed calibration" />

      <PhoneCard title="Sensing" className="mb-3">
        <div className="mt-2 flex gap-2">
          {["10 m", "20 m", "40 m"].map((r) => (
            <span
              key={r}
              className={cn(
                "rounded-full px-3 py-1.5 text-[12px]",
                r === "20 m" ? "bg-sand font-medium text-clay" : "bg-pampas text-ink/70",
              )}
            >
              Radius {r}
            </span>
          ))}
        </div>
        <div className="mt-3 space-y-2.5">
          {[
            ["BLE beacons · 2.4 GHz", true],
            ["Wi-Fi APs · 2.4 GHz", true],
            ["Wi-Fi APs · 5 GHz", true],
            ["Classic BT RFCOMM counter", true],
          ].map(([l, on]) => (
            <div key={l as string} className="flex items-center justify-between gap-3">
              <span className="text-[12.5px] text-ink/80">{l as string}</span>
              <Toggle on={on as boolean} />
            </div>
          ))}
        </div>
      </PhoneCard>

      <PhoneCard title="Ranging" sub="dBm → metres, with an explicit refusal" className="mb-3">
        <KeyRow k="Range gate" v="−75 dBm" accent={CLAY} />
        <KeyRow k="Noise floor" v="−90 dBm (excluded)" />
        <KeyRow k="Radial band" v="f ∈ [0.15, 0.92]" accent={DENIM} />
        <KeyRow k="BLE n · R₁ₘ" v="2.4 · −42 dBm" />
        <KeyRow k="Wi-Fi 2.4 n · R₁ₘ" v="2.6 · −38 dBm" />
        <KeyRow k="Wi-Fi 5 n · R₁ₘ" v="2.9 · −35 dBm" />
      </PhoneCard>

      <PhoneCard title="Scan epochs" className="mb-3">
        <KeyRow k="Epoch interval" v="10 s" accent={CLAY} />
        <KeyRow k="Comparison rule" v="stable level · median 20" />
        <KeyRow k="Transient dip" v="≥ 3 dB" />
        <KeyRow k="History cap" v="120 scans" />
      </PhoneCard>

      <PhoneCard title="Privacy" className="mb-3">
        <div className="mt-2 space-y-2.5">
          {[
            ["Camera & microphone", false],
            ["Store beacon identifiers", false],
            ["Upload raw dBm tables", false],
            ["Aggregate mesh reports", true],
          ].map(([l, on]) => (
            <div key={l as string} className="flex items-center justify-between gap-3">
              <span className="text-[12.5px] text-ink/80">{l as string}</span>
              <Toggle on={on as boolean} />
            </div>
          ))}
        </div>
      </PhoneCard>

      <PhoneCard title="About" className="mb-3">
        <KeyRow k="Classifier weights" v="v1.1" accent={OLIVE} />
        <KeyRow k="Engine" v="Kotlin · on-device" />
        <KeyRow k="Evaluation" v="13-pp report · PDF" />
      </PhoneCard>
    </>
  );
}

export const screens: Record<TabId, () => React.JSX.Element> = {
  sense: SenseScreen,
  scans: ScansScreen,
  mesh: MeshScreen,
  transit: TransitScreen,
  club: ClubScreen,
  settings: SettingsScreen,
};

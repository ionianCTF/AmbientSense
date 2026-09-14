import { useMemo, useState } from "react";
import { bands } from "../data";
import { Section } from "./ui";
import { cn } from "../utils/cn";

const ladder = [
  { min: -20, max: -55, label: "Strong", note: "direct path likely, tight band", color: "#629a90" },
  { min: -55, max: -65, label: "Good", note: "usable estimate, narrow band", color: "#8fb3a5" },
  { min: -65, max: -75, label: "Usable", note: "band reported, gate boundary", color: "#d4a27f" },
  { min: -75, max: -90, label: "Weak", note: "range refused, band only", color: "#cc785c" },
  { min: -90, max: -120, label: "Noise floor", note: "excluded from range and accuracy maths", color: "#8f8e86" },
];

function levelOf(dbm: number) {
  return ladder.find((l) => dbm >= l.min && dbm < l.max) ?? ladder[ladder.length - 1];
}

export function RangeExplorer() {
  const [bandId, setBandId] = useState<(typeof bands)[number]["id"]>("ble");
  const [dbm, setDbm] = useState(-72);
  const band = bands.find((b) => b.id === bandId)!;
  const level = levelOf(dbm);
  const gated = dbm <= -75;

  const { naive, lo, hi } = useMemo(() => {
    const d = Math.pow(10, (band.r1m - dbm) / (10 * band.n));
    return { naive: d, lo: d * 0.15, hi: d * 0.92 };
  }, [band, dbm]);

  const axisMax = 80;
  const pct = (v: number) => `${Math.max(0, Math.min(100, (v / axisMax) * 100))}%`;

  return (
    <Section id="ranging" tone="ink" className="py-24 md:py-32">
      <div className="grid gap-12 lg:grid-cols-[0.85fr_1.15fr]">
        <div>
          <div className="eyebrow flex items-center gap-3 text-kraft">
            <span className="inline-block h-[7px] w-[7px] rounded-full bg-kraft" />
            Interactive · ranging
          </div>
          <h2 className="head mt-5 text-[2rem] text-ivory sm:text-[2.6rem] md:text-[3rem]">
            A range, or a refusal?
          </h2>
          <p className="font-serif-body mt-5 text-[1.12rem] leading-[1.65] text-ivory/70">
            On a weak link the inverse of the log-distance model is extremely ill-conditioned, and an unconstrained estimate can be
            wrong by tens of metres. Ambient Sense therefore reports a radial band f ∈ [0.15, 0.92] instead of a point, gates
            estimation at −75 dBm, and hard-excludes links at or below the −90 dBm floor from range and accuracy mathematics — they
            are retained only for multi-link agreement.
          </p>
          <p className="mt-5 text-[0.9rem] leading-relaxed text-ivory/50">
            Drag the received strength below and watch the gate take over. On the 288-scene validation the gate demonstrably
            suppressed 14–23 m illusions that the naive inversion would have published as fact.
          </p>
        </div>

        <div className="rounded-[24px] border border-ivory/12 bg-ivory/[0.04] p-6 sm:p-8">
          {/* band selector */}
          <div className="flex flex-wrap gap-2">
            {bands.map((b) => (
              <button
                key={b.id}
                onClick={() => setBandId(b.id)}
                className={cn(
                  "rounded-full px-4 py-2 text-[0.85rem] font-medium transition-colors",
                  b.id === bandId ? "bg-ivory text-ink" : "border border-ivory/20 text-ivory/60 hover:text-ivory",
                )}
              >
                {b.label}
                <span className="ml-2 text-[0.72rem] opacity-60">{b.hz}</span>
              </button>
            ))}
          </div>

          {/* slider */}
          <div className="mt-8">
            <div className="flex items-end justify-between">
              <span className="text-[0.85rem] uppercase tracking-[0.12em] text-ivory/45">Observed link strength</span>
              <span className="num text-[2.2rem] leading-none text-ivory">{dbm} dBm</span>
            </div>
            <input
              type="range"
              min={-20}
              max={-95}
              step={1}
              value={dbm}
              onChange={(e) => setDbm(Number(e.target.value))}
              className="mt-4 w-full accent-[#d4a27f]"
              style={{ accentColor: level.color }}
            />
            <div className="num mt-1.5 flex justify-between text-[0.72rem] text-ivory/35">
              <span>−20 strong</span>
              <span>−75 gate</span>
              <span>−90 floor</span>
            </div>
          </div>

          {/* verdict */}
          <div className="mt-7 rounded-2xl border p-5" style={{ borderColor: `${level.color}55`, background: `${level.color}14` }}>
            <div className="flex flex-wrap items-baseline justify-between gap-3">
              <span className="text-[1.05rem] font-semibold" style={{ color: level.color }}>
                {level.label}
              </span>
              <span className="text-[0.85rem] text-ivory/55">{level.note}</span>
            </div>

            {gated ? (
              <div className="mt-4 border-t border-ivory/10 pt-4">
                <div className="head text-[1.35rem] text-ivory">Range refused</div>
                <p className="mt-2 text-[0.9rem] leading-relaxed text-ivory/65">
                  The unconstrained inversion would have said{" "}
                  <span className="num text-ivory/90">{naive.toFixed(1)} m</span> from a {dbm} dBm {band.label} link with n ={" "}
                  {band.n}. Ambient Sense publishes no distance for this link and keeps it for multi-link agreement only.
                </p>
                <div className="relative mt-5 h-9 rounded-lg border border-ivory/15 bg-ivory/[0.05]">
                  <div className="absolute inset-y-0 left-0 w-full" />
                  <div
                    className="absolute inset-y-0 rounded-l-lg opacity-40"
                    style={{ left: 0, width: pct(naive), background: level.color }}
                  />
                  <span className="num absolute left-3 top-1/2 -translate-y-1/2 text-[0.78rem] text-ivory/80 line-through">
                    naive {naive.toFixed(1)} m
                  </span>
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[0.78rem] text-ivory">refused</span>
                </div>
              </div>
            ) : (
              <div className="mt-4 border-t border-ivory/10 pt-4">
                <div className="head text-[1.35rem] text-ivory">
                  {lo.toFixed(1)} – {hi.toFixed(1)} m
                </div>
                <p className="mt-2 text-[0.9rem] leading-relaxed text-ivory/65">
                  Point estimate {naive.toFixed(1)} m, published as the radial band f ∈ [0.15, 0.92] → {lo.toFixed(1)}–
                  {hi.toFixed(1)} m from a {dbm} dBm {band.label} link (n = {band.n}, R₁ₘ = {band.r1m} dBm).
                </p>
                <div className="relative mt-5 h-9 rounded-lg border border-ivory/15 bg-ivory/[0.05]">
                  <div
                    className="absolute inset-y-0 rounded-lg"
                    style={{ left: pct(lo), width: pct(hi - lo), background: level.color, opacity: 0.85 }}
                  />
                  <span className="num absolute right-3 top-1/2 -translate-y-1/2 text-[0.72rem] text-ivory/45">80 m axis</span>
                </div>
              </div>
            )}
          </div>

          {/* ladder */}
          <div className="mt-7">
            <div className="text-[0.78rem] uppercase tracking-[0.12em] text-ivory/40">Quality ladder</div>
            <div className="mt-3 space-y-1.5">
              {ladder.map((l) => {
                const on = level.label === l.label;
                return (
                  <div
                    key={l.label}
                    className={cn(
                      "flex items-center justify-between rounded-xl px-4 py-2.5 text-[0.85rem] transition-colors",
                      on ? "bg-ivory/10 text-ivory" : "text-ivory/40",
                    )}
                  >
                    <span className="flex items-center gap-2.5">
                      <span className="h-2 w-2 rounded-full" style={{ background: on ? l.color : "#5f5e57" }} />
                      <span className="font-medium">{l.label}</span>
                      <span className="num text-[0.78rem]">
                        {l.min} … {l.max === -120 ? "−∞" : l.max} dBm
                      </span>
                    </span>
                    <span className="hidden text-[0.78rem] sm:block">{l.note}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </Section>
  );
}

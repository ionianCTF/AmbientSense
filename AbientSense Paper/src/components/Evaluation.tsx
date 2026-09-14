import { calibration, conditions, perRadius } from "../data";
import { Section, SectionHead, Card } from "./ui";
import { cn } from "../utils/cn";

function heat(v: number) {
  if (v >= 80) return "bg-olive/85 text-ivory";
  if (v >= 60) return "bg-olive/55 text-ivory";
  if (v >= 40) return "bg-kraft/60 text-ink";
  if (v >= 20) return "bg-book/35 text-ink";
  return "bg-pampas-deep text-muted";
}

const harness = [
  { k: "4,161", v: "generated scenes replayed through the production Kotlin engine" },
  { k: "1,512", v: "Monte-Carlo scenes for multi-band fusion" },
  { k: "378", v: "occupied scenes per band in the per-band evaluation" },
  { k: "288", v: "ranging scenes, near 10 m and far 55 m AP layouts" },
  { k: "160", v: "empty scenes audited for false positives and confirmations" },
  { k: "13 pp.", v: "published full evaluation report (PDF) backing this page" },
];

export function Evaluation() {
  return (
    <Section id="evaluation" tone="pampas" className="py-24 md:py-32">
      <SectionHead
        eyebrow="Section 3 · Evaluation"
        title={
          <>
            What was measured, and
            <br />
            what the same evidence rejected.
          </>
        }
        lede="Every constant that ships was first executed against a measurement harness that replays generated scenes through the production signal-processing code, then subjected to a falsification protocol: a candidate change is adopted only if it wins on complete scenes, not on row-level aggregates."
      />

      <div className="mt-12 grid gap-x-10 gap-y-8 sm:grid-cols-2 lg:grid-cols-3">
        {harness.map((h) => (
          <div key={h.k} className="border-t border-line-strong pt-5">
            <div className="head num text-[2.1rem]">{h.k}</div>
            <p className="mt-2 max-w-[32ch] text-[0.9rem] leading-snug text-muted">{h.v}</p>
          </div>
        ))}
      </div>

      {/* per-radius matrix */}
      <div className="mt-16">
        <h3 className="head text-[1.6rem]">Per-radius accuracy matrix</h3>
        <p className="font-serif-body mt-3 max-w-2xl text-[1.05rem] leading-relaxed text-muted">
          Accuracy is stated per radius because a single number would be a lie of aggregation. Detection — the weaker claim —
          survives further out than typing; beyond ~20 m the Fisher information about position along a link collapses and the system
          reports bands rather than points.
        </p>
        <div className="mt-7 overflow-x-auto rounded-[var(--radius-card)] border border-line bg-ivory p-4 sm:p-6">
          <table className="w-full min-w-[640px] border-collapse text-left">
            <thead>
              <tr className="text-[0.72rem] uppercase tracking-[0.12em] text-mist">
                <th className="py-3 pr-4 font-semibold">Radius</th>
                <th className="py-3 pr-4 font-semibold">BLE typing</th>
                <th className="py-3 pr-4 font-semibold">Wi-Fi 5 GHz</th>
                <th className="py-3 pr-4 font-semibold">Wi-Fi 2.4 GHz</th>
                <th className="py-3 font-semibold">Detection (fused)</th>
              </tr>
            </thead>
            <tbody className="num text-[0.95rem]">
              {perRadius.map((r) => (
                <tr key={r.radius} className="border-t border-line">
                  <td className="py-2.5 pr-4 font-semibold text-ink">{r.radius}</td>
                  {[r.ble, r.wf5, r.wf24].map((v, i) => (
                    <td key={i} className="py-2.5 pr-4">
                      <span className={cn("inline-block min-w-[4.4rem] rounded-md px-2.5 py-1.5 text-center text-[0.85rem]", heat(v))}>
                        {v} %
                      </span>
                    </td>
                  ))}
                  <td className="py-2.5">
                    <span className={cn("inline-block min-w-[4.4rem] rounded-md px-2.5 py-1.5 text-center text-[0.85rem]", heat(r.detect))}>
                      {r.detect} %
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-4 text-[0.82rem] text-mist">
            Matrix values are the measured per-radius yields of the deployed classifier on generated scenes; BLE reaches 57.7 %
            per-crossing typing overall against 25.7 % (5 GHz) and 16.1 % (2.4 GHz).
          </p>
        </div>
      </div>

      {/* transit + mesh */}
      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <Card>
          <div className="eyebrow text-clay">Two-node transit counter</div>
          <ul className="mt-4 space-y-3 text-[0.95rem]">
            {[
              ["100 %", "fused detection at 3–10 m separations"],
              ["100 %", "entity typing at 3–10 m (pedestrian vs. vehicle)"],
              ["94 %", "detection at 15 m separation"],
              ["2 links", "Classic BT RFCOMM + BLE L2CAP, presence ∧ loss fused"],
            ].map(([k, v]) => (
              <li key={v} className="flex items-baseline gap-4 border-t border-line pt-3">
                <span className="num w-20 shrink-0 text-[1.1rem] font-semibold text-clay">{k}</span>
                <span className="text-muted">{v}</span>
              </li>
            ))}
          </ul>
        </Card>
        <Card>
          <div className="eyebrow text-kelp-deep">Mesh agreement</div>
          <ul className="mt-4 space-y-3 text-[0.95rem]">
            {[
              ["0", "false confirmations across 160 empty scenes"],
              ["2–5", "cooperating instances per mesh"],
              ["20–40 m", "useful radius, from 4–12 m single-phone"],
              ["×1.25", "reliability bonus nearest the fused estimate"],
            ].map(([k, v]) => (
              <li key={v} className="flex items-baseline gap-4 border-t border-line pt-3">
                <span className="num w-20 shrink-0 text-[1.1rem] font-semibold text-kelp-deep">{k}</span>
                <span className="text-muted">{v}</span>
              </li>
            ))}
          </ul>
        </Card>
      </div>

      {/* calibration record */}
      <div className="mt-16">
        <h3 className="head text-[1.6rem]">Calibration record</h3>
        <p className="font-serif-body mt-3 max-w-2xl text-[1.05rem] leading-relaxed text-muted">
          Stating what the system achieves is only half the record. The same evidence rejected these candidates — including a
          ranging policy that survives in the shipped binary as a refusal rather than an estimate.
        </p>
        <div className="mt-7 overflow-hidden rounded-[var(--radius-card)] border border-line bg-ivory">
          {calibration.map((c, i) => (
            <div key={c.change} className={cn("grid gap-3 p-5 sm:grid-cols-[1.1fr_7.5rem_1.6fr] sm:items-baseline", i > 0 && "border-t border-line")}>
              <div className="text-[1rem] font-semibold">{c.change}</div>
              <div>
                <span
                  className={cn(
                    "inline-block rounded-full px-3 py-1 text-[0.72rem] font-semibold uppercase tracking-[0.1em]",
                    c.verdict === "accepted" ? "bg-olive/15 text-olive" : "bg-clay/12 text-clay",
                  )}
                >
                  {c.verdict}
                </span>
              </div>
              <p className="text-[0.92rem] leading-relaxed text-muted">{c.detail}</p>
            </div>
          ))}
        </div>
      </div>

      {/* condition matrix */}
      <div className="mt-14 grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <Card className="bg-ink text-ivory">
          <div className="eyebrow text-kraft">Condition matrix for further evaluation</div>
          <div className="mt-5 grid gap-x-8 gap-y-3 sm:grid-cols-2">
            {conditions.map((c) => (
              <div key={c.axis} className="border-t border-ivory/15 pt-3">
                <div className="text-[0.95rem] font-semibold text-ivory">{c.axis}</div>
                <div className="num mt-1 text-[0.85rem] text-ivory/55">{c.span}</div>
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <div className="eyebrow text-clay">Method of validation</div>
          <p className="mt-4 text-[0.95rem] leading-relaxed text-muted">
            The harness is not a simulator beside the app — it is the app's own code. Scenes are replayed through the production
            Kotlin classes, so a change that looks better in a notebook but loses on complete scenes never reaches a release.
          </p>
          <p className="mt-3 text-[0.95rem] leading-relaxed text-muted">
            That is why this page can state, with equal precision, both the configuration that shipped and the configuration the
            evidence threw away.
          </p>
        </Card>
      </div>
    </Section>
  );
}

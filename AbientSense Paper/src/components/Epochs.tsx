import { Section, SectionHead, Card } from "./ui";

const detect = [
  { d: "2 m", det: 100, naive: 25 },
  { d: "4 m", det: 88, naive: 34 },
  { d: "6 m", det: 81, naive: 41 },
  { d: "8 m", det: 75, naive: 48 },
  { d: "12 m", det: 62, naive: 55 },
  { d: "20 m", det: 44, naive: 62 },
];

const steps = [
  { n: "1", t: "Store", b: "A timed snapshot: timestamp, place, geo and the full per-link dBm table." },
  { n: "2", t: "Reset", b: "The engine clears its baselines and ring windows. Nothing carries over." },
  { n: "3", t: "Rescan", b: "A fresh epoch is captured on the same ≈10 s moving-object time scale." },
  { n: "4", t: "Compare", b: "Stable levels — median of the last 20 samples — or a transient dip of ≥ 3 dB." },
];

export function Epochs() {
  return (
    <Section id="epochs" className="py-24 md:py-32">
      <SectionHead
        eyebrow="Scan-epoch change detection"
        title={
          <>
            The difference between two
            <br />
            instants of the field.
          </>
        }
        lede="A single RSSI sample carries about 1 dB of standard deviation, so comparing last samples is comparing noise. Ambient Sense instead treats the field as two timed snapshots and computes their difference from stable levels — which is why the false-positive rate collapses from 25–62 % to zero at every tested distance."
      />

      <div className="mt-12 grid gap-6 lg:grid-cols-4">
        {steps.map((s) => (
          <Card key={s.n} className="bg-pampas/60">
            <div className="flex items-baseline gap-3">
              <span className="head num text-[1.5rem] text-clay">{s.n}</span>
              <span className="text-[1.02rem] font-semibold">{s.t}</span>
            </div>
            <p className="mt-2.5 text-[0.92rem] leading-relaxed text-muted">{s.b}</p>
          </Card>
        ))}
      </div>

      <div className="mt-8 grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <Card className="p-7">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h3 className="head text-[1.35rem]">Movement detection and false positives</h3>
            <span className="text-[0.8rem] text-mist">per distance · measured</span>
          </div>

          <div className="mt-6 space-y-4">
            {detect.map((r) => (
              <div key={r.d} className="grid grid-cols-[3.4rem_1fr] items-center gap-4">
                <span className="num text-[0.9rem] text-muted">{r.d}</span>
                <div>
                  <div className="flex items-center gap-3">
                    <div className="h-[13px] flex-1 overflow-hidden rounded-full bg-pampas-deep">
                      <div className="h-full rounded-full bg-olive" style={{ width: `${r.det}%` }} />
                    </div>
                    <span className="num w-12 text-right text-[0.85rem] font-semibold text-olive">{r.det}%</span>
                  </div>
                  <div className="mt-1.5 flex items-center gap-3">
                    <div className="h-[13px] flex-1 overflow-hidden rounded-full bg-pampas-deep">
                      <div className="h-full rounded-full bg-clay/70" style={{ width: `${r.naive}%` }} />
                    </div>
                    <span className="num w-12 text-right text-[0.85rem] text-clay/80">{r.naive}%</span>
                  </div>
                </div>
              </div>
            ))}
          </div>

          <div className="mt-6 flex flex-wrap gap-5 border-t border-line pt-4 text-[0.82rem]">
            <span className="flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full bg-olive" /> movement detected — 20-sample median rule
            </span>
            <span className="flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full bg-clay/70" /> false positives — naive last-sample comparison
            </span>
          </div>
          <p className="mt-4 text-[0.9rem] leading-relaxed text-muted">
            The naive rule is not merely noisier — it reports movement on completely empty scenes. Every candidate replacement was
            required to win on complete scenes, not on row-level aggregates, before it shipped.
          </p>
        </Card>

        <div className="grid gap-6">
          <Card className="bg-ink text-ivory">
            <div className="eyebrow text-kraft">The verdict string</div>
            <p className="font-mono mt-4 text-[0.92rem] leading-relaxed text-ivory/90">
              5 links moved ≥3 dB (max 14.0 dB) → field CHANGED since previous scan
            </p>
            <p className="mt-4 text-[0.9rem] leading-relaxed text-ivory/60">
              Verdicts are computed, not asserted. A quiet room returns “unchanged” with the same arithmetic that returns CHANGED
              when someone walks past — and both outcomes are logged with their link tables.
            </p>
          </Card>
          <Card>
            <div className="eyebrow text-denim">Why ≈10 seconds</div>
            <p className="mt-3 text-[0.94rem] leading-relaxed text-muted">
              The epoch interval is set by the time scale of the phenomenon: a pedestrian crossing a link leg occupies a few
              seconds, a vehicle less. Shorter epochs compare partially-formed events; longer ones merge two crossings into one
              verdict.
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              {["10 s epoch", "median 20", "≥ 3 dB transient", "cap 120 scans"].map((t) => (
                <span key={t} className="rounded-full bg-pampas px-3 py-1.5 text-[0.8rem] text-ink/75">
                  {t}
                </span>
              ))}
            </div>
          </Card>
        </div>
      </div>
    </Section>
  );
}

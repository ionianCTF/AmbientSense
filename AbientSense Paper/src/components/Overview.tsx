import { contributions } from "../data";
import { Section, SectionHead, Card } from "./ui";
import { cn } from "../utils/cn";

const accentText: Record<string, string> = {
  clay: "text-clay",
  olive: "text-olive",
  denim: "text-denim",
  kelp: "text-kelp-deep",
};
const accentBorder: Record<string, string> = {
  clay: "border-clay/40",
  olive: "border-olive/40",
  denim: "border-denim/40",
  kelp: "border-kelp/40",
};
const accentHex: Record<string, string> = {
  clay: "#c15f3c",
  olive: "#5f7a45",
  denim: "#3f6b8a",
  kelp: "#629a90",
};

const difficulties = [
  {
    t: "Detection is physics-limited",
    b: "Occlusion attenuation decays with distance through Fresnel-zone occupancy while the environment's own fluctuation grows with range. Past some distance the event is statistically indistinguishable from noise — a system that does not quantify that limit is misleading its user.",
    tag: "2 m → 68 m span measured",
  },
  {
    t: "RSSI ranging is ill-conditioned",
    b: "A log-distance model converts decibels into metres, but on weak links the inverse function explodes. An unconstrained estimate can be wrong by tens of metres and still look perfectly confident in a table.",
    tag: "14–23 m illusions suppressed",
  },
  {
    t: "Community mode is an attack surface",
    b: "A device can report forged beacons — spoofed SSIDs, rogue APs, phantom BLE advertising — or forged reports. The mesh mean, the very quantity gamified reliability rewards, becomes the target.",
    tag: "integrity treated as first-class",
  },
];

export function Overview() {
  return (
    <Section id="overview" tone="pampas" className="py-24 md:py-32">
      <SectionHead
        eyebrow="Section 1 · Introduction"
        title={
          <>
            Laboratory passive sensing
            <br />
            versus one phone in a hand.
          </>
        }
        lede="Device-free RF sensing is well established in controlled settings: radio tomographic imaging, learned field inversion and CSI activity classifiers all report strong numbers when node geometry is known and hardware is dedicated. Ambient Sense asks the harder question — what can a single, unmodified smartphone held by an ordinary user actually deliver in the field?"
      />

      <div className="mt-12 grid gap-6 lg:grid-cols-3">
        {difficulties.map((d) => (
          <Card key={d.t} className="flex flex-col">
            <h3 className="head text-[1.28rem]">{d.t}</h3>
            <p className="font-serif-body mt-3 flex-1 text-[1.02rem] leading-[1.6] text-muted">{d.b}</p>
            <div className="mt-5 border-t border-line pt-3 text-[0.8rem] font-medium uppercase tracking-[0.1em] text-clay">
              {d.tag}
            </div>
          </Card>
        ))}
      </div>

      <div className="mt-20">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <h3 className="head text-[1.7rem] sm:text-[2rem]">Four contributions, one honest method</h3>
          <p className="max-w-md text-[0.92rem] leading-snug text-muted">
            Plus the contribution that makes the others citable: a validation method that records its own rejections.
          </p>
        </div>

        <div className="mt-8 grid gap-6 lg:grid-cols-2">
          {contributions.map((c) => (
            <Card key={c.n} className={cn("border-t-[3px]", accentBorder[c.accent])}>
              <div className="flex items-baseline gap-4">
                <span className={cn("head num text-[1.7rem]", accentText[c.accent])}>{c.n}</span>
                <h4 className="head text-[1.32rem]">{c.title}</h4>
              </div>
              <p className="mt-3.5 text-[0.97rem] leading-relaxed text-muted">{c.body}</p>
              <ul className="mt-5 space-y-2">
                {c.facts.map((f) => (
                  <li key={f} className="num flex items-center gap-2.5 border-t border-line pt-2 text-[0.86rem] text-ink/75">
                    <span className="h-1.5 w-1.5 shrink-0 rounded-full" style={{ background: accentHex[c.accent] }} />
                    {f}
                  </li>
                ))}
              </ul>
            </Card>
          ))}
        </div>
      </div>
    </Section>
  );
}

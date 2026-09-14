import { references } from "../data";
import { Section, SectionHead, Card, Eyebrow } from "./ui";

const uses = [
  {
    t: "Ambient assisted living",
    b: "Presence and crossing counts in a home without cameras, worn devices or a hardware installation.",
  },
  {
    t: "Occupancy analytics",
    b: "Flow through a workplace, a shop or a corridor, measured opportunistically by the devices already inside it.",
  },
  {
    t: "Roadside traffic studies",
    b: "Two phones on opposite sides of a road become a barrier counter with typed pedestrian and vehicle flow.",
  },
  {
    t: "Coverage scouting",
    b: "Scan epochs make the RF field itself the subject: what changed here since the last pass, and by how many dB.",
  },
];

export function Uses() {
  return (
    <Section id="uses" tone="pampas" className="py-24 md:py-32">
      <SectionHead
        eyebrow="How it can be used"
        title="Deployed for free, by people who already carry the sensor."
        lede="Because the sensing hardware is the handset, the marginal cost of a new measurement point is approximately zero — and because the observer holds no camera, the privacy argument is structural rather than promised."
      />
      <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {uses.map((u) => (
          <Card key={u.t} className="bg-ivory">
            <h3 className="head text-[1.15rem]">{u.t}</h3>
            <p className="mt-2.5 text-[0.92rem] leading-relaxed text-muted">{u.b}</p>
          </Card>
        ))}
      </div>
    </Section>
  );
}

export function ConclusionSection() {
  return (
    <Section id="conclusion" tone="ink" className="py-24 md:py-32">
      <div className="grid gap-12 lg:grid-cols-[1.15fr_0.85fr]">
        <div>
          <div className="eyebrow flex items-center gap-3 text-kraft">
            <span className="inline-block h-[7px] w-[7px] rounded-full bg-kraft" />
            Section 4 · Conclusion &amp; future work
          </div>
          <h2 className="head mt-5 text-[2rem] text-ivory sm:text-[2.6rem] md:text-[3rem]">
            State the achievement. State the limit. Repeat.
          </h2>
          <p className="font-serif-body mt-6 text-[1.14rem] leading-[1.65] text-ivory/70">
            Ambient Sense demonstrates that a commodity phone can perform multi-band passive sensing end to end: fuse BLE and
            dual-band Wi-Fi features into entity typing, convert measured dBm into a stated range with an explicit gate, detect
            change between two instants of the field without inventing movement, count transit across a two-node Bluetooth barrier,
            and accrue accuracy by agreeing with neighbours — while a gamification layer keeps the volunteers coming back.
          </p>
          <p className="font-serif-body mt-5 text-[1.14rem] leading-[1.65] text-ivory/70">
            Just as importantly, it reports where the physics withdraws its support: 0 % typing accuracy past ~68 m on BLE, 94 %
            rather than 100 % detection at 15 m, and a noise floor below which ranging is not merely uncertain but refused.
          </p>

          <div className="mt-10 grid gap-6 sm:grid-cols-2">
            <div className="rounded-[var(--radius-card)] border border-ivory/15 p-6">
              <div className="text-[1.05rem] font-semibold text-ivory">Cybersecurity module</div>
              <p className="mt-2.5 text-[0.92rem] leading-relaxed text-ivory/60">
                Malicious SSIDs, rogue access points and forged reporting are the next front: physical-fingerprint spoofing
                detection, RTT-based rogue-AP analysis and reputation-weighted fusion, validated on the same falsification protocol.
              </p>
            </div>
            <div className="rounded-[var(--radius-card)] border border-ivory/15 p-6">
              <div className="text-[1.05rem] font-semibold text-ivory">Extending the club</div>
              <p className="mt-2.5 text-[0.92rem] leading-relaxed text-ivory/60">
                Team meshes, seasonal ladders and quality-weighted seasons that reward accuracy rather than volume — with the
                reliability bonus becoming a published, auditable score.
              </p>
            </div>
          </div>

          <div className="mt-10 flex flex-wrap gap-3">
            <a
              href="#/screens"
              className="inline-flex items-center justify-center gap-2 rounded-full bg-clay px-6 py-3 text-[0.92rem] font-semibold tracking-tight text-ivory transition-colors hover:bg-book"
            >
              Back to the screens
            </a>
            <a
              href="#/"
              className="inline-flex items-center justify-center gap-2 rounded-full border border-ivory/25 px-6 py-3 text-[0.92rem] font-semibold tracking-tight text-ivory transition-colors hover:border-ivory hover:bg-ivory/10"
            >
              Back to home
            </a>
          </div>
        </div>

        <div>
          <Eyebrow className="text-kraft">References</Eyebrow>
          <ol className="mt-6 space-y-2.5">
            {references.map((r, i) => (
              <li key={r} className="border-t border-ivory/12 pt-2.5 text-[0.86rem] leading-snug text-ivory/55">
                <span className="num mr-2 text-ivory/35">{String(i + 1).padStart(2, "0")}</span>
                {r}
              </li>
            ))}
          </ol>
        </div>
      </div>
    </Section>
  );
}

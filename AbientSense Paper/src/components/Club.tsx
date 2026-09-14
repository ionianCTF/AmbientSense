import { Section, SectionHead, Card } from "./ui";

const xp = [
  ["Pedestrian crossing", "+10 XP"],
  ["Vehicle crossing", "+14 XP"],
  ["Disturbance logged", "+2 XP"],
  ["Confirmed by ≥ 2 mesh nodes", "+15 XP and ×2 fusion bonus"],
  ["Nearest the fused estimate", "×1.25 reliability multiplier"],
  ["Day streak, sensor kept on", "streak + badge ladder"],
];

const threats = [
  {
    t: "Forged beacons",
    b: "Spoofed SSIDs, rogue access points and phantom BLE advertising can be injected to manufacture disturbances.",
    d: "Physical-layer fingerprints rather than identifiers (Wu et al., 2020); client-side round-trip-time rogue-AP analysis (Kitisriworapan et al., 2020).",
  },
  {
    t: "Forged reports",
    b: "A node can publish crossings it never observed, dragging the mesh mean — and every bonus computed from it.",
    d: "Reputation-weighted fusion, agreement tolerance, and down-weighting of rays that disagree with the majority.",
  },
  {
    t: "Protocol-level attack",
    b: "Downgrade attacks on key negotiation and known privacy flaws in the BLE specification affect the radios we ride on.",
    d: "BLE security survey bounds (Cäsar et al., 2022; Barua et al., 2022; Antonioli et al., 2020; Wu et al., 2024) define the future adversarial module.",
  },
];

export function Club() {
  return (
    <Section id="club" className="py-24 md:py-32">
      <div className="grid gap-12 lg:grid-cols-[1fr_1fr] lg:items-start">
        <div>
          <SectionHead
            eyebrow="Gamified community reporting"
            title={
              <>
                Incentives are the
                <br />
                infrastructure.
              </>
            }
            lede="A sensing mesh built from phones that people already own has no deployment budget — it has participants. Mobile crowdsensing research is unambiguous on this point: users contribute when mechanisms are transparent, rewarded and socially engaging, and quality-weighted rewards outperform raw volume."
          />
          <p className="font-serif-body mt-6 max-w-xl text-[1.05rem] leading-[1.65] text-muted">
            Gamification here is therefore not cosmetic. Points, badges and progress feedback are the machinery that keeps a
            voluntary mesh alive — and the same machinery makes the trustworthiness of contributed measurements an engineering
            problem rather than an afterthought.
          </p>

          <Card className="mt-8">
            <div className="eyebrow text-clay">Reward schedule</div>
            <ul className="mt-4">
              {xp.map(([k, v]) => (
                <li key={k} className="flex items-baseline justify-between gap-4 border-t border-line py-3 text-[0.95rem]">
                  <span className="text-ink/85">{k}</span>
                  <span className="num shrink-0 font-semibold text-clay">{v}</span>
                </li>
              ))}
            </ul>
          </Card>
        </div>

        <div className="grid gap-6">
          <Card className="bg-ink text-ivory">
            <div className="eyebrow text-kraft">Privacy boundary</div>
            <ul className="mt-4 space-y-3 text-[0.95rem]">
              {[
                "Device-free: nothing is attached to, or carried by, the observed target.",
                "No camera and no microphone are used or requested, anywhere in the app.",
                "Beacon identifiers (MAC, SSID) are not persisted; per-link dBm tables stay on device.",
                "Only aggregate crossing records leave the handset, and only when a mesh exists.",
                "Presence is inferred from attenuation, never from identity — counting without identifying.",
              ].map((t) => (
                <li key={t} className="flex gap-3 border-t border-ivory/15 pt-3 text-ivory/75">
                  <span className="mt-[7px] h-[6px] w-[6px] shrink-0 rounded-full bg-kelp" />
                  {t}
                </li>
              ))}
            </ul>
          </Card>

          <div className="eyebrow mt-4 text-clay">The adversarial surface, stated openly</div>
          {threats.map((t) => (
            <Card key={t.t}>
              <h4 className="head text-[1.2rem]">{t.t}</h4>
              <p className="mt-2 text-[0.93rem] leading-relaxed text-muted">{t.b}</p>
              <p className="mt-3 border-t border-line pt-3 text-[0.88rem] leading-relaxed text-ink/75">
                <span className="font-semibold">Planned defence · </span>
                {t.d}
              </p>
            </Card>
          ))}
        </div>
      </div>
    </Section>
  );
}

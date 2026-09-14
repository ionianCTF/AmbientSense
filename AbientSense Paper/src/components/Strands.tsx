import { Section, SectionHead } from "./ui";

const strands = [
  {
    n: "I",
    t: "Device-free sensing & RTI",
    b: "Radio tomographic imaging reconstructs an attenuation field from many links (Wilson & Patwari, 2010); later work removes measurement artefacts training-free (Ma et al., 2021), recovers images generatively (Cao et al., 2020) and fuses spatiotemporal information (J. Zhang et al., 2021). CNN reconstruction on ESP32 meshes reports >92 % in controlled geometry (Jabbar & Shoaib, 2025).",
    ours: "We keep the detector tradition but replace learned field inversion with an explicit physical reading of each dip — because a hand-held phone has no known node geometry.",
  },
  {
    n: "II",
    t: "Activity & identity recognition",
    b: "CSI pipelines classify performed activities (Jiao & Zhang, 2023; Hu et al., 2025), vision transformers operate on CSI (Luo et al., 2024), and identity is recoverable from RSSI distributions alone (R. Zhang & Jing, 2021).",
    ours: "Our target is opportunistic detection of people passing by, where only link-level RSSI exists and no one performed anything for us.",
  },
  {
    n: "III",
    t: "Counting & flow",
    b: "Device-free counting with CSI and deep networks (Zhou et al., 2020b; Choi et al., 2022), Doppler-based throughput estimation (Zhou et al., 2020a), privacy-preserving fingerprint counting (Rusca et al., 2024), and barrier-coverage formalisation (Chang et al., 2018).",
    ours: "We realise the barrier geometry on commodity Bluetooth — two phones, no dedicated hardware.",
  },
  {
    n: "IV",
    t: "Ranging & shadowing physics",
    b: "BLE positioning needs integrity monitoring against unstable beacons (Yao et al., 2020) and obstruction-aware treatment of missing links (Taşkan & Alemdar, 2021); body shadowing at 2.4 GHz is characterised (Januszkiewicz, 2018; Mamun et al., 2021); Fisher-information limits bound device-free localization in ISAC networks (Wang et al., 2026).",
    ours: "Beyond the range where link SNR carries position information, we report bands rather than points — and refuse below the floor.",
  },
  {
    n: "V",
    t: "Participation, incentives, integrity",
    b: "Data-quality-guided incentives (Peng et al., 2018), ability-reputation mechanisms (Luo et al., 2021), coverage-and-reputation constraints (Zhang et al., 2020), and measurable engagement effects from gamification (Bitrián et al., 2021).",
    ours: "Quality-weighted reward becomes a reliability bonus for nodes nearest the fused estimate — which also makes trustworthiness an engineering target.",
  },
];

export function Strands() {
  return (
    <Section className="py-24 md:py-32">
      <SectionHead
        eyebrow="Position in the literature"
        title="Five strands, one gap."
        lede="Prior work frequently reports accuracy without stating at which distance the signal-to-noise ratio actually supports it, treats classification and detection as one metric, or assumes synchronised nodes and CSI-capable chipsets that phones simply do not have."
      />
      <div className="mt-12 grid gap-x-10 gap-y-10 md:grid-cols-2 lg:grid-cols-3">
        {strands.map((s) => (
          <div key={s.n} className="border-t border-line-strong pt-5">
            <div className="flex items-baseline gap-3">
              <span className="head num text-[1.1rem] text-clay">{s.n}</span>
              <h3 className="head text-[1.12rem]">{s.t}</h3>
            </div>
            <p className="mt-3 text-[0.9rem] leading-relaxed text-muted">{s.b}</p>
            <p className="mt-3.5 border-t border-line pt-3 text-[0.9rem] leading-relaxed text-ink/80">
              <span className="font-semibold">Ambient Sense · </span>
              {s.ours}
            </p>
          </div>
        ))}
      </div>
    </Section>
  );
}

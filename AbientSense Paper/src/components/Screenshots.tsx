import { useState } from "react";
import { PhoneFrame, type TabId } from "./phone/parts";
import { screens } from "./phone/screens";
import { Section, SectionHead, Eyebrow } from "./ui";
import { cn } from "../utils/cn";

type Meta = { id: TabId; label: string; caption: string; look: string[] };

const meta: Meta[] = [
  {
    id: "sense",
    label: "Sense",
    caption:
      "The live instrument. Three radio front-ends are polled without ever connecting to them, and every observable link becomes a measurement ray whose attenuation state is tracked in real time.",
    look: [
      "33 passive links across BLE, Wi-Fi 2.4 GHz and Wi-Fi 5 GHz inside a 20 m radius",
      "Counters for people (67), vehicles (66), raw disturbances (165) and live links (33)",
      "Live feature readout — moving variance 11.45 dB², jitter 1.79 dB, classifier confidence 68 %",
      "Attenuation envelope: max dip below the adaptive baseline, markers = recent crossings",
    ],
  },
  {
    id: "scans",
    label: "Scans",
    caption:
      "Scan-epoch change detection on a ≈10 s moving-object time scale. The phone stores a timed snapshot of the whole field, resets the engine, rescans, and then computes the difference between the two instants.",
    look: [
      "Window 19:56:03 – 19:56:18, place and geo captured with the per-link dBm table",
      "Delta computed from stable levels: 33 links · 5 moved ≥ 3 dB (max 14.0 dB)",
      "Field verdict CHANGED since previous scan — after the 20-sample median replaced last-sample comparison",
      "History capped at 120 scans; each row opens the full per-link dBm table",
    ],
  },
  {
    id: "mesh",
    label: "Mesh",
    caption:
      "Mesh-accrued accuracy. Neighbouring Ambient Sense instances exchange crossing records, and the geometric diversity of their rays both raises accuracy and pushes the useful radius outward.",
    look: [
      "2–5 cooperating nodes confirm each other's crossings before a count is published",
      "Effective radius extends from 4–12 m (single phone) toward 20–40 m",
      "Zero false confirmations measured across 160 audited empty scenes",
      "Reliability bonus: devices nearest the fused estimate earn a ×1.25 multiplier",
    ],
  },
  {
    id: "transit",
    label: "Transit",
    caption:
      "The two-node roadside counter. Two phones form a barrier across a road over Classic Bluetooth RFCOMM and BLE L2CAP, and a hybrid link-presence/loss classifier counts and types what passes between them.",
    look: [
      "100 % fused detection and 100 % entity typing at 3–10 m separations",
      "94 % detection at 15 m — the honest decay is shown, not hidden",
      "Per-link presence and loss are fused, so a link drop and a link appearance are both evidence",
      "Unattributed dips are held below threshold rather than counted",
    ],
  },
  {
    id: "club",
    label: "Club",
    caption:
      "The incentive machinery. Gamification is not cosmetic here — it is what keeps a voluntary sensing mesh alive, and it makes the trustworthiness of contributed measurements a first-class problem.",
    look: [
      "People +10 XP · vehicles +14 XP · disturbances +2 XP",
      "×2 fusion bonus and +15 XP when a crossing is confirmed by ≥ 2 mesh nodes",
      "Fused accuracy and crowd level are reported back to the contributor",
      "Badges and day streaks reward keeping sensors on, not reporting volume",
    ],
  },
  {
    id: "settings",
    label: "Settings",
    caption:
      "Every constant that ships is exposed, because every one of them was the outcome of an executed calibration-and-rejection protocol on the Monte-Carlo harness.",
    look: [
      "Radius 10 / 20 / 40 m, and per-band radios that can be switched off individually",
      "Range gate at −75 dBm and a hard noise floor at −90 dBm where ranging is refused",
      "Radial-distance band f ∈ [0.15, 0.92] replaces a false point estimate",
      "Privacy boundary: no camera, no microphone, no stored beacon identifiers",
    ],
  },
];

export function Screenshots() {
  const [active, setActive] = useState<TabId>("sense");
  const current = meta.find((m) => m.id === active)!;
  const Screen = screens[active];

  return (
    <Section id="screens" tone="pampas" className="py-24 md:py-32">
      <SectionHead
        eyebrow="App screenshots"
        title={
          <>
            The whole engine,
            <br />
            six tabs on one handset.
          </>
        }
        lede="Ambient Sense is a native Android application. Nothing below is a mock-up of an idea — these are the screens that carried the measurement campaign, reproduced from the deployed build."
      />

      <div className="mt-10 flex flex-wrap gap-2">
        {meta.map((m) => (
          <button
            key={m.id}
            onClick={() => setActive(m.id)}
            className={cn(
              "rounded-full px-4 py-2 text-[0.85rem] font-medium tracking-tight transition-colors",
              m.id === active
                ? "bg-ink text-ivory"
                : "border border-line-strong bg-ivory/60 text-muted hover:border-ink hover:text-ink",
            )}
          >
            {m.label}
          </button>
        ))}
      </div>

      <div className="mt-10 grid items-start gap-12 lg:grid-cols-[1fr_auto]">
        <div key={active} className="fade-up max-w-xl">
          <Eyebrow>{current.label} tab</Eyebrow>
          <p className="font-serif-body mt-4 text-[1.16rem] leading-[1.62] text-ink-soft">{current.caption}</p>
          <ul className="mt-7 space-y-3.5">
            {current.look.map((l) => (
              <li key={l} className="flex gap-3 border-t border-line pt-3.5 text-[0.98rem] leading-snug text-muted">
                <span className="mt-[7px] h-[6px] w-[6px] shrink-0 rounded-full bg-clay" />
                {l}
              </li>
            ))}
          </ul>

          <div className="mt-9 rounded-[var(--radius-card)] border border-line bg-ivory p-5">
            <div className="eyebrow text-mist">Tab {meta.findIndex((m) => m.id === active) + 1} of 6</div>
            <p className="mt-2 text-[0.9rem] leading-relaxed text-muted">
              Screens are rendered from the production layout: the same counters, the same constants and the same verdict strings
              the engine emits — including refusals such as links held below the noise floor.
            </p>
          </div>
        </div>

        <div className="flex justify-center lg:justify-end">
          <PhoneFrame tab={active} time={active === "sense" ? "19:55" : "19:56"}>
            <Screen />
          </PhoneFrame>
        </div>
      </div>
    </Section>
  );
}

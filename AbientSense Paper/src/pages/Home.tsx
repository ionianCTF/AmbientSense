import { PhoneFrame } from "../components/phone/parts";
import { SenseScreen } from "../components/phone/screens";
import { Eyebrow } from "../components/ui";
import { heroStats } from "../data";
import { routes } from "../router";

const icons: Record<string, string> = {
  "/introduction": "M3.5 3.5h7v7h-7zM13.5 3.5h7v7h-7zM3.5 13.5h7v7h-7zM13.5 13.5h7v7h-7z",
  "/method": "M3 4h7v5H3zM14 15h7v5h-7zM6.5 9v4.5a2 2 0 0 0 2 2H14",
  "/screens": "M6.5 2.5h11v19h-11zM10.5 5.5h3",
  "/ranging": "M3.6 14.6 14.6 3.6a2 2 0 0 1 2.8 0l3 3a2 2 0 0 1 0 2.8l-11 11a2 2 0 0 1-2.8 0l-3-3a2 2 0 0 1 0-2.8z",
  "/evaluation": "M4 20V10M10 20V4M16 20v-7M22 20H2.4",
  "/community": "M8 4h8v4.6a4 4 0 0 1-8 0zM12 12.6V16M9 19.6h6",
  "/conclusion": "M5.5 21V3.8M5.5 4.4h11.2l-1.9 3.9 1.9 3.9H5.5",
};

const capabilities = [
  {
    to: "/ranging",
    tag: "Ranging",
    head: "A range, or a refusal.",
    body: "dBm becomes metres through log-distance inversion — gated at −75 dBm, hard-excluded at the −90 dBm noise floor, published as a band rather than a false point.",
    stat: "14–23 m",
    statNote: "illusions suppressed",
    color: "#c15f3c",
  },
  {
    to: "/method",
    tag: "Change detection",
    head: "Two instants of the field.",
    body: "Every ≈10 s epoch stores a snapshot, resets the engine and rescans. The difference is computed from the median of the last 20 samples — never from a single noisy sample.",
    stat: "0 %",
    statNote: "false positives, all distances",
    color: "#5f7a45",
  },
  {
    to: "/evaluation",
    tag: "Counting",
    head: "Two phones make a barrier.",
    body: "Classic Bluetooth RFCOMM and BLE L2CAP turn two handsets into a roadside transit counter that types pedestrians and vehicles as they cross the line between them.",
    stat: "100 %",
    statNote: "detection at 3–10 m",
    color: "#3f6b8a",
  },
  {
    to: "/community",
    tag: "Community",
    head: "Incentives are the infrastructure.",
    body: "A mesh of 2–5 phones confirms each other's crossings, extends the useful radius, and pays a reliability bonus to the devices nearest the fused estimate.",
    stat: "20–40 m",
    statNote: "useful mesh radius",
    color: "#629a90",
  },
];

const pills = ["Device-free", "No camera · no mic", "BLE + dual-band Wi-Fi", "On-device Kotlin", "Commodity hardware"];

export function Home() {
  return (
    <div className="bg-ink">
      {/* hero */}
      <div id="top" className="relative overflow-hidden pt-28 text-ivory sm:pt-32">
        <div
          aria-hidden
          className="pointer-events-none absolute -right-40 top-[-12rem] h-[38rem] w-[38rem] rounded-full opacity-60 blur-3xl"
          style={{ background: "radial-gradient(circle at 45% 45%, #7a3b23 0%, rgba(20,20,19,0) 68%)" }}
        />
        <div
          aria-hidden
          className="pointer-events-none absolute -left-52 bottom-[-14rem] h-[34rem] w-[34rem] rounded-full opacity-40 blur-3xl"
          style={{ background: "radial-gradient(circle at 50% 50%, #3f6b60 0%, rgba(20,20,19,0) 70%)" }}
        />

        <div className="relative mx-auto w-full max-w-6xl px-5 sm:px-8">
          <div className="grid items-center gap-14 lg:grid-cols-[1.08fr_auto]">
            <div className="fade-up">
              <Eyebrow className="text-kraft">System research report · build v1.1</Eyebrow>
              <h1 className="head mt-6 text-[2.8rem] leading-[0.97] sm:text-[3.8rem] lg:text-[4.4rem]">
                Your phone is
                <br />
                already a sensor.
              </h1>
              <p className="font-serif-body mt-7 max-w-xl text-[1.18rem] leading-[1.6] text-ivory/65 sm:text-[1.3rem]">
                People and vehicles leave electromagnetic wakes. Ambient Sense reads them passively across BLE and dual-band Wi-Fi
                — counting, ranging and detecting change, and admitting exactly where the physics stops supporting it.
              </p>
              <div className="mt-9 flex flex-wrap gap-3">
                <a
                  href="#/screens"
                  className="inline-flex items-center justify-center gap-2 rounded-full bg-ivory px-6 py-3 text-[0.92rem] font-semibold tracking-tight text-ink transition-colors hover:bg-sand"
                >
                  See the app screens
                </a>
                <a
                  href="#/method"
                  className="inline-flex items-center justify-center gap-2 rounded-full border border-ivory/25 px-6 py-3 text-[0.92rem] font-semibold tracking-tight text-ivory transition-colors hover:border-ivory hover:bg-ivory/5"
                >
                  How it works
                </a>
              </div>
              <div className="mt-9 flex flex-wrap gap-2">
                {pills.map((p) => (
                  <span
                    key={p}
                    className="inline-flex items-center gap-2 rounded-full border border-ivory/12 bg-ivory/[0.04] px-3.5 py-1.5 text-[0.78rem] font-medium tracking-tight text-ivory/70"
                  >
                    <span className="h-1.5 w-1.5 rounded-full bg-kelp" />
                    {p}
                  </span>
                ))}
              </div>
            </div>

            <div className="relative flex justify-center lg:justify-end">
              <div
                aria-hidden
                className="absolute left-1/2 top-1/2 h-[30rem] w-[30rem] -translate-x-1/2 -translate-y-1/2 rounded-full bg-ivory/[0.05]"
              />
              <div className="relative">
                <PhoneFrame tab="sense" time="19:55">
                  <SenseScreen />
                </PhoneFrame>
                <div
                  className="absolute -left-8 top-24 hidden rounded-2xl border border-ivory/12 bg-[#1c1b19]/95 px-4 py-3 backdrop-blur sm:block"
                  style={{ boxShadow: "0 18px 40px -22px rgba(0,0,0,0.8)" }}
                >
                  <div className="eyebrow text-kraft">Live</div>
                  <div className="num mt-1 text-[1.6rem] leading-none text-ivory">33</div>
                  <div className="text-[0.72rem] text-ivory/45">passive links</div>
                </div>
                <div
                  className="absolute -right-6 bottom-32 hidden rounded-2xl border border-ivory/12 bg-[#1c1b19]/95 px-4 py-3 backdrop-blur sm:block"
                  style={{ boxShadow: "0 18px 40px -22px rgba(0,0,0,0.8)" }}
                >
                  <div className="eyebrow text-kelp">Epoch delta</div>
                  <div className="num mt-1 text-[1.6rem] leading-none text-ivory">0 %</div>
                  <div className="text-[0.72rem] text-ivory/45">false positives</div>
                </div>
              </div>
            </div>
          </div>

          {/* headline numbers */}
          <div className="mt-24 grid gap-x-10 gap-y-8 border-t border-ivory/12 pb-20 pt-10 sm:grid-cols-2 lg:grid-cols-4">
            {heroStats.map((s) => (
              <div key={s.k} className="border-t border-ivory/12 pt-5">
                <div className="head num text-[2.1rem] text-ivory">{s.k}</div>
                <p className="mt-2 max-w-[30ch] text-[0.9rem] leading-snug text-ivory/50">{s.v}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* capabilities — catchy cards into subpages */}
      <div className="bg-ivory px-5 py-24 text-ink sm:px-8 md:py-28">
        <div className="mx-auto w-full max-w-6xl">
          <div className="max-w-3xl">
            <Eyebrow>What it does</Eyebrow>
            <h2 className="head mt-5 text-[2rem] sm:text-[2.6rem] md:text-[3rem]">
              Four things, measured
              <br />
              rather than promised.
            </h2>
            <p className="font-serif-body mt-5 text-[1.12rem] leading-[1.65] text-muted sm:text-[1.22rem]">
              Every number below comes from the evaluation artefacts of the deployed Android build — not from a claim. Open a card
              for the full treatment.
            </p>
          </div>

          <div className="mt-12 grid gap-6 sm:grid-cols-2">
            {capabilities.map((c) => (
              <a
                key={c.to}
                href={`#${c.to}`}
                className="group flex flex-col rounded-[var(--radius-card)] border border-line bg-pampas/40 p-7 transition-all duration-200 hover:-translate-y-0.5 hover:border-ink/25 hover:bg-pampas"
              >
                <div className="flex items-center justify-between gap-4">
                  <span className="eyebrow" style={{ color: c.color }}>
                    {c.tag}
                  </span>
                  <svg viewBox="0 0 24 24" className="h-4 w-4 text-mist transition-transform duration-200 group-hover:translate-x-1 group-hover:text-ink" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <path d="m9 5 7 7-7 7" />
                  </svg>
                </div>
                <h3 className="head mt-4 text-[1.45rem]">{c.head}</h3>
                <p className="mt-3 flex-1 text-[0.96rem] leading-relaxed text-muted">{c.body}</p>
                <div className="mt-6 flex items-baseline gap-3 border-t border-line pt-4">
                  <span className="head num text-[1.7rem]" style={{ color: c.color }}>
                    {c.stat}
                  </span>
                  <span className="text-[0.82rem] text-mist">{c.statNote}</span>
                </div>
              </a>
            ))}
          </div>
        </div>
      </div>

      {/* page directory */}
      <div className="bg-pampas px-5 py-24 text-ink sm:px-8 md:py-28">
        <div className="mx-auto w-full max-w-6xl">
          <div className="flex flex-wrap items-end justify-between gap-6">
            <div className="max-w-xl">
              <Eyebrow>The report</Eyebrow>
              <h2 className="head mt-5 text-[1.9rem] sm:text-[2.4rem]">Read it in order, or jump in.</h2>
            </div>
            <p className="max-w-sm text-[0.92rem] leading-snug text-muted">
              The full paper is split into seven pages: introduction, methodology, screenshots, ranging, evaluation, community and
              conclusion.
            </p>
          </div>

          <div className="mt-10 overflow-hidden rounded-[var(--radius-card)] border border-line bg-ivory">
            {routes
              .filter((r) => r.path !== "/")
              .map((r, i) => (
                <a
                  key={r.path}
                  href={`#${r.path}`}
                  className="group flex items-center gap-4 border-t border-line px-5 py-5 transition-colors first:border-t-0 hover:bg-pampas/60 sm:px-7"
                >
                  <span className="num w-7 shrink-0 text-[0.8rem] text-mist">{String(i + 1).padStart(2, "0")}</span>
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-line text-clay">
                    <svg viewBox="0 0 24 24" className="h-[19px] w-[19px]" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                      <path d={icons[r.path]} />
                    </svg>
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="head block text-[1.12rem]">{r.label}</span>
                    <span className="mt-0.5 block text-[0.85rem] text-muted">{r.note}</span>
                  </span>
                  <svg viewBox="0 0 24 24" className="h-4 w-4 shrink-0 text-mist transition-transform duration-200 group-hover:translate-x-1 group-hover:text-ink" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <path d="m9 5 7 7-7 7" />
                  </svg>
                </a>
              ))}
          </div>

          <div className="mt-14 grid gap-6 rounded-[24px] bg-ink p-8 text-ivory sm:grid-cols-[1.4fr_1fr] sm:items-center sm:p-10">
            <div>
              <h3 className="head text-[1.7rem] sm:text-[2.1rem]">4,161 scenes. One honest record.</h3>
              <p className="font-serif-body mt-3 max-w-lg text-[1.05rem] leading-relaxed text-ivory/65">
                Including the configurations the evidence rejected — because a result you cannot falsify is not a result.
              </p>
            </div>
            <a
              href="#/evaluation"
              className="inline-flex items-center justify-center gap-2 rounded-full bg-clay px-6 py-3.5 text-[0.95rem] font-semibold text-ivory transition-colors hover:bg-book"
            >
              Open the evaluation
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}

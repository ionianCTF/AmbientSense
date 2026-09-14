import { routes } from "../router";

export function Footer() {
  return (
    <footer className="border-t border-ivory/10 bg-[#1a1917] px-5 py-16 text-ivory sm:px-8">
      <div className="mx-auto grid w-full max-w-6xl gap-12 md:grid-cols-[1.4fr_1fr_1fr]">
        <div>
          <div className="flex items-center gap-2.5">
            <svg viewBox="0 0 24 24" className="h-[22px] w-[22px] text-clay" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
              <circle cx="12" cy="12" r="1.7" fill="currentColor" stroke="none" />
              <path d="M8.4 15.6a5.1 5.1 0 0 1 0-7.2M15.6 8.4a5.1 5.1 0 0 1 0 7.2M5.6 18.4a9 9 0 0 1 0-12.8M18.4 5.6a9 9 0 0 1 0 12.8" />
            </svg>
            <span className="head text-[1.05rem]">Ambient Sense</span>
          </div>
          <p className="font-serif-body mt-4 max-w-sm text-[0.98rem] leading-relaxed text-ivory/55">
            Multi-band passive RF sensing on commodity smartphones with dBm-based ranging, scan-epoch change detection, two-node
            transit counting, mesh-accrued accuracy and gamified community reporting.
          </p>
          <div className="mt-6 flex flex-wrap gap-2">
            {["Device-free", "No camera · no mic", "On-device only"].map((t) => (
              <span key={t} className="rounded-full border border-ivory/12 px-3 py-1.5 text-[0.75rem] text-ivory/50">
                {t}
              </span>
            ))}
          </div>
        </div>
        <div className="text-[0.9rem]">
          <div className="eyebrow text-ivory/35">Report</div>
          <ul className="mt-4 space-y-2.5 text-ivory/55">
            {routes
              .filter((r) => r.path !== "/")
              .map((r) => (
                <li key={r.path}>
                  <a href={`#${r.path}`} className="transition-colors hover:text-clay">
                    {r.label}
                  </a>
                </li>
              ))}
          </ul>
        </div>
        <div className="text-[0.9rem]">
          <div className="eyebrow text-ivory/35">Artefacts</div>
          <ul className="mt-4 space-y-2.5 text-ivory/55">
            <li className="font-mono text-[0.84rem]">app/ — deployed Android app</li>
            <li className="font-mono text-[0.84rem]">experiments/ — Monte-Carlo harness</li>
            <li className="font-mono text-[0.84rem]">reports/FULL_EVALUATION_REPORT.pdf</li>
          </ul>
        </div>
      </div>
      <div className="mx-auto mt-14 flex w-full max-w-6xl flex-wrap items-center justify-between gap-3 border-t border-ivory/10 pt-6 text-[0.82rem] text-ivory/35">
        <span>System research report · figures taken from measured evaluation artefacts.</span>
        <span className="num">Classifier weights v1.1 · on-device, Kotlin</span>
      </div>
    </footer>
  );
}

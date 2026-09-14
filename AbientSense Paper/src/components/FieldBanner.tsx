function Arc({ r, color, width = 1.4 }: { r: number; color: string; width?: number }) {
  return <circle cx={190} cy={210} r={r} fill="none" stroke={color} strokeWidth={width} />;
}

function Silhouette({ x, label }: { x: number; label: string }) {
  return (
    <g>
      {/* shadow wedge — the region the entity removes from the field */}
      <path d={`M${x} 210 L1200 ${210 - 190} L1200 ${210 + 190} Z`} fill="#f0eee6" opacity="0.9" />
      {/* figure */}
      <g fill="#141413">
        <circle cx={x} cy={148} r={17} />
        <rect x={x - 23} y={172} width={46} height={126} rx={22} />
      </g>
      <line x1={x + 26} y1={188} x2={x + 86} y2={120} stroke="#8f8e86" strokeWidth="1" />
      <text x={x + 92} y={118} fontSize="14" fill="#6b6a61" fontFamily="JetBrains Mono, monospace">
        {label}
      </text>
    </g>
  );
}

function Vehicle({ x, label }: { x: number; label: string }) {
  return (
    <g>
      <path d={`M${x} 210 L1200 ${210 - 120} L1200 ${210 + 120} Z`} fill="#f0eee6" opacity="0.9" />
      <g fill="#141413">
        <path d={`M${x - 62} 232 h124 a10 10 0 0 0 0 -66 h-92 l-32 30 z`} />
        <circle cx={x - 34} cy={236} r={13} />
        <circle cx={x + 42} cy={236} r={13} />
      </g>
      <line x1={x + 56} y1={166} x2={x + 108} y2={116} stroke="#8f8e86" strokeWidth="1" />
      <text x={x + 114} y={114} fontSize="14" fill="#6b6a61" fontFamily="JetBrains Mono, monospace">
        {label}
      </text>
    </g>
  );
}

export function FieldBanner() {
  return (
    <section className="bg-ivory px-5 pb-24 pt-2 sm:px-8 md:pb-28">
      <div className="mx-auto w-full max-w-6xl">
        <figure className="overflow-hidden rounded-[24px] border border-line bg-pampas">
          <svg viewBox="0 0 1200 420" className="h-[250px] w-full sm:h-[330px] md:h-[400px]" preserveAspectRatio="xMidYMid slice">
            <rect width="1200" height="420" fill="#f0eee6" />

            {/* the observable field */}
            <Arc r={78} color="#c15f3c" />
            <Arc r={150} color="#c15f3c" width={1.2} />
            <Arc r={222} color="#d4a27f" />
            <Arc r={294} color="#629a90" width={1.2} />
            <Arc r={366} color="#3f6b8a" width={1.1} />
            <Arc r={438} color="#8f8e86" width={1} />

            <Silhouette x={520} label="pedestrian · σ = 0.35 m" />
            <Vehicle x={850} label="vehicle · σ = 1.15 m" />

            {/* source */}
            <circle cx={190} cy={210} r={30} fill="#c15f3c" opacity="0.14" />
            <circle cx={190} cy={210} r={9} fill="#c15f3c" />
            <line x1={190} y1={252} x2={190} y2={330} stroke="#8f8e86" strokeWidth="1" />
            <text x={128} y={352} fontSize="14" fill="#6b6a61" fontFamily="JetBrains Mono, monospace">
              beacon · AP
            </text>

            <text x={700} y={404} fontSize="14" fill="#8f8e86" fontFamily="JetBrains Mono, monospace">
              A_eff · (1 + d/10)^−0.6
            </text>
          </svg>
          <figcaption className="flex flex-wrap items-baseline justify-between gap-3 border-t border-line px-6 py-4 text-[0.82rem] leading-relaxed text-mist">
            <span>
              An occlusion is a Gaussian-profiled attenuation: 5–12 dB and jittery for a pedestrian, 15–26 dB and smooth for a
              vehicle, with amplitude decaying through Fresnel-zone occupancy.
            </span>
            <span className="num shrink-0">fig. 0 — the observable</span>
          </figcaption>
        </figure>
      </div>
    </section>
  );
}

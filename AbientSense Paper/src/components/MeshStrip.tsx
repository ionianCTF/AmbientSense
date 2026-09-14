import { Section } from "./ui";

const results = [
  { k: "100 %", label: "fused two-node detection", note: "at 3–10 m separations", tone: "#629a90" },
  { k: "0", label: "false mesh confirmations", note: "across 160 empty scenes", tone: "#d4a27f" },
  { k: "94 %", label: "detection at 15 m", note: "the decay is published, not hidden", tone: "#3f6b8a" },
  { k: "20–40 m", label: "useful mesh radius", note: "from 4–12 m single-phone", tone: "#c15f3c" },
];

function MeshGlyph() {
  const nodes = [
    [300, 96],
    [176, 190],
    [424, 190],
  ];
  return (
    <svg viewBox="0 0 600 260" className="h-[220px] w-full sm:h-[260px]">
      {/* single-phone radius */}
      <circle cx={nodes[0][0]} cy={nodes[0][1]} r="52" fill="none" stroke="#6b6a61" strokeWidth="1.2" strokeDasharray="4 4" />
      {/* mesh radius */}
      <circle cx={nodes[0][0]} cy={nodes[0][1]} r="112" fill="none" stroke="#c15f3c" strokeWidth="1.3" strokeDasharray="6 5" opacity="0.7" />
      {/* rays */}
      {nodes.slice(1).map((n, i) => (
        <line key={i} x1={nodes[0][0]} y1={nodes[0][1]} x2={n[0]} y2={n[1]} stroke="#57564f" strokeWidth="1.2" />
      ))}
      {nodes.map(([x, y], i) => (
        <g key={i}>
          <circle cx={x} cy={y} r="16" fill="#232220" stroke={i === 0 ? "#faf9f5" : "#629a90"} strokeWidth="1.3" />
          <circle cx={x} cy={y} r="4.5" fill={i === 0 ? "#faf9f5" : "#629a90"} />
        </g>
      ))}
      {/* a crossing seen from three angles */}
      <circle cx="300" cy="212" r="6" fill="#d4a27f" />
      <line x1="300" y1="212" x2="196" y2="196" stroke="#d4a27f" strokeWidth="1" strokeDasharray="3 3" opacity="0.6" />
      <line x1="300" y1="212" x2="404" y2="196" stroke="#d4a27f" strokeWidth="1" strokeDasharray="3 3" opacity="0.6" />
      <text x="300" y="244" fontSize="12" fill="#8f8e86" fontFamily="JetBrains Mono, monospace" textAnchor="middle">
        one crossing · three rays · agreement
      </text>
      <text x="176" y="40" fontSize="12" fill="#6b6a61" fontFamily="JetBrains Mono, monospace" textAnchor="middle">
        single 4–12 m
      </text>
      <text x="440" y="80" fontSize="12" fill="#c15f3c" fontFamily="JetBrains Mono, monospace">
        mesh 20–40 m
      </text>
    </svg>
  );
}

export function MeshStrip() {
  return (
    <Section tone="ink" className="py-24 md:py-28">
      <div className="grid gap-14 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
        <div>
          <div className="eyebrow flex items-center gap-3 text-kraft">
            <span className="inline-block h-[7px] w-[7px] rounded-full bg-kraft" />
            Mesh &amp; transit results
          </div>
          <h2 className="head mt-5 text-[2rem] text-ivory sm:text-[2.5rem] md:text-[2.9rem]">
            Two phones make a barrier.
            <br />
            Five make a verdict.
          </h2>
          <p className="font-serif-body mt-6 max-w-xl text-[1.1rem] leading-[1.65] text-ivory/65">
            A single handset is one measurement ray, so it is limited by the geometry it happens to be held at. Federating
            2–5 Ambient Sense instances adds rays — and rays that disagree with the majority are down-weighted before a count is
            published. Accuracy accrues to the mesh, and the radius extends with it.
          </p>
          <div className="mt-10 grid gap-x-10 gap-y-8 sm:grid-cols-2">
            {results.map((r) => (
              <div key={r.k} className="border-t border-ivory/15 pt-5">
                <div className="head num text-[2.1rem]" style={{ color: r.tone }}>
                  {r.k}
                </div>
                <p className="mt-2 text-[0.95rem] text-ivory/85">{r.label}</p>
                <p className="mt-1 text-[0.82rem] text-ivory/45">{r.note}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-[24px] border border-ivory/12 bg-ivory/[0.03] p-4 sm:p-6">
          <MeshGlyph />
          <div className="mt-2 border-t border-ivory/10 pt-4">
            <p className="text-[0.9rem] leading-relaxed text-ivory/55">
              The same coherence primitive drives the roadside counter: two nodes across a road over Classic Bluetooth RFCOMM and
              BLE L2CAP, with link presence <span className="font-mono text-ivory/80">∧</span> link loss fused so that a drop and an
              appearance are both evidence.
            </p>
          </div>
        </div>
      </div>
    </Section>
  );
}

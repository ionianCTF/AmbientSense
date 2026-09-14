import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { cn } from "../../utils/cn";

export type TabId = "sense" | "scans" | "mesh" | "transit" | "club" | "settings";

/* ------------------------------- icons ------------------------------- */

const S = { fill: "none", stroke: "currentColor", strokeWidth: 1.7, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };

export function IconSense({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={cn("h-[19px] w-[19px]", className)} {...S}>
      <circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none" />
      <path d="M8.6 15.4a4.8 4.8 0 0 1 0-6.8M15.4 8.6a4.8 4.8 0 0 1 0 6.8" />
      <path d="M5.9 18.2a8.6 8.6 0 0 1 0-12.4M18.1 5.8a8.6 8.6 0 0 1 0 12.4" />
    </svg>
  );
}
export function IconScans({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={cn("h-[19px] w-[19px]", className)} {...S}>
      <path d="M4.2 9.4A8.1 8.1 0 1 1 3.9 13" />
      <path d="M3.6 5.4v4.2h4.2" />
      <path d="M12 8v4.4l3 1.8" />
    </svg>
  );
}
export function IconMesh({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={cn("h-[19px] w-[19px]", className)} {...S}>
      <circle cx="12" cy="5.4" r="2.1" />
      <circle cx="5.2" cy="17.4" r="2.1" />
      <circle cx="18.8" cy="17.4" r="2.1" />
      <path d="M10.9 7.3 6.4 15.6M13.1 7.3l4.5 8.3M7.3 17.4h9.4" />
    </svg>
  );
}
export function IconTransit({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={cn("h-[19px] w-[19px]", className)} {...S}>
      <path d="M4 9h13l-2.6-2.6M20 15H7l2.6 2.6" />
    </svg>
  );
}
export function IconClub({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={cn("h-[19px] w-[19px]", className)} {...S}>
      <path d="M8 4h8v4.6a4 4 0 0 1-8 0z" />
      <path d="M8 5.4H5.4v1.4A3 3 0 0 0 8 9.7M16 5.4h2.6v1.4a3 3 0 0 1-2.6 2.9" />
      <path d="M12 12.6V16M9 19.6h6" />
    </svg>
  );
}
export function IconSettings({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={cn("h-[19px] w-[19px]", className)} {...S}>
      <path d="M4 8h10M18 8h2M4 16h4M12 16h8" />
      <circle cx="16" cy="8" r="2" />
      <circle cx="10" cy="16" r="2" />
    </svg>
  );
}
function Chevron() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5 shrink-0 text-mist" {...S}>
      <path d="m9 5 7 7-7 7" />
    </svg>
  );
}

/* ------------------------------ phone frame ------------------------------ */

const tabs: { id: TabId; label: string; Icon: (p: { className?: string }) => ReactNode }[] = [
  { id: "sense", label: "Sense", Icon: IconSense },
  { id: "scans", label: "Scans", Icon: IconScans },
  { id: "mesh", label: "Mesh", Icon: IconMesh },
  { id: "transit", label: "Transit", Icon: IconTransit },
  { id: "club", label: "Club", Icon: IconClub },
  { id: "settings", label: "Settings", Icon: IconSettings },
];

export function PhoneFrame({
  tab,
  time = "19:55",
  children,
  className,
}: {
  tab: TabId;
  time?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("relative w-[292px] shrink-0 sm:w-[330px] lg:w-[352px]", className)}>
      <div className="rounded-[44px] bg-[#232220] p-[9px] phone-shadow">
        <div className="relative overflow-hidden rounded-[36px] bg-[#f0eee6]">
          {/* status bar */}
          <div className="flex items-center justify-between px-6 pt-3 text-[10.5px] font-semibold text-ink/70">
            <span className="num">{time}</span>
            <div className="flex items-center gap-1.5 text-ink/55">
              <svg viewBox="0 0 24 24" className="h-[11px] w-[11px]" {...S} strokeWidth={2}>
                <path d="M3 9.5a13 13 0 0 1 18 0M6.6 13a8 8 0 0 1 10.8 0M10.2 16.4a3.4 3.4 0 0 1 3.6 0" />
              </svg>
              <svg viewBox="0 0 24 24" className="h-[11px] w-[11px]" {...S} strokeWidth={2}>
                <path d="M4 15h3v5H4zM10 11h3v9h-3zM16 7h3v13h-3z" />
              </svg>
              <span className="num text-[10px]">52%</span>
              <svg viewBox="0 0 24 24" className="h-[12px] w-[12px]" {...S} strokeWidth={1.8}>
                <rect x="3" y="7" width="15" height="10" rx="2.6" />
                <path d="M20.4 11v2" />
              </svg>
            </div>
          </div>

          {/* app content */}
          <div className="hide-scrollbar h-[618px] overflow-y-auto px-4 pb-2 pt-4">{children}</div>

          {/* bottom nav */}
          <div className="border-t border-black/5 bg-[#f0eee6] px-2 pb-1.5 pt-2">
            <div className="flex items-start justify-between">
              {tabs.map(({ id, label, Icon }) => {
                const active = id === tab;
                return (
                  <div key={id} className="flex w-[15%] flex-col items-center gap-1">
                    <div
                      className={cn(
                        "flex h-[30px] w-[46px] items-center justify-center rounded-full transition-colors",
                        active ? "bg-sand text-clay" : "text-ink/55",
                      )}
                    >
                      <Icon />
                    </div>
                    <span className={cn("text-[9.5px] tracking-tight", active ? "font-semibold text-clay" : "text-ink/55")}>
                      {label}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* system nav */}
          <div className="flex h-[26px] items-center justify-center gap-14 bg-[#8b8a86]">
            <svg viewBox=" 0 0 24 24" className="h-3 w-3 text-white/85" {...S} strokeWidth={2}>
              <rect x="4" y="6" width="4" height="12" rx="1" />
              <rect x="10" y="6" width="4" height="12" rx="1" />
              <rect x="16" y="6" width="4" height="12" rx="1" />
            </svg>
            <svg viewBox="0 0 24 24" className="h-3 w-3 text-white/85" {...S} strokeWidth={2}>
              <circle cx="12" cy="12" r="7" />
            </svg>
            <svg viewBox="0 0 24 24" className="h-3 w-3 text-white/85" {...S} strokeWidth={2}>
              <path d="m14 6-6 6 6 6" />
            </svg>
          </div>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------- atoms -------------------------------- */

export function ScreenTitle({ title, sub }: { title: string; sub?: string }) {
  return (
    <div className="px-1 pb-3.5">
      <h3 className="head text-[23px] leading-tight text-ink">{title}</h3>
      {sub ? <p className="mt-1.5 text-[12.5px] leading-[1.35] text-muted">{sub}</p> : null}
    </div>
  );
}

export function PhoneCard({
  title,
  sub,
  children,
  className,
}: {
  title?: string;
  sub?: string;
  children?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("rounded-[20px] border border-black/[0.07] bg-white p-4 shadow-[0_1px_2px_rgba(20,20,19,0.03)]", className)}>
      {title ? <div className="head text-[17.5px] text-ink">{title}</div> : null}
      {sub ? <div className="mt-0.5 text-[12px] leading-[1.35] text-muted">{sub}</div> : null}
      {children}
    </div>
  );
}

export function KeyRow({ k, v, accent }: { k: string; v: string; accent?: string }) {
  return (
    <div className="mt-2.5 flex items-baseline justify-between gap-3 text-[12.5px]">
      <span className="text-muted">{k}</span>
      <span className="num text-right font-medium" style={accent ? { color: accent } : undefined}>
        {v}
      </span>
    </div>
  );
}

export function Bar({ value, color, height = 6 }: { value: number; color: string; height?: number }) {
  return (
    <div className="w-full overflow-hidden rounded-full bg-pampas-deep" style={{ height }}>
      <div
        className="h-full rounded-full transition-[width] duration-700 ease-out"
        style={{ width: `${Math.max(2, Math.min(100, value))}%`, background: color }}
      />
    </div>
  );
}

export function FeatureRow({ label, value, pct, color }: { label: string; value: string; pct: number; color: string }) {
  return (
    <div className="mt-3">
      <div className="mb-1.5 flex items-baseline justify-between">
        <span className="text-[12.5px] text-ink/75">{label}</span>
        <span className="num text-[13px] font-semibold" style={{ color }}>
          {value}
        </span>
      </div>
      <Bar value={pct} color={color} />
    </div>
  );
}

export function HistoryRow({
  time,
  place,
  dur,
  detail,
  changed,
  last,
}: {
  time: string;
  place: string;
  dur: string;
  detail: string;
  changed?: boolean;
  last?: boolean;
}) {
  return (
    <div className={cn("flex items-center gap-2.5 py-3", !last && "border-b border-black/[0.06]")}>
      <span className={cn("h-[9px] w-[9px] shrink-0 rounded-full", changed ? "bg-clay" : "bg-olive")} />
      <div className="min-w-0 flex-1">
        <div className="num text-[12.5px] text-ink">
          {time} · {place} · {dur}
        </div>
        <div className={cn("num text-[12px]", changed ? "text-clay" : "text-muted")}>{detail}</div>
      </div>
      <Chevron />
    </div>
  );
}

/* --------------------------- live envelope chart --------------------------- */

export function EnvelopeChart() {
  const [series, setSeries] = useState<number[]>(() => Array.from({ length: 90 }, () => 4 + Math.random() * 0.6));
  const dipRef = useRef({ active: false, until: 0, depth: 0 });

  useEffect(() => {
    const id = setInterval(() => {
      setSeries((prev) => {
        const next = prev.slice(1);
        const d = dipRef.current;
        if (!d.active && Math.random() < 0.045) {
          d.active = true;
          d.until = 8 + Math.floor(Math.random() * 14);
          d.depth = 5 + Math.random() * 14;
        }
        let v = 4 + Math.random() * 0.7;
        if (d.active) {
          const k = d.until;
          v = 4 + Math.random() * 0.7 - d.depth * Math.sin((Math.PI * (14 - k)) / 14) ** 2;
          d.until -= 1;
          if (d.until <= 0) d.active = false;
        }
        next.push(v);
        return next;
      });
    }, 260);
    return () => clearInterval(id);
  }, []);

  const W = 300;
  const H = 96;
  const min = -14;
  const pts = series.map((v, i) => {
    const x = (i / (series.length - 1)) * W;
    const y = ((Math.max(min, v) - min) / (4 - min)) * H;
    return [x, y] as const;
  });
  const line = pts.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
  const area = `${line} L${W},${H} L0,${H} Z`;
  const markers = pts.filter((_, i) => i > 0 && series[i - 1] - series[i] > 4.5);

  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="mt-2 h-[96px] w-full" preserveAspectRatio="none">
      <defs>
        <linearGradient id="envfill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#e2b4a2" stopOpacity="0.55" />
          <stop offset="100%" stopColor="#f6e3da" stopOpacity="0.15" />
        </linearGradient>
      </defs>
      <path d={area} fill="url(#envfill)" />
      <path d={line} fill="none" stroke="#c15f3c" strokeWidth="1.4" vectorEffect="non-scaling-stroke" />
      {markers.map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r="2.2" fill="#141413" />
      ))}
    </svg>
  );
}

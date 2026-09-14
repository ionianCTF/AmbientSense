import { useEffect, useState, type ReactNode } from "react";
import { routes, useRoute, type Path } from "../router";
import { cn } from "../utils/cn";

const S = { fill: "none", stroke: "currentColor", strokeWidth: 1.6, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };

function Icon({ name, className }: { name: string; className?: string }) {
  const shapes: Record<string, ReactNode> = {
    home: (
      <>
        <path d="M4 10.5 12 4l8 6.5V19a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19z" />
        <path d="M9.8 20.5v-6h4.4v6" />
      </>
    ),
    grid: (
      <>
        <rect x="3.5" y="3.5" width="7" height="7" rx="2" />
        <rect x="13.5" y="3.5" width="7" height="7" rx="2" />
        <rect x="3.5" y="13.5" width="7" height="7" rx="2" />
        <rect x="13.5" y="13.5" width="7" height="7" rx="2" />
      </>
    ),
    flow: (
      <>
        <rect x="3" y="4" width="7" height="5" rx="1.6" />
        <rect x="14" y="15" width="7" height="5" rx="1.6" />
        <path d="M6.5 9v4.5a2 2 0 0 0 2 2H14" />
        <path d="m12.2 13.8 2 1.7-2 1.7" />
      </>
    ),
    phone: (
      <>
        <rect x="6.5" y="2.5" width="11" height="19" rx="3" />
        <path d="M10.5 5.5h3" />
        <circle cx="12" cy="18.2" r="0.9" fill="currentColor" stroke="none" />
      </>
    ),
    ruler: (
      <>
        <path d="M3.6 14.6 14.6 3.6a2 2 0 0 1 2.8 0l3 3a2 2 0 0 1 0 2.8l-11 11a2 2 0 0 1-2.8 0l-3-3a2 2 0 0 1 0-2.8Z" />
        <path d="M8 8.4l1.6 1.6M11.2 5.2l1.6 1.6M5 11.6l1.6 1.6" />
      </>
    ),
    chart: (
      <>
        <path d="M4 20V10M10 20V4M16 20v-7M22 20H2.4" />
      </>
    ),
    trophy: (
      <>
        <path d="M8 4h8v4.6a4 4 0 0 1-8 0z" />
        <path d="M8 5.4H5.4v1.4A3 3 0 0 0 8 9.7M16 5.4h2.6v1.4a3 3 0 0 1-2.6 2.9" />
        <path d="M12 12.6V16M9 19.6h6" />
      </>
    ),
    flag: (
      <>
        <path d="M5.5 21V3.8M5.5 4.4h11.2l-1.9 3.9 1.9 3.9H5.5" />
      </>
    ),
  };
  return (
    <svg viewBox="0 0 24 24" className={cn("h-[21px] w-[21px]", className)} {...S}>
      {shapes[name]}
    </svg>
  );
}

function Logo() {
  return (
    <svg viewBox="0 0 24 24" className="h-[22px] w-[22px] text-clay" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
      <circle cx="12" cy="12" r="1.7" fill="currentColor" stroke="none" />
      <path d="M8.4 15.6a5.1 5.1 0 0 1 0-7.2M15.6 8.4a5.1 5.1 0 0 1 0 7.2M5.6 18.4a9 9 0 0 1 0-12.8M18.4 5.6a9 9 0 0 1 0 12.8" />
    </svg>
  );
}

const desktop = routes.filter((r) => r.path !== "/");

export function Nav() {
  const path = useRoute();
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => setOpen(false), [path]);

  useEffect(() => {
    document.body.style.overflow = open ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <>
      <header
        className={cn(
          "fixed inset-x-0 top-0 z-50 border-b transition-colors duration-300",
          scrolled || open ? "border-ivory/10 bg-ink/92 backdrop-blur-md" : "border-transparent bg-ink",
        )}
      >
        <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-5 sm:px-8">
          <a href="#/" className="flex items-center gap-2.5 text-ivory">
            <Logo />
            <span className="head text-[1.05rem]">Ambient Sense</span>
          </a>

          <nav className="hidden items-center gap-5 lg:flex xl:gap-6">
            {desktop.map((l, i) => (
              <a
                key={l.path}
                href={`#${l.path}`}
                className={cn(
                  "text-[0.88rem] font-medium transition-colors",
                  i === desktop.length - 1 && "hidden xl:inline",
                  path === l.path ? "text-ivory" : "text-ivory/60 hover:text-ivory",
                )}
              >
                {l.short}
              </a>
            ))}
          </nav>

          <div className="flex items-center gap-2.5">
            <a
              href="#/evaluation"
              className={cn(
                "hidden rounded-full px-4.5 py-2 text-[0.85rem] font-semibold tracking-tight transition-colors sm:inline-flex",
                path === "/evaluation" ? "bg-clay text-ivory hover:bg-book" : "bg-ivory text-ink hover:bg-sand",
              )}
            >
              Full evaluation
            </a>
            <button
              onClick={() => setOpen(true)}
              aria-label="Open menu"
              className="flex h-10 w-10 items-center justify-center rounded-full border border-ivory/15 text-ivory transition-colors hover:border-ivory/40 hover:bg-ivory/5 lg:hidden"
            >
              <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" {...S}>
                <path d="M3.5 7.5h17M3.5 12.5h17M3.5 17.5h11" />
              </svg>
            </button>
          </div>
        </div>
      </header>

      {/* fullscreen dark menu */}
      <div
        className={cn(
          "fixed inset-0 z-[60] flex flex-col bg-ink transition-all duration-300 lg:hidden",
          open ? "pointer-events-auto opacity-100" : "pointer-events-none opacity-0",
        )}
      >
        <div className="flex h-16 shrink-0 items-center justify-between border-b border-ivory/10 px-5">
          <div className="flex items-center gap-2.5 text-ivory">
            <Logo />
            <span className="head text-[1.05rem]">Ambient Sense</span>
          </div>
          <button
            onClick={() => setOpen(false)}
            aria-label="Close menu"
            className="flex h-10 w-10 items-center justify-center rounded-full border border-ivory/15 text-ivory transition-colors hover:bg-ivory/10"
          >
            <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" {...S}>
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </div>

        <nav className="hide-scrollbar flex-1 overflow-y-auto px-5 py-5">
          {routes.map((l, i) => {
            const active = path === l.path;
            return (
              <a
                key={l.path}
                href={`#${l.path}`}
                onClick={() => setOpen(false)}
                className={cn("flex items-center gap-4 border-b border-ivory/10 py-4", open ? "fade-up" : "")}
                style={open ? { animationDelay: `${50 + i * 45}ms` } : undefined}
              >
                <span
                  className={cn(
                    "flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border transition-colors",
                    active ? "border-clay/50 bg-clay/15 text-clay" : "border-ivory/12 bg-ivory/[0.04] text-ivory/70",
                  )}
                >
                  <Icon name={l.icon} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className={cn("head block text-[1.28rem]", active ? "text-clay" : "text-ivory")}>{l.label}</span>
                  <span className="mt-0.5 block text-[0.83rem] text-ivory/45">{l.note}</span>
                </span>
                <span className="num text-[0.8rem] text-ivory/30">{String(i + 1).padStart(2, "0")}</span>
              </a>
            );
          })}
        </nav>

        <div className="shrink-0 border-t border-ivory/10 px-5 py-6">
          <a
            href="#/evaluation"
            onClick={() => setOpen(false)}
            className="flex w-full items-center justify-center rounded-full bg-clay px-6 py-3.5 text-[0.95rem] font-semibold text-ivory transition-colors hover:bg-book"
          >
            Read the full evaluation
          </a>
          <p className="num mt-4 text-center text-[0.75rem] text-ivory/35">Classifier weights v1.1 · on-device, Kotlin</p>
        </div>
      </div>
    </>
  );
}

export type { Path };

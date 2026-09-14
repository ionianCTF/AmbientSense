import type { ReactNode } from "react";
import { routes, type Path } from "../router";
import { cn } from "../utils/cn";

export function PageFrame({ path, children }: { path: Path; children: ReactNode }) {
  const i = routes.findIndex((r) => r.path === path);
  const current = routes[i];
  const prev = i > 0 ? routes[i - 1] : undefined;
  const next = i < routes.length - 1 ? routes[i + 1] : undefined;

  return (
    <div className="bg-ivory">
      {/* breadcrumb strip */}
      <div className="bg-ink px-5 pb-4 pt-24 text-ivory sm:px-8 sm:pt-28">
        <div className="mx-auto w-full max-w-6xl">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[0.78rem] text-ivory/40">
            <a href="#/" className="transition-colors hover:text-ivory">
              Home
            </a>
            <span>/</span>
            {routes.slice(1, i).map((r) => (
              <span key={r.path} className="hidden sm:inline">
                <a href={`#${r.path}`} className="transition-colors hover:text-ivory">
                  {r.short}
                </a>
                <span className="ml-2">/</span>
              </span>
            ))}
            <span className="text-ivory/80">{current.short}</span>
          </div>
          <div className="mt-4 flex items-center gap-3">
            <span className="head text-[1.15rem] text-ivory">{current.label}</span>
            <span className="text-[0.82rem] text-ivory/40">{current.note}</span>
          </div>
        </div>
      </div>

      {children}

      {/* pager */}
      <div className="border-t border-line bg-pampas px-5 py-10 sm:px-8">
        <div className="mx-auto grid w-full max-w-6xl gap-4 sm:grid-cols-2">
          {prev ? (
            <a
              href={`#${prev.path}`}
              className="group rounded-[var(--radius-card)] border border-line bg-ivory p-5 transition-colors hover:border-ink/25"
            >
              <div className="flex items-center gap-2 text-[0.75rem] uppercase tracking-[0.12em] text-mist">
                <svg viewBox="0 0 24 24" className="h-3.5 w-3.5 transition-transform group-hover:-translate-x-1" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="m14 6-6 6 6 6" />
                </svg>
                Previous
              </div>
              <div className="head mt-2 text-[1.2rem]">{prev.label}</div>
              <p className="mt-1 text-[0.85rem] text-muted">{prev.note}</p>
            </a>
          ) : (
            <div className="hidden sm:block" />
          )}
          {next ? (
            <a
              href={`#${next.path}`}
              className={cn(
                "group rounded-[var(--radius-card)] border border-line bg-ivory p-5 text-right transition-colors hover:border-ink/25",
                !prev && "sm:col-start-2",
              )}
            >
              <div className="flex items-center justify-end gap-2 text-[0.75rem] uppercase tracking-[0.12em] text-mist">
                Next
                <svg viewBox="0 0 24 24" className="h-3.5 w-3.5 transition-transform group-hover:translate-x-1" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="m10 6 6 6-6 6" />
                </svg>
              </div>
              <div className="head mt-2 text-[1.2rem]">{next.label}</div>
              <p className="mt-1 text-[0.85rem] text-muted">{next.note}</p>
            </a>
          ) : (
            <a
              href="#/"
              className="group rounded-[var(--radius-card)] border border-line bg-ink p-5 text-right text-ivory transition-colors hover:border-ink"
            >
              <div className="flex items-center justify-end gap-2 text-[0.75rem] uppercase tracking-[0.12em] text-ivory/45">
                Back
                <svg viewBox="0 0 24 24" className="h-3.5 w-3.5 transition-transform group-hover:translate-x-1" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="m10 6 6 6-6 6" />
                </svg>
              </div>
              <div className="head mt-2 text-[1.2rem]">Home</div>
              <p className="mt-1 text-[0.85rem] text-ivory/50">The one-screen summary</p>
            </a>
          )}
        </div>
      </div>
    </div>
  );
}

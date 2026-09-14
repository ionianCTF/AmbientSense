import type { ReactNode } from "react";
import { cn } from "../utils/cn";

export function Section({
  id,
  children,
  className,
  tone = "ivory",
}: {
  id?: string;
  children: ReactNode;
  className?: string;
  tone?: "ivory" | "pampas" | "ink";
}) {
  const tones = {
    ivory: "bg-ivory text-ink",
    pampas: "bg-pampas text-ink",
    ink: "bg-ink text-ivory",
  };
  return (
    <section id={id} className={cn("scroll-mt-16 px-5 py-20 sm:px-8 md:py-28", tones[tone], className)}>
      <div className="mx-auto w-full max-w-6xl">{children}</div>
    </section>
  );
}

export function Eyebrow({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("eyebrow flex items-center gap-3 text-clay", className)}>
      <span className="inline-block h-[7px] w-[7px] rounded-full bg-clay" />
      {children}
    </div>
  );
}

export function SectionHead({
  eyebrow,
  title,
  lede,
  className,
  dark,
}: {
  eyebrow: string;
  title: ReactNode;
  lede?: ReactNode;
  className?: string;
  dark?: boolean;
}) {
  return (
    <div className={cn("max-w-3xl", className)}>
      <Eyebrow className={dark ? "text-kraft" : undefined}>{eyebrow}</Eyebrow>
      <h2 className={cn("head mt-5 text-[2rem] sm:text-[2.6rem] md:text-[3.1rem]", dark && "text-ivory")}>{title}</h2>
      {lede ? (
        <p className={cn("font-serif-body mt-5 text-[1.12rem] leading-[1.65] sm:text-[1.22rem]", dark ? "text-ivory/65" : "text-muted")}>
          {lede}
        </p>
      ) : null}
    </div>
  );
}

export function DarkCard({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "rounded-[var(--radius-card)] border border-ivory/12 bg-ivory/[0.035] p-6 transition-colors hover:border-ivory/25 sm:p-7",
        className,
      )}
    >
      {children}
    </div>
  );
}

export function DarkStat({ k, v, className }: { k: string; v: string; className?: string }) {
  return (
    <div className={cn("border-t border-ivory/15 pt-5", className)}>
      <div className="head num text-[2rem] text-ivory sm:text-[2.4rem]">{k}</div>
      <p className="mt-2 max-w-[26ch] text-[0.92rem] leading-snug text-ivory/50">{v}</p>
    </div>
  );
}

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("rounded-[var(--radius-card)] border border-line bg-ivory p-6 sm:p-7", className)}>{children}</div>
  );
}

export function Pill({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 rounded-full border border-line-strong bg-pampas px-3.5 py-1.5 text-[0.78rem] font-medium tracking-tight",
        className,
      )}
    >
      {children}
    </span>
  );
}

export function Button({
  children,
  href,
  variant = "solid",
  className,
}: {
  children: ReactNode;
  href?: string;
  variant?: "solid" | "outline" | "clay";
  className?: string;
}) {
  const styles = {
    solid: "bg-ink text-ivory hover:bg-ink-soft",
    outline: "border border-line-strong text-ink hover:border-ink hover:bg-pampas",
    clay: "bg-clay text-ivory hover:bg-book",
  };
  return (
    <a
      href={href}
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-full px-6 py-3 text-[0.92rem] font-semibold tracking-tight transition-colors duration-200",
        styles[variant],
        className,
      )}
    >
      {children}
    </a>
  );
}

export function Metric({ k, v, className }: { k: string; v: string; className?: string }) {
  return (
    <div className={cn("border-t border-line pt-5", className)}>
      <div className="head text-[2rem] text-ink sm:text-[2.4rem]">{k}</div>
      <p className="mt-2 max-w-[26ch] text-[0.92rem] leading-snug text-muted">{v}</p>
    </div>
  );
}

export function Rule({ className }: { className?: string }) {
  return <div className={cn("h-px w-full bg-line", className)} />;
}

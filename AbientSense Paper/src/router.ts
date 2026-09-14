import { useEffect, useState } from "react";

export const routes = [
  { path: "/", label: "Home", short: "Home", note: "The one-screen summary", icon: "home" },
  { path: "/introduction", label: "Introduction", short: "Intro", note: "The gap and four contributions", icon: "grid" },
  { path: "/method", label: "Methodology", short: "Method", note: "Physics, DSP, fusion, epochs", icon: "flow" },
  { path: "/screens", label: "App Screens", short: "Screens", note: "Six tabs of the deployed app", icon: "phone" },
  { path: "/ranging", label: "Ranging", short: "Ranging", note: "dBm → metres, or a refusal", icon: "ruler" },
  { path: "/evaluation", label: "Evaluation", short: "Evaluation", note: "Measured results and rejections", icon: "chart" },
  { path: "/community", label: "Community", short: "Community", note: "Gamified reporting and integrity", icon: "trophy" },
  { path: "/conclusion", label: "Conclusion", short: "Conclusion", note: "Uses, limits, references", icon: "flag" },
] as const;

export type Path = (typeof routes)[number]["path"];

function getPath(): string {
  const h = window.location.hash.replace(/^#/, "");
  if (!h || h === "/") return "/";
  const clean = h.split("?")[0].replace(/\/$/, "");
  const found = routes.find((r) => r.path === clean);
  return found ? found.path : "/";
}

export function navigate(to: string) {
  if (getPath() === to) {
    window.scrollTo({ top: 0, behavior: "smooth" });
    return;
  }
  window.location.hash = to;
}

export function useRoute(): string {
  const [path, setPath] = useState<string>(() => getPath());

  useEffect(() => {
    const onHash = () => {
      setPath(getPath());
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  return path;
}

import sharp from "sharp";
import fs from "fs";

const C = {
  ivory: "#faf9f5",
  pampas: "#f0eee6",
  pampasDeep: "#e6e3d8",
  ink: "#141413",
  inkSoft: "#35342f",
  muted: "#6b6a61",
  mist: "#8f8e86",
  line: "#e2e0d6",
  lineStrong: "#cfcdc2",
  clay: "#c15f3c",
  book: "#cc785c",
  kraft: "#d4a27f",
  sand: "#ebdbbc",
  kelp: "#629a90",
  kelpDeep: "#3f6b60",
  olive: "#5f7a45",
  denim: "#3f6b8a",
};

const F_SANS = "DejaVu Sans, Arial, sans-serif";
const F_SERIF = "DejaVu Serif, Georgia, serif";
const F_MONO = "DejaVu Sans Mono, monospace";

function esc(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function box(x, y, w, h, { fill = C.ivory, stroke = C.lineStrong, sw = 1.3, rx = 13, title, sub, titleFill = C.ink, subFill = C.muted, mono, fs = 13.5, fsSub = 11, right, titleWeight = 600 } = {}) {
  let t = "";
  if (title) {
    const cx = right ? x + w - 16 : x + 16;
    const anchor = right ? "end" : "start";
    const ty = y + (sub ? h / 2 - 3 : h / 2 + 5);
    t += `<text x="${cx}" y="${ty}" font-size="${fs}" font-weight="${titleWeight}" fill="${titleFill}" font-family="${F_SANS}" text-anchor="${anchor}">${esc(title)}</text>`;
    if (sub) {
      t += `<text x="${cx}" y="${y + h / 2 + 16}" font-size="${fsSub}" fill="${subFill}" font-family="${mono ? F_MONO : F_SANS}" text-anchor="${anchor}">${esc(sub)}</text>`;
    }
  }
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}"/>${t}`;
}

function arrow(x1, y1, x2, y2, { stroke = C.lineStrong, sw = 1.3, dash } = {}) {
  return `<g stroke="${stroke}" stroke-width="${sw}" ${dash ? `stroke-dasharray="${dash}"` : ""} fill="none"><line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}"/><path d="M${x2 - 4} ${y2 - 5} L${x2} ${y2} L${x2 - 4} ${y2 + 5}"/></g>`;
}

function label(x, y, text, { fill = C.muted, fs = 11.5, anchor = "start", mono, weight, family } = {}) {
  return `<text x="${x}" y="${y}" font-size="${fs}" fill="${fill}" font-family="${family || (mono ? F_MONO : F_SANS)}" text-anchor="${anchor}" ${weight ? `font-weight="${weight}"` : ""}>${esc(text)}</text>`;
}

/* ------------------------- Figure 1: functional decomposition ------------------------- */
function fig1() {
  const W = 980, H = 430;
  let s = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}">`;
  s += `<rect width="${W}" height="${H}" fill="${C.ivory}"/>`;

  const groups = [
    { x: 8, w: 224, title: "BLE scan · beacons", sub: "2.4 GHz", color: C.clay },
    { x: 246, w: 224, title: "Wi-Fi scan · APs", sub: "2.4 GHz", color: C.olive },
    { x: 484, w: 224, title: "Wi-Fi scan · APs", sub: "5 GHz", color: C.denim },
    { x: 722, w: 250, title: "Classic BT + BLE L2CAP", sub: "two-node counter link", color: C.kelp },
  ];
  for (const g of groups) {
    s += box(g.x, 12, g.w, 58, { title: g.title, sub: g.sub, stroke: g.color, fill: "#ffffff" });
    s += arrow(g.x + g.w / 2, 72, g.x + g.w / 2, 104);
  }

  s += box(8, 106, 964, 74, { fill: C.pampas, stroke: C.lineStrong, title: "Per-link DSP — production Kotlin classes", sub: "RingWindow · Stats (median, robust dispersion) · BaselineTracker · DipDetector / PeakDetector", fs: 14 });
  s += label(742, 158, "noise-aware floor · sustained gate", { fill: C.clay, mono: true, fs: 11.5 });

  s += arrow(490, 182, 490, 214);
  const cols = [
    { x: 8, title: "Feature extraction", sub: "depth · duration · texture", sub2: "variance · prominence · coherence", color: C.clay },
    { x: 334, title: "dBm → metres", sub: "log-distance per band", sub2: "gate −75 dBm · f ∈ [0.15, 0.92]", color: C.olive },
    { x: 660, title: "Scan-epoch delta", sub: "store → reset → rescan", sub2: "stable level = median of last 20", color: C.denim },
  ];
  for (const c of cols) {
    s += box(c.x, 218, 312, 82, { title: c.title, stroke: c.color, fill: "#ffffff" });
    s += label(c.x + 16, 266, c.sub, { fill: C.muted });
    s += label(c.x + 16, 284, c.sub2, { fill: C.muted });
    s += arrow(c.x + 156, 300, c.x + 156, 330);
  }

  s += box(8, 334, 964, 82, { fill: C.ink, stroke: C.ink, title: "EntityClassifier v1.1 → entity · confidence · event record", sub: "Counters · range band · field verdict · transit count · mesh confirmation · XP", titleFill: C.ivory, subFill: "#bfbfba", fs: 14 });
  s += label(742, 386, "artefacts: tables + logs", { fill: C.kraft, mono: true, fs: 11.5 });

  s += `</svg>`;
  return s;
}

/* ------------------------- Figure 2: layered architecture ------------------------- */
function fig2() {
  const W = 860, H = 560;
  let s = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}">`;
  s += `<rect width="${W}" height="${H}" fill="${C.ivory}"/>`;

  const layers = [
    { y: 12, h: 96, fill: C.sand, stroke: C.kraft, t: "APPLICATION / GAMIFICATION", items: ["counters · range band · field verdict", "XP · badges · day streak · reliability bonus"] },
    { y: 134, h: 104, fill: "#ffffff", stroke: C.clay, t: "INFERENCE / FUSION", items: ["EntityClassifier v1.1 · mesh agreement", "fused accuracy · reliability bonus"] },
    { y: 264, h: 104, fill: "#ffffff", stroke: C.denim, t: "PER-LINK DSP (Kotlin)", items: ["RingWindow · Stats · BaselineTracker", "DipDetector / PeakDetector → event features"] },
    { y: 394, h: 96, fill: C.pampas, stroke: C.lineStrong, t: "RADIO ABSTRACTION", items: ["BLE scanner · Wi-Fi 2.4 · Wi-Fi 5 · BT stacks"] },
    { y: 516, h: 0, fill: C.ink, stroke: C.ink, t: "UNMODIFIED HARDWARE", items: ["BLE radio · 802.11 dual-band radio · RFCOMM / L2CAP"] },
  ];

  for (const l of layers) {
    s += box(16, l.y, W - 32, l.h, { fill: l.fill, stroke: l.stroke, title: l.t, right: true, fs: 13, titleFill: l.fill === C.ink ? C.ivory : C.ink });
    if (l.items.length) {
      let iy = l.y + (l.h / 2) + 6;
      if (l.h <= 96) iy = l.y + l.h / 2 + (l.items.length === 1 ? 0 : 8);
      for (const it of l.items) {
        s += label(l.x + 16, iy, it, { fill: l.fill === C.ink ? "#d0cfc9" : C.muted });
        iy += 17;
      }
    }
  }

  // arrows between layers
  const midY = [108, 238, 368, 492];
  for (let i = 0; i < midY.length; i++) {
    s += `<g stroke="${C.lineStrong}" stroke-width="1.3" fill="none"><line x1="60" y1="${midY[i]}" x2="60" y2="${midY[i] + (i === midY.length - 1 ? 10 : 22)}"/><path d="M56 ${midY[i] + (i === midY.length - 1 ? 6 : 18)} L60 ${midY[i] + (i === midY.length - 1 ? 10 : 22)} L64 ${midY[i] + (i === midY.length - 1 ? 6 : 18)}"/></g>`;
  }

  // side callout
  s += box(16, 132, 150, 250, { fill: C.pampasDeep, stroke: C.line, title: "", fs: 0 });
  s += label(30, 168, "device-free", { fill: C.kelpDeep, weight: 600, fs: 13 });
  s += label(30, 188, "no camera · no mic", { fill: C.muted, fs: 11.5 });
  s += label(30, 208, "no device on target", { fill: C.muted, fs: 11.5 });
  s += label(30, 240, "data stays on device", { fill: C.muted, fs: 11.5 });

  s += `</svg>`;
  return s;
}

async function run() {
  const out = [
    ["fig1_method.svg", fig1(), 2],
    ["fig2_arch.svg", fig2(), 2],
  ];
  fs.mkdirSync("figures", { recursive: true });
  for (const [name, svg, scale] of out) {
    fs.writeFileSync("figures/" + name, svg);
    await sharp(Buffer.from(svg)).png({ compressionLevel: 9 }).toFile("figures/" + name.replace(".svg", ".png"));
    const meta = await sharp("figures/" + name.replace(".svg", ".png")).metadata();
    console.log(name, "->", meta.width, "x", meta.height);
  }
}
run().catch((e) => {
  console.error(e);
  process.exit(1);
});

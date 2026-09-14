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
  oliveDeep: "#3f552b",
  denim: "#3f6b8a",
};
const F = "DejaVu Sans, Arial, sans-serif";
const F_MONO = "DejaVu Sans Mono, monospace";
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

/* --- phone frame --- */
function phone(content, { active = "sense", time = "19:55" } = {}) {
  const tabs = ["sense", "scans", "mesh", "transit", "club", "settings"];
  const labels = { sense: "Sense", scans: "Scans", mesh: "Mesh", transit: "Transit", club: "Club", settings: "Settings" };
  const tabColor = { sense: C.clay, scans: C.book, mesh: C.kelpDeep, transit: C.denim, club: C.olive, settings: C.muted };
  const W = 330, H = 720;

  let s = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}">`;
  // bezel
  s += `<rect width="${W}" height="${H}" rx="44" fill="#211f1e"/><rect x="11" y="11" width="${W - 22}" height="${H - 22}" rx="33" fill="${C.pampas}"/>`;

  // status bar
  s += `<text x="34" y="40" font-size="11" font-family="${F}" font-weight="600" fill="#57564f">${time}</text>`;
  s += `<g fill="#57564f"><circle cx="${W - 78}" cy="36" r="1"/><circle cx="${W - 72}" cy="36" r="1"/><circle cx="${W - 66}" cy="36" r="1"/><rect x="${W - 56}" y="32" width="10" height="8" rx="2" fill="none" stroke="#57564f" stroke-width="1"/><rect x="${W - 34}" y="32" width="20" height="8" rx="3" fill="none" stroke="#57564f" stroke-width="1"/></g>`;
  s += `<text x="${W - 90}" y="40" font-size="10" font-family="${F_MONO}" fill="#57564f">52%</text>`;

  // content (clipped to the scrollable screen area above the bottom nav)
  s += `<defs><clipPath id="scr"><rect x="20" y="50" width="290" height="584" rx="18"/></clipPath></defs>`;
  s += `<g clip-path="url(#scr)">${content}</g>`;

  // bottom nav
  s += `<rect x="11" y="${H - 22 - 64}" width="${W - 22}" height="64" fill="${C.pampas}"/>`;
  const tabW = (W - 22) / 6;
  tabs.forEach((t, i) => {
    const cx = 11 + tabW * i + tabW / 2;
    const top = H - 22 - 52;
    const activeT = t === active;
    if (activeT) {
      s += `<rect x="${cx - 24}" y="${top}" width="48" height="30" rx="16" fill="${C.sand}"/>`;
    }
    // tiny icon glyphs
    const ic = tabColor[t];
    s += `<g stroke="${activeT ? C.clay : "#7a786f"}" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round">${navIcon(t, cx, top + 15)}</g>`;
    s += `<text x="${cx}" y="${top + 44}" font-size="9" font-family="${F}" text-anchor="middle" fill="${activeT ? C.clay : "#7a786f"}" ${activeT ? 'font-weight="600"' : ""}>${labels[t]}</text>`;
  });

  // system nav
  s += `<rect x="11" y="${H - 22}" width="${W - 22}" height="22" fill="#8b8a86"/>`;
  const sysY = H - 22 + 14;
  s += `<g stroke="#ffffff" stroke-width="1.6" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="${W / 2 - 30}" y="${sysY - 6}" width="11" height="12" rx="2"/><circle cx="${W / 2}" cy="${sysY}" r="6"/><path d="M${W / 2 + 30 - 6} ${sysY - 6} l-6 6 6 6"/></g>`;

  s += `</svg>`;
  return s;
}

function navIcon(name, cx, cy) {
  switch (name) {
    case "sense":
      return `<circle cx="${cx}" cy="${cy}" r="1.4" fill="currentColor"/><path d="M${cx - 7} ${cy + 8} a9 9 0 0 1 0 -16 M${cx + 7} ${cy - 8} a9 9 0 0 1 0 16" stroke="currentColor"/>`;
    case "scans":
      return `<circle cx="${cx}" cy="${cy}" r="7"/><path d="M${cx} ${cy - 7} v7 l5 1"/>`;
    case "mesh":
      return `<circle cx="${cx}" cy="${cy - 5}" r="2"/><circle cx="${cx - 6}" cy="${cy + 5}" r="2"/><circle cx="${cx + 6}" cy="${cy + 5}" r="2"/><path d="M${cx - 1} ${cy - 3} l-4 6 M${cx + 1} ${cy - 3} l4 6 M${cx - 4} ${cy + 5} h8"/>`;
    case "transit":
      return `<path d="M${cx - 8} ${cy} h12 M${cx + 8} ${cy} l-3 -3 M${cx + 8} ${cy} l-3 3"/>`;
    case "club":
      return `<rect x="${cx - 6}" y="${cy - 7}" width="12" height="8" rx="1"/><path d="M${cx} ${cy + 1} v4 M${cx - 6} ${cy + 5} h12"/>`;
    default:
      return `<path d="M${cx - 9} ${cy - 4} h8 M${cx - 9} ${cy} h5 M${cx - 9} ${cy + 4} h7"/><circle cx="${cx + 4}" cy="${cy - 4}" r="2"/><circle cx="${cx + 5}" cy="${cy}" r="2"/><circle cx="${cx + 4}" cy="${cy + 4}" r="2"/>`;
  }
}

/* --- reusable UI atoms (coordinates relative to phone content area) --- */
const PAD = 20;
const CW = 330 - 22; // 308

function text(x, y, t, { fs = 13, fill = C.ink, weight, anchor = "start", mono, family = F } = {}) {
  return `<text x="${x}" y="${y}" font-size="${fs}" fill="${fill}" font-family="${mono ? F_MONO : family}" text-anchor="${anchor}" ${weight ? `font-weight="${weight}"` : ""}>${esc(t)}</text>`;
}

function card(x, y, w, h, { fill = "#ffffff", stroke = "rgba(0,0,0,0.07)", rx = 16 } = {}) {
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" stroke="${stroke}"/>`;
}

function titleBlock(x, y, title, sub) {
  let s = text(x, y, title, { fs: 24, weight: 600 });
  if (sub) s += text(x, y + 20, sub, { fs: 12.5, fill: C.muted });
  return s;
}

function row(x, y, k, v, { vFill = C.ink, accent } = {}) {
  return (
    text(x, y + 11, k, { fs: 12.5, fill: C.muted }) +
    text(x + 190, y + 11, v, { fs: 12.5, fill: accent || vFill, anchor: "end", mono: true, weight: 500 })
  );
}

function pill(x, y, label, { fill = C.pampas, fg = "#57564f" } = {}) {
  const w = 20 + label.length * 6.4;
  return `<rect x="${x}" y="${y}" width="${w}" height="24" rx="12" fill="${fill}"/>` + text(x + 10, y + 16, label, { fs: 11.5, fill: fg, weight: 500 });
}

function bar(x, y, w, pct, color, h = 6) {
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${h / 2}" fill="${C.pampasDeep}"/><rect x="${x}" y="${y}" width="${(w * pct) / 100}" height="${h}" rx="${h / 2}" fill="${color}"/>`;
}

function legendDot(x, y, color) {
  return `<circle cx="${x}" cy="${y}" r="3.4" fill="${color}"/>`;
}

/* ---------------- Fig 3: Sense ---------------- */
function sense() {
  let s = "";
  s += titleBlock(PAD, 74, "Ambient Sense", "Passive RF sensing — people & vehicles leave electromagnetic wakes");
  const top = 100;

  // sensing card
  s += card(PAD, top, CW - PAD * 2, 76);
  s += `<circle cx="${PAD + 16}" cy="${top + 26}" r="5" fill="${C.clay}"/>`;
  s += text(PAD + 30, top + 22, "Sensing the RF field", { fs: 16, weight: 600 });
  s += text(PAD + 30, top + 44, "33 passive links · BLE + Wi-Fi · 20 m radius", { fs: 11.5, fill: C.muted });
  s += text(PAD + 20 + (CW - PAD * 2) - 16, top + 26, "Stop", { fs: 14, fill: C.muted, anchor: "end" });

  // counters
  const cy0 = top + 90;
  const cw = (CW - PAD * 2 - 10) / 2;
  function counter(x, y, label, val, color, tint) {
    return (
      card(x, y, cw, 76, { fill: "#ffffff" }) +
      `<rect x="${x}" y="${y}" width="${cw}" height="76" rx="16" fill="${tint}" opacity="0.55"/>` +
      text(x + 13, y + 21, label, { fs: 9, fill: color, weight: 600 }) +
      text(x + 13, y + 54, val, { fs: 28, fill: color, weight: 600 })
    );
  }
  s += counter(PAD, cy0, "PEOPLE", "67", C.clay, "#f7e2d8");
  s += counter(PAD + cw + 10, cy0, "VEHICLES", "66", C.olive, "#e6ecdb");
  s += counter(PAD, cy0 + 84, "DISTURBANCES", "165", C.mist, "#eae9e2");
  s += counter(PAD + cw + 10, cy0 + 84, "RF LINKS", "33", C.denim, "#dfe7ee");

  // live signal features
  const fy = cy0 + 176;
  s += card(PAD, fy, CW - PAD * 2, 138);
  s += text(PAD + 16, fy + 24, "Live signal features", { fs: 16, weight: 600 });
  const fw = CW - PAD * 2 - 32;
  function feat(ly, label, val, pct, color) {
    return [
      text(PAD + 16, ly, label, { fs: 12, fill: "#57564f" }),
      text(PAD + 16 + fw, ly, val, { fs: 12, fill: color, anchor: "end", weight: 600, mono: true }),
      bar(PAD + 16, ly + 7, fw, pct, color),
    ].join("");
  }
  s += feat(fy + 46, "Moving variance", "11.45 dB²", 34, C.clay);
  s += feat(fy + 84, "Jitter", "1.79 dB", 46, C.denim);
  s += feat(fy + 122, "Classifier confidence", "68%", 68, C.olive);

  // envelope
  const ey = fy + 150;
  s += card(PAD, ey, CW - PAD * 2, 150);
  s += text(PAD + 16, ey + 24, "Attenuation envelope", { fs: 16, weight: 600 });
  s += text(PAD + 16, ey + 42, "Max dip below adaptive baseline · markers = recent crossings", { fs: 10.5, fill: C.muted });
  // chart
  const ch = `M${PAD + 16} ${ey + 66} L${PAD + 60} ${ey + 66} L${PAD + 74} ${ey + 96} L${PAD + 92} ${ey + 96} L${PAD + 106} ${ey + 68} L${PAD + 150} ${ey + 68} L${PAD + 170} ${ey + 66} L${PAD + 210} ${ey + 64} L${PAD + 238} ${ey + 66} L${PAD + 246} ${ey + 118} L${PAD + 270} ${ey + 108} L${PAD + 290} ${ey + 66}`;
  s += `<path d="${ch} L${PAD + 294} ${ey + 140} L${PAD + 16} ${ey + 140} Z" fill="#f6e3da" opacity="0.6"/>`;
  s += `<path d="${ch}" fill="none" stroke="${C.clay}" stroke-width="1.4"/>`;
  s += `<circle cx="${PAD + 246}" cy="${ey + 118}" r="2.2" fill="${C.ink}"/><circle cx="${PAD + 74}" cy="${ey + 96}" r="2.2" fill="${C.ink}"/>`;
  s += `<line x1="${PAD + 16}" y1="${ey + 140}" x2="${PAD + 294}" y2="${ey + 140}" stroke="${C.pampasDeep}"/>`;

  // last crossing (peeks at bottom edge)
  const lc = ey + 162;
  s += card(PAD, lc, CW - PAD * 2, 120);
  s += text(PAD + 16, lc + 26, "Last crossing", { fs: 16, weight: 600 });
  s += row(PAD + 16, lc + 42, "Entity", "pedestrian · 68%", { accent: C.clay });
  s += row(PAD + 16, lc + 68, "Dominant band", "BLE · −58 dBm dip");
  s += row(PAD + 16, lc + 94, "Range band", "f 0.41 → 4.4–8.2 m", { accent: C.denim });

  return s;
}

/* ---------------- Fig 4: Scans ---------------- */
function scans() {
  let s = "";
  const top = 40;
  s += card(PAD, top, CW - PAD * 2, 218);
  s += text(PAD + 16, top + 26, "Latest stored scan", { fs: 17, weight: 600 });
  s += text(PAD + 16, top + 44, "stored, then the engine reset and rescanned", { fs: 11, fill: C.muted });
  s += row(PAD + 16, top + 62, "Window", "19:56:03 – 19:56:18 (15s)");
  s += row(PAD + 16, top + 88, "Place", "My place");
  s += row(PAD + 16, top + 114, "Geo", "40.3075, 21.7906");
  s += row(PAD + 16, top + 140, "Counts (this window)", "p 0 · v 0 · o 1");
  s += row(PAD + 16, top + 166, "Links / changed", "33 · 5 moved ≥3 dB");
  s += text(PAD + 16, top + 194, "5 links moved ≥3 dB (max 14.0 dB) → field CHANGED since previous scan", { fs: 11, fill: C.clay });

  const hy = top + 234;
  s += card(PAD, hy, CW - PAD * 2, 328);
  s += text(PAD + 16, hy + 26, "History", { fs: 17, weight: 600 });
  s += text(PAD + 16, hy + 45, "120 scans stored (cap 120) — tap a row for the per-link dBm table", { fs: 11, fill: C.muted });
  s += text(PAD + 16 + (CW - PAD * 2 - 32), hy + 26, "Clear", { fs: 13, fill: C.mist, anchor: "end" });

  const history = [
    ["19:56:03", "15s", "p 0 · v 0 · o 1 · 33 links · field CHANGED (5 links moved)", true],
    ["19:55:48", "15s", "p 0 · v 0 · o 1 · 33 links · field CHANGED (7 links moved)", true],
    ["19:55:40", "5s", "p 0 · v 0 · o 0 · 33 links · unchanged", false],
    ["19:55:35", "5s", "p 0 · v 0 · o 0 · 33 links · unchanged", false],
    ["19:55:30", "5s", "p 0 · v 0 · o 0 · 33 links · unchanged", false],
  ];
  history.forEach((h, i) => {
    const ry = hy + 64 + i * 50;
    s += legendDot(PAD + 18, ry, h[3] ? C.clay : C.olive);
    s += text(PAD + 32, ry, `${h[0]} · My place · ${h[1]}`, { fs: 11.5, mono: true, weight: 600 });
    s += text(PAD + 32, ry + 16, h[2], { fs: 10.5, fill: h[3] ? C.clay : C.muted, mono: true });
    if (i < history.length - 1) s += `<line x1="${PAD + 16}" y1="${ry + 26}" x2="${PAD + CW - PAD * 2 - 16}" y2="${ry + 26}" stroke="rgba(0,0,0,0.06)"/>`;
    // chevron
    s += `<path d="M${PAD + 20 + (CW - PAD * 2 - 16) - 10} ${ry - 2} l4 4 -4 4" fill="none" stroke="${C.mist}" stroke-width="1.4"/>`;
  });

  return s;
}

/* ---------------- Fig 5: Mesh ---------------- */
function mesh() {
  let s = "";
  s += text(PAD, 88, "Mesh", { fs: 24, weight: 600 });
  s += text(PAD, 108, "Agreement between nearby Ambient Sense instances", { fs: 12.5, fill: C.muted });

  const top = 122;
  const nodes = [
    ["This phone · Pixel 8", "72%", true],
    ["node-2 · Galaxy A54", "69%", false],
    ["node-3 · Xiaomi 12", "64%", false],
  ];
  s += card(PAD, top, CW - PAD * 2, 250);
  s += text(PAD + 16, top + 26, "Nodes in your mesh", { fs: 17, weight: 600 });
  s += text(PAD + 16, top + 45, "2–5 instances confirm each other's crossings", { fs: 11, fill: C.muted });
  nodes.forEach((n, i) => {
    const ry = top + 66 + i * 26;
    s += legendDot(PAD + 18, ry, n[2] ? C.clay : C.kelp);
    s += text(PAD + 34, ry, n[0], { fs: 12, fill: n[2] ? C.ink : "#57564f" });
    s += text(PAD + 16 + (CW - PAD * 2 - 32), ry, n[1], { fs: 12, fill: C.muted, anchor: "end", mono: true, weight: 600 });
  });
  s += `<line x1="${PAD + 16}" y1="${top + 160}" x2="${PAD + (CW - PAD * 2) - 16}" y2="${top + 160}" stroke="rgba(0,0,0,0.06)"/>`;
  s += row(PAD + 16, top + 172, "Fused accuracy", "81 %", { accent: C.olive });
  s += row(PAD + 16, top + 198, "Agreement tolerance", "±1 crossing / ±4 m");
  s += row(PAD + 16, top + 224, "Effective radius", "20–40 m (single: 4–12 m)", { accent: C.denim });

  // radius diagram
  const dy = top + 266;
  s += card(PAD, dy, CW - PAD * 2, 180);
  s += text(PAD + 16, dy + 26, "Radius extension", { fs: 17, weight: 600 });
  s += text(PAD + 16, dy + 45, "Geometric diversity of measurement rays", { fs: 11, fill: C.muted });
  const cx = PAD + (CW - PAD * 2) / 2, cyc = dy + 108;
  s += `<circle cx="${cx}" cy="${cyc}" r="52" fill="none" stroke="${C.mist}" stroke-width="1" stroke-dasharray="4 3"/>`;
  s += `<circle cx="${cx}" cy="${cyc}" r="30" fill="none" stroke="${C.clay}" stroke-width="1.2" stroke-dasharray="4 3" opacity="0.6"/>`;
  s += `<circle cx="${cx}" cy="${cyc}" r="5" fill="${C.ink}"/>`;
  s += text(cx + 30, cyc - 56, "mesh 20–40 m", { fs: 10.5, fill: C.mist });
  s += text(cx + 30, cyc - 20, "single 4–12 m", { fs: 10.5, fill: C.mist });

  // reliability bonus
  const by = dy + 196;
  s += card(PAD, by, CW - PAD * 2, 150);
  s += text(PAD + 16, by + 26, "Reliability bonus", { fs: 17, weight: 600 });
  s += text(PAD + 16, by + 46, "Distance from the mesh mean", { fs: 11, fill: C.muted });
  s += text(PAD + 16, by + 70, "Devices nearest the fused estimate earn a ×1.25", { fs: 12, fill: "#57564f" });
  s += text(PAD + 16, by + 90, "multiplier; outliers never set the mesh value.", { fs: 12, fill: "#57564f" });
  s += pill(PAD + 16, by + 104, "you ×1.25", { fill: C.pampas });
  s += pill(PAD + 96, by + 104, "node-2 ×1.0", { fill: C.pampas });
  s += pill(PAD + 184, by + 104, "node-3 ×0.9", { fill: C.pampas });

  return s;
}

/* ---------------- Fig 6: Transit ---------------- */
function transit() {
  let s = "";
  s += text(PAD, 74, "Transit", { fs: 24, weight: 600 });
  s += text(PAD, 94, "Two-node roadside counter · barrier coverage on commodity Bluetooth", { fs: 12.5, fill: C.muted });

  const top = 108;
  s += card(PAD, top, CW - PAD * 2, 150);
  s += text(PAD + 16, top + 24, "Barrier link", { fs: 16, weight: 600 });
  s += text(PAD + 16, top + 43, "Classic BT RFCOMM + BLE L2CAP", { fs: 11, fill: C.muted });
  const bx = PAD + 20, byc = top + 88;
  s += `<line x1="${bx + 16}" y1="${byc - 34}" x2="${bx + 16}" y2="${byc + 44}" stroke="${C.clay}" stroke-width="2"/>`;
  s += `<line x1="${bx + 200}" y1="${byc - 34}" x2="${bx + 200}" y2="${byc + 44}" stroke="${C.clay}" stroke-width="2"/>`;
  s += `<circle cx="${bx + 16}" cy="${byc - 34}" r="4" fill="${C.clay}"/><circle cx="${bx + 200}" cy="${byc - 34}" r="4" fill="${C.clay}"/>`;
  s += text(bx + 4, byc - 46, "node A", { fs: 10, fill: C.muted });
  s += text(bx + 182, byc - 46, "node B", { fs: 10, fill: C.muted });
  s += `<ellipse cx="${bx + 108}" cy="${byc}" rx="84" ry="11" fill="${C.olive}" opacity="0.18"/>`;
  s += `<rect x="${bx + 70}" y="${byc - 14}" width="76" height="22" rx="6" fill="${C.olive}"/>`;
  s += `<circle cx="${bx + 86}" cy="${byc + 8}" r="5" fill="${C.olive}"/><circle cx="${bx + 130}" cy="${byc + 8}" r="5" fill="${C.olive}"/>`;
  s += text(bx + 78, byc + 62, "barrier · 8 m separation", { fs: 10, fill: C.muted });

  // counters
  const cy0 = top + 166;
  const cw = (CW - PAD * 2 - 10) / 2;
  function counter(x, y, label, val, color, tint) {
    return (
      card(x, y, cw, 76, { fill: "#ffffff" }) +
      `<rect x="${x}" y="${y}" width="${cw}" height="76" rx="16" fill="${tint}" opacity="0.55"/>` +
      text(x + 13, y + 21, label, { fs: 9, fill: color, weight: 600 }) +
      text(x + 13, y + 54, val, { fs: 28, fill: color, weight: 600 })
    );
  }
  s += counter(PAD, cy0, "PEDESTRIANS", "12", C.clay, "#f7e2d8");
  s += counter(PAD + cw + 10, cy0, "VEHICLES", "8", C.olive, "#e6ecdb");

  // link state
  const ly = cy0 + 86;
  s += card(PAD, ly, CW - PAD * 2, 128);
  s += text(PAD + 16, ly + 24, "Link state", { fs: 16, weight: 600 });
  s += row(PAD + 16, ly + 42, "RFCOMM (Classic)", "connected · 8.1 pps", { accent: C.olive });
  s += row(PAD + 16, ly + 66, "BLE L2CAP", "connected · 4.0 pps", { accent: C.olive });
  s += row(PAD + 16, ly + 90, "Separation", "8 m");
  s += row(PAD + 16, ly + 114, "Hybrid classifier", "presence ∧ loss");

  // detection by separation
  const dy = ly + 142;
  s += card(PAD, dy, CW - PAD * 2, 152);
  s += text(PAD + 16, dy + 24, "Detection by separation", { fs: 16, weight: 600 });
  s += text(PAD + 16, dy + 43, "Fused two-node detection", { fs: 11, fill: C.muted });
  const det = [
    ["3 m", 100, C.olive],
    ["6 m", 100, C.olive],
    ["10 m", 100, C.olive],
    ["15 m", 94, C.denim],
    ["25 m", 61, "#b0402a"],
  ];
  det.forEach((d, i) => {
    const ry = dy + 62 + i * 18;
    s += text(PAD + 16, ry, d[0], { fs: 11, fill: C.muted, mono: true });
    s += bar(PAD + 58, ry - 6, 150, d[1], d[2]);
    s += text(PAD + 222, ry, d[1] + "%", { fs: 11.5, fill: d[2], anchor: "end", mono: true, weight: 600 });
  });

  return s;
}

/* ---------------- Fig 7: Club ---------------- */
function club() {
  let s = "";
  s += card(PAD, 36, CW - PAD * 2, 200);
  s += text(PAD + 16, 60, "Mesh contribution", { fs: 16, weight: 600 });
  s += text(PAD + 16, 78, "What the fusion network reports through you", { fs: 11, fill: C.muted });
  s += row(PAD + 16, 96, "Nodes in your mesh", "1");
  s += row(PAD + 16, 120, "Fused accuracy", "72%", { accent: C.olive });
  s += row(PAD + 16, 144, "Crowd level", "Calm");
  s += row(PAD + 16, 168, "Estimated accuracy", "72 %", { accent: C.olive });
  s += row(PAD + 16, 192, "Confirmed crossings (1 h)", "0", { accent: C.clay });

  const sy = 250;
  s += card(PAD, sy, CW - PAD * 2, 150);
  s += text(PAD + 16, sy + 24, "Scoring", { fs: 16, weight: 600 });
  s += `<rect x="${PAD + 16}" y="${sy + 36}" width="${CW - PAD * 2 - 32}" height="94" rx="12" fill="${C.pampas}"/>`;
  s += text(PAD + 30, sy + 58, "People +10 XP · Vehicles +14 XP · Disturbances +2 XP.", { fs: 11.5 });
  s += text(PAD + 30, sy + 78, "When a crossing is confirmed by ≥2 mesh nodes", { fs: 11.5 });
  s += text(PAD + 30, sy + 98, "you earn a ×2 fusion bonus +15 XP per", { fs: 11.5 });

  const hy = sy + 164;
  s += card(PAD, hy, CW - PAD * 2, 138);
  s += text(PAD + 16, hy + 24, "Session history", { fs: 16, weight: 600 });
  const sessions = [
    ["Sep 6, 19:21", "43 · 43", true],
    ["Sep 6, 19:20", "43 · 43", true],
    ["Sep 6, 19:02", "24 · 35", true],
    ["Sep 6, 18:45", "18 · 30", true],
  ];
  sessions.forEach((v, i) => {
    const ry = hy + 46 + i * 22;
    s += text(PAD + 16, ry, v[0], { fs: 11, fill: "#57564f", mono: true });
    s += text(PAD + 16 + (CW - PAD * 2 - 32), ry, v[1], { fs: 11, fill: C.ink, anchor: "end", mono: true, weight: 600 });
    s += text(PAD + 16 + (CW - PAD * 2 - 32) - 70, ry, "· ambient", { fs: 10.5, fill: C.muted, anchor: "end" });
  });

  // badges
  const by = hy + 152;
  s += card(PAD, by, CW - PAD * 2, 90);
  s += text(PAD + 16, by + 22, "Badges", { fs: 16, weight: 600 });
  s += text(PAD + 16, by + 40, "Keeping sensors on is the whole game", { fs: 10.5, fill: C.muted });
  const badges = [["First 10"], ["Night watch"], ["Mesh of 3"], ["50 % fused"]];
  let bx = PAD + 16;
  badges.forEach((b) => {
    const w = 16 + b[0].length * 5.6;
    s += `<rect x="${bx}" y="${by + 50}" width="${w}" height="24" rx="12" fill="rgba(235,221,188,0.7)"/>`;
    s += text(bx + 6, by + 66, b[0], { fs: 10, fill: C.ink, weight: 500 });
    bx += w + 8;
  });

  return s;
}

async function run() {
  const out = [
    ["fig3_sense.svg", sense(), "sense"],
    ["fig4_scans.svg", scans(), "scans"],
    ["fig5_mesh.svg", mesh(), "mesh"],
    ["fig6_transit.svg", transit(), "transit"],
    ["fig7_club.svg", club(), "club"],
  ];
  fs.mkdirSync("figures", { recursive: true });
  for (const [name, content, active] of out) {
    const svg = phone(content, { active });
    fs.writeFileSync("figures/" + name, svg);
    const input = Buffer.from(svg);
    const buf = await sharp(input, { density: 2 * 96 }).png({ compressionLevel: 9 }).toFile("figures/" + name.replace(".svg", ".png"));
    const meta = await sharp("figures/" + name.replace(".svg", ".png")).metadata();
    console.log(name, "->", meta.width, "x", meta.height);
  }
}
run().catch((e) => {
  console.error(e);
  process.exit(1);
});

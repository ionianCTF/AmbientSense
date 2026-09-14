// Minimal inline markup parser for **bold** and *italic*.
export function runify(text, base = {}) {
  const runs = [];
  const tokenRe = /(\*\*.+?\*\*|\*[^*]+?\*)/g;
  let last = 0;
  let m;
  while ((m = tokenRe.exec(text))) {
    if (m.index > last) runs.push({ text: text.slice(last, m.index), ...base });
    const tok = m[0];
    if (tok.startsWith("**")) {
      runs.push({ text: tok.slice(2, -2), ...base, bold: true });
    } else {
      runs.push({ text: tok.slice(1, -1), ...base, italics: true });
    }
    last = m.index + tok.length;
  }
  if (last < text.length) runs.push({ text: text.slice(last), ...base });
  return runs;
}

export function textRuns(text, base = {}) {
  return runify(text, base);
}

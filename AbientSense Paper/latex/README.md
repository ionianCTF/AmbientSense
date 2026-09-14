# Ambient Sense — IEEE Two-Column LaTeX Manuscript

`Ambient_Sense_Paper.tex` is the complete, self-contained LaTeX source of the
paper **"Ambient Sense: Multi-Band Passive RF Sensing on Commodity
Smartphones"**, in IEEE two-column journal format with all seven figures
embedded.

## Contents

```
latex/
├── Ambient_Sense_Paper.tex      # main manuscript source
├── README.md                    # this file
└── figures/
    ├── fig1_method.png          # Fig. 1 — functional decomposition
    ├── fig2_arch.png            # Fig. 2 — layered architecture
    ├── fig3_sense.png           # Fig. 3 — Sense dashboard screenshot
    ├── fig4_scans.png           # Fig. 4 — Scans / scan-epoch screen
    ├── fig5_mesh.png            # Fig. 5 — Mesh screen
    ├── fig6_transit.png         # Fig. 6 — Transit / two-node counter
    └── fig7_club.png            # Fig. 7 — Club / gamification screen
```

## How to compile

The document uses the **IEEEtran** class and the **cite** package. From inside
the `latex/` directory run `pdflatex` twice (the second pass resolves the
cross-references and citations):

```sh
cd latex
pdflatex -interaction=nonstopmode Ambient_Sense_Paper.tex
pdflatex -interaction=nonstopmode Ambient_Sense_Paper.tex
```

The output `Ambient_Sense_Paper.pdf` will be produced.

### Requirements

- A TeX distribution (TeX Live / MiKTeX / MacTeX) with the `IEEEtran` class.
  `IEEEtran` is bundled with TeX Live and MiKTeX; otherwise install it or place
  `IEEEtran.cls` in the working directory.
- Packages: `cite`, `graphicx`, `amsmath`, `amssymb`, `textcomp`, `array`,
  `booktabs`, `url`, `hyperref`.

### Notes

- References are provided inline with the `thebibliography` environment, so no
  `.bib` file or BibTeX pass is required. Numbers appear in order of first
  citation, and the `cite` package compresses consecutive citations (e.g.
  `[3]–[6]`).
- The figures are tall phone screenshots (4:9 aspect); they are placed as
  single-column figures at `0.58\columnwidth` so they fit within a column.
  Adjust the width in each `\includegraphics` if you prefer a different size.
- The two wide diagrams use a `figure*` environment so they span both columns.

For a camera-ready submission, replace the placeholder author block and the
`\markboth` header with the real author names and the submission ID.

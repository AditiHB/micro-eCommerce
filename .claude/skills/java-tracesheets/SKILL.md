---
name: handwritten-dryrun
description: Build the printable hand-written-style dry-run sheet (4 A4 pages) for one DSA pattern from a small spec file: recipe card, worked example storyboard, "your turn" page with blanks, answer key with interview script and a tick-off ladder. Use when a pattern in this repo needs its DryRunSheet.pdf, or when the user wants a pen-and-pencil trace sheet.
---

# Hand-written dry-run sheet (4 pages per pattern)

Reference: `src/p01_hashingandfrequencymap/docs/p01_DryRunSheet.pdf` built from `sheets/p01_hashingandfrequencymap.py`.
(Sliding Window keeps its older, hand-composed 27-page sheet.) The user rejected "mechanical" table sheets: keep the notebook paper, Ink Free font, wobbly pen boxes, teal frame, red strike-through of old values, yellow highlighter and the circled answer.

## Pages
1. **Recipe card**: mantra on a highlighter band, the recipe (template, colour-coded lines `g|`, `r|`, `t|`, `i|`, `p|`), cost line, the 3 questions, trigger words in pen ellipses, "NOT when", a bug checklist with tick boxes.
2. **Worked example**: the input line, then one row per step: boxes with index numbers, optional teal `frame=(l, r)`, fills `hi={idx: g|r|t|y}`, `cross=[idx]`, `dim=[idx]`, pointers `ptr={"i": 3}`, state strips `state=[("seen", ["4:0", ("9:2", "g")])]`, variables `vars={"need": 9}` (old values are crossed out automatically) and a one-line `note`. Answer circled.
3. **Your turn**: a new input, the same rows blank (pencil lines for pointers, state, variables, "what happens"); the answer printed upside down at the bottom.
4. **Answer key**: the practice solution step by step, the 5-step interview script (`say`), spaced-repetition tick boxes, and the pattern's 12-problem ladder to tick off (from `ladder.json`).

Adding a whole new pattern (solutions, tests, guide, cards, this sheet, commit) is the job of the `dsa-pattern-authoring` skill: `python .claude/skills/dsa-pattern-authoring/tools/new_pattern.py N` scaffolds a valid spec here and `finish_pattern.py N` builds the sheet and places it in the docs.

## Spec file (`sheets/<package>.py`)
Defines `SPEC = dict(n, name, card=dict(mantra, recipe, cost, questions, triggers, donts, bugs), worked=dict(title, subtitle, input_label, cells, steps, answer), practice=dict(same keys), say=dict(brute, name_it, three, cost))`.
**Generate the steps by simulating the algorithm in the spec file and assert the answer against an independent brute force** (see the p01 spec): a hand-typed trace is how wrong answers get printed. Keep each trace to 5 steps (6 shrinks the boxes); long notes wrap, 640 px wide; a single word wider than its column raises an error on purpose.
A step is `dict(cells=[...], ptr={name: index}, hi={index: 't'|'g'|'r'|'y'}, dim=[...], vars={label: value}, state=[(label, [items])], note=text)`; every key is optional. The row of cells is one-dimensional: draw a table (a DP grid, a tree, a graph) as one row in reading order and say so in `input_label` (p21: index = 3 * row + col). The PRACTICE page must show the INPUT: leave `cells` out of its steps (a per-step `cells` would print the answers into the blanks). Keep `input_label` and `subtitle` short, they are clipped at the right edge of the page.

## Build and verify
```bash
python .claude/skills/handwritten-dryrun/scripts/build_dry.py .claude/skills/handwritten-dryrun/sheets/<package>.py <workdir>
```
Writes `<workdir>/DryRunSheet.html|pdf` and `shots/p1..p4.png`. It checks: generator asserts (spec answers, nothing below y = 1085, no overlong word), PDF has exactly 4 pages of 209.9 x 297.0 mm, fonts embedded, text present on every page. **Then look at the four PNGs** (overlaps and collisions are not detectable by the script), fix the spec, rebuild. Copy `DryRunSheet.html/pdf` to `src/<package>/docs/` as `pNN_DryRunSheet.html/pdf` (every file in a docs folder starts with its pattern number, e.g. `p05_DryRunSheet.pdf`).

## Printing all sheets together (the book)

`rebuild-flashcards --book` (or `build-book`, `scripts/make_book.py` of the `handwritten-flashcards` skill) binds the 4-page sheet PDF of every pattern and all flash cards into one PDF (`book/DSA_Book.pdf`), and `--dry-layout 2up` prints two sheet pages per A4 side (front: worked example + your turn; back: recipe card + answer key) to halve the paper. It reads the finished `src/<pkg>/docs/pNN_DryRunSheet.pdf` files, so build a sheet first (`finish_pattern.py N`). Details in the flash-card skill.

## Tools
Python 3 + Pillow (`ImageFont.getlength` on `C:\Windows\Fonts\Inkfree.ttf` so ellipses, strikes and highlighter fit the words; override with `FLASH_INKFONT`; the dry-run sheet is not driven by `handwritten-flashcards/flashcards.config.md`, only the flash cards are), seeded `random` for a deterministic wobble, SVG per page (794 x 1123 px = A4 at 96 dpi), headless Edge/Chrome for `--print-to-pdf` and `--screenshot`, pypdf for the structure check.

## Gotchas
* Ink Free has no `>=`, `<=`, arrows or minus glyphs: `asc()` maps them to ASCII.
* SVG `<text>` needs `xml:space="preserve"` or double spaces collapse.
* `print-color-adjust: exact` is required or Edge drops the paper tint and highlighter.
* The checker cannot see collisions: always open the PNGs.

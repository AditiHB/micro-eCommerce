---
name: handwritten-flashcards
description: Build printable, hand-written-looking flash cards (default 100 x 144 mm, 4 per A4 sheet, printed double-sided with mirrored backs) as HTML + PDF, then prove the layout is right (no overflow, safe margins, every back lines up with its front). Paper size, card size, margins, gap, insets, scale and fonts are set in flashcards.config.md; font_specimen.py shows how the installed fonts look. Also runs offline from a terminal without Claude: rebuild_cards.py regenerates one pattern, several or ALL patterns in one command. Use when the user asks for flash cards, revision cards, cut-out cards or a double-sided card print layout, e.g. one card per problem of a DSA pattern in this repo, asks to change the card size, margins or font, or asks to regenerate / rebuild the flash cards of one or all patterns, or to print everything (all dry-run sheets and all cards) as one book with less paper (rebuild-flashcards --book).
---

# Hand-written flash cards (print, flip, cut)

Reference implementation: the Sliding Window deck, `src/p03_slidingwindow/docs/p03_FlashCards.{html,pdf}` (12 cards, 6 pages), defined by `decks/p03_slidingwindow.py`. All 21 pattern decks (`decks/p01_...py` .. `decks/p21_...py`) follow the same shape; copy the one closest to your new pattern (p14 / p15 for trees, p18 for arrows between nodes, p21 for labelled 2D tables).
The engine and every check live in `scripts/`; **each pattern is one deck file in `decks/`** (`decks/<package>.py`, e.g. `p04_prefixsum.py`). A clean rebuild reproduces the delivered HTML byte for byte (seeded randomness).

**Adding a whole new pattern (solutions, tests, guide, deck, sheet, commit) is the job of the `dsa-pattern-authoring` skill; this skill is only the card printing.**

**Selecting decks:** a pattern number selects ALL decks of that pattern (`rebuild-flashcards 2` = the main deck and its LeetCode 75 and Top 150 decks), `2:lc75` / `2:top150` / `2:main` one of them, `lc75` / `top150` every deck of that set, `all` everything. The extras of a set live in `decks/pNN_<pkg>_lc75.py` / `decks/pNN_<pkg>_top150.py` and build into `src/<pkg>/lc75/docs/` / `src/<pkg>/top150/docs/`.

**Run it offline (no Claude, no network): `rebuild-flashcards <pattern>` / `rebuild-flashcards all`** (repo root; `rebuild-flashcards.cmd` for cmd / PowerShell, `./rebuild-flashcards.sh` for Git Bash / Linux / macOS; both call `scripts/rebuild_cards.py`). `21`, `p21`, `p21_griddynamicprogramming2d`, `3 5 21`, `1-7` or `all` select patterns; `--list` shows what exists. Each selected pattern runs `build_all.py` in its own work folder (default `<temp>/flashcards-work/<pattern>`, one browser profile per folder so builds run side by side, `--jobs`, default half the CPUs, at most 4) and is copied to `src/<pattern>/docs/pNN_FlashCards.{pdf,html}` only when every check passed; `--out DIR` collects the PDFs in one folder instead (nothing in `src/` is touched), `--no-copy`, `--skip-unchanged` (stamp = deck + settings + engine), `--config FILE`. The exit code is 0 only when every selected pattern passed. A full rebuild of all 21 patterns takes about 2.5 minutes with 4 jobs. PyMuPDF (for the alignment check) lives in `~/.cache/handwritten-flashcards/pylib` (not under AppData: some Python launchers virtualise it); `rebuild-flashcards --install-deps` installs it once. When Claude is asked to "regenerate the flash cards", just run this command instead of rebuilding by hand; the rebuilt HTML of all 21 shipped decks is byte-identical to the committed files, so a rebuild changes only the PDF timestamps.

**Regression tests: run them after ANY change to the Python (`scripts/`, `decks/`, `flashcards.config.md` code paths): `rebuild-flashcards --test` (about 40 s, no browser) and `rebuild-flashcards --test --e2e` (about 4 min, real browser).** They live in `tests/` (standard library `unittest`; `tests/run_tests.py` is the runner, `python tests/run_tests.py -v test_config.Layout` runs one class). `test_engine` builds the HTML of ALL 21 decks with the pinned default settings and the stored fitted text scales and requires it to be byte-identical to `tests/golden.json` (a hash per deck), plus two variants (3 x 3 scaled sheet, own margins + gap + font); it also checks placement, mirroring (front left + back left + card width = page width), partly filled sheets, cut guides, page size, scaling for five pinned settings files in `tests/configs/`. `test_config` covers `flashconfig.py` and every kind of settings mistake, `test_cli` the pattern selection of `rebuild_cards.py` and `font_specimen.py`, `test_book` the book (`make_book.py`), and `test_e2e` (only with `--e2e`) the whole pipeline in a browser including MUTANTS that the checks must reject: the old print-clipping bug, a back sheet shifted by 2 mm, a card with far too much text. A failing golden test means the cards changed; if that is wanted: `rebuild-flashcards all`, look at the cards, `rebuild-flashcards --update-golden`, and commit `tests/golden.json` with the change. Adding a deck (a new pattern) also needs `--update-golden`, the test says so.

**All cards together, no empty slots: a request for EVERYTHING (`rebuild-flashcards all`) also packs the cards of all decks onto shared sheets.** Patterns with 1 to 3 problems leave empty slots on their own sheet (a 2-card deck takes a whole sheet); after an `all` build every deck is bound to the next one: one extra file `FlashCards_All.pdf` (cards only, no cover, page 1 is a front side, the margin label says FRONT n/N) plus `FlashCards_All_contents.md` (deck, cards, sheets, first page), written to ONE output folder: `--out DIR` (then the per-deck PDFs/HTML are in the same folder, nothing in `src/` is touched) or `book/` (git-ignored) when `--out` is not given. It happens ONLY for `all` (the selection must name every deck): one pattern, a few or a range build exactly as before, `--no-combine` switches it off, `--no-copy` skips it. The packed PDF is made by `scripts/make_book.py` (`combine_cards`) from the deck PDFs that were just built (it knows where they are: `--out`, the docs folders, the work folder), refuses PDFs built with other card / margin settings, and compares every card face with its source face (same check as the book). Today: 385 cards on 97 sheets instead of 108 deck by deck (the book of the previous paragraph is the same packing plus the dry-run sheets). Tests: `tests/test_cli.py` (Combining: only `all` combines, where each deck PDF is) and `tests/test_book.py` (the packed PDF of three patterns, one output folder, no cover).

**The book: every dry-run sheet and every flash card in ONE PDF, with no wasted paper: `rebuild-flashcards --book` (or `build-book` / `./build-book.sh`, same thing; `scripts/make_book.py`, no browser needed).** Printed one by one, the last sheet of every small deck is mostly empty (a 2-card deck takes a whole sheet); the book packs the cards of ALL decks back to back (4 per sheet, fronts and mirrored backs, so only the very last sheet has empty slots) behind the dry-run sheets of all patterns, with a cover, a contents page (page and sheet numbers) and PDF bookmarks, in `book/DSA_Book.pdf` (git-ignored: it is built from the PDFs in `src/`, never committed). It copies the finished, checked vector pages (nothing is re-rendered) and then proves it: every card face of the book is rendered and compared with its face in the source PDF (worst difference about 0.01 of 255; a face shifted by 3 pt is rejected). Options: selectors like `rebuild-flashcards` (`1-7`, `13`, `2:top150`, `lc75` ...: the sheets of those patterns and their decks), `--only cards|sheets`, `--order pattern|set` (main, LeetCode 75, Top 150 decks pattern by pattern, or set by set), `--dry-layout full|2up`, `--out DIR`, `--name FILE`, `--no-cover`, `--config FILE`, `--no-verify`, `--list` (what goes in, how many sheets it saves, when each PDF was built; writes nothing). `--dry-layout 2up` prints two sheet pages on one A4 side (turned 90 degrees, 71 %: front side = worked example + your turn, back side = recipe card + answer key, so the answers are never on the page you write on) and writes `book/DSA_Book_2up.pdf`; the page stays portrait A4, so ONE duplex setting ("flip on the long edge") is right for the whole book. Part 2 (the cards) always starts on a front side, otherwise the backs would not land behind their fronts; the cover tells how to print. Today (55 decks, 385 cards, 31 sheets): 344 pages = 172 sheets against 182 for the 86 separate PDFs (5 % saved), 136 sheets with 2up (25 % saved). Build the sheets and the decks first (`rebuild-flashcards all`, `finish_pattern.py N`): the book only binds the PDFs that are in `src/`. PyMuPDF is needed (`--install-deps`); `tests/test_book.py` covers packing, page numbers (cards on odd pages), the printed result, the face check with a displaced-face mutant, and the hand-over from `rebuild_cards.py` without a browser.

**Size, margins and fonts are settings, not code: edit `flashcards.config.md`** (next to this file; a copy in the work folder or `build_all.py --config <file>` overrides it for one project). Settings: `paper`, `orientation`, `card_width`, `card_height`, `margin_x`, `margin_y`, `gap`, `columns`, `rows`, `content_inset`, `border_inset`, `scale`, `handwriting_font`, `code_font`, `font_folder`, `cut_guides`, `sheet_label`. `python scripts/flashconfig.py [file]` prints what a file gives (cards per sheet, real margins, warnings). `python scripts/font_specimen.py` renders every installed font with real card text (writes `fonts_handwriting.png`, `fonts_text.png`, `fonts_code.png`, `fonts.html` into the work folder) so the user can choose, then put the family name (or a `.ttf` path) into `handwriting_font`. The defaults reproduce the 100 x 144 mm / A4 / Ink Free cards byte for byte.

## 1. What you must know before starting

| Ask / decide | Default used in the reference deck |
|---|---|
| Card size | 100 x 144 mm (user's request), `card_width` / `card_height` in `flashcards.config.md`. Cards per A4 = 2 x 2 -> 200 x 288 mm, so side margin 5 mm, top/bottom 4.5 mm (worked out from `margin_x` / `margin_y` = 4) |
| Cards in the deck | ONE CARD PER PROBLEM, so a deck has as many cards as its pattern has problems: 12 for the playbook patterns (3 sheets = 6 PDF pages: front1, back1, front2, back2, ...), 2 or 3 for a data-structure pattern of LeetCode 75 problems. Any count works: the last sheet may have empty slots (12 cards on a 3 x 3 sheet = 9 + 3). Filler cards (pattern cheat-sheet, template, trigger words) only if you want full sheets. The LeetCode 75 problems added to an existing pattern have their own deck `decks/pNN_<pkg>_lc75.py` (docs in `src/<pkg>/lc75/docs/pNN_lc75_FlashCards.*`) |
| What goes on each side | Front = the question. Back = the answer (pattern + how to solve). See section 3 |
| Look | Hand-written (same as `DryRunSheet`): Ink Free font, wobbly pen strokes, teal window frame, highlighter. The user rejected "mechanical" table styles earlier, so keep the pen look |
| Where it goes | `src/<package>/docs/pNN_FlashCards.{html,pdf}` (docs live INSIDE the Java package; EVERY file of a docs folder starts with its pattern number, e.g. `p05_FlashCards.pdf`), linked from that docs folder's `pNN_README.md` and guide and from the root `README.md` |

Per card you need: LeetCode number, title, short title (for the back header), tier (MUST / CORE / STRETCH; EASY / MEDIUM / HARD for the cards of LeetCode 75 problems, same colours and dots), difficulty, the problem in kid words, one example with its answer, pattern kind, what to keep, BAD/HAPPY rule, 2-3 trigger words, 3-5 steps, a 5-8 line code skeleton, complexity, one trap, twin problems, and a small diagram. Take the facts from the repo (`src/<pattern>/.../HighlyOptimized.java` PSEUDO CODE block and the guide tables) so cards never disagree with the code.

## 2. Geometry (the part that makes double-sided printing work)

```
default (all of it comes from flashcards.config.md):
page 210 x 297 mm   card 100 x 144 mm   cards at x = 5 + 100*col,  y = 4.5 + 144*row   (col,row in {0,1})
content box   = card inset 5 mm  -> 90 x 134 mm      (all text, diagrams)
pen border    = card inset 2.6 mm                     (decoration only)
cut guides    = faint dashed lines on the card edges, FRONT side only
```

* **How the sheet is worked out** (`flashconfig.py`): columns = `floor((paper width - 2 x margin_x + gap) / (card width + gap))`, rows likewise; the block of cards is **centred**, so the real margins are `(paper - used) / 2` (>= `margin_x` / `margin_y`) and the mirrored back lands behind the front. `columns` / `rows` can force fewer. The build prints the result.
* **`scale`** (default `auto`): a card is always laid out as if its text area were 90 x 134 mm (the size the text, boxes and diagrams were designed for) and then scaled as a whole with a CSS transform, so a different card size keeps every proportion. `auto` = `min(text area width / 90, text area height / 134)`. The steps print at 9.4 pt x scale; the build warns below about 7 pt.
* **Mirroring.** Print "flip on the LONG edge" (Windows default for portrait). The paper turns around its vertical axis, so the card in column c goes behind column (columns - 1 - c) of the same row. With 2 columns the back order is `[c1, c0, c3, c2]` for fronts `[c0, c1, c2, c3]`. (Short-edge flip would swap rows instead; not implemented.)
* **Why 5 mm inside the cut line.** Duplex registration is typically off by 1-2 mm and people cut crookedly. Nothing important may live in that band. No full-bleed colour for the same reason (also printers cannot print to the paper edge).
* **Other sizes / other paper.** Just change the settings. The build warns when a sheet margin is below about 3 mm (many printers cannot print there, so cut guides in that margin may be clipped; content is unaffected because it is inset) and when `content_inset` is below 4 mm.
* Sheet label ("FRONT 1/3", "BACK 1/3 (mirrored)") sits in the top margin in grey 5.5 pt. It is cut away and only helps if the user prints single-sided and has to re-feed sheets.

## 3. Card design (decisions and reasons)

* **Front hides the pattern name.** Shows "DSA flash card . 03 / 12", tier chip, LC number, difficulty, question, example (drawn array + answer) and 4 check boxes ("Fixed or variable? Longest, shortest or count? What lives inside / what is the rule? When do I score, what does brute force cost?"). Reason: the user wants to recognise the pattern, not read it.
* **Back** top to bottom: header band (LC + short title + tier dots) -> pattern box (big "FIXED window | size k" + what to keep + BAD/HAPPY rule) -> "SPOT IT" trigger words -> diagram -> numbered steps -> yellow code skeleton -> footer (cost, twins, red TRAP).
* **Colour meaning (keep consistent with the dry-run sheet):** green = IN / GROW / answer, red = OUT / DROP / BAD / trap, teal = the window frame / SCORE, yellow highlighter = the one phrase to remember, pencil grey = hints. Tier colours: MUST green, CORE blue, STRETCH purple (dots 1/2/3 as well, so a black-and-white print still works).
* **Diagrams** are inline SVG, 90 mm wide = 340.2 px viewBox, built from pen primitives (`line/rect/ellipse/arrow/strike` with seeded jitter). Helper API in `gen_flash.py`: `Dia(top)`, `cells(d, values, ...)`, `frame(d, g, l, r)`, `pointer(d, g, i, 'l'|'r', colour)` (draws "tail"/"head"), `tag_above` (IN / OUT labels), `note` (caption with optional highlighter), `strike`, `ellipse`. Show ONE key move per card (the jump, the IN/OUT pair, the deque pop), not a whole trace.
* **Fonts.** Handwriting for prose (`handwriting_font`, default Ink Free; the other installed candidates are Segoe Print, Segoe Script, Comic Sans MS, MV Boli, Gabriola, see `font_specimen.py`); **Consolas for code** (`code_font`; handwritten code is unreadable at 7-8 pt). A font that is only a file (not installed) is loaded through an `@font-face`. The writing font needs the ASCII characters the cards print: the specimen shows which a font cannot draw. Body 9.5 pt x scale, never below about 9 pt.
* **Auto-fit text.** Every face has a CSS variable `--k` (all `font-size` are `calc(Npt * var(--k))`). `sweep.py` renders the deck at k = 1.0 ... 1.4 and stores, per face, the largest k that still leaves >= 1.8 mm free (fronts capped 1.3, backs 1.25) in `flash_scales.json`; a face that does not fit even at 1.0 is retried at 0.95 ... 0.5 and listed in a NOTE (shorten its text to get bigger letters). Dense cards get smaller text, light cards bigger text, nothing overflows. `flash_scales.json` carries a fingerprint of the settings: after changing the size or font the engine warns until `build_all.py` has swept again.

## 4. Tools and why

| Tool | Used for |
|---|---|
| Python 3 + Pillow | the generator `gen_flash.py` (writes one HTML file, no template engine); Pillow `ImageFont.getlength` measures Ink Free text so highlighter boxes fit words |
| Ink Free (`C:\Windows\Fonts\Inkfree.ttf`) + Consolas | handwriting + code: the defaults. Any other installed font, or a `.ttf` file, is chosen with `handwriting_font` / `code_font` in `flashcards.config.md` |
| Headless Edge / Chromium | (1) `--print-to-pdf --no-pdf-header-footer` for the final PDF, (2) `--screenshot` with `--force-device-scale-factor` for visual review, (3) `--dump-dom` to read a layout report that an injected script writes into the page |
| pypdf | PDF structure: page count, A4 size, embedded fonts, which LC numbers are on which page |
| PyMuPDF + numpy | rasterise the real PDF and compare each front with its mirrored back. Install **outside** the user's Python: `python -m pip install --target "$FLASH_WORK/pylib" pymupdf` (`align_check.py` adds `$FLASH_WORK/pylib` to `sys.path`) |
| fontTools | reads font names and glyph coverage: `flashconfig.py` finds a font by family name, `font_specimen.py` lists every installed font |

No network is needed once those are installed.

## 5. Pipeline

(For the normal case use `rebuild-flashcards` above; this section is what it runs, step by step.)

`scripts/` below means `.claude/skills/handwritten-flashcards/scripts/`. Run the scripts with plain `python`, from any folder.

```bash
export FLASH_WORK="$(cygpath -w /path/to/workdir)"      # all generated files go here, nothing is written into the repo
python .claude/skills/handwritten-flashcards/scripts/build_all.py FlashCards --deck .claude/skills/handwritten-flashcards/decks/p03_slidingwindow.py    # does steps 0-6
#   add  --config my.config.md  to use other size / margin / font settings than flashcards.config.md
python scripts/font_specimen.py                                      # how the installed fonts look -> fonts_*.png / fonts.html in the work folder
# or step by step:
python scripts/flashconfig.py                                        # 0 the resolved settings: cards per sheet, real margins, fonts, warnings
export FLASH_DECK="$(cygpath -w .claude/skills/handwritten-flashcards/decks/p03_slidingwindow.py)"
python scripts/gen_flash.py "$FLASH_WORK/flash_check.html" check     # 1 build with the layout probe
python scripts/sweep.py                                              # 2 pick text scale per face -> flash_scales.json
python scripts/flash_report.py                                       # 3 DOM report, exit 1 if any face needs attention
python scripts/gen_flash.py "$FLASH_WORK/FlashCards.html"            # 4 final HTML
#   print:  msedge --headless=new --disable-gpu --virtual-time-budget=20000 --no-pdf-header-footer --print-to-pdf=out.pdf file:///.../FlashCards.html
python scripts/check_pdf.py FlashCards.pdf 6                         # 5 structure
python scripts/align_check.py FlashCards.pdf                         # 6 front/back geometry
python scripts/gen_flash.py "$FLASH_WORK/flash_shot.html" shot && python scripts/shot.py 3 1 2    # visual review PNGs
```

To make a new deck: copy `decks/p03_slidingwindow.py` to `decks/<package>.py`, replace `DECK_TITLE`, the `dia_*` functions, the `CARDS` list and the example checks, then `python scripts/build_all.py FlashCards --deck decks/<package>.py`. A deck defines `DECK_TITLE`, **`CHECK_QS`** (the four self-check prompts printed on every front: they differ per pattern, the engine default is the Sliding Window set), `CARDS` (12 dicts) and its `dia_*` functions. Deck-local helpers are welcome (p21 has `table()` for a grid with row and column labels), but never redefine an engine global such as `OUT`. A pointer or tag label of a single `l` looks like a stroke in the handwriting font: use `L` / `R` or a word. `shot.py <scale> <pages...>` takes the scale FIRST (`shot.py 2 1 3 5` = fronts at 2x). Write number checks as module-level `assert`s next to the deck (the p21 deck computes every table it draws with small Python functions and asserts the printed examples). The deck file is exec'd inside `gen_flash.py`, so every engine helper (`Dia`, `cells`, `frame`, `pointer`, ...) is in scope; the engine itself is not edited per pattern (add new drawing helpers to it only when a deck needs a new kind of picture). `@page { size: 210mm 297mm; margin: 0 }` plus `print-color-adjust: exact` are already in the CSS; without them Edge adds margins and drops the tinted backgrounds.

## 6. Verification (do all of it, report numbers, do not just say "looks fine")

| Check | How | Pass criterion | Caught in the reference deck |
|---|---|---|---|
| Example answers are true | `_bf*` brute-force functions + `assert` at import time for every example printed on a card and for numbers quoted in diagrams (`2 + 50 - 1 = 51`) | script starts | - |
| No overflow, enough room | `flash_report.py` (DOM probe in headless Edge) | per face: gap before footer >= 0.4 mm (sweep demands 1.8), overflow_x = 0, overflow_y <= 1 px (the lowest BLOCK box against the text area; the glyph area of the last line is not counted, it is taller than the line box in fonts such as Segoe Print and Comic Sans), content >= `content_inset - 0.3` mm (4.7 by default) from every edge. Final run: "faces needing attention: 0" | example diagram 2 mm too wide; 1 px rounding; 3 px of descender area flagged as overflow with Segoe Print |
| Page structure | `check_pdf.py` | exactly 6 pages (or 2 x sheets), the paper size of the settings (209.9 x 297.0 mm for A4), all fonts embedded, LC numbers on the right pages (front page 1 lists the cards of sheet 1, back page 2 the same cards in mirrored positions) | - |
| Every card is printed once on each side | `gen_flash.py` asserts that the number of front and back faces placed on the sheets equals the number of cards | the build runs | the sheet slice was still "4 per sheet" after the sheet became configurable: cards 9-12 of a 3 x 3 layout were silently dropped (the alignment check cannot see a card that is missing on both sides) |
| Back sits behind front | `align_check.py`: rasterise PDF at 200 dpi, flip each back horizontally, compare the pen-border bounding box of every card with its front (every slot of the sheet; an empty slot on both sides is skipped) | worst difference < 0.6 mm (reference deck 0.25 mm = pen wobble + 1 px) | - |
| It really looks right | `shot.py` crops every card face at 3x; Read every PNG, fix, repeat. Also zoom the PDF render (`pymupdf` clip at 400 dpi) on small details | no clipped text, no overlap, diagram labels readable | clipped deque drawing, circle on wrong letter, labels touching frames, caption overlapping pointer labels, unreadable "l" / "r" labels (now "tail" / "head") |
| Build is deterministic | rebuild in a fresh folder and diff against the delivered HTML (`rebuild-flashcards --test` does this for all 21 decks against `tests/golden.json`) | identical | - |
| Every card is complete in the PDF | `check_pdf.py` reads `<cards>.expect.json` (written by `gen_flash.py`) and counts, with PyMuPDF, the bottom line of every card on every page ("flip for the pattern" on fronts, "TRAP" on backs); pypdf cannot extract the text of scaled cards completely, so it is not used for this | the counts equal the cards on the sheet | the bottom row of a scaled 3 x 3 sheet was cut by the print while all other checks passed |

Do **not** test margins with pypdf text coordinates: text inside inline SVG gets wrong positions (nested transforms), producing false alarms. The DOM probe is the margin test.

## 7. Gotchas (each one cost time once)

* **Ink Free has no glyphs for `>=`, `<=`, arrows, minus (U+2212), check marks, stars.** Write ASCII (`>=`, `<=`, `->`, `-`) in handwriting text (`asc()` does it); symbols are fine inside Consolas spans. Check with fontTools `getBestCmap()`.
* **Highlighter:** `linear-gradient(transparent 55%, yellow 55%)` renders as a dark line through the word in the PDF. Use a flat band: `linear-gradient(#fff176,#fff176) 0 92% / 100% 46% no-repeat`.
* `display:flex` on an `li` that contains inline `<u>`/`<b>` splits every inline child into a flex item and wraps badly: put the text in one `<span>`.
* Keep `k = 3` style fragments unbroken (`&nbsp;=&nbsp;`) or the answer wraps as "k =" / "3 -> ...".
* SVG `text` needs `xml:space="preserve"`; SVG content outside the viewBox is clipped silently, so keep a 8 px gutter (`pitch = (DW - 16) / n`) and leave head-room above cells for IN/OUT tags.
* A pointer label "l" in a handwriting font looks like a stroke. Use words ("tail", "head").
* Changing a setting changes the fit: `build_all.py` sweeps again; running `gen_flash.py` by hand with an old `flash_scales.json` prints a warning (the file carries a fingerprint of the settings).
* A font that is not installed (a `.ttf` file path, or a `font_folder`) is embedded through `@font-face` pointing at the file; the PDF still embeds the glyphs. A family name that is not found stops the build and suggests close names.
* **A scaled card must not have a layout box that reaches past the page.** `scale` shrinks the card with `transform: scale()`, which leaves the layout box at the design size, and the print cuts content at the page edge by its LAYOUT position: the bottom row of a 3 x 3 sheet lost its lower third (visually at about 2/3 of the card) although every check said "0 faces needing attention". So a scaled card is placed at the page corner (`left:0; top:0`) and moved into position with `transform: translate(...)` (no layout effect). A clip wrapper around the content does NOT help. Always LOOK at the printed page of a new size (`align_check.py` writes `pdf_pN.png`), the DOM probe cannot see this.
* `text-wrap` differs per font: the same words take 3 lines in Segoe Print and 2 in Ink Free, so a font switch can make a card too full. The sweep then shrinks that card's text and says so in the NOTE line.
* The bash tool turns `\\` into `\` and sometimes `\n` into a real newline inside heredocs: for regex replacements use `chr(92)` or write the file with the Write tool; Python 3.11 f-strings cannot reuse the same quote type inside `{}`.
* `msedge --print-to-pdf` returns before the file is flushed: poll for it (`print_pdf()` does). If a viewer holds the old PDF open, write to a new name, then copy.
* Edge's PDF viewer does not render in headless screenshots, so use PyMuPDF to look at the PDF.
* Page size comes out 209.9 x 297.0 mm (Edge rounds to whole points). That is normal.
* Python `-I` (isolated) does not put the script folder on `sys.path`; the scripts add it themselves, so run them with plain `python`.

## 8. Delivering

1. Copy `<name>.html` and `<name>.pdf` to `src/<package>/docs/` as `pNN_FlashCards.html` / `pNN_FlashCards.pdf` (NN = the two-digit pattern number).
2. Make sure the pattern's `docs/pNN_README.md` and guide link to the cards (with a short "how to print" block) and the root `README.md` row has the PDF link; check every relative link resolves.
3. Tell the user how to print (this text, adapted):
   *A4, Actual size / 100 % (never "Fit to page"), colour, both sides, flip on the long edge. Without duplex: print pages 1, 3, 5, put them back in the tray, print 2, 4, 6; test one sheet and hold it to the light. Cut along the dashed lines on the front side.*
4. Send the PDF with `SendUserFile`. Report the verification numbers (cards, pages, worst alignment difference, faces needing attention).
5. Update the memory note `dsa-pattern-deliverable-format` if the format changed. **Do not commit unless the user asks.**

"""THE BOOK: every trace sheet and every flash card of the interview curriculum in ONE printable PDF, with no wasted paper.

Why: each topic has its own trace-sheet PDF and its own card PDF. Printed one by one, the last sheet of every small deck is mostly empty (a deck of 6 cards
still takes two whole sheets). The book packs the cards of ALL decks back to back (4 per sheet, fronts and mirrored backs), so only the very last sheet can
have empty slots, and it can print the trace sheets two to a side. It reuses the finished, checked PDFs (vector, nothing is re-rendered).

usage (from the repository root; `rebuild_cards.py --book ...` is a short name for this script):
  python .claude/skills/java-flashcards/scripts/make_book.py                  everything: all trace sheets + all cards       -> book/Interview_Book.pdf
  python .../make_book.py 1-2                                                  only topics 1 to 2 (same selectors as rebuild_cards.py)
  python .../make_book.py --dry-layout 2up                                     two trace-sheet pages on one side of a sheet (half the paper)   -> book/Interview_Book_2up.pdf
  python .../make_book.py --only cards    |  --only sheets                     only the cut-out cards / only the trace sheets
  python .../make_book.py --list                                               show what would go in and how many sheets it saves, write nothing
options:
  --dry-layout full|2up   full = every sheet page on its own A4 page (default); 2up = two pages per A4 side (see below)
  --only both|sheets|cards
  --out DIR               default: book/ in the repository root (it is git-ignored: the book is built from the PDFs in docs/interview/print/, never committed)
  --name FILE             file name inside --out (default Interview_Book.pdf, Interview_Book_2up.pdf for 2up)
  --no-cover              no cover and contents pages (the bookmarks stay)
  --config FILE           the flash-card settings the PDFs were built with (default: flashcards.config.md of the skill)
  --no-verify             skip the check that every card face of the book is pixel-identical to its face in the source PDF
  --install-deps          one-time: pip install PyMuPDF into ~/.cache/handwritten-flashcards/pylib (needs the network once)

The book, in print order:  cover + contents (+ a notes page when needed)  |  Part 1: the trace sheets, topic by topic  |  Part 2: the flash cards.
Print it on A4, "Actual size / 100 %", in colour, BOTH sides, "flip on the LONG edge". Part 2 starts on a front side, so every card back lands behind its front;
cut the cards along the dashed guides (Part 2 only). Page numbers and bookmarks are in the PDF; the contents page lists them.
2up: each A4 side holds two sheet pages turned 90 degrees (turn the sheet to read it; the page on the left is the upper half). Front side: the worked trace
and your turn; back side: the mechanism card and the answer key, so the answers are never on the same side as the page you write on. The pages print at 71 %.
Everything stays vector (sharp at any size). Sheet and card PDFs must be built first (rebuild_cards.py all, build_dry.py --all); the book warns when one is missing.
"""
import argparse
import datetime
import json
import math
import os
import re
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
os.environ.setdefault("FLASH_WORK", os.path.join(tempfile.gettempdir(), "flashcards-work", "_book"))      # flashconfig keeps its font catalog in FLASH_WORK
os.makedirs(os.environ["FLASH_WORK"], exist_ok=True)
import deck_index as DI  # noqa: E402

SKILL, REPO = DI.SKILL, DI.REPO
BOOK_TITLE = "The Java and Microservices Interview Book"
BOOK_FILE = "Interview_Book"
MM = 72 / 25.4                                   # points per millimetre
FACE_INSET_MM = 0.25                             # the cut guides sit ON the card edges: a face is taken a hair inside them (nothing is drawn there)
VERIFY_DPI = 40
VERIFY_MAX_DIFF = 0.6                            # mean absolute difference (0..255) between a face of the book and its face in the source PDF


class BookError(SystemExit):
    pass


# ------------------------------------------------------------------ PyMuPDF
def pymupdf():
    sys.path.insert(0, DI.pylib_dir())
    try:
        import pymupdf as m
    except ImportError:
        try:
            import fitz as m
        except ImportError:
            raise BookError("PyMuPDF is missing: run  python .claude/skills/java-flashcards/scripts/rebuild_cards.py --install-deps  (once, needs the network)")
    return m


# ------------------------------------------------------------------ geometry of a card sheet (the same numbers as gen_flash.py: left = X0 + col * (CARD_W + GAP))
class Geometry:
    def __init__(self, cols, rows, card_w, card_h, gap, x0, y0, page_w, page_h, cut_guides=True, sheet_label=True):
        self.cols, self.rows, self.per = cols, rows, cols * rows
        self.card_w, self.card_h, self.gap, self.x0, self.y0 = card_w, card_h, gap, x0, y0
        self.page_w, self.page_h = page_w, page_h
        self.cut_guides, self.sheet_label = cut_guides, sheet_label

    @staticmethod
    def from_config(path=None):
        import flashconfig
        c = flashconfig.load(path or os.path.join(SKILL, "flashcards.config.md"))
        return Geometry(c.cols, c.rows, c.card_w, c.card_h, c.gap, c.x0, c.y0, c.page_w, c.page_h, c.cut_guides, c.sheet_label)

    def slot(self, k):
        """(column, row) of slot k on a FRONT sheet: the slots fill row by row"""
        return k % self.cols, k // self.cols

    def back_slot(self, k):
        """the back of the card in front slot k sits in the mirrored column (the sheet is turned over on its long edge)"""
        c, r = self.slot(k)
        return self.cols - 1 - c, r

    def rect_mm(self, col, row, inset=0.0):
        x, y = self.x0 + col * (self.card_w + self.gap), self.y0 + row * (self.card_h + self.gap)
        return (x + inset, y + inset, x + self.card_w - inset, y + self.card_h - inset)

    def rect(self, col, row, inset=FACE_INSET_MM):
        return pymupdf().Rect(*[v * MM for v in self.rect_mm(col, row, inset)])


# ------------------------------------------------------------------ packing (pure functions: tested without any PDF)
def pack(counts, per):
    """cards of several decks, in order, onto sheets of `per` slots: returns a list of sheets, each a list of (deck index, card index inside the deck)"""
    flat = [(d, i) for d, n in enumerate(counts) for i in range(n)]
    return [flat[k:k + per] for k in range(0, len(flat), per)]


def source_slot(card_index, per):
    """where card i of a deck was printed in the deck's own PDF: (sheet number from 0, front slot)"""
    return card_index // per, card_index % per


def separate_sheets(dry_pages, deck_counts, per):
    """paper sheets (printed on both sides) when every PDF is printed on its own: a sheet PDF of q pages takes ceil(q / 2), a deck of n cards ceil(n / per)"""
    return sum(math.ceil(q / 2) for q in dry_pages) + sum(math.ceil(n / per) for n in deck_counts)


def saved_text(separate, book):
    """'saves 10' / 'saves 0' / 'needs 1 more (the cover and the contents)': a tiny selection can cost a page more than printing its PDFs alone"""
    d = separate - book
    return f"saves {d}" if d >= 0 else f"needs {-d} more (cover and contents pages; --no-cover drops them)"


def dry_sides(pages, layout):
    """how many printed SIDES the pages of one sheet PDF take: every page on its own (full), or two to a side (2up)"""
    return pages if layout == "full" else math.ceil(pages / 2)


def two_up_sides(q):
    """the page numbers (0-based) on each side of a 2up sheet: for the usual 4 pages  front = worked example + your turn, back = recipe + answer key"""
    if q == 4:
        return [(1, 2), (0, 3)]
    return [tuple(range(i, min(i + 2, q))) for i in range(0, q, 2)]


# ------------------------------------------------------------------ what goes in
class Deck:
    def __init__(self, d, path, doc, count, expect, name=""):
        self.d, self.path, self.doc, self.count, self.expect, self.name = d, path, doc, count, expect, name

    @property
    def title(self):
        return f"t{self.d['n']:02d} {self.name}"


def sheet_name(spec_path):
    """the SPEC["name"] of a trace-sheet spec (the file is plain Python that builds its trace and asserts it: running it is what the generator does too)"""
    ns = {}
    exec(compile(open(spec_path, encoding="utf-8").read(), spec_path, "exec"), ns)
    return ns["SPEC"]["name"]


def count_cards(doc, g):
    """cards in a deck PDF: the front slots (pages 1, 3, 5 ...) that hold text, in order"""
    n = 0
    for s in range(len(doc) // 2):
        page = doc[2 * s]
        for k in range(g.per):
            col, row = g.slot(k)
            if not page.get_text("text", clip=g.rect(col, row, 0)).strip():
                return n
            n += 1
    return n


def check_geometry(doc, g, path):
    """the PDF must have been built with the settings we cut it by: every text block of a front page (but the label in the top margin) sits inside one card slot"""
    page = doc[0]
    slots = [g.rect(*g.slot(k), 0) for k in range(g.per)]
    pad = 1.2 * MM
    for b in page.get_text("blocks"):
        x0, y0, x1, y1 = b[:4]
        if y1 < g.y0 * MM + 1:
            continue                                                  # the sheet label sits in the top margin
        if not any(s.x0 - pad <= x0 and x1 <= s.x1 + pad and s.y0 - pad <= y0 and y1 <= s.y1 + pad for s in slots):
            raise BookError(f"{os.path.relpath(path, REPO)} was built with other card / margin settings than the ones in use now "
                            f"(a text block crosses the card slots): rebuild it (rebuild_cards.py) or pass --config with the settings it was built with")


def collect(selectors, only, g, warn, pdf_of=None):
    """the sheet PDFs and the decks that go into the book; pdf_of(deck) says where the PDF of a deck is (default: the print folder)"""
    m = pymupdf()
    decks_all = DI.all_decks()
    specs = DI.sheet_specs()
    chosen = DI.select(selectors or ["all"], decks_all)
    topics = sorted({d["n"] for d in chosen})
    dry = []
    if only in ("both", "sheets"):
        for n in topics:
            if n not in specs:
                continue                                                  # a topic need not have a trace sheet
            path = os.path.join(DI.docs_dir(), f"t{n:02d}_TraceSheet.pdf")
            if not os.path.exists(path):
                warn(f"topic {n}: {os.path.relpath(path, REPO)} is missing, no trace sheet (build it: build_dry.py {specs[n]} --publish)")
                continue
            doc = m.open(path)
            dry.append(dict(n=n, name=sheet_name(specs[n]), path=path, doc=doc, pages=len(doc)))
    decks = []
    if only in ("both", "cards"):
        for d in sorted(chosen, key=lambda d: d["n"]):
            path = pdf_of(d) if pdf_of else os.path.join(DI.docs_dir(d), f"{d['prefix']}_FlashCards.pdf")
            if not os.path.exists(path):
                warn(f"{d['stem']}: {os.path.relpath(path, REPO)} is missing, its cards are left out (rebuild_cards.py {d['n']})")
                continue
            doc = m.open(path)
            check_geometry(doc, g, path)
            decks.append(Deck(d, path, doc, count_cards(doc, g), None, DI.deck_title(d)))
    return dry, decks


# ------------------------------------------------------------------ the pages around the sheets
class Layout:
    """the page numbers of the book (1-based), worked out before anything is drawn so that the contents page can list them"""

    def __init__(self, dry, decks, a, g):
        self.layout = a.dry_layout
        self.sheets = pack([k.count for k in decks], g.per)
        self.part1 = sum(dry_sides(x["pages"], self.layout) for x in dry)
        self.part2 = 2 * len(self.sheets)
        self.cover = not a.no_cover
        self.toc_pages = 0
        self.pad = 0
        self.front = 0

    def finish(self, n_toc_lines):
        self.toc_pages = math.ceil(n_toc_lines / LINES_PER_PAGE) if self.cover else 0
        front = (1 + self.toc_pages) if self.cover else 0
        self.pad = 1 if self.part2 and (front + self.part1) % 2 else 0          # Part 2 must start on a front side: card backs land behind card fronts
        self.front = front + self.pad
        self.part1_start = self.front + 1
        self.part2_start = self.front + self.part1 + 1
        self.total = self.front + self.part1 + self.part2


LINES_PER_PAGE = 54
LINE_H = 13.2
TOP_Y = 84


def toc_entries(dry, decks, lay, g):
    """the lines of the contents page: ('head', text) or ('item', code, name, info, pages)"""
    out = []
    if dry:
        out.append(("head", f"Part 1: trace sheets ({'two to a side' if lay.layout == '2up' else 'one page each'})"))
        page = lay.part1_start
        for x in dry:
            sides = dry_sides(x["pages"], lay.layout)
            out.append(("item", f"t{x['n']:02d}", x["name"], "", f"{x['pages']} pages", "", f"p. {page}" if sides == 1 else f"p. {page}-{page + sides - 1}"))
            x["page"] = page
            page += sides
    if decks:
        out.append(("head", f"Part 2: flash cards ({sum(k.count for k in decks)} cards on {len(lay.sheets)} sheets, cut along the dashed guides)"))
        first_sheet = {}
        for j, sheet in enumerate(lay.sheets, 1):
            for di, ci in sheet:
                first_sheet.setdefault(di, j)
        last_sheet = {}
        for j, sheet in enumerate(lay.sheets, 1):
            for di, ci in sheet:
                last_sheet[di] = j
        for di, k in enumerate(decks):
            if not k.count:
                continue
            a, b = first_sheet[di], last_sheet[di]
            pa = lay.part2_start + 2 * (a - 1)
            sheets = f"sheet {a}" if a == b else f"sheets {a}-{b}"
            name = k.name if len(k.name) <= 30 else k.name[:28].rstrip() + ".."
            out.append(("item", f"t{k.d['n']:02d}", name, "", f"{k.count} cards", sheets, f"p. {pa}"))
            k.first_page = pa
    return out


def plan(dry, decks, a, g):
    """the page numbers and the lines of the contents page (the contents may take two pages, and the numbers depend on that: two passes)"""
    lay = Layout(dry, decks, a, g)
    lay.finish(0)
    lay.finish(len(toc_entries(dry, decks, lay, g)))
    return lay, toc_entries(dry, decks, lay, g)


# ------------------------------------------------------------------ drawing
GREY = (0.45, 0.45, 0.45)
LIGHT = (0.725, 0.725, 0.725)
INK = (0.11, 0.25, 0.58)


def put(page, x, y, text, size=9.0, font="helv", color=(0.12, 0.12, 0.12)):
    page.insert_text((x, y), text, fontsize=size, fontname=font, color=color)


def draw_cover(out, lay, dry, decks, a, g, stats):
    page = out.new_page(width=595, height=842)
    put(page, 50, 92, BOOK_TITLE, 24, "hebo", INK)
    put(page, 50, 116, "every trace sheet and every flash card, in one PDF", 13, "helv", GREY)
    y = 160
    put(page, 50, y, "What is inside", 12, "hebo", INK)
    y += 18
    lines = []
    if dry:
        lines.append(f"Part 1: {len(dry)} trace sheets ({sum(x['pages'] for x in dry)} pages, printed "
                     + ("two to a side" if lay.layout == "2up" else "one page each") + f"), pages {lay.part1_start}-{lay.part1_start + lay.part1 - 1}")
    if decks:
        lines.append(f"Part 2: {sum(k.count for k in decks)} flash cards from {len(decks)} decks, packed on {len(lay.sheets)} sheets "
                     f"(front and back), pages {lay.part2_start}-{lay.part2_start + lay.part2 - 1}")
    lines.append(f"{lay.total} pages = {math.ceil(lay.total / 2)} sheets of paper printed on both sides")
    for ln in lines:
        put(page, 62, y, "- " + ln, 10)
        y += 15
    y += 10
    put(page, 50, y, "Paper saved", 12, "hebo", INK)
    y += 18
    sep, book = stats["separate"], stats["book"]
    put(page, 62, y, f"- printing the {stats['files']} separate PDFs: {sep} sheets (every small deck takes a whole sheet)", 10)
    y += 15
    put(page, 62, y, f"- printing this book: {book} sheets  ->  " + (f"{sep - book} sheets ({100 * (sep - book) / max(sep, 1):.0f} %) saved" if sep >= book else f"{book - sep} more (the cover and contents pages)"), 10)
    y += 25
    put(page, 50, y, "How to print", 12, "hebo", INK)
    y += 18
    tips = ["A4, Actual size / 100 % (never Fit to page), in colour, BOTH sides, flip on the LONG edge.",
            "Part 2 starts on a front side on purpose: every card back lands behind its front. Do not remove pages.",
            "Cut the cards along the dashed guides (Part 2 only), 4 cards per sheet. Hold a printed sheet to the light to check front and back.",
            "No duplex printer: print the odd pages, put the stack back in the tray (same orientation) and print the even pages."]
    if lay.layout == "2up":
        tips.insert(1, "2up: each side holds two sheet pages turned 90 degrees; turn the sheet to read it (left page = upper half). Pages print at 71 %.")
    for t in tips:
        put(page, 62, y, "- " + t, 9.2)
        y += 14
    y += 18
    put(page, 50, y, "Rebuild it (no Claude needed)", 12, "hebo", INK)
    y += 18
    put(page, 62, y, "python .claude/skills/java-flashcards/scripts/rebuild_cards.py --book " + ("--dry-layout 2up " if lay.layout == "2up" else "") + "[topics]", 8.2, "cour")
    y += 14
    put(page, 62, y, "The book is built from the PDFs in docs/interview/print/: rebuild_cards.py all after changing a deck, build_dry.py --all for the sheets.", 9.2)
    put(page, 50, 810, f"Generated {datetime.date.today().isoformat()}  |  {os.path.basename(a.out_path)}", 8, "helv", GREY)


def draw_toc(out, entries, lay):
    for pg in range(lay.toc_pages):
        page = out.new_page(width=595, height=842)
        put(page, 50, 56, "Contents" + (f" ({pg + 1}/{lay.toc_pages})" if lay.toc_pages > 1 else ""), 18, "hebo", INK)
        y = TOP_Y
        for e in entries[pg * LINES_PER_PAGE:(pg + 1) * LINES_PER_PAGE]:
            if e[0] == "head":
                y += 5
                put(page, 50, y, e[1], 10.5, "hebo", INK)
                page.draw_line((50, y + 3), (545, y + 3), color=LIGHT, width=0.5)
            else:
                _, code, name, deck, count, sheets, pages = e
                put(page, 52, y, code, 9, "cour")
                put(page, 92, y, name, 9)
                put(page, 262, y, deck, 9)
                put(page, 372, y, count, 9, color=GREY)
                put(page, 420, y, sheets, 9, color=GREY)
                put(page, 490, y, pages, 9, "hebo")
            y += LINE_H


def draw_notes(out):
    page = out.new_page(width=595, height=842)
    put(page, 50, 56, "Notes", 18, "hebo", INK)
    for i in range(1, 36):
        page.draw_line((50, 70 + i * 21), (545, 70 + i * 21), color=(0.82, 0.88, 0.95), width=0.6)


def draw_guides_and_label(page, g, idx, total, side):
    if side == "front" and g.cut_guides:
        xs = sorted({g.x0 + k * (g.card_w + g.gap) + o for k in range(g.cols) for o in (0, g.card_w)})
        ys = sorted({g.y0 + k * (g.card_h + g.gap) + o for k in range(g.rows) for o in (0, g.card_h)})
        dash = f"[{1.2 * MM:.2f} {1.2 * MM:.2f}] 0"
        for x in xs:
            page.draw_line((x * MM, 1.5 * MM), (x * MM, (g.page_h - 1.5) * MM), color=LIGHT, width=0.18 * MM, dashes=dash)
        for y in ys:
            page.draw_line((1.5 * MM, y * MM), ((g.page_w - 1.5) * MM, y * MM), color=LIGHT, width=0.18 * MM, dashes=dash)
    if g.sheet_label:
        label = f"FRONT {idx}/{total}" if side == "front" else f"BACK {idx}/{total} (mirrored)"
        w = pymupdf().get_text_length(label, fontname="helv", fontsize=5.5)
        put(page, (g.page_w * MM - w) / 2, 1.3 * MM + 5, label, 5.5, "helv", (0.67, 0.67, 0.67))


def add_dry_pages(out, dry, lay, g):
    m = pymupdf()
    for x in dry:
        src = x["doc"]
        if lay.layout == "full":
            out.insert_pdf(src)
            continue
        for side in two_up_sides(len(src)):
            ref = src[side[0]].rect
            page = out.new_page(width=ref.width, height=ref.height)
            h = ref.height / 2
            for slot, pno in enumerate(side):
                page.show_pdf_page(m.Rect(0, slot * h, ref.width, (slot + 1) * h), src, pno, rotate=-90)
            page.draw_line((6, h), (ref.width - 6, h), color=LIGHT, width=0.6, dashes="[4 4] 0")


def add_card_sheets(out, decks, lay, g, size):
    """pack the faces: returns the placements (target page, target rect, source deck, source page, source rect) for the check"""
    placed = []
    pw, ph = size
    total = len(lay.sheets)
    for j, sheet in enumerate(lay.sheets, 1):
        for side in ("front", "back"):
            page = out.new_page(width=pw, height=ph)
            pno = out.page_count - 1
            for k, (di, ci) in enumerate(sheet):
                s, kk = source_slot(ci, g.per)
                if side == "front":
                    dst, srcr = g.rect(*g.slot(k)), g.rect(*g.slot(kk))
                else:
                    dst, srcr = g.rect(*g.back_slot(k)), g.rect(*g.back_slot(kk))
                src_pno = 2 * s + (0 if side == "front" else 1)
                page.show_pdf_page(dst, decks[di].doc, src_pno, clip=srcr)
                placed.append((pno, dst, di, src_pno, srcr))
            draw_guides_and_label(page, g, j, total, side)
    return placed


# ------------------------------------------------------------------ the check
def verify(out_path, decks, placed):
    """render every face of the book and its face in the source PDF, each from its own top-left corner at low resolution: the two must be the same picture
    (a face that sits in another slot is sampled on the same pixel grid, so a correct copy differs by about 0, a displaced one by a lot)"""
    import numpy as np
    m = pymupdf()
    book = m.open(out_path)
    worst, bad = 0.0, []

    zoom = VERIFY_DPI / 72

    def face(doc, page_no, r):
        mat = m.Matrix(zoom, 0, 0, zoom, -r.x0 * zoom, -r.y0 * zoom)         # the face's top-left corner lands on pixel (0, 0): the same pixel grid for every slot
        pix = doc[page_no].get_pixmap(matrix=mat, clip=r, colorspace=m.csRGB, alpha=False)
        return np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, 3).astype(np.int16)

    for pno, dst, di, src_pno, srcr in placed:
        a, b = face(book, pno, dst), face(decks[di].doc, src_pno, srcr)
        h, w = min(a.shape[0], b.shape[0]), min(a.shape[1], b.shape[1])
        diff = float(np.abs(a[:h, :w] - b[:h, :w]).mean())
        worst = max(worst, diff)
        if diff > VERIFY_MAX_DIFF:
            bad.append((pno + 1, decks[di].title, src_pno + 1, round(diff, 2)))
    book.close()
    return worst, bad


# ------------------------------------------------------------------ main
def build(a, log=print):
    m = pymupdf()
    warnings = []
    warn = warnings.append
    g = Geometry.from_config(a.config)
    dry, decks = collect(a.patterns, a.only, g, warn, getattr(a, "pdf_of", None))
    if not dry and not decks:
        raise BookError("nothing to put into the book (see the warnings above)" if warnings else "nothing to put into the book")
    lay, entries = plan(dry, decks, a, g)
    stats = dict(files=len(dry) + len(decks), book=math.ceil(lay.total / 2),
                 separate=separate_sheets([x["pages"] for x in dry], [k.count for k in decks], g.per))
    a.out_path = os.path.join(os.path.abspath(a.out), a.name or (BOOK_FILE + ("_2up.pdf" if a.dry_layout == "2up" else ".pdf")))
    if a.list:
        log(f"trace sheets: {len(dry)}   decks: {len(decks)}   cards: {sum(k.count for k in decks)}")
        for x in dry:
            log(f"  sheet  t{x['n']:02d} {x['name']:<40}{x['pages']:>2} pages   built {time.strftime('%Y-%m-%d %H:%M', time.localtime(os.path.getmtime(x['path'])))}")
        for k in decks:
            log(f"  cards  {k.d['stem']:<42}{k.count:>3} cards   built {time.strftime('%Y-%m-%d %H:%M', time.localtime(os.path.getmtime(k.path)))}")
        log(f"book: {lay.total} pages ({lay.front} front matter, {lay.part1} trace sheets, {lay.part2} cards) = {stats['book']} sheets of paper;  "
            f"printing the {stats['files']} separate PDFs takes {stats['separate']} sheets: {saved_text(stats['separate'], stats['book'])}")
        for w in warnings:
            log("warning:", w)
        return 0, a.out_path
    size = (decks[0].doc[0].rect.width, decks[0].doc[0].rect.height) if decks else (g.page_w * MM, g.page_h * MM)
    out = m.open()
    if lay.cover:
        draw_cover(out, lay, dry, decks, a, g, stats)
        draw_toc(out, entries, lay)
    if lay.pad:
        draw_notes(out)
    add_dry_pages(out, dry, lay, g)
    placed = add_card_sheets(out, decks, lay, g, size) if decks else []
    assert out.page_count == lay.total, (out.page_count, lay.total)
    toc = []
    if lay.cover:
        toc += [[1, "Cover", 1], [1, "Contents", 2]]
    if dry:
        toc.append([1, "Part 1: trace sheets", lay.part1_start])
        toc += [[2, f"t{x['n']:02d} {x['name']}", x["page"]] for x in dry]
    if decks:
        toc.append([1, "Part 2: flash cards", lay.part2_start])
        toc += [[2, f"{k.title} ({k.count} cards)", k.first_page] for k in decks if k.count]
    out.set_toc(toc)
    out.set_metadata({"title": BOOK_TITLE if dry else "Java and microservices flash cards, all decks", "subject": "trace sheets and flash cards" if dry else "flash cards", "creator": "make_book.py"})
    os.makedirs(os.path.dirname(a.out_path), exist_ok=True)
    tmp = a.out_path + ".tmp"
    out.save(tmp, garbage=4, deflate=True, clean=True)
    out.close()
    os.replace(tmp, a.out_path)
    result = dict(pages=lay.total, sheets=stats["book"], separate=stats["separate"], cards=sum(k.count for k in decks), dry=len(dry), decks=len(decks),
                  card_sheets=len(lay.sheets), card_sheets_separate=sum(math.ceil(k.count / g.per) for k in decks), path=a.out_path)
    a.result = result
    if a.verify and placed:
        worst, bad = verify(a.out_path, decks, placed)
        result.update(faces=len(placed), worst=round(worst, 3), bad=bad)
        if bad:
            raise BookError(f"{len(bad)} card faces of the book differ from their source faces (first: {bad[0]}); the book was written to {a.out_path} but must not be printed")
    if getattr(a, "contents_file", None):
        write_contents(a.contents_file, decks, lay, g, result)
        result["contents"] = a.contents_file
    for w in warnings:
        log("warning:", w)
    log(f"book: {a.out_path}  ({os.path.getsize(a.out_path) / 1e6:.1f} MB)")
    log(f"  {lay.total} pages = {stats['book']} sheets of paper: {len(dry)} trace sheets"
        f" ({'2up' if a.dry_layout == '2up' else 'full size'}), {result['cards']} cards on {len(lay.sheets)} sheets, {lay.front} front matter pages")
    log(f"  the {stats['files']} separate PDFs would take {stats['separate']} sheets: {saved_text(stats['separate'], stats['book'])}"
        + (f";  {result['faces']} card faces checked against the sources (worst difference {result['worst']} of 255)" if "faces" in result else ""))
    return 0, a.out_path


def write_contents(path, decks, lay, g, result):
    """a small markdown table next to a cards-only PDF: which deck is on which sheets (the labels FRONT n/N in the margin of the PDF)"""
    first, last = {}, {}
    for j, sheet in enumerate(lay.sheets, 1):
        for di, ci in sheet:
            first.setdefault(di, j)
            last[di] = j
    lines = ["# Flash cards of all decks, packed on shared sheets", "",
             f"{result['cards']} cards from {result['decks']} decks on {result['card_sheets']} sheets ({2 * result['card_sheets']} pages, front and back). "
             "The decks run into each other, so only the very last sheet can have empty slots. "
             f"Printing the decks one by one would take {result['card_sheets_separate']} sheets: "
             f"{saved_text(result['card_sheets_separate'], result['card_sheets'])}.", "",
             "Print on A4, 100 % (never Fit to page), in colour, BOTH sides, flip on the LONG edge; cut along the dashed guides. "
             "Page 1 is a front side, so every card back lands behind its front.", "",
             "| Deck | Cards | Sheets | First page |", "|---|---|---|---|"]
    for di, k in enumerate(decks):
        if not k.count:
            continue
        a, b = first[di], last[di]
        lines.append(f"| t{k.d['n']:02d} {k.name} | {k.count} | {a if a == b else f'{a}-{b}'} | {2 * (a - 1) + 1} |")
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def combine_cards(pdf_of, out_dir, config=None, log=print, verify=True, selectors=("all",), name="FlashCards_All.pdf"):
    """the cards of the given decks (default: all) packed onto shared sheets, nothing else: no cover, no contents page, page 1 is a front side.
    pdf_of(deck) = where the PDF of a deck is. Writes <out_dir>/<name> and <name>_contents.md; returns the numbers (cards, sheets, sheets when printed one deck at a time)."""
    a = argparse.Namespace(patterns=list(selectors), dry_layout="full", only="cards", out=out_dir, name=name, no_cover=True, config=config,
                           verify=verify, list=False, pdf_of=pdf_of, contents_file=os.path.join(os.path.abspath(out_dir), os.path.splitext(name)[0] + "_contents.md"))
    build(a, log)
    return a.result


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("patterns", nargs="*", help="2 | t02 | 1 3 | 1-3 | all  (default: all)")
    ap.add_argument("--dry-layout", choices=("full", "2up"), default="full")
    ap.add_argument("--only", choices=("both", "sheets", "cards"), default="both")
    ap.add_argument("--out", default=os.path.join(REPO, "book"))
    ap.add_argument("--name")
    ap.add_argument("--no-cover", action="store_true")
    ap.add_argument("--config")
    ap.add_argument("--no-verify", dest="verify", action="store_false")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--install-deps", action="store_true")
    a = ap.parse_args(argv)
    if a.install_deps:
        import subprocess
        os.makedirs(DI.pylib_dir(), exist_ok=True)
        r = subprocess.run([sys.executable, "-m", "pip", "install", "--target", DI.pylib_dir(), "pymupdf"])
        if r.returncode:
            raise BookError("pip failed (no network?)")
        return 0
    t0 = time.time()
    rc, _ = build(a)
    print(f"done in {time.time() - t0:.0f} s")
    return rc


if __name__ == "__main__":
    sys.exit(main())

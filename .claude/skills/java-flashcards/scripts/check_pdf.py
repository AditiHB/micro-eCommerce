"""Structure check of the finished PDF with pypdf: page count, paper size, embedded fonts, how many cards each page carries and that
every card is complete: when <cards>.expect.json sits next to the PDF (gen_flash.py writes it), each front page must carry the bottom line
of every card on its sheet (the deck's FLIP_TEXT) and each back page the deck's TRAP_LABEL line, so a card whose lower part was cut off by the
print (or a card that was not placed at all) is caught.

usage: python check_pdf.py <cards.pdf> [expected_pages]
Do NOT use pypdf text coordinates to test margins: text inside the inline SVG diagrams gets wrong positions (nested transforms).
Margins are verified in the browser by flash_report.py, geometry by align_check.py.
"""
import json
import os
import re
import sys

from pypdf import PdfReader

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import flashconfig  # noqa: E402
from _common import pylib_dir  # noqa: E402

CFG = flashconfig.load()
sys.path.insert(0, pylib_dir())
try:                                                    # PyMuPDF extracts the text of scaled / transformed cards completely; pypdf does not
    import pymupdf
except ImportError:
    try:
        import fitz as pymupdf
    except ImportError:
        pymupdf = None

rd = PdfReader(sys.argv[1])
mu = pymupdf.open(sys.argv[1]) if pymupdf else None
expected = int(sys.argv[2]) if len(sys.argv) > 2 else None
EXPECT = None
_exp = os.path.splitext(sys.argv[1])[0] + ".expect.json"
if os.path.exists(_exp):
    EXPECT = json.load(open(_exp))
    if expected is None:
        expected = 2 * EXPECT["sheets"]
ok = True
print("pages:", len(rd.pages))
if expected and len(rd.pages) != expected:
    ok = False
    print("!! expected", expected, "pages")
for i, p in enumerate(rd.pages, 1):
    w, h = float(p.mediabox.width) / 72 * 25.4, float(p.mediabox.height) / 72 * 25.4
    fonts = {}
    for _, f in (p["/Resources"].get("/Font") or {}).items():
        f = f.get_object()
        desc = f.get("/DescendantFonts")
        emb = True                                          # Type3 fonts carry their glyphs inline
        if desc:
            fd = desc[0].get_object().get("/FontDescriptor")
            emb = fd is not None and any(x in fd.get_object() for x in ("/FontFile", "/FontFile2", "/FontFile3"))
        fonts[str(f.get("/BaseFont"))] = emb
    print(f"page {i} {'front' if i % 2 else 'back '} {w:.1f} x {h:.1f} mm  fonts embedded: {all(fonts.values())}")
    if abs(w - CFG.page_w) > 0.3 or abs(h - CFG.page_h) > 0.3 or not all(fonts.values()):
        ok = False
    if EXPECT and mu is None and i == 1:
        print("note: PyMuPDF is not installed, the check that every card is complete was skipped (rebuild_cards.py --install-deps)")
    if EXPECT and mu is not None and (i + 1) // 2 <= len(EXPECT["cards_on_sheet"]):
        want = EXPECT["cards_on_sheet"][(i + 1) // 2 - 1]
        text = re.sub(r"\s+", " ", mu[i - 1].get_text())
        flip, trap = EXPECT.get("flip_text", "flip for the answer"), EXPECT.get("trap_label", "TRAP")
        have = len(re.findall(re.escape(flip), text)) if i % 2 else len(re.findall(r"\b" + re.escape(trap) + r"\b", text))
        print(f"        complete cards found: {have} of {want}")
        if have != want:
            ok = False
            print(f"!! page {i}: {have} complete cards found ({'flip line' if i % 2 else 'trap line'}), {want} expected: a card is cut off or missing")
print("PDF STRUCTURE OK" if ok else "PDF STRUCTURE PROBLEM")
sys.exit(0 if ok else 1)

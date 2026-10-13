"""Rasterise the finished PDF and prove that every back sits behind its front.

For each sheet the back page is flipped left<->right (what you see through the paper after a long-edge flip) and the bounding box of
the coloured pen border is compared with the front's, card by card. Also checks the page count and size.
usage: python align_check.py <cards.pdf>
needs PyMuPDF:  python rebuild_cards.py --install-deps   (= python -m pip install --target <pylib_dir()> pymupdf; kept out of the global site-packages on purpose)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import WORK, pylib_dir, work  # noqa: E402
import flashconfig  # noqa: E402

CFG = flashconfig.load()

sys.path.insert(0, os.path.join(WORK, "pylib"))          # an old-style per-work-folder install still works
sys.path.insert(0, pylib_dir())
try:
    import pymupdf
except ImportError:
    import fitz as pymupdf
import numpy as np
from PIL import Image

pdf = sys.argv[1]
DPI = 200
doc = pymupdf.open(pdf)
w_mm, h_mm = (v / 72 * 25.4 for v in doc[0].rect[2:])
print(f"pages: {doc.page_count}   page size: {w_mm:.2f} x {h_mm:.2f} mm")
assert doc.page_count % 2 == 0, "odd page count: front/back pairs are broken"
assert abs(w_mm - CFG.page_w) < 0.3 and abs(h_mm - CFG.page_h) < 0.3, f"page is not {CFG.page_w:g} x {CFG.page_h:g} mm"

imgs = []
for i, page in enumerate(doc):
    pix = page.get_pixmap(dpi=DPI)
    im = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
    im.save(work(f"pdf_p{i + 1}.png"))
    imgs.append(im)
W, H = imgs[0].size
sx, sy = W / w_mm, H / h_mm


def border_bbox(im, col, row):
    """bbox in mm, relative to the card's nominal top-left, of the saturated (coloured) pixels = the pen border"""
    x0, y0 = CFG.x0 + col * (CFG.card_w + CFG.gap), CFG.y0 + row * (CFG.card_h + CFG.gap)
    box = (int((x0 - 1) * sx), int((y0 - 1) * sy), int((x0 + CFG.card_w + 1) * sx), int((y0 + CFG.card_h + 1) * sy))
    a = np.asarray(im.crop(box)).astype(int)
    ys, xs = np.where((a.max(2) - a.min(2)) > 70)
    if xs.size == 0:
        return None                                  # an empty slot (the last sheet is not full)
    return ((xs.min() + box[0]) / sx - x0, (xs.max() + box[0]) / sx - x0, (ys.min() + box[1]) / sy - y0, (ys.max() + box[1]) / sy - y0)


worst = 0.0
for sheet in range(1, doc.page_count // 2 + 1):
    front, back = imgs[2 * sheet - 2], imgs[2 * sheet - 1].transpose(Image.FLIP_LEFT_RIGHT)
    for col in range(CFG.cols):
        for row in range(CFG.rows):
            f, b = border_bbox(front, col, row), border_bbox(back, col, row)
            if f is None and b is None:
                continue
            if f is None or b is None:
                print(f"sheet {sheet} cell({col},{row}): only one side has a card"); worst = 99.0
                continue
            d = max(abs(f[k] - b[k]) for k in range(4))
            worst = max(worst, d)
            print(f"sheet {sheet} cell({col},{row}) front L{f[0]:.2f} R{f[1]:.2f} T{f[2]:.2f} B{f[3]:.2f} | back L{b[0]:.2f} R{b[1]:.2f} T{b[2]:.2f} B{b[3]:.2f} | diff {d:.2f} mm")
print(f"WORST front/back border difference: {worst:.2f} mm  (pass < 0.6 mm; the pen wobble alone gives ~0.25)")
sys.exit(0 if worst < 0.6 else 1)

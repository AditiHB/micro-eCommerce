"""Render sheets of the *shot build* to PNG and crop every card, for visual review.

usage: python shot.py <scale> <sheet page numbers...>      e.g.  python shot.py 3 1 2
needs FLASH_WORK/flash_shot.html (python gen_flash.py <work>/flash_shot.html shot)
writes FLASH_WORK/flash_shots/pN.png (whole sheet) and pN_c0.png, pN_c1.png ... (one crop per card slot in reading order:
left to right, then top to bottom; with the default 2 x 2 sheet 0 = top left, 1 = top right, 2 = bottom left, 3 = bottom right)
"""
import os
import subprocess
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import EDGE, edge_profile, file_url, work  # noqa: E402
import flashconfig  # noqa: E402

CFG = flashconfig.load()

scale = float(sys.argv[1])
pages = [int(x) for x in sys.argv[2:]]
os.makedirs(work("flash_shots"), exist_ok=True)
MM = 96 / 25.4
for p in pages:
    out = work(f"flash_shots/p{p}.png")
    if os.path.exists(out):
        os.remove(out)
    subprocess.run([EDGE, "--headless=new", "--disable-gpu", edge_profile(), "--hide-scrollbars", f"--force-device-scale-factor={scale}",
                    f"--window-size={round(CFG.page_w * MM)},{round(CFG.page_h * MM)}", "--virtual-time-budget=6000", f"--screenshot={out}",
                    file_url(work("flash_shot.html")) + f"#p{p}"], capture_output=True)
    im = Image.open(out)
    print("page", p, im.size)
    sx = im.size[0] / float(round(CFG.page_w * MM))
    for pos in range(CFG.per_sheet):
        col, row = pos % CFG.cols, pos // CFG.cols
        x0, y0 = (CFG.x0 + col * (CFG.card_w + CFG.gap)) * MM * sx, (CFG.y0 + row * (CFG.card_h + CFG.gap)) * MM * sx
        im.crop((round(x0), round(y0), round(x0 + CFG.card_w * MM * sx), round(y0 + CFG.card_h * MM * sx))).save(work(f"flash_shots/p{p}_c{pos}.png"))

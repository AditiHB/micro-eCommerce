"""DOM layout check: open the *check build* of the cards in headless Edge and report, per card face,
the free space before the footer, overflow and the distance of the content to the cut line.

usage: python flash_report.py [check.html]      (default FLASH_WORK/flash_check.html)
writes FLASH_WORK/flash_report.json ; exits 1 when a face needs attention
"""
import html as H
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import EDGE, WORK, edge_profile, file_url, work  # noqa: E402
import flashconfig  # noqa: E402

SAFE = flashconfig.load().safe_mm       # the content must stay this far (mm) inside the card edge (content_inset - 0.3)

page = sys.argv[1] if len(sys.argv) > 1 else work("flash_check.html")
out = subprocess.run([EDGE, "--headless=new", "--disable-gpu", edge_profile(), "--virtual-time-budget=8000", "--dump-dom", file_url(page)],
                     capture_output=True, text=True, encoding="utf-8").stdout
found = re.findall(r"REPORT_BEGIN(\{.*?\})REPORT_END", out, re.S)
if not found:
    raise SystemExit("NO REPORT in the dumped DOM (was the page generated with the 'check' flag?)")
rep = json.loads(H.unescape(found[-1]))          # the last match is the rendered <pre>, the first is the script source

print("card side  slack_mm  gapBeforeFoot  ovY ovX   L     R     T     B")
bad = 0
for r in rep["faces"]:
    flag = ""
    if (float(r["gap_before_foot_mm"]) < 0.4 or r["overflow_y"] > 1 or r["overflow_x"] > 0
            or min(float(r[k]) for k in ("left_mm", "right_mm", "top_mm", "bottom_mm")) < SAFE):
        flag = "  <-- CHECK"
        bad += 1
    print(f'{r["card"]:>4} {r["side"]:<5} {r["slack_mm"]:>7} {r["gap_before_foot_mm"]:>12} {r["overflow_y"]:>5}{r["overflow_x"]:>4} '
          f'{r["left_mm"]:>6} {r["right_mm"]:>5} {r["top_mm"]:>5} {r["bottom_mm"]:>5}{flag}')
print("faces needing attention:", bad)
json.dump(rep, open(work("flash_report.json"), "w"))
sys.exit(1 if bad else 0)

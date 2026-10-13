"""Pick the largest text scale step (--k) at which every card face still fits, and write FLASH_WORK/flash_scales.json.

Every face is rendered at k = 1.0 .. 1.4; a step is accepted when the free gap before the footer is >= 1.8 mm, nothing overflows and
the content stays safe_mm (content_inset - 0.3, 4.7 mm by default) inside the card edge. Fronts are capped at 1.3, backs at 1.25.
The step is relative to the text size of the card design; the whole card is already scaled by the settings (flashcards.config.md: scale).
When a face does not fit even at step 1.0 the sweep also tries smaller steps (0.95 .. 0.5) and says which cards had to shrink:
shorten their text to get bigger letters back.
usage: python sweep.py
"""
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import SCRIPTS, WORK, work  # noqa: E402
import flashconfig  # noqa: E402

CFG = flashconfig.load()
KS_UP = ["1.0", "1.05", "1.1", "1.15", "1.2", "1.25", "1.3", "1.35", "1.4"]
KS_DOWN = ["0.95", "0.9", "0.85", "0.8", "0.75", "0.7", "0.65", "0.6", "0.55", "0.5"]
CAP = {"front": 1.3, "back": 1.25}
GEN = os.path.join(SCRIPTS, "gen_flash.py")
REPORT = os.path.join(SCRIPTS, "flash_report.py")

data = {}


def measure(k):
    env = dict(os.environ, FLASH_K=k, FLASH_IGNORE="1", FLASH_WORK=WORK)       # ignore any old scales file
    subprocess.run([sys.executable, GEN, work("flash_check.html"), "check"], env=env, cwd=WORK, capture_output=True, check=True)
    subprocess.run([sys.executable, REPORT], env=env, cwd=WORK, capture_output=True)
    data[k] = {(r["card"], r["side"]): r for r in json.load(open(work("flash_report.json")))["faces"]}


def fits(r, k):
    return (float(r["gap_before_foot_mm"]) >= 1.8 and r["overflow_y"] <= 1 and r["overflow_x"] <= 0
            and min(float(r[x]) for x in ("left_mm", "right_mm", "top_mm")) >= CFG.safe_mm and float(k) <= CAP[r["side"]])


for k in KS_UP:
    measure(k)
keys = sorted(data["1.0"], key=lambda t: (int(t[0]), t[1]))
if any(not any(fits(data[k][key], k) for k in KS_UP) for key in keys):
    for k in KS_DOWN:
        measure(k)
order = sorted(data, key=float)

scales = {"_config": flashconfig.signature(CFG)}
shrunk = []
for key in keys:
    best = None
    for k in order:
        if fits(data[k][key], k):
            best = k
    if best is None:
        raise SystemExit(f"card {key} does not fit even at half the design text size: shorten its text, or use a bigger card")
    scales[f"{key[0]}-{key[1]}"] = float(best)
    if float(best) < 1.0:
        shrunk.append(f"{key[0]}-{key[1]} (x{best})")
    print(f"{key[0]:>3} {key[1]:<5} k={best}  gap={data[best][key]['gap_before_foot_mm']} mm")
if shrunk:
    print("NOTE: these faces needed text smaller than the design size: " + ", ".join(shrunk) + ". Shorten their text if you want bigger letters.")
json.dump(scales, open(work("flash_scales.json"), "w"), indent=1)

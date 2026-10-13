"""One command for the whole pipeline.

usage: python build_all.py <name> --deck decks/<deck>.py [--config my.config.md] [--generator path/to/gen_flash.py]
  0. flashconfig.py                         prints the resolved settings (paper, cards per sheet, margins, fonts) and stops on a settings error
  1. python gen_flash.py <check build>      -> FLASH_WORK/flash_check.html
  2. sweep.py                               -> FLASH_WORK/flash_scales.json   (largest text scale that fits, per face)
  3. gen_flash.py <final build>             -> FLASH_WORK/<name>.html
  4. headless Edge print-to-pdf             -> FLASH_WORK/<name>.pdf
  5. flash_report.py, check_pdf.py, align_check.py   (any failure stops the run with exit code 1)
The deck file holds the cards (see decks/t01_event_driven_consistency.py); the engine gen_flash.py stays untouched.
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import SCRIPTS, WORK, print_pdf, work  # noqa: E402
import flashconfig  # noqa: E402

name = sys.argv[1] if len(sys.argv) > 1 else "FlashCards"
gen = os.path.join(SCRIPTS, "gen_flash.py")
if "--generator" in sys.argv:
    gen = os.path.abspath(sys.argv[sys.argv.index("--generator") + 1])
if "--deck" not in sys.argv:
    raise SystemExit("usage: build_all.py <name> --deck decks/<deck>.py")
deck = os.path.abspath(sys.argv[sys.argv.index("--deck") + 1])
env = dict(os.environ, FLASH_WORK=WORK, FLASH_DECK=deck)
if "--config" in sys.argv:
    env["FLASH_CONFIG"] = os.path.abspath(sys.argv[sys.argv.index("--config") + 1])
    os.environ["FLASH_CONFIG"] = env["FLASH_CONFIG"]
try:
    print(flashconfig.describe(flashconfig.load()), flush=True)
except flashconfig.ConfigError as e:
    raise SystemExit("SETTINGS ERROR: " + str(e))


def run(*args):
    print("\n$", " ".join(os.path.basename(a) if os.path.exists(a) else a for a in args))
    r = subprocess.run([sys.executable, *args], env=env, cwd=WORK)
    if r.returncode:
        raise SystemExit(f"step failed: {args[0]}")


run(gen, work("flash_check.html"), "check")
run(os.path.join(SCRIPTS, "sweep.py"))
run(gen, work("flash_check.html"), "check")                      # re-check with the chosen scales
run(os.path.join(SCRIPTS, "flash_report.py"))
run(gen, work(name + ".html"))
print_pdf(work(name + ".html"), work(name + ".pdf"))
run(os.path.join(SCRIPTS, "check_pdf.py"), work(name + ".pdf"))
run(os.path.join(SCRIPTS, "align_check.py"), work(name + ".pdf"))
print(f"\nDONE: {work(name + '.pdf')}")

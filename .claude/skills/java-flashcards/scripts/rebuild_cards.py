"""Rebuild the flash cards of one topic, a few topics or ALL topics from the terminal. No Claude, no network needed.

usage (from the repository root):
  python .claude/skills/java-flashcards/scripts/rebuild_cards.py 2            one topic (2, t02 or t02_resilience_and_communication)
  python .../rebuild_cards.py 1 3                                              several topics
  python .../rebuild_cards.py 1-3                                              a range
  python .../rebuild_cards.py all                                              every deck
One deck per topic: decks/tNN_<slug>.py, built into docs/interview/print/tNN_FlashCards.{pdf,html}.
  python .../rebuild_cards.py --list                                           which decks exist and what is already built
  python .../rebuild_cards.py --book [topics] [--dry-layout 2up] [--only cards|sheets]   NOT a rebuild: bind every trace sheet and every card into ONE PDF, packed
                                                                               with no empty slots (book/Interview_Book.pdf); needs no browser. See make_book.py --help
options:
  --jobs N            decks built side by side (default: half of the CPUs, at most 4; each build runs a headless browser)
  --config FILE       settings file (paper, card size, margins, fonts); default: flashcards.config.md of the skill
  --out DIR           put tNN_FlashCards.pdf/.html into DIR instead of docs/interview/print/ (e.g. one folder to print from)
  --no-copy           build only, copy nothing (the files stay in the work folder)
  --work DIR          where the build folders go (default: <temp>/flashcards-work/<deck>)
  --skip-unchanged    skip a deck whose source, settings and engine did not change since its last successful build
  --no-combine        with `all` only: do not write the packed cards of all decks (see below); --no-copy also skips it
  --install-deps      one-time: pip install PyMuPDF into ~/.cache/handwritten-flashcards/pylib (needs the network once)
  --test              run the regression tests of this tooling instead (about 40 s, no browser); --test --e2e adds the browser tier (about 4 min)
  --update-golden     approve the current deck output as the new golden output of the tests (after an intended change)
`all` (every deck in one request) ALSO packs the cards of all decks onto shared sheets, so a small deck no longer leaves empty card slots:
<out>/FlashCards_All.pdf + FlashCards_All_contents.md, where <out> is --out DIR (then the per-deck PDFs are in the same folder: one folder for everything) or book/ in the
repository root (git-ignored). Only for `all`: one deck, a few or a range build exactly as before. The packed PDF is checked face by face against the deck PDFs (make_book.py).
For every deck this runs build_all.py: fit every card, build the PDF, check the structure and that every back lines up with its front.
A deck is copied into the print folder only when ALL checks pass. Exit code 0 = every selected deck passed.
"""
import argparse
import concurrent.futures
import hashlib
import importlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
SKILL = os.path.dirname(HERE)
DECKS = os.path.join(SKILL, "decks")
REPO = os.path.abspath(os.path.join(SKILL, "..", "..", ".."))
sys.path.insert(0, HERE)
if "--book" in sys.argv[1:]:                  # the book (every trace sheet and card in one PDF) needs no browser: hand over before _common looks for one
    import make_book
    sys.exit(make_book.main([a for a in sys.argv[1:] if a != "--book"]))
import _common  # noqa: E402  (needs the browser: checked in check_deps first, SystemExit caught there)
PYLIB = _common.pylib_dir()

print_lock = threading.Lock()


def say(*a):
    with print_lock:
        print(*a, flush=True)


# ------------------------------------------------------------------ what can be built
from deck_index import all_decks, select, docs_dir, deck_title  # noqa: E402,F401  (shared with make_book.py)


def list_decks(decks):
    print(f"{'#':>2}  {'deck':<44}{'built (docs/interview/print)'}")
    for d in decks:
        pdf = os.path.join(docs_dir(d), f"{d['prefix']}_FlashCards.pdf")
        have = time.strftime("%Y-%m-%d %H:%M", time.localtime(os.path.getmtime(pdf))) if os.path.exists(pdf) else "missing"
        print(f"{d['n']:>2}  {d['stem']:<44}{have}   {deck_title(d)}")
    print(f"\nsettings: {os.path.join(SKILL, 'flashcards.config.md')}   work folders: {os.path.join(tempfile.gettempdir(), 'flashcards-work')}")


# ------------------------------------------------------------------ dependencies
def check_deps(install=False):
    """everything the pipeline needs; PyMuPDF lives in a per-user folder outside the repository (and outside the global Python)"""
    if install:
        os.makedirs(PYLIB, exist_ok=True)
        print("installing PyMuPDF into", PYLIB)
        r = subprocess.run([sys.executable, "-m", "pip", "install", "--target", PYLIB, "pymupdf"])
        if r.returncode:
            raise SystemExit("pip failed (no network?). PyMuPDF is only needed once; copy a folder that has it to " + PYLIB)
    sys.path.insert(0, PYLIB)
    problems = []
    for mod, pkg in (("PIL", "pillow"), ("numpy", "numpy"), ("pypdf", "pypdf"), ("fontTools", "fonttools")):
        try:
            importlib.import_module(mod)
        except ImportError:
            problems.append(f"python -m pip install {pkg}")
    try:
        importlib.import_module("pymupdf")
    except ImportError:
        try:
            importlib.import_module("fitz")
        except ImportError:
            problems.append("python rebuild_cards.py --install-deps      (PyMuPDF, once, into " + PYLIB + ")")
    if problems:
        raise SystemExit("missing pieces:\n  " + "\n  ".join(problems))
    if not _common.EDGE or not os.path.exists(_common.EDGE):
        raise SystemExit("no Edge / Chrome found: set the EDGE environment variable to the browser executable")


# ------------------------------------------------------------------ one deck
def stamp_of(deck_path, config_path):
    h = hashlib.md5()
    parts = [deck_path, config_path] + [os.path.join(HERE, f) for f in sorted(os.listdir(HERE)) if f.endswith(".py")]
    for p in parts:
        if os.path.exists(p):
            h.update(open(p, "rb").read())
    return h.hexdigest()


def summary_of(log_text):
    pages = re.findall(r"^pages: (\d+)\s*$", log_text, re.M)
    attention = re.findall(r"faces needing attention: (\d+)", log_text)
    worst = re.findall(r"WORST front/back border difference: ([\d.]+) mm", log_text)
    return dict(pages=pages[0] if pages else "?", attention=attention[-1] if attention else "?", worst=worst[-1] if worst else "?")


def build_one(d, a):
    t0 = time.time()
    nn = d["prefix"]
    work = os.path.join(a.work, d["stem"])
    os.makedirs(work, exist_ok=True)
    cfg = os.path.abspath(a.config) if a.config else os.path.join(SKILL, "flashcards.config.md")
    local = os.path.join(work, "flashcards.config.md")
    if not a.config and os.path.exists(local):          # a per-deck settings file in its work folder wins, like in build_all.py
        cfg = local
    stamp = stamp_of(d["deck"], cfg)
    stamp_file = os.path.join(work, ".stamp")
    out_dir = os.path.abspath(a.out) if a.out else docs_dir(d)
    targets = [os.path.join(out_dir, f"{nn}_FlashCards.{ext}") for ext in ("pdf", "html")]
    res = dict(d=d, ok=False, secs=0, note="", log=os.path.join(work, "build.log"))
    if a.skip_unchanged and os.path.exists(stamp_file) and open(stamp_file).read() == stamp and (a.no_copy or all(os.path.exists(t) for t in targets)):
        res.update(ok=True, skipped=True, note="unchanged, skipped")
        return res
    env = dict(os.environ, FLASH_WORK=work, PYTHONIOENCODING="utf-8", FLASH_PYLIB=PYLIB)
    if a.config:
        env["FLASH_CONFIG"] = cfg
    say(f"[start] {nn} {d['stem']}")
    with open(res["log"], "w", encoding="utf-8") as log:
        r = subprocess.run([sys.executable, os.path.join(HERE, "build_all.py"), "FlashCards", "--deck", d["deck"]],
                           env=env, cwd=work, stdout=log, stderr=subprocess.STDOUT)
    text = open(res["log"], encoding="utf-8", errors="replace").read()
    res.update(summary_of(text))
    res["secs"] = time.time() - t0
    if r.returncode:
        tail = [l for l in text.strip().splitlines() if l.strip()][-3:]
        res["note"] = "FAILED: " + " | ".join(tail)
        return res
    if not a.no_copy:
        os.makedirs(out_dir, exist_ok=True)
        for ext, t in zip(("pdf", "html"), targets):
            shutil.copyfile(os.path.join(work, f"FlashCards.{ext}"), t)
        res["note"] = "copied to " + (os.path.relpath(out_dir, REPO) if out_dir.startswith(REPO) else out_dir)
    else:
        res["note"] = "built in " + work
    open(stamp_file, "w").write(stamp)
    res["ok"] = True
    return res


def covers_all(chosen, decks):
    """True when the request names every deck: `all` (or every topic by hand)"""
    return bool(decks) and {d["stem"] for d in chosen} == {d["stem"] for d in decks}


def built_pdf(d, a):
    """where the PDF of a built deck is: next to the others in --out, else in the print folder (the work folder when it was built but not copied)"""
    name = f"{d['prefix']}_FlashCards.pdf"
    path = os.path.join(os.path.abspath(a.out) if a.out else docs_dir(d), name)
    return path if os.path.exists(path) else os.path.join(a.work, d["stem"], "FlashCards.pdf")


def combine(chosen, a):
    """`all` was asked for: pack the cards of every deck onto shared sheets, into the one output folder"""
    import make_book
    out_dir = os.path.abspath(a.out) if a.out else os.path.join(REPO, "book")
    pdfs = {d["stem"]: built_pdf(d, a) for d in chosen}
    say(f"\npacking the cards of all {len(chosen)} decks onto shared sheets (no empty slots) -> {out_dir}")
    try:
        r = make_book.combine_cards(lambda d: pdfs[d["stem"]], out_dir, a.config, say)
    except SystemExit as e:                           # make_book.BookError
        say(f"COMBINED CARDS FAILED: {e}")
        return 1
    say(f"all cards: {r['cards']} cards on {r['card_sheets']} sheets;  deck by deck they would take {r['card_sheets_separate']} sheets: "
        f"{make_book.saved_text(r['card_sheets_separate'], r['card_sheets'])}")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("topics", nargs="*", help="2 | t02 | t02_resilience_and_communication | 1 3 | 1-3 | all")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--jobs", type=int, default=max(1, min(4, (os.cpu_count() or 2) // 2)))
    ap.add_argument("--config")
    ap.add_argument("--out")
    ap.add_argument("--no-copy", action="store_true")
    ap.add_argument("--work", default=os.path.join(tempfile.gettempdir(), "flashcards-work"))
    ap.add_argument("--skip-unchanged", action="store_true")
    ap.add_argument("--no-combine", action="store_true")
    ap.add_argument("--install-deps", action="store_true")
    ap.add_argument("--test", action="store_true")
    ap.add_argument("--e2e", action="store_true")
    ap.add_argument("--update-golden", action="store_true")
    a = ap.parse_args()
    if a.test or a.update_golden:
        check_deps()
        cmd = [sys.executable, os.path.join(SKILL, "tests", "run_tests.py")] + (["--e2e"] if a.e2e else []) + (["--update-golden"] if a.update_golden else [])
        return subprocess.call(cmd)
    decks = all_decks()
    if a.list:
        list_decks(decks)
        return 0
    if a.install_deps:
        check_deps(install=True)
        print("dependencies OK")
        if not a.topics:
            return 0
    chosen = select(a.topics, decks)
    check_deps()
    if a.config and not os.path.exists(a.config):
        raise SystemExit("settings file not found: " + a.config)
    a.work = os.path.abspath(a.work)
    try:
        os.makedirs(a.work, exist_ok=True)
    except OSError as e:
        raise SystemExit(f"cannot use the work folder {a.work}: {e}")
    try:                                              # show the layout once, and stop early on a settings mistake
        import flashconfig
        os.environ["FLASH_WORK"] = a.work
        cfg = flashconfig.load(a.config)
        print(flashconfig.describe(cfg))
    except Exception as e:                            # noqa: BLE001
        raise SystemExit("SETTINGS ERROR: " + str(e))
    jobs = max(1, min(a.jobs, len(chosen)))
    say(f"\nbuilding {len(chosen)} deck(s), {jobs} at a time; work folders in {a.work}\n")
    t0 = time.time()
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as pool:
        futs = {pool.submit(build_one, d, a): d for d in chosen}
        for fut in concurrent.futures.as_completed(futs):
            r = fut.result()
            results.append(r)
            d = r["d"]
            if r.get("skipped"):
                say(f"[ skip] t{d['n']:02d} {d['stem']}  {r['note']}")
            elif r["ok"]:
                say(f"[ done] t{d['n']:02d} {d['stem']}  {r['secs']:.0f}s  pages {r['pages']}  faces needing attention {r['attention']}  worst misalignment {r['worst']} mm  -> {r['note']}")
            else:
                say(f"[FAILED] t{d['n']:02d} {d['stem']}  {r['note']}\n         full log: {r['log']}")
    results.sort(key=lambda r: r["d"]["n"])
    failed = [r for r in results if not r["ok"]]
    say(f"\n{len(results) - len(failed)} of {len(results)} decks OK in {(time.time() - t0) / 60:.1f} min" + (f"; FAILED: {', '.join('t%02d' % r['d']['n'] for r in failed)}" if failed else ""))
    if failed:
        return 1
    if covers_all(chosen, decks) and not a.no_combine and not a.no_copy:
        return combine(chosen, a)
    return 0


if __name__ == "__main__":
    sys.exit(main())

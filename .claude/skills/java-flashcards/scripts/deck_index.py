"""Which decks exist and where their files live: shared by rebuild_cards.py (builds the cards) and make_book.py (binds them into one PDF).
No browser and no PyMuPDF needed here, so everything that only has to NAME decks can import this module.

A topic is one numbered unit of the interview curriculum (see ../../java-senior-prep/references/topic-map.md): t01, t02, ...
Its flash cards are decks/tNN_<slug>.py, its trace sheet (optional) is ../java-tracesheets/sheets/tNN_<slug>.py, and the printable PDFs land
side by side in docs/interview/print/ as tNN_FlashCards.pdf / tNN_TraceSheet.pdf.

  all_decks()        every deck file: decks/tNN_<slug>.py
  select(specs, d)   the decks named by 2 | t2 | t02 | 1-3 | event_driven | t01_event_driven_consistency | all
  docs_dir(deck)     the folder that holds the built tNN_FlashCards.pdf of a deck (docs/interview/print)
  deck_title(deck)   the DECK_TITLE of a deck, read from its source (the deck is not executed)
  sheet_specs()      every trace-sheet spec file: {n: path}
  pylib_dir()        the per-user folder with PyMuPDF (pip install --target <folder> pymupdf)
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SKILL = os.path.dirname(HERE)
DECKS = os.path.join(SKILL, "decks")
REPO = os.path.abspath(os.path.join(SKILL, "..", "..", ".."))
PRINT_DIR = os.path.join(REPO, "docs", "interview", "print")
SHEETS = os.path.join(SKILL, "..", "java-tracesheets", "sheets")


def pylib_dir():
    """where PyMuPDF lives (it is kept out of the global Python on purpose): FLASH_PYLIB, else a per-user folder outside the repository"""
    if os.environ.get("FLASH_PYLIB"):
        return os.path.abspath(os.environ["FLASH_PYLIB"])
    return os.path.join(os.path.expanduser("~"), ".cache", "handwritten-flashcards", "pylib")      # not under AppData: some Python launchers virtualise it


# ------------------------------------------------------------------ what can be built
def all_decks():
    """every deck file: decks/tNN_<slug>.py, sorted by topic number"""
    out = []
    if not os.path.isdir(DECKS):
        return out
    for f in sorted(os.listdir(DECKS)):
        m = re.fullmatch(r"t(\d\d)_(\w+)\.py", f)
        if m:
            n = int(m.group(1))
            out.append(dict(n=n, stem=f[:-3], base=f[:-3], deck=os.path.join(DECKS, f), prefix=f"t{n:02d}"))
    return out


def sheet_specs():
    """{topic number: path of its trace-sheet spec}"""
    out = {}
    if os.path.isdir(SHEETS):
        for f in sorted(os.listdir(SHEETS)):
            m = re.fullmatch(r"t(\d\d)_(\w+)\.py", f)
            if m:
                out[int(m.group(1))] = os.path.join(SHEETS, f)
    return out


def select(specs, decks):
    if not specs:
        raise SystemExit("name the topics to build: a number (2), t02, a range (1-3), a name fragment or 'all'.  python rebuild_cards.py --list shows them")
    chosen = []

    def add(d):
        if d not in chosen:
            chosen.append(d)

    for spec in specs:
        s = spec.lower().strip()
        if s == "all":
            for d in decks:
                add(d)
            continue
        m = re.fullmatch(r"t?(\d{1,2})\s*-\s*t?(\d{1,2})", s)
        if m:
            lo, hi = int(m.group(1)), int(m.group(2))
            hit = [d for d in decks if lo <= d["n"] <= hi]
            if not hit:
                raise SystemExit(f"no topic with a deck between {lo} and {hi}")
            for d in hit:
                add(d)
            continue
        m = re.fullmatch(r"t?(\d{1,2})", s)
        if m:
            hit = [d for d in decks if d["n"] == int(m.group(1))]
        else:
            hit = [d for d in decks if d["stem"] == s]
            if not hit:
                hit = [d for d in decks if s in d["stem"]]
                hit = hit if len(hit) == 1 else []
        if not hit:
            raise SystemExit(f"'{spec}' does not name exactly one topic (matches: none, or several).  python rebuild_cards.py --list shows them")
        for d in hit:
            add(d)
    return chosen


def docs_dir(d=None):
    return PRINT_DIR


def deck_title(d):
    """the DECK_TITLE of a deck, read from the source so that no browser, engine or Pillow is needed to name a deck"""
    try:
        text = open(d["deck"], encoding="utf-8").read()
    except OSError:
        return d["stem"]
    m = re.search(r'^DECK_TITLE\s*=\s*(["\'])(.+?)\1', text, re.M)
    return m.group(2) if m else d["stem"]

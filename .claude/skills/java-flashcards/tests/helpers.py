"""Shared helpers of the flash-card regression tests (standard library only: unittest, no pytest)."""
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile

TESTS = os.path.dirname(os.path.abspath(__file__))
SKILL = os.path.dirname(TESTS)
SCRIPTS = os.path.join(SKILL, "scripts")
DECKS = os.path.join(SKILL, "decks")
REPO = os.path.abspath(os.path.join(SKILL, "..", "..", ".."))
PRINT_DIR = os.path.join(REPO, "docs", "interview", "print")
CONFIGS = os.path.join(TESTS, "configs")
GOLDEN = os.path.join(TESTS, "golden.json")
E2E = os.environ.get("FLASH_TEST_E2E") == "1"
FIXTURE = os.path.join(TESTS, "fixtures", "layout12.py")      # 12 plain cards: the layout tests use these, never a real deck

sys.path.insert(0, SCRIPTS)


def docs_html(stem):
    """where the published HTML of a deck sits: docs/interview/print/tNN_FlashCards.html"""
    return os.path.join(PRINT_DIR, f"{stem[:3]}_FlashCards.html")


def deck_path(stem):
    """the file of a deck by its name; 'layout12' is the fixture of the layout tests"""
    return FIXTURE if stem == "layout12" else os.path.join(DECKS, stem + ".py")


def cfg(name):
    """a pinned settings file of the tests (the live flashcards.config.md may change without breaking the golden output)"""
    return os.path.join(CONFIGS, name + ".config.md")


def decks():
    return sorted(f[:-3] for f in os.listdir(DECKS) if re.fullmatch(r"t\d\d_\w+\.py", f))


def sha(path_or_bytes):
    data = path_or_bytes if isinstance(path_or_bytes, bytes) else open(path_or_bytes, "rb").read()
    return hashlib.sha256(data).hexdigest()


def load_golden():
    return json.load(open(GOLDEN, encoding="utf-8")) if os.path.exists(GOLDEN) else {"decks": {}, "variants": {}}


def scales_from_html(path):
    """the fitted text step of every face, read back from a built HTML: {"1-front": 1.2, ...}"""
    text = open(path, encoding="utf-8").read()
    return {f"{n}-{side}": float(k) for n, side, k in re.findall(r'data-card="(\d+)" data-side="(front|back)" style="--k:([0-9.]+)"', text)}


def env_for(work, deck=None, config=None, **extra):
    env = {k: v for k, v in os.environ.items() if k not in ("FLASH_K", "FLASH_IGNORE", "FLASH_CONFIG", "FLASH_DECK", "FLASH_WORK", "FLASH_INKFONT")}
    env["FLASH_WORK"] = work
    env["PYTHONIOENCODING"] = "utf-8"
    if deck:
        env["FLASH_DECK"] = deck
    if config:
        env["FLASH_CONFIG"] = config
    env.update(extra)
    return env


def build_html(deck_path, config, scales, mode=None, work=None):
    """run the engine (no browser) with the given fitted scales; returns (html bytes, completed process)"""
    work = work or tempfile.mkdtemp(prefix="flashtest-")
    with open(os.path.join(work, "flash_scales.json"), "w") as f:
        json.dump(scales or {}, f)
    out = os.path.join(work, "out.html")
    cmd = [sys.executable, os.path.join(SCRIPTS, "gen_flash.py"), out] + ([mode] if mode else [])
    r = subprocess.run(cmd, env=env_for(work, deck_path, config), capture_output=True, text=True, encoding="utf-8", cwd=work)
    html = open(out, "rb").read() if os.path.exists(out) else b""
    return html, r


def first_difference(a, b, width=90):
    """a short human-readable description of where two byte strings first differ"""
    n = min(len(a), len(b))
    i = next((k for k in range(n) if a[k] != b[k]), n)
    sa, sb = a[max(0, i - 30):i + width].decode("utf-8", "replace"), b[max(0, i - 30):i + width].decode("utf-8", "replace")
    return f"first difference at byte {i} (lengths {len(a)} / {len(b)}):\n    now   : ...{sa!r}\n    golden: ...{sb!r}"

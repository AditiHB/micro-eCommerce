"""Shared paths for the flash-card pipeline.

FLASH_WORK  working directory for every generated file (default: current directory)
EDGE        path to msedge.exe / chrome / chromium (default: auto-detected)
FLASH_PYLIB folder with PyMuPDF (pip install --target <folder> pymupdf); default: a per-user folder, see pylib_dir()
"""
import os
import shutil
import subprocess
import time

SCRIPTS = os.path.dirname(os.path.abspath(__file__))
SKILL = os.path.dirname(SCRIPTS)
WORK = os.path.abspath(os.environ.get("FLASH_WORK", os.getcwd()))


from deck_index import pylib_dir  # noqa: E402,F401  (one definition, shared with make_book.py)


def find_browser():
    cands = [
        os.environ.get("EDGE"),
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        shutil.which("msedge"),
        shutil.which("google-chrome"),
        shutil.which("chromium"),
    ]
    for c in cands:
        if c and os.path.exists(c):
            return c
    raise SystemExit("No Edge/Chrome found: set the EDGE environment variable to the browser executable")


EDGE = find_browser()


def edge_profile():
    """a browser profile folder of its own for this work folder, so builds that run side by side never share (and lock) one profile"""
    d = os.path.join(WORK, ".edge-profile")
    os.makedirs(d, exist_ok=True)
    return "--user-data-dir=" + d


def file_url(path):
    return "file:///" + os.path.abspath(path).replace("\\", "/")


def work(name):
    return os.path.join(WORK, name)


def print_pdf(html_path, pdf_path, budget=20000, wait=60):
    """headless print to PDF; the page's own @page rule sets the paper size (from the settings, margin 0)."""
    if os.path.exists(pdf_path):
        os.remove(pdf_path)
    subprocess.run([EDGE, "--headless=new", "--disable-gpu", edge_profile(), f"--virtual-time-budget={budget}", "--no-pdf-header-footer",
                    f"--print-to-pdf={pdf_path}", file_url(html_path)], capture_output=True)
    for _ in range(wait // 2):          # a big page takes a few seconds to appear on disk
        if os.path.exists(pdf_path) and os.path.getsize(pdf_path) > 0:
            return pdf_path
        time.sleep(2)
    raise SystemExit("PDF was not written: " + pdf_path)

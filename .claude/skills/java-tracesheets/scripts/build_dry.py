"""Build trace sheets: HTML -> PDF (headless Edge), structure check, and PNG screenshots of the 4 pages for review.

usage: python build_dry.py <sheet spec .py> <work dir> [name] [--publish]    -> <work>/<name>.html, <name>.pdf, shots/p1..p4.png
       python build_dry.py --all [--publish] [--work DIR]                      every spec in ../sheets/, each in <work>/<spec stem>
--publish  after ALL checks passed, copy the HTML and PDF to docs/interview/print/tNN_TraceSheet.{html,pdf} (NN = the number in the spec file name)
env EDGE = path to msedge.exe / chrome (auto-detected otherwise), FLASH_INKFONT = handwriting .ttf
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
SKILL = os.path.dirname(HERE)
SHEETS = os.path.join(SKILL, "sheets")
REPO = os.path.abspath(os.path.join(SKILL, "..", "..", ".."))
PRINT_DIR = os.path.join(REPO, "docs", "interview", "print")
GEN = os.path.join(HERE, "gen_dryrun.py")


def find_browser():
    for c in (os.environ.get("EDGE"), r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
              r"C:\Program Files\Microsoft\Edge\Application\msedge.exe", shutil.which("msedge"), shutil.which("google-chrome"), shutil.which("chromium")):
        if c and os.path.exists(c):
            return c
    raise SystemExit("No Edge/Chrome found: set EDGE")


def url(p):
    return "file:///" + os.path.abspath(p).replace("\\", "/")


def build(spec, work, name="TraceSheet"):
    """returns True when the generator ran and every check of the PDF passed"""
    os.makedirs(os.path.join(work, "shots"), exist_ok=True)
    env = dict(os.environ, DRY_SPEC=spec)
    html, shot_html, pdf = (os.path.join(work, n) for n in (name + ".html", name + "_shot.html", name + ".pdf"))
    for out, flag in ((html, []), (shot_html, ["shot"])):
        r = subprocess.run([sys.executable, GEN, out, *flag], env=env, capture_output=True, text=True)
        if r.returncode:
            print("generator failed:\n" + r.stderr[-1500:])
            return False
    edge = find_browser()
    profile = "--user-data-dir=" + os.path.join(work, ".edge-profile")
    if os.path.exists(pdf):
        os.remove(pdf)
    subprocess.run([edge, "--headless=new", "--disable-gpu", profile, "--virtual-time-budget=20000", "--no-pdf-header-footer", f"--print-to-pdf={pdf}", url(html)], capture_output=True)
    for _ in range(30):
        if os.path.exists(pdf) and os.path.getsize(pdf) > 0:
            break
        time.sleep(2)
    else:
        print("PDF not written")
        return False
    for p in (1, 2, 3, 4):
        png = os.path.join(work, "shots", f"p{p}.png")
        if os.path.exists(png):
            os.remove(png)
        subprocess.run([edge, "--headless=new", "--disable-gpu", profile, "--hide-scrollbars", "--window-size=794,1123", "--virtual-time-budget=8000",
                        f"--screenshot={png}", url(shot_html) + f"#p{p}"], capture_output=True)
    from pypdf import PdfReader
    rd = PdfReader(pdf)
    ok = len(rd.pages) == 4
    for i, pg in enumerate(rd.pages, 1):
        w, h = float(pg.mediabox.width) / 72 * 25.4, float(pg.mediabox.height) / 72 * 25.4
        fonts = {}
        for _, f in (pg["/Resources"].get("/Font") or {}).items():
            f = f.get_object()
            desc = f.get("/DescendantFonts")
            emb = True
            if desc:
                fd = desc[0].get_object().get("/FontDescriptor")
                emb = fd is not None and any(x in fd.get_object() for x in ("/FontFile", "/FontFile2", "/FontFile3"))
            fonts[str(f.get("/BaseFont"))] = emb
        txt = pg.extract_text() or ""
        if abs(w - 210) > .3 or abs(h - 297) > .3 or not all(fonts.values()) or len(txt.strip()) < 40:
            ok = False
        print(f"page {i}: {w:.1f} x {h:.1f} mm, fonts embedded: {all(fonts.values())}, {len(txt)} text chars")
    print("TRACE SHEET PDF OK (4 pages)" if ok else "TRACE SHEET PDF PROBLEM")
    print("html:", html, "\npdf:", pdf, "\nshots:", os.path.join(work, "shots"))
    return ok


def publish(spec, work, name="TraceSheet"):
    m = re.match(r"t(\d\d)_", os.path.basename(spec))
    if not m:
        raise SystemExit(f"cannot publish {spec}: the spec file name must start with tNN_")
    os.makedirs(PRINT_DIR, exist_ok=True)
    for ext in ("html", "pdf"):
        dst = os.path.join(PRINT_DIR, f"t{m.group(1)}_TraceSheet.{ext}")
        shutil.copyfile(os.path.join(work, f"{name}.{ext}"), dst)
        print("published", os.path.relpath(dst, REPO))


def main(argv):
    do_publish = "--publish" in argv
    args = [a for a in argv if a != "--publish"]
    if "--all" in args:
        base = os.path.join(tempfile.gettempdir(), "tracesheets-work")
        if "--work" in args:
            base = os.path.abspath(args[args.index("--work") + 1])
        specs = sorted(f for f in os.listdir(SHEETS) if re.fullmatch(r"t\d\d_\w+\.py", f))
        if not specs:
            raise SystemExit("no sheet specs in " + SHEETS)
        bad = []
        for f in specs:
            print(f"\n=== {f}")
            work = os.path.join(base, f[:-3])
            if build(os.path.join(SHEETS, f), work):
                if do_publish:
                    publish(os.path.join(SHEETS, f), work)
            else:
                bad.append(f)
        print("\nALL TRACE SHEETS OK" if not bad else "FAILED: " + ", ".join(bad))
        return 1 if bad else 0
    if len(args) < 2:
        raise SystemExit(__doc__)
    spec, work = os.path.abspath(args[0]), os.path.abspath(args[1])
    name = args[2] if len(args) > 2 else "TraceSheet"
    ok = build(spec, work, name)
    if ok and do_publish:
        publish(spec, work, name)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

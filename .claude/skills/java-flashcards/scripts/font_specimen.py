"""Show how every installed font looks on a card, so you can pick the `handwriting_font` / `code_font` of flashcards.config.md.

usage: python font_specimen.py [extra_font_folder ...] [--no-png]
  Looks at the Windows / system font folders, the folders given here and the `font_folder` of the settings file.
  Writes into FLASH_WORK (default: the current folder):
    fonts_handwriting.html / .png   handwriting and casual fonts: the candidates for the cards
    fonts_text.html / .png          clean text fonts (less personality, very readable)
    fonts_code.html / .png          monospace fonts: the candidates for the code box
    fonts.html                      all of the above on one page
  Every font is shown in a column exactly 90 mm wide (the text area of a card) at the real sizes (title 14 pt, question 11 pt,
  steps 9.4 pt, code 7.3 pt), so it wraps like the real card, and what you see on the screen at 100 % zoom is the printed size.
  Beside it: the letters and digits, the symbols the cards print, whether the font has a real bold face (the cards use bold for
  key words; without one the browser smears the regular face) and which characters the cards print that it cannot draw.
  To use a font: put its family name (or the path of its .ttf) into `handwriting_font` in flashcards.config.md.
"""
import html
import os
import re
import subprocess
import sys
import urllib.parse

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import EDGE, edge_profile, file_url, work  # noqa: E402
import flashconfig  # noqa: E402

args = [a for a in sys.argv[1:] if not a.startswith("--")]
PNG = "--no-png" not in sys.argv

try:
    CFG = flashconfig.load()
    CUR_HAND, CUR_CODE, FOLDERS = CFG.font_family.lower(), CFG.code_family.lower(), CFG.font_folders
except flashconfig.ConfigError as e:
    print("note: the settings file has a problem (", e, ") - the 'in use' marks are skipped")
    CUR_HAND = CUR_CODE = ""
    FOLDERS = []

HAND = re.compile(r"ink free|segoe (print|script)|comic|mv boli|gabriola|hand|script|brush|marker|chalk|kristen|bradley|mistral|pristina|"
                  r"noteworthy|caveat|kalam|patrick|indie|schoolbell|permanent|architect|handlee|gochi|shadows|coming soon|reenie|"
                  r"covered by|nanum pen|gloria|rock salt|walter|zeyada|swanky|cedarville|vibur|waiting|journal|sketch|pen$|casual|felt", re.I)
NOT_TEXT = re.compile(r"emoji|symbol|math|icon|ding", re.I)          # symbol / emoji / math fonts are no candidates for the writing

SAMPLE = dict(
    title="The order that charged twice",
    q="The order is saved, then the service crashes before the Kafka send. After the restart the customer was charged twice. Why, and how do you stop it?",
    step="outbox: the event row is written in the SAME transaction as the order. A relay publishes it later, at least once.",
    small="Cost: one extra table + a poller   see also: inbox pattern   TRAP: the relay can send twice, so consumers must be idempotent.",
    sym="[ ] { } ( ) < > = + - * / | ^ _ # ~ & % @ $ ! ? ' \" : ;   >= <= ->",
    letters="ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789",
    code=["@Transactional",
          "public Order place(OrderRequest r) {",
          "    Order o = orders.save(Order.from(r));",
          "    outbox.save(OutboxEvent.of(o, \"order-created\"));",
          "    return o;   // relay sends it after the commit",
          "}"],
)
BG = (255, 253, 242)


def url(path):
    return "file:///" + urllib.parse.quote(os.path.abspath(path).replace("\\", "/"), safe="/:")


def families():
    """[{name, regular, bold(file or None), mono, missing}] - one per family; symbol, icon and emoji fonts left out"""
    by = {}
    for f in flashconfig.scan_fonts([*args, *FOLDERS]):
        by.setdefault(f["family"], []).append(f)
    out, skipped = [], []
    for name, faces in sorted(by.items(), key=lambda kv: kv[0].lower()):
        reg = flashconfig.pick_regular(faces)
        if len(reg["missing"]) > 25 or NOT_TEXT.search(name):          # no Latin letters / digits: icons, symbols or another script
            skipped.append(name)
            continue
        bold = [f for f in faces if f["weight"] >= 600 and not f["italic"]]
        out.append(dict(name=name, regular=reg["file"], bold=bold[0]["file"] if bold else None, mono=any(f["mono"] for f in faces),
                        missing=reg["missing"]))
    return out, skipped


def entry(i, f, kind):
    fam = f"spec{i}"
    css = f"@font-face {{ font-family: '{fam}'; font-weight: 400; src: url('{url(f['regular'])}'); }}\n"
    if f["bold"]:
        css += f"@font-face {{ font-family: '{fam}'; font-weight: 700; src: url('{url(f['bold'])}'); }}\n"
    inuse = (kind == "code" and f["name"].lower() == CUR_CODE) or (kind != "code" and f["name"].lower() == CUR_HAND)
    mark = '<span class="inuse">in use now</span>' if inuse else ""
    ff = f"font-family:'{fam}'"
    e = html.escape
    if kind == "code":
        left = f"<pre class='code' style=\"{ff}\">{e(chr(10).join(SAMPLE['code']))}</pre>"
        right = (f"<div class='side' style=\"{ff}\"><div style='font-size:9pt'>{e(SAMPLE['sym'])}</div>"
                 f"<div style='font-size:9pt'>{e(SAMPLE['letters'])}</div>")
    else:
        left = (f"<div class='cardtext' style=\"{ff}\">"
                f"<div style='font-size:14pt;font-weight:700;color:#1c3f94;line-height:1.12'>{e(SAMPLE['title'])}"
                f"<span style='font-weight:400;font-size:10pt;color:#5b616b'>&nbsp; EVENTS &middot; DEBUG</span></div>"
                f"<div style='font-size:11pt;color:#1c3f94;line-height:1.22;margin-top:1.2mm'>{e(SAMPLE['q'])}</div>"
                f"<div style='font-size:9.4pt;color:#1c3f94;line-height:1.17;margin-top:1.2mm'>"
                f"<b style='color:#0b8f86'>outbox: the event row is written</b> {e(SAMPLE['step'][len('outbox: the event row is written'):])}</div>"
                f"<div style='font-size:8.8pt;color:#5b616b;line-height:1.17;margin-top:1.2mm'>{e(SAMPLE['small'])}</div></div>")
        right = (f"<div class='side' style=\"{ff}\"><div style='font-size:12pt;color:#1c3f94;line-height:1.3'>{e(SAMPLE['letters'])}</div>"
                 f"<div style='font-size:10pt;color:#1b8a46;margin-top:2mm'>{e(SAMPLE['sym'])}</div>")
    notes = ["real bold face" if f["bold"] else "no bold face: the browser fakes bold",
             "all card characters present" if not f["missing"] else "cannot draw: " + e(f["missing"])]
    if f["mono"]:
        notes.append("monospace")
    how = f"code_font | {f['name']}" if kind == "code" else f"handwriting_font | {f['name']}"
    right += f"<div class='note'>{' &middot; '.join(notes)}</div><div class='how'>{e(how)}</div></div>"
    return (f"<style>{css}</style><section class='entry'><div class='head'><b>{e(f['name'])}</b>"
            f"<span class='file'>{e(os.path.basename(f['regular']))}</span>{mark}</div><div class='cols'>{left}{right}</div></section>")


PAGE_CSS = """
body { margin: 0; background: #fffdf2; font-family: 'Segoe UI', Arial, sans-serif; color: #222; }
.wrap { width: 920px; margin: 0 auto; padding: 14px 0 16px; }
h1 { font-size: 19px; margin: 6px 0 2px; }
p.sub { margin: 0 0 12px; color: #555; font-size: 13px; line-height: 1.35; }
.entry { border: 1px solid #c9c4a8; border-radius: 6px; background: #fff; padding: 8px 12px 10px; margin-bottom: 10px; }
.head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 6px; font-size: 14px; }
.file { color: #888; font-size: 12px; }
.inuse { background: #fff176; border-radius: 3px; padding: 0 6px; font-size: 12px; margin-left: auto; }
.cols { display: flex; gap: 20px; align-items: flex-start; }
.cardtext, pre.code { width: 90mm; flex: none; box-sizing: border-box; }
.cardtext { border: 1px dashed #b9b9b9; padding: 2mm; background: #fffdf2; }
pre.code { font-size: 7.3pt; line-height: 1.3; background: #fff8c6; border: 1px dashed #5b616b; border-radius: 4px; padding: 4px 8px; margin: 0; overflow: hidden; white-space: pre; }
.side { flex: 1; min-width: 0; }
.note { font-family: 'Segoe UI', Arial, sans-serif; font-size: 11.5px; color: #666; margin-top: 8px; }
.how { font-family: Consolas, monospace; font-size: 11.5px; color: #0b6e66; margin-top: 4px; }
h2 { font-size: 15px; margin: 18px 0 6px; color: #1c3f94; }
"""


def page(title, sub, sections):
    parts = [f"<!doctype html><html><head><meta charset='utf-8'><title>{html.escape(title)}</title><style>{PAGE_CSS}</style></head><body><div class='wrap'>",
             f"<h1>{html.escape(title)}</h1><p class='sub'>{sub}</p>"]
    for head, entries in sections:
        if head:
            parts.append(f"<h2>{html.escape(head)}</h2>")
        parts.append("".join(entries))
    parts.append("</div></body></html>")
    return "".join(parts)


def shoot(html_path, png_path, n_entries):
    """screenshot with room to spare, then cut the picture off below the last entry"""
    scale = 1.4
    subprocess.run([EDGE, "--headless=new", "--disable-gpu", edge_profile(), "--hide-scrollbars", f"--force-device-scale-factor={scale}",
                    f"--window-size=960,{160 + n_entries * 330}", "--virtual-time-budget=8000", f"--screenshot={png_path}", file_url(html_path)],
                   capture_output=True)
    im = Image.open(png_path).convert("RGB")
    px = im.load()
    last = 0
    for y in range(im.size[1] - 1, 0, -1):               # the last row that is not page background
        if any(px[x, y] != BG for x in range(0, im.size[0], 7)):
            last = y
            break
    im.crop((0, 0, im.size[0], min(im.size[1], last + int(16 * scale)))).save(png_path)


fams, skipped = families()
hand = [f for f in fams if not f["mono"] and HAND.search(f["name"])]
code = [f for f in fams if f["mono"]]
text = [f for f in fams if f not in hand and f not in code]
print(f"{len(fams)} text fonts found ({len(skipped)} symbol/icon fonts left out): {len(hand)} handwriting-like, {len(text)} clean text, {len(code)} monospace")
print(f"{'font':<28}{'kind':<13}{'bold':<6}missing characters")
for kind, group in (("handwriting", hand), ("text", text), ("monospace", code)):
    for f in group:
        print(f"{f['name']:<28}{kind:<13}{'yes' if f['bold'] else 'no':<6}{f['missing'] or '-'}")

SUB_H = ("The same card text in every handwriting-like font, in a column as wide as the text area of a card (90 mm) at the real printed sizes. "
         "Pick one and put its name into <code>handwriting_font</code> in flashcards.config.md.")
SUB_T = "Clean text fonts: very readable, little personality. Same real sizes."
SUB_C = "Monospace fonts for the code box (7.3 pt on the card). Put the name into <code>code_font</code>."
idx = 0


def build(group, kind):
    global idx
    out = []
    for f in group:
        idx += 1
        out.append(entry(idx, f, kind))
    return out


eh, et, ec = build(hand, "hand"), build(text, "text"), build(code, "code")
files = [("fonts_handwriting", "Handwriting & casual fonts", SUB_H, eh),
         ("fonts_text", "Clean text fonts", SUB_T, et),
         ("fonts_code", "Monospace fonts (code box)", SUB_C, ec)]
for name, title, sub, entries in files:
    p = work(name + ".html")
    open(p, "w", encoding="utf-8").write(page(title, sub, [("", entries)]))
    print("wrote", p)
    if PNG and entries:
        shoot(p, work(name + ".png"), len(entries))
        print("wrote", work(name + ".png"))
allp = work("fonts.html")
open(allp, "w", encoding="utf-8").write(page("Fonts for the flash cards", SUB_H, [("Handwriting & casual", eh), ("Clean text fonts", et), ("Monospace (code box)", ec)]))
print("wrote", allp)
if skipped:
    print("left out (no Latin letters, symbol or emoji fonts):", ", ".join(skipped))

"""Flash-card engine: cards on a sheet, printed double sided. The cards come from a deck file (FLASH_DECK); the paper size, card size,
margins, insets, text size and fonts come from flashcards.config.md (see flashconfig.py).

Default sheet geometry (mm):  page 210 x 297 | cards 100 x 144, 2 x 2 = 200 x 288 | side margin 5 | top/bottom margin 4.5
Back pages are MIRRORED left<->right (print "flip on LONG edge") so every back sits exactly behind its front.
All content sits >= content_inset (5 mm) inside the cut line; the pen border sits border_inset (2.6 mm) inside the cut line.

usage: python gen_flash.py <out.html> [check|shot]
  (none)  final build -> print it to PDF
  check   adds a script that dumps per-face layout numbers (used by flash_report.py)
  shot    shows one sheet at a time via #pN so a headless screenshot is exactly one A4 page (used by shot.py)
env: FLASH_DECK (required: deck .py), FLASH_WORK (folder with flash_scales.json, default cwd), FLASH_K (force one text scale), FLASH_IGNORE=1 (ignore scales file),
     FLASH_CONFIG (settings file, default: <FLASH_WORK>/flashcards.config.md, else the one in the skill folder)
A deck file defines DECK_TITLE, the dia_* functions, CARDS and (optionally) CHECK_QS, the label constants below and fact checks; see decks/.
Card fields:  n, tier (MUST|CORE|STRETCH), ref (big area tag, e.g. EVENTS), ask (EXPLAIN|DESIGN|DEBUG|TRADE-OFF), title, q, ex_dia (function -> Dia, the
scenario picture) or ex_draw (box labels), ex [(given, observed, why)], short, kind, keep, rule, spot, dia (function -> Dia), steps, code, cost, twins, trap.
"""
import html
import io
import json
import math
import os
import random
import re
import sys

from PIL import ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import flashconfig  # noqa: E402
from flashconfig import fmt  # noqa: E402

try:
    CFG = flashconfig.load()
except flashconfig.ConfigError as e:
    raise SystemExit("SETTINGS ERROR: " + str(e))

OUT = sys.argv[1]
CHECK = len(sys.argv) > 2 and sys.argv[2] == "check"
SHOT = len(sys.argv) > 2 and sys.argv[2] == "shot"
R = random.Random(2026)

INK, RED, GREEN, TEAL = "#1c3f94", "#c8352b", "#1b8a46", "#0b8f86"
GREY, PENCIL = "#9aa0aa", "#5b616b"
TIER = {"MUST": "#1b8a46", "CORE": "#1c5fb4", "STRETCH": "#8a3fb0"}          # MUST = asked in almost every senior loop, CORE = asked often, STRETCH = staff-level depth
TIER_TINT = {"MUST": "#e4f5ea", "CORE": "#e3edfa", "STRETCH": "#f0e6f6"}
TIER_DOTS = {"MUST": 1, "CORE": 2, "STRETCH": 3}                              # dots as well as colour, so a black-and-white print still works
FONT_CSS = CFG.font_css             # e.g. 'Ink Free','Segoe Print',...,cursive
INKFONT = CFG.font_file             # the .ttf behind the writing font (Pillow measures text with it)

MM = 96 / 25.4                      # CSS px per mm
CARD_W, CARD_H = CFG.card_w, CFG.card_h           # mm
PAGE_W, PAGE_H = CFG.page_w, CFG.page_h
COLS, ROWS, PER_SHEET, GAP = CFG.cols, CFG.rows, CFG.per_sheet, CFG.gap
X0, Y0 = CFG.x0, CFG.y0             # first card's top-left on the sheet (mm)
INSET, BORDER_INSET = CFG.content_inset, CFG.border_inset
CONTENT_W, CONTENT_H = CFG.content_w, CFG.content_h
CW, CH = CARD_W * MM, CARD_H * MM   # card size in px (svg units)
DW = 90 * MM                        # width of the drawing area of a diagram (px): decks draw in this coordinate system
SCALE = CFG.scale                   # the card is laid out at the design size and scaled as a whole (1 = as designed)
DESIGN_W, DESIGN_H = CFG.design_w, CFG.design_h     # the text area in layout mm: CONTENT_W / SCALE
DIA_SCALE = CFG.dia_scale           # a diagram is drawn for 90 mm and stretched to the layout width

_fonts = {}


def tw(txt, px):
    """width in px of txt in Ink Free at px size"""
    if px not in _fonts:
        _fonts[px] = ImageFont.truetype(INKFONT, int(round(px * 4)))
    return _fonts[px].getlength(txt) / 4


def j(a):
    return R.uniform(-a, a)


def f(v):
    return f"{v:.1f}"


def esc(s):
    return html.escape(str(s))


def asc(s):
    """Ink Free has no >=, <=, arrows or minus - spell them in ASCII."""
    for a, b in (("\u2265", ">="), ("\u2264", "<="), ("\u2192", "->"), ("\u2190", "<-"), ("\u2212", "-")):
        s = s.replace(a, b)
    return s


# ------------------------------------------------------------------ pen primitives (px units)
def stroke(d, color=INK, w=1.8, extra=""):
    return (f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{w}" stroke-linecap="round" '
            f'stroke-linejoin="round" {extra}/>')


def smooth(pts, closed=False):
    if closed:
        n = len(pts)
        mids = [((pts[i][0] + pts[(i + 1) % n][0]) / 2, (pts[i][1] + pts[(i + 1) % n][1]) / 2) for i in range(n)]
        d = f"M{f(mids[-1][0])} {f(mids[-1][1])}"
        for i in range(n):
            d += f" Q{f(pts[i][0])} {f(pts[i][1])} {f(mids[i][0])} {f(mids[i][1])}"
        return d + " Z"
    d = f"M{f(pts[0][0])} {f(pts[0][1])}"
    for i in range(1, len(pts) - 1):
        mx, my = (pts[i][0] + pts[i + 1][0]) / 2, (pts[i][1] + pts[i + 1][1]) / 2
        d += f" Q{f(pts[i][0])} {f(pts[i][1])} {f(mx)} {f(my)}"
    return d + f" L{f(pts[-1][0])} {f(pts[-1][1])}"


def line(x1, y1, x2, y2, color=INK, w=1.8, wob=.9, over=0.0, extra=""):
    dx, dy = x2 - x1, y2 - y1
    L = math.hypot(dx, dy) or 1
    ux, uy = dx / L, dy / L
    nx, ny = -uy, ux
    if over:
        x1 -= ux * R.uniform(0, over); y1 -= uy * R.uniform(0, over)
        x2 += ux * R.uniform(0, over); y2 += uy * R.uniform(0, over)
        dx, dy = x2 - x1, y2 - y1
    c1 = (x1 + dx * .30 + nx * j(wob), y1 + dy * .30 + ny * j(wob))
    c2 = (x1 + dx * .70 + nx * j(wob), y1 + dy * .70 + ny * j(wob))
    d = (f"M{f(x1 + j(.4))} {f(y1 + j(.4))} C{f(c1[0])} {f(c1[1])} {f(c2[0])} {f(c2[1])} "
         f"{f(x2 + j(.4))} {f(y2 + j(.4))}")
    return stroke(d, color, w, extra)


def rect(x, y, w, h, color=INK, sw=1.8, over=2.2, wob=.8, fill=None, extra=""):
    out = []
    if fill:
        out.append(f'<path d="M{f(x)} {f(y)} L{f(x + w)} {f(y)} L{f(x + w)} {f(y + h)} L{f(x)} {f(y + h)} Z" '
                   f'fill="{fill}" stroke="none"/>')
    out += [line(x, y, x + w, y, color, sw, wob, over, extra), line(x + w, y, x + w, y + h, color, sw, wob, over, extra),
            line(x + w, y + h, x, y + h, color, sw, wob, over, extra), line(x, y + h, x, y, color, sw, wob, over, extra)]
    return "".join(out)


def ellipse(cx, cy, rx, ry, color=RED, sw=2.0):
    n = 16
    a0 = R.uniform(0, math.tau)
    pts = []
    for i in range(int(n * 1.12) + 1):
        a = a0 + math.tau * 1.12 * i / int(n * 1.12)
        k = 1 + j(.03) + .05 * i / n
        pts.append((cx + rx * k * math.cos(a), cy + ry * k * math.sin(a)))
    return stroke(smooth(pts), color, sw)


def arrow(x1, y1, x2, y2, bend=6, color=GREEN, w=2.0, head=6):
    mx, my = (x1 + x2) / 2, (y1 + y2) / 2
    dx, dy = x2 - x1, y2 - y1
    L = math.hypot(dx, dy) or 1
    cx, cy = mx + (-dy / L) * bend, my + (dx / L) * bend
    out = [stroke(f"M{f(x1)} {f(y1)} Q{f(cx)} {f(cy)} {f(x2)} {f(y2)}", color, w)]
    tx, ty = x2 - cx, y2 - cy
    tl = math.hypot(tx, ty) or 1
    tx, ty = tx / tl, ty / tl
    for ang in (math.radians(28), -math.radians(28)):
        hx = -tx * math.cos(ang) + ty * math.sin(ang)
        hy = -tx * math.sin(ang) - ty * math.cos(ang)
        out.append(stroke(f"M{f(x2)} {f(y2)} L{f(x2 + hx * head)} {f(y2 + hy * head)}", color, w))
    return "".join(out)


def strike(x, y, w, h=11, color=RED, sw=1.8):
    return (line(x - 2, y - 1, x + w + 2, y - h, color, sw, .6) + line(x - 2, y - h + 1, x + w + 2, y - 2, color, sw - .3, .6))


DIA_BOOST = 1.14


def svg_text(x, y, s, size=12, color=INK, anchor="start", weight="normal", rot=None, opacity=1):
    size = size * DIA_BOOST
    a = j(.8) if rot is None else rot
    op = f' opacity="{opacity}"' if opacity != 1 else ""
    return (f'<text x="{f(x)}" y="{f(y)}" font-size="{size}" fill="{color}" text-anchor="{anchor}" font-weight="{weight}"{op} '
            f'xml:space="preserve" transform="rotate({a:.2f} {f(x)} {f(y)})">{esc(asc(s))}</text>')


def highlighter(x, y, w, h, color="#fff176", op=.65, rot=-.5):
    return (f'<rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}" rx="3" fill="{color}" opacity="{op}" '
            f'transform="rotate({rot} {f(x)} {f(y)})"/>')


# ------------------------------------------------------------------ card border
def card_border(color):
    """wobbly pen rectangle, 2.6 mm inside the cut line, drawn twice like a double pen stroke"""
    ins = BORDER_INSET * MM
    out = []
    for rep in range(2):
        w, h = CW - 2 * ins, CH - 2 * ins
        rad = 14
        # walk the rounded rectangle
        def pt(x, y):
            return (ins + x + j(.9 if rep == 0 else 1.2), ins + y + j(.9 if rep == 0 else 1.2))
        n = 14
        # corners: sample quarter circles
        def corner(cx, cy, a0):
            return [pt(cx + rad * math.cos(a0 + math.pi / 2 * t / 4), cy + rad * math.sin(a0 + math.pi / 2 * t / 4))
                    for t in range(5)]
        pts = []
        pts += [pt(rad + (w - 2 * rad) * i / n, 0) for i in range(n)]
        pts += corner(w - rad, rad, -math.pi / 2)
        pts += [pt(w, rad + (h - 2 * rad) * i / (n * 2)) for i in range(n * 2)]
        pts += corner(w - rad, h - rad, 0)
        pts += [pt(w - rad - (w - 2 * rad) * i / n, h) for i in range(n)]
        pts += corner(rad, h - rad, math.pi / 2)
        pts += [pt(0, h - rad - (h - 2 * rad) * i / (n * 2)) for i in range(n * 2)]
        pts += corner(rad, rad, math.pi)
        out.append(stroke(smooth(pts, closed=True), color, 1.7 if rep == 0 else 1.0, 'opacity="%s"' % (1 if rep == 0 else .55)))
    return "".join(out)


# ------------------------------------------------------------------ diagrams
CH_CELL = 26          # cell height (px)


class Dia:
    """array row drawing inside an svg of width 90 mm; everything in px.
    top = y of the cell row; noteY = baseline of the caption line"""

    def __init__(self, top=14, extra_bottom=0):
        self.top = top
        self.noteY = top + CH_CELL + 54 + extra_bottom
        self.h = self.noteY + 6
        self.parts = []

    def add(self, s):
        self.parts.append(s)

    def svg(self):
        return (f'<svg class="dia" xmlns="http://www.w3.org/2000/svg" width="{fmt(DESIGN_W)}mm" height="{self.h / MM * DIA_SCALE:.2f}mm" '
                f'viewBox="0 0 {DW:.2f} {self.h:.2f}" font-family="{FONT_CSS}">' + "".join(self.parts) + "</svg>")


def cells(d, vals, y=None, colors=None, fills=None, idx=True, fs=None, gap=3, x0=None, maxpitch=44):
    y = d.top if y is None else y
    n = len(vals)
    pitch = min(maxpitch, (DW - 16) / n)
    cw = pitch - gap
    ch = CH_CELL
    left = (DW - pitch * n + gap) / 2 if x0 is None else x0
    fs = fs or min(15, cw * .62)
    colors, fills = colors or {}, fills or {}
    geo = []
    for i, v in enumerate(vals):
        x = left + i * pitch
        d.add(rect(x, y, cw, ch, INK, 1.6, 1.6, .6, fill=fills.get(i)))
        d.add(svg_text(x + cw / 2, y + ch / 2 + fs * .36, str(v), fs, colors.get(i, INK), "middle", rot=j(1.2)))
        if idx:
            d.add(svg_text(x + cw / 2, y + ch + 14, str(i), 8, RED, "middle", opacity=.85))
        geo.append(x)
    return dict(left=left, pitch=pitch, cw=cw, ch=ch, y=y, geo=geo)


def frame(d, g, l, r, color=TEAL, dashed=False, grow=3.5, sw=2.4):
    x = g["geo"][l] - grow
    w = g["geo"][r] + g["cw"] - g["geo"][l] + 2 * grow
    y, h = g["y"] - grow, g["ch"] + 2 * grow
    if dashed:
        d.add(rect(x, y, w, h, color, 1.5, 2, .8, extra='stroke-dasharray="5 4"'))
    else:
        for _ in range(2):
            d.add(rect(x, y, w, h, color, sw, 3, 1.0))


def cx_of(g, i):
    return g["geo"][i] + g["cw"] / 2


def pointer(d, g, i, label, color, dy=29):
    x = cx_of(g, i)
    y = g["y"] + g["ch"] + dy
    word = {"l": "tail", "r": "head"}.get(label, label)
    d.add(arrow(x, y, x, y - 9, 0, color, 1.6, 4))
    d.add(svg_text(x, y + 10, word, 10, color, "middle"))


def tag_above(d, g, i, label, color):
    x = cx_of(g, i)
    d.add(svg_text(x, g["y"] - 14, label, 10, color, "middle"))
    d.add(arrow(x, g["y"] - 11, x, g["y"] - 2, 0, color, 1.6, 4))


def note(d, x, s, size=11, color=INK, anchor="start", hl=None):
    s = asc(s)
    y = d.noteY
    if hl:
        sz = size * DIA_BOOST
        w = tw(s, sz)
        xx = x if anchor == "start" else x - w
        d.add(highlighter(xx - 3, y - sz * .85, w + 6, sz * 1.2, hl))
    d.add(svg_text(x, y, s, size, color, anchor))


GREENISH = "#dff5ee"


# ---- generic picture helpers for decks: labelled boxes, maps, grids and linked chains -------------------
def box_text(d, x, y, text, size=11, h=22, color=INK, fill=None, pad=6, edge=INK, dashed=False, minw=0):
    """one pen box sized to its text; returns its width"""
    sz = size * DIA_BOOST
    w = max(minw, tw(asc(text), sz) + 2 * pad)
    d.add(rect(x, y, w, h, edge, 1.5, 1.4, .5, fill=fill, extra='stroke-dasharray="5 4"' if dashed else ""))
    d.add(svg_text(x + w / 2, y + h / 2 + sz * .3, text, size, color, "middle", rot=j(.8)))
    return w


def strip(d, items, y, x=8, label=None, size=11, h=22, gap=5, fills=None, colors=None, edge=INK):
    """a row of boxes, e.g. the contents of a map, stack or queue. `items` are strings; returns the right edge x"""
    fills, colors = fills or {}, colors or {}
    if label:
        d.add(svg_text(x, y + h / 2 + 4, label, 10.5, PENCIL))
        x += tw(asc(label), 10.5 * DIA_BOOST) + 8
    for i, t in enumerate(items):
        x += box_text(d, x, y, t, size, h, colors.get(i, INK), fills.get(i), edge=edge) + gap
    return x


def rowmap(d, rows, y, x=8, h=22, size=11, rowgap=5, keyfill=GREENISH, colors=None):
    """map drawing: each row is  KEY -> value boxes.  rows = [(key, [values...]), ...]"""
    colors = colors or {}
    for r, (k, vs) in enumerate(rows):
        yy = y + r * (h + rowgap)
        kw = box_text(d, x, yy, str(k), size, h, colors.get(r, INK), keyfill)
        ax = x + kw + 3
        d.add(arrow(ax, yy + h / 2, ax + 16, yy + h / 2, 0, GREY, 1.5, 4))
        xx = ax + 21
        for v in vs:
            xx += box_text(d, xx, yy, str(v), size, h) + 4
    return y + len(rows) * (h + rowgap)


def grid(d, rows, x, y, cw=26, ch=20, size=11, fills=None, colors=None):
    """a small table of boxes; fills / colors are keyed by (row, col)"""
    fills, colors = fills or {}, colors or {}
    for r, row in enumerate(rows):
        for c, t in enumerate(row):
            xx, yy = x + c * (cw + 2), y + r * (ch + 2)
            d.add(rect(xx, yy, cw, ch, INK, 1.4, 1.2, .4, fill=fills.get((r, c))))
            d.add(svg_text(xx + cw / 2, yy + ch / 2 + size * DIA_BOOST * .3, str(t), size, colors.get((r, c), INK), "middle", rot=j(.8)))
    return y + len(rows) * (ch + 2)


def chain(d, labels, y, x=8, h=22, size=11, fills=None, tags=None, gap=24, double=True):
    """boxes joined by arrows (a linked list). double=True draws both directions. tags = {index: (text, color)} above a box"""
    fills, tags = fills or {}, tags or {}
    prev_right = None
    for i, t in enumerate(labels):
        w = tw(asc(t), size * DIA_BOOST) + 12
        if prev_right is not None:
            d.add(arrow(prev_right + 1, y + h / 2 - (3 if double else 0), x - 1, y + h / 2 - (3 if double else 0), 0, GREY, 1.4, 4))
            if double:
                d.add(arrow(x - 1, y + h / 2 + 4, prev_right + 1, y + h / 2 + 4, 0, GREY, 1.4, 4))
        box_text(d, x, y, t, size, h, INK, fills.get(i), minw=w)
        if i in tags:
            d.add(svg_text(x + w / 2, y - 6, tags[i][0], 10, tags[i][1], "middle"))
        prev_right = x + w
        x += w + gap
    return x


def flow(d, labels, y, x=8, h=22, size=10.5, fills=None, colors=None, under=None, above=None, gap=18, dashed=(), edge_colors=None, fit_to=None):
    """boxes in a row joined by left-to-right arrows (a pipeline: service -> table -> topic -> service), shrunk to fit the drawing width.
    fills / colors / edge_colors are keyed by box index; dashed = indexes drawn with a dashed border (something that did NOT happen);
    under / above = {index: (text, colour)} small captions below / above a box. Returns [(x, width)] of every box."""
    fills, colors, under, above, edge_colors = fills or {}, colors or {}, under or {}, above or {}, edge_colors or {}
    limit = (fit_to or DW) - 8
    while True:
        ws = [tw(asc(t), size * DIA_BOOST) + 12 for t in labels]
        total = x + sum(ws) + gap * (len(labels) - 1)
        if total <= limit or size <= 7.5:
            break
        size -= 0.5
    assert total <= limit + 1, f"flow does not fit the drawing width ({total:.0f} > {limit:.0f}px): {labels}"
    geo, cur = [], x
    for i, t in enumerate(labels):
        box_text(d, cur, y, t, size, h, colors.get(i, INK), fills.get(i), edge=edge_colors.get(i, INK), dashed=i in dashed, minw=ws[i])
        geo.append((cur, ws[i]))
        for caps, cy in ((under, y + h + 12), (above, y - 5)):
            if i in caps:                        # centred under / over the box, but never outside the drawing area
                half = tw(asc(caps[i][0]), 9 * DIA_BOOST) / 2
                d.add(svg_text(min(max(cur + ws[i] / 2, 8 + half), (fit_to or DW) - 8 - half), cy, caps[i][0], 9, caps[i][1], "middle"))
        if i + 1 < len(labels):
            d.add(arrow(cur + ws[i] + 1, y + h / 2, cur + ws[i] + gap - 1, y + h / 2, 0, GREY, 1.5, 4))
        cur += ws[i] + gap
    return geo


def canvas(height, note_y=None):
    """an empty drawing of the given height (px) for pictures that are not a row of array cells; note() writes its caption at note_y"""
    d = Dia(4)
    d.h = height
    d.noteY = note_y if note_y is not None else height - 8
    return d


# ---- front-side example drawings (no frame, no pointers) --------------------------------
class ExDia(Dia):
    def __init__(self):
        super().__init__(4)
        self.h = 4 + CH_CELL + 4


def ex_cells(vals, gap=3, fs=None, maxpitch=44):
    d = ExDia()
    cells(d, vals, idx=False, gap=gap, fs=fs, maxpitch=maxpitch)
    return d


# ------------------------------------------------------------------ the deck (cards, diagrams, example checks)
DECK = os.environ.get("FLASH_DECK")
if not DECK:
    raise SystemExit("set FLASH_DECK to a deck file, e.g. decks/t01_event_driven_consistency.py")
DECK_TITLE = "Java and microservices"
CHECK_QS = ["What <u>guarantee</u> do I need, and from whom?",       # the four self-check prompts on every front; a deck overrides this list
            "Where does it <u>fail</u>: crash, duplicate, slow, out of order?",
            "What is the <u>mechanism</u> that makes it hold?",
            "What does it <u>cost</u>, and when would I not use it?"]
DECK_LABEL = "Java interview card"                 # header of every front:  "<DECK_LABEL> . 03 / 08"
Q_LABEL = "THE QUESTION"
EX_LABEL = "THE SCENARIO"
SAY_LABEL = "SAY IT OUT LOUD, THEN FLIP"
FLIP_TEXT = "flip for the answer + the plan"        # the bottom line of every front: check_pdf.py counts it to prove no card was cut off
SPOT_LABEL = "SPOT IT"
TRAP_LABEL = "TRAP"                                # the bottom line of every back: counted by check_pdf.py as well
TWINS_LABEL = "see also"
OUT_COLOR = RED                                     # colour of the "observed" part of a scenario row: red = the symptom (a deck about answers may set GREEN)
exec(compile(io.open(DECK, encoding="utf-8").read(), DECK, "exec"), globals())
N_SHEETS = -(-len(CARDS) // PER_SHEET)          # ceil: the last sheet may have empty slots




# ------------------------------------------------------------------ card faces (html)
def tier_dots(tier):
    k = TIER_DOTS[tier]
    c = TIER[tier]
    return "".join(f'<i class="dot" style="background:{c if i < k else "transparent"};border-color:{c}"></i>' for i in range(3))


def hdr_front(c):
    col = TIER[c["tier"]]
    return (f'<div class="top"><span class="deck">{asc(esc(DECK_LABEL))} &middot; {c["n"]:02d} / {len(CARDS)}</span>'
            f'<span class="tierchip" style="color:{col};border-color:{col};background:{TIER_TINT[c["tier"]]}">'
            f'{tier_dots(c["tier"])}{c["tier"]}</span></div>')


def scenario_dia(c):
    """the picture in the scenario box: whatever the deck draws (c["ex_dia"]), or a plain row of labelled boxes (c["ex_draw"])"""
    if c.get("ex_dia"):
        return c["ex_dia"]()
    vals = c["ex_draw"]
    if isinstance(vals, str):
        vv = list(vals)
        fs = 11 if len(vv) > 11 else None
        return ex_cells(vv, gap=2.0 if len(vv) > 10 else 3, fs=fs)
    longest = max(len(str(v)) for v in vals)
    if longest > 4:        # long labels (words, operations): wider boxes, smaller letters
        return ex_cells(vals, gap=3, fs=9, maxpitch=min(110, 14 + 6 * longest))
    return ex_cells(vals, gap=2.4 if len(vals) > 9 else 3)


def front(c):
    col = TIER[c["tier"]]
    dia = scenario_dia(c)
    exs = ""
    for row in c["ex"]:          # (given, observed, why) and optionally a 4th item "g" / "r": colour of the observed part (default OUT_COLOR)
        inp, out, why = row[:3]
        col_out = {"g": GREEN, "r": RED}.get(row[3] if len(row) > 3 else "", OUT_COLOR)
        why_html = f' <span class="why">({asc(esc(why)).replace("&quot;", chr(34))})</span>' if why else ""
        exs += (f'<div class="exrow"><span class="in">{asc(esc(inp)).replace(" = ", "&nbsp;=&nbsp;")}</span>'
                f'<span class="res"><span class="arrow" style="color:{col_out}">&rarr;</span><span class="out" style="color:{col_out}">{asc(esc(out))}</span>{why_html}</span></div>')
    checks = "".join(f'<li><i class="box"></i><span>{t}</span></li>' for t in CHECK_QS)
    return f'''
  <div class="face front" data-card="{c["n"]}" data-side="front" style="--k:{kfor(c["n"], "front")}">
    <svg class="deco" viewBox="0 0 {CW:.2f} {CH:.2f}" width="{fmt(CARD_W)}mm" height="{fmt(CARD_H)}mm" xmlns="http://www.w3.org/2000/svg">{card_border(col)}</svg>
    <div class="content">
      {hdr_front(c)}
      <div class="lcrow"><span class="lcno" style="color:{col}">{asc(esc(c["ref"]))}</span><span class="diff">{asc(esc(c["ask"]))}</span></div>
      <div class="title">{asc(esc(c["title"]))}</div>
      <div class="label">{asc(esc(Q_LABEL))}</div>
      <div class="q">{asc(c["q"])}</div>
      <div class="label">{asc(esc(EX_LABEL))}</div>
      <div class="exbox">{dia.svg()}{exs}</div>
      <div class="label">{asc(esc(SAY_LABEL))}</div>
      <ul class="checks">{checks}</ul>
      <div class="flip">{asc(esc(FLIP_TEXT))} &#8635;</div>
    </div>
  </div>'''


def back(c):
    col = TIER[c["tier"]]
    steps = "".join(f"<li>{asc(s)}</li>" for s in c["steps"])
    code = "\n".join(html.escape(l) for l in c["code"])
    return f'''
  <div class="face back" data-card="{c["n"]}" data-side="back" style="--k:{kfor(c["n"], "back")}">
    <svg class="deco" viewBox="0 0 {CW:.2f} {CH:.2f}" width="{fmt(CARD_W)}mm" height="{fmt(CARD_H)}mm" xmlns="http://www.w3.org/2000/svg">{card_border(col)}</svg>
    <div class="content">
      <div class="bhead" style="background:{TIER_TINT[c["tier"]]};border-color:{col}">
        <span class="bno" style="color:{col}">{asc(esc(c["ref"]))}</span>
        <span class="bti">{asc(esc(c["short"]))}</span>
        <span class="btier" style="color:{col}">{tier_dots(c["tier"])}</span>
      </div>
      <div class="pat"><div class="kind">{asc(esc(c["kind"]))}</div><div class="keep">{asc(esc(c["keep"]))} &middot; {asc(esc(c["rule"]))}</div></div>
      <div class="spot"><span class="lab">{asc(esc(SPOT_LABEL))}</span> {asc(esc(c["spot"]))}</div>
      {c["dia"]().svg()}
      <ol class="steps">{steps}</ol>
      <pre class="code">{code}</pre>
      <div class="foot"><div class="cost"><b>{asc(esc(c["cost"]))}</b><span class="twins">{asc(esc(TWINS_LABEL))}: {esc(c["twins"])}</span></div>
      <div class="trap"><span class="lab red">{asc(esc(TRAP_LABEL))}</span> {asc(c["trap"])}</div></div>
    </div>
  </div>'''


def sheet_html(idx, side, cards):
    """idx 1..N_SHEETS; side front/back. back sheets are mirrored left<->right (the card of front column c sits in back column COLS-1-c)."""
    place = []                                                  # (card, column, row) in document order
    for r in range(ROWS):
        row = cards[r * COLS:(r + 1) * COLS]
        if not row:
            break
        pairs = [(c, i % COLS) for i, c in enumerate(row)]
        place += [(c, col, r) for c, col in pairs] if side == "front" else [(c, COLS - 1 - col, r) for c, col in reversed(pairs)]
    pn = (idx - 1) * 2 + (1 if side == "front" else 2)
    out = [f'<section class="sheet {side}" id="p{pn}" data-sheet="{idx}" data-side="{side}">']
    if CFG.sheet_label:
        label = (f"FRONT {idx}/{N_SHEETS}" if side == "front" else f"BACK {idx}/{N_SHEETS} (mirrored)")
        out.append(f'<div class="sheetlabel">{label}</div>')
    for c, col, row in place:
        left, top = X0 + col * (CARD_W + GAP), Y0 + row * (CARD_H + GAP)
        face = front(c) if side == "front" else back(c)
        # a scaled card keeps a layout box bigger than what it shows (transform does not shrink the layout box), and the print cuts a box at the page
        # edge by its LAYOUT position: so every card sits at the page corner in layout terms and is moved into place with a translate (no layout effect)
        pos = f"left:{fmt(left)}mm;top:{fmt(top)}mm" if SCALE == 1 else f"left:0;top:0;transform:translate({fmt(left)}mm,{fmt(top)}mm)"
        out.append(f'<div class="card" data-pos="{row * COLS + col}" style="{pos}">{face}</div>')
    if side == "front" and CFG.cut_guides:
        # cut guides, light dashed, front side only: the edges of every card
        xs = sorted({X0 + k * (CARD_W + GAP) + o for k in range(COLS) for o in (0, CARD_W)})
        ys = sorted({Y0 + k * (CARD_H + GAP) + o for k in range(ROWS) for o in (0, CARD_H)})
        g = [f'<line x1="{fmt(x)}" y1="1.5" x2="{fmt(x)}" y2="{fmt(PAGE_H - 1.5)}"/>' for x in xs]
        g += [f'<line x1="1.5" y1="{fmt(y)}" x2="{fmt(PAGE_W - 1.5)}" y2="{fmt(y)}"/>' for y in ys]
        out.append(f'<svg class="cut" viewBox="0 0 {fmt(PAGE_W)} {fmt(PAGE_H)}" width="{fmt(PAGE_W)}mm" height="{fmt(PAGE_H)}mm" xmlns="http://www.w3.org/2000/svg">'
                   f'<g stroke="#b9b9b9" stroke-width=".18" stroke-dasharray="1.2 1.2">{"".join(g)}</g></svg>')
    out.append("</section>")
    return "\n".join(out)


SCALE_CSS = "" if SCALE == 1 else f" transform: scale({SCALE:g}); transform-origin: 0 0;"
CSS = f"""
@page {{ size: {fmt(PAGE_W)}mm {fmt(PAGE_H)}mm; margin: 0; }}
* {{ box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }}
html, body {{ margin: 0; padding: 0; background: #e9e9ec; }}
body {{ font-family: {FONT_CSS}; color: {INK}; }}
.sheet {{ position: relative; width: {fmt(PAGE_W)}mm; height: {fmt(PAGE_H)}mm; background: #fff; overflow: hidden; margin: 0 auto 8mm; break-after: page; page-break-after: always; }}
.sheet:last-of-type {{ break-after: auto; page-break-after: auto; margin-bottom: 0; }}
.sheetlabel {{ position: absolute; top: 1.3mm; left: 0; right: 0; text-align: center; font: 5.5pt {FONT_CSS}; color: #aaa; letter-spacing: .3mm; }}
.cut {{ position: absolute; left: 0; top: 0; pointer-events: none; }}
.card {{ position: absolute; width: {fmt(CARD_W)}mm; height: {fmt(CARD_H)}mm; }}
.face {{ position: relative; width: {fmt(CARD_W)}mm; height: {fmt(CARD_H)}mm; }}
.deco {{ position: absolute; left: 0; top: 0; }}
.content {{ position: absolute; left: {fmt(INSET)}mm; top: {fmt(INSET)}mm; width: {fmt(DESIGN_W)}mm; height: {fmt(DESIGN_H)}mm;{SCALE_CSS} display: flex; flex-direction: column; overflow: hidden; font-size: 9.5pt; line-height: 1.2; }}
.dia {{ display: block; flex: none; }}
b {{ font-weight: 700; }}
.g {{ color: {GREEN}; }} .r {{ color: {RED}; }} .t {{ color: {TEAL}; }}
.lab {{ font-size: 7.5pt; letter-spacing: .25mm; color: {PENCIL}; border: .25mm solid {PENCIL}; border-radius: 1mm; padding: 0 1mm; margin-right: 1mm; }}
.lab.red {{ color: {RED}; border-color: {RED}; }}
/* ---------- front ---------- */
.top {{ display: flex; justify-content: space-between; align-items: center; height: 6.5mm; flex: none; }}
.deck {{ font-size: 8pt; color: {PENCIL}; letter-spacing: .2mm; }}
.tierchip {{ font-size: 8.5pt; font-weight: 700; border: .3mm solid; border-radius: 3mm; padding: .2mm 2.4mm .2mm 1.8mm; display: inline-flex; align-items: center; gap: 1.2mm; }}
.dot {{ display: inline-block; width: 1.9mm; height: 1.9mm; border: .3mm solid; border-radius: 50%; margin-right: -.2mm; }}
.lcrow {{ display: flex; align-items: baseline; gap: 3mm; margin-top: 2mm; flex: none; }}
.lcno {{ font-size: 24pt; font-weight: 700; line-height: 1; transform: rotate(-1.2deg); }}
.diff {{ font-size: 10pt; color: {PENCIL}; border-bottom: .3mm dashed {PENCIL}; }}
.title {{ font-size: 14pt; font-weight: 700; line-height: 1.12; margin-top: 1.2mm; flex: none; }}
.label {{ font-size: 7.5pt; letter-spacing: .3mm; color: {PENCIL}; margin-top: 2.2mm; margin-bottom: .6mm; flex: none; }}
.q {{ font-size: 11pt; line-height: 1.22; flex: none; }}
.exbox {{ border: .35mm solid {PENCIL}; border-radius: 255px 15px 225px 15px / 15px 225px 15px 255px; padding: 1.2mm 2mm 1.6mm; background: #fffdf2; flex: none; }}
.exbox .dia {{ width: 100%; height: auto; margin: 0 auto .6mm; }}
.exrow {{ font-size: 10pt; line-height: 1.25; margin-top: .5mm; }}
.exrow .in {{ color: {INK}; }} .exrow .arrow {{ color: {GREEN}; font-family: {CFG.arrow_css}; padding: 0 1.4mm; }}
.exrow .out {{ color: {GREEN}; font-weight: 700; }} .exrow .why {{ color: {PENCIL}; font-size: 9pt; }}
.checks {{ list-style: none; padding: 0; margin: 0; font-size: 10.5pt; flex: none; }}
.checks li {{ display: flex; align-items: flex-start; gap: 2.2mm; margin-top: 1.2mm; line-height: 1.15; }}
.box {{ flex: none; width: 3.6mm; height: 3.6mm; border: .35mm solid {INK}; border-radius: 45% 12% 40% 12%; margin-top: .5mm; }}
.flip {{ margin-top: auto; text-align: right; font-size: 9pt; color: {PENCIL}; flex: none; }}
/* ---------- back ---------- */
.bhead {{ display: flex; align-items: center; gap: 2.4mm; border: .4mm solid; border-radius: 255px 15px 225px 15px / 15px 225px 15px 255px; padding: 1mm 2.2mm; flex: none; }}
.bno {{ font-size: 17pt; font-weight: 700; line-height: 1; white-space: nowrap; transform: rotate(-1.5deg); }}
.bti {{ font-size: 10pt; font-weight: 700; line-height: 1.08; flex: 1; }}
.btier {{ white-space: nowrap; }}
.pat {{ margin-top: 1.4mm; border: .4mm solid {TEAL}; border-radius: 15px 225px 15px 255px / 255px 15px 225px 15px; background: #e6f6f3; padding: .8mm 2.2mm 1mm; flex: none; }}
.kind {{ font-size: 12pt; font-weight: 700; color: {TEAL}; line-height: 1.15; }}
.keep {{ font-size: 8.8pt; color: {INK}; line-height: 1.15; }}
.spot {{ font-size: 8.8pt; color: {PENCIL}; margin-top: 1.2mm; line-height: 1.2; flex: none; }}
.steps {{ list-style: none; counter-reset: s; padding: 0; margin: .8mm 0 0; flex: none; }}
.steps li {{ counter-increment: s; position: relative; padding-left: 5.2mm; margin-top: .9mm; line-height: 1.17; font-size: 9.4pt; min-height: 4.5mm; }}
.steps li::before {{ content: counter(s); position: absolute; left: 0; top: .35mm; width: 3.9mm; height: 3.9mm; border: .3mm solid {INK}; border-radius: 50%; font-size: 7.5pt; line-height: 3.3mm; text-align: center; }}
.steps u {{ text-decoration: none; background: linear-gradient(#fff176, #fff176) 0 92% / 100% 46% no-repeat; padding: 0 .4mm; }}
.q u, .checks u {{ text-decoration: none; background: linear-gradient(#fff176, #fff176) 0 92% / 100% 46% no-repeat; }}
.code {{ font-family: {CFG.code_css}; font-size: 7.3pt; line-height: 1.3; color: #222; background: #fff8c6; border: .3mm dashed {PENCIL}; border-radius: 1.2mm; margin: 1.4mm 0 0; padding: 1mm 2mm; white-space: pre; flex: none; }}
.foot {{ margin-top: auto; flex: none; }}
.cost {{ display: flex; justify-content: space-between; font-size: 9pt; color: {INK}; border-top: .3mm solid {PENCIL}; padding-top: .8mm; }}
.twins {{ color: {PENCIL}; }}
.trap {{ font-size: 8.8pt; line-height: 1.17; margin-top: .6mm; color: #222; }}
.trap b {{ color: {RED}; }}
@media screen {{ body {{ padding: 6mm 0; }} .sheet {{ box-shadow: 0 .5mm 3mm rgba(0,0,0,.25); }} }}
@media screen {{ body.one .sheet {{ display: none; }} body.one .sheet:target {{ display: block; }} }}
body.one {{ padding: 0 !important; background: #fff; }} body.one .sheet {{ margin: 0; box-shadow: none; }}
@media print {{ html, body {{ background: #fff; }} body {{ padding: 0; }} .sheet {{ margin: 0; box-shadow: none; }} }}
"""

CSS = re.sub(r"font-size:\s*([\d.]+)pt", lambda m: "font-size: calc(" + m.group(1) + "pt * var(--k))", CSS)
CSS += ".face { --k: 1; }" + chr(10)
if CFG.font_from_file:        # a font that is only a file (not installed): the page loads it itself
    CSS = ("@font-face { font-family: '%s'; src: url('file:///%s'); }" % (CFG.font_family, CFG.font_file.replace(chr(92), "/"))) + chr(10) + CSS

HERE = os.path.abspath(os.environ.get("FLASH_WORK", os.getcwd()))      # where flash_scales.json lives
KDEF = os.environ.get("FLASH_K")
try:
    SCALES = {} if os.environ.get("FLASH_IGNORE") else json.load(open(os.path.join(HERE, "flash_scales.json")))
except FileNotFoundError:
    SCALES = {}


if SCALES.get("_config") not in (None, flashconfig.signature(CFG)):
    print("WARNING: flash_scales.json was made with other settings; run sweep.py (build_all.py does) so every card is fitted again", file=sys.stderr)


def kfor(n, side):
    """the text scale of one face: the fitted step (0.5 .. 1.3) from the sweep"""
    return float(KDEF) if KDEF else float(SCALES.get(f"{n}-{side}", 1.0))


SCRIPT = """
<script>
// layout self-check (only present in the check build): overflow, safe margins, mirror alignment
window.addEventListener('load', () => { document.fonts.ready.then(() => {
  const res = [];
  const mm = 96 / 25.4;
  document.querySelectorAll('.face').forEach(face => {
    const c = face.querySelector('.content');
    const fr = face.getBoundingClientRect(), cr = c.getBoundingClientRect();
    let maxB = 0, minL = 1e9, maxR = 0, minT = 1e9;
    c.querySelectorAll('*').forEach(e => {
      const r = e.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) return;
      maxB = Math.max(maxB, r.bottom); maxR = Math.max(maxR, r.right); minL = Math.min(minL, r.left); minT = Math.min(minT, r.top);
    });
    const off = [];
    c.querySelectorAll('*').forEach(e => {
      if (e.closest('svg') && e.tagName.toLowerCase() !== 'svg') return;
      const r = e.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) return;
      if (r.left < cr.left - 0.5 || r.right > cr.right + 0.5)
        off.push((e.tagName + '.' + (e.className.baseVal !== undefined ? e.className.baseVal : e.className) + ' L' + ((r.left - cr.left) / mm).toFixed(2) + ' R' + ((r.right - cr.right) / mm).toFixed(2) + ' ' + (e.textContent || '').slice(0, 24)));
    });
    // real layout overflow: the lowest BLOCK box. (scrollHeight also counts the glyph area of the last text line, which is taller than its
    // line box in fonts with big ascenders / descenders such as Segoe Print: a few px that are not an overflow at all)
    let blockB = 0;
    c.querySelectorAll('*').forEach(e => {
      if (e.closest('svg') || getComputedStyle(e).display === 'inline') return;
      blockB = Math.max(blockB, e.getBoundingClientRect().bottom);
    });
    const foot = c.querySelector('.foot, .flip');
    let kids = [...c.children].filter(e => !e.classList.contains('foot') && !e.classList.contains('flip'));
    const lastKid = kids[kids.length - 1].getBoundingClientRect().bottom;
    const footTop = foot.getBoundingClientRect().top;
    res.push({card: face.dataset.card, side: face.dataset.side, off,
      slack_mm: ((cr.bottom - maxB) / mm).toFixed(2),
      gap_before_foot_mm: ((footTop - lastKid) / mm).toFixed(2),
      overflow_y: Math.max(0, Math.min(c.scrollHeight - c.clientHeight, Math.ceil(blockB - cr.bottom))), overflow_x: c.scrollWidth - c.clientWidth,
      left_mm: ((minL - fr.left) / mm).toFixed(2), right_mm: ((fr.right - maxR) / mm).toFixed(2),
      top_mm: ((minT - fr.top) / mm).toFixed(2), bottom_mm: ((fr.bottom - maxB) / mm).toFixed(2)});
  });
  const pos = [];
  document.querySelectorAll('.sheet').forEach(s => {
    const sr = s.getBoundingClientRect();
    s.querySelectorAll('.card').forEach(cd => {
      const r = cd.getBoundingClientRect();
      pos.push({sheet: s.dataset.sheet, side: s.dataset.side, card: cd.querySelector('.face').dataset.card,
        x_mm: ((r.left - sr.left) / mm).toFixed(2), y_mm: ((r.top - sr.top) / mm).toFixed(2),
        w_mm: (r.width / mm).toFixed(2), h_mm: (r.height / mm).toFixed(2)});
    });
  });
  const pre = document.createElement('pre'); pre.id = 'report';
  pre.textContent = 'REPORT_BEGIN' + JSON.stringify({faces: res, pos}) + 'REPORT_END';
  document.body.appendChild(pre);
}); });
</script>
"""


def build():
    parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><title>' + DECK_TITLE + ' flash cards</title>'
             '<meta name="viewport" content="width=device-width, initial-scale=1">'
             f"<style>{CSS}</style></head><body" + (' class="one"' if SHOT else '') + ">"]
    for s in range(1, N_SHEETS + 1):
        cs = CARDS[(s - 1) * PER_SHEET:s * PER_SHEET]
        parts.append(sheet_html(s, "front", cs))
        parts.append(sheet_html(s, "back", cs))
    if CHECK:
        parts.append(SCRIPT)
    parts.append("</body></html>")
    text = "\n".join(parts)
    for side in ("front", "back"):          # every card must be printed exactly once on each side
        placed = len(re.findall(r'data-side="%s" style=' % side, text))
        assert placed == len(CARDS), f"{placed} {side} faces placed on the sheets, the deck has {len(CARDS)} cards"
    return text


if __name__ == "__main__":
    html_out = build()
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(html_out)
    # what the printed PDF must contain, for check_pdf.py: every card once on each side, with its bottom line (a clipped card loses it)
    with open(os.path.splitext(OUT)[0] + ".expect.json", "w", encoding="utf-8") as fh:
        json.dump(dict(cards=len(CARDS), sheets=N_SHEETS, per_sheet=PER_SHEET,
                       cards_on_sheet=[len(CARDS[(s - 1) * PER_SHEET:s * PER_SHEET]) for s in range(1, N_SHEETS + 1)],
                       flip_text=asc(FLIP_TEXT), trap_label=asc(TRAP_LABEL)), fh)
    print("wrote", OUT, len(html_out), "bytes")

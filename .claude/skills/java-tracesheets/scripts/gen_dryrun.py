"""Hand-written style trace sheet, 4 A4 pages per topic, from a spec file (see ../sheets/).

  page 1  mechanism card  mantra, the mechanism (numbered recipe), 3 questions, interview signals, NOT-when, pitfall checklist
  page 2  worked trace    the starting state drawn as boxes + one storyboard row per step (frame, pointers, state strips,
                          variables with crossed-out old values, a note), conclusion circled
  page 3  your turn       a new scenario, the same rows left blank for a pencil; the conclusion is printed upside down at the bottom
  page 4  answer key      the practice trace step by step, the 5-step interview answer, spaced-repetition boxes, the topic's question ladder

usage: python gen_dryrun.py <out.html> [shot]       env DRY_SPEC = path of the sheet spec (.py defining SPEC)
All strokes use a seeded wobble, so the output is deterministic.
"""
import html
import io
import math
import os
import random
import sys

from PIL import ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1]
SHOT = len(sys.argv) > 2 and sys.argv[2] == "shot"
R = random.Random(2027)

INK, RED, GREEN, TEAL = "#1c3f94", "#c8352b", "#1b8a46", "#0b8f86"
GREY, PENCIL = "#9aa0aa", "#5b616b"
FILL = {"g": "#d9f2e0", "r": "#fde3df", "t": "#dff5ee", "y": "#fff3a8"}
COL = {"g": GREEN, "r": RED, "t": TEAL, "i": INK, "p": PENCIL}
FONT_CSS = "'Ink Free','Segoe Print','Bradley Hand ITC','Comic Sans MS',cursive"
INKFONT = os.environ.get("FLASH_INKFONT", r"C:\Windows\Fonts\Inkfree.ttf")
W, H = 794, 1123
LIMIT_Y = 1085                                    # nothing may be drawn below this

_fonts = {}


def tw(txt, px):
    if px not in _fonts:
        _fonts[px] = ImageFont.truetype(INKFONT, int(round(px * 4)))
    return _fonts[px].getlength(txt) / 4


def asc(s):
    for a, b in (("\u2265", ">="), ("\u2264", "<="), ("\u2192", "->"), ("\u2190", "<-"), ("\u2212", "-")):
        s = s.replace(a, b)
    return s


def j(a):
    return R.uniform(-a, a)


def f(v):
    return f"{v:.1f}"


def esc(s):
    return html.escape(str(s))


# ------------------------------------------------------------------ pen primitives
def stroke(d, color=INK, w=2.2, extra=""):
    return (f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{w}" stroke-linecap="round" '
            f'stroke-linejoin="round" {extra}/>')


def smooth(pts):
    d = f"M{f(pts[0][0])} {f(pts[0][1])}"
    for i in range(1, len(pts) - 1):
        mx, my = (pts[i][0] + pts[i + 1][0]) / 2, (pts[i][1] + pts[i + 1][1]) / 2
        d += f" Q{f(pts[i][0])} {f(pts[i][1])} {f(mx)} {f(my)}"
    return d + f" L{f(pts[-1][0])} {f(pts[-1][1])}"


def line(x1, y1, x2, y2, color=INK, w=2.2, wob=1.3, over=0.0, extra=""):
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
    d = (f"M{f(x1 + j(.5))} {f(y1 + j(.5))} C{f(c1[0])} {f(c1[1])} {f(c2[0])} {f(c2[1])} {f(x2 + j(.5))} {f(y2 + j(.5))}")
    return stroke(d, color, w, extra)


def rect(x, y, w, h, color=INK, sw=2.2, over=4.0, wob=1.1, fill=None, extra=""):
    out = []
    if fill:
        out.append(f'<path d="M{f(x)} {f(y)} L{f(x + w)} {f(y)} L{f(x + w)} {f(y + h)} L{f(x)} {f(y + h)} Z" fill="{fill}" stroke="none"/>')
    out += [line(x, y, x + w, y, color, sw, wob, over, extra), line(x + w, y, x + w, y + h, color, sw, wob, over, extra),
            line(x + w, y + h, x, y + h, color, sw, wob, over, extra), line(x, y + h, x, y, color, sw, wob, over, extra)]
    return "".join(out)


def ellipse(cx, cy, rx, ry, color=RED, sw=2.6):
    n = 18
    a0 = R.uniform(0, math.tau)
    pts = []
    for i in range(int(n * 1.12) + 1):
        a = a0 + math.tau * 1.12 * i / int(n * 1.12)
        k = 1 + j(.035) + .06 * i / n
        pts.append((cx + rx * k * math.cos(a), cy + ry * k * math.sin(a)))
    return stroke(smooth(pts), color, sw)


def arrow(x1, y1, x2, y2, bend=18, color=GREEN, w=2.6, head=11):
    mx, my = (x1 + x2) / 2, (y1 + y2) / 2
    dx, dy = x2 - x1, y2 - y1
    L = math.hypot(dx, dy) or 1
    cx, cy = mx + (-dy / L) * bend, my + (dx / L) * bend
    out = [stroke(f"M{f(x1)} {f(y1)} Q{f(cx)} {f(cy)} {f(x2)} {f(y2)}", color, w)]
    tx, ty = x2 - cx, y2 - cy
    tl = math.hypot(tx, ty) or 1
    tx, ty = tx / tl, ty / tl
    for ang in (math.radians(27), -math.radians(27)):
        hx = -tx * math.cos(ang) + ty * math.sin(ang)
        hy = -tx * math.sin(ang) - ty * math.cos(ang)
        out.append(stroke(f"M{f(x2)} {f(y2)} L{f(x2 + hx * head)} {f(y2 + hy * head)}", color, w))
    return "".join(out)


def strike(x, y, w, color=RED, sw=2.4, h=15):
    return (line(x - 4, y - 2, x + w + 4, y - h, color, sw, 1.0) + line(x - 3, y - h + 4, x + w + 3, y - 3, color, sw - .4, 1.0))


def tick(x, y, s=1.0, color=GREEN, sw=3.0):
    return stroke(f"M{f(x)} {f(y)} L{f(x + 9 * s)} {f(y + 11 * s)} L{f(x + 28 * s)} {f(y - 15 * s)}", color, sw)


def sparkle(cx, cy, r=11, color=RED):
    out = []
    for k in range(7):
        a = math.tau * k / 7 + j(.2)
        out.append(line(cx + math.cos(a) * r * .45, cy + math.sin(a) * r * .45, cx + math.cos(a) * r, cy + math.sin(a) * r, color, 2.2, .4))
    return "".join(out)


def highlighter(x, y, w, h, color="#fff176", op=.6, rot=-.6):
    return f'<rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}" rx="5" fill="{color}" opacity="{op}" transform="rotate({rot} {f(x)} {f(y)})"/>'


def text(x, y, s, size=20, color=INK, anchor="start", rot=None, weight="normal", opacity=1.0):
    a = j(.9) if rot is None else rot
    op = f' opacity="{opacity}"' if opacity != 1 else ""
    return (f'<text x="{f(x)}" y="{f(y)}" font-size="{size}" fill="{color}" text-anchor="{anchor}" font-weight="{weight}"{op} '
            f'xml:space="preserve" transform="rotate({a:.2f} {f(x)} {f(y)})">{esc(asc(s))}</text>')


def blank(x, y, w, color=PENCIL):
    return line(x, y + 4, x + w, y + 3, color, 1.6, .8)


def wrap(s, size, width):
    words, lines, cur = asc(s).split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if tw(t, size) <= width or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    for l in lines:
        assert tw(l, size) <= width + 2, f"word too wide for the column: {l!r}"
    return lines


def para(x, y, s, size, width, color=INK, lh=1.28, weight="normal"):
    """wrapped paragraph; returns the y below it"""
    for l in wrap(s, size, width):
        yield_ = text(x, y, l, size, color, weight=weight, rot=j(.5))
        OUTBUF.append(yield_)
        y += size * lh
    return y


OUTBUF = []          # the svg parts of the page being drawn


def add(s):
    OUTBUF.append(s)


def need(y, what):
    assert y <= LIMIT_Y, f"{what}: content reaches y={y:.0f}, the page ends at {LIMIT_Y}"


def paper():
    out = [f'<rect width="{W}" height="{H}" fill="#fffef9"/>']
    for y in range(150, H - 20, 34):
        out.append(f'<line x1="0" y1="{y}" x2="{W}" y2="{y}" stroke="#dce8f4" stroke-width="1.2"/>')
    out.append(f'<line x1="40" y1="0" x2="40" y2="{H}" stroke="#f2b8b8" stroke-width="1.6"/>')
    return "".join(out)


def title(main, tag, sub=None):
    add(text(58, 82, main, 28, INK, rot=-.5, weight="bold"))
    ln = min(tw(main, 28) * 1.0, 480)
    add(line(56, 96, 56 + ln, 95, INK, 2.4, 1.6, 3))
    add(line(60, 102, 56 + ln * .97, 101, INK, 1.4, 1.6, 3))
    add(rect(588, 46, 176, 44, TEAL, 2.4, 4, fill="#e6f6f3"))
    add(text(676, 76, tag, 17, TEAL, "middle", rot=-1.2))
    if sub:
        add(text(58, 132, sub, 18, PENCIL, rot=-.4))


def codebox(x, y, lines, size=18, lh=26, width=400):
    """recipe in yellow note-paper; each line 'g|text' picks a colour (g r t i p)"""
    h = lh * len(lines) + 26
    add(highlighter(x - 10, y - 22, width, h, "#fff59d", .55, -.7))
    add(rect(x - 10, y - 22, width, h, PENCIL, 1.4, 3, extra='stroke-dasharray="6 4"'))
    for i, ln in enumerate(lines):
        c = INK
        if len(ln) > 2 and ln[1] == "|":
            c, ln = COL[ln[0]], ln[2:]
        assert tw(ln, size) <= width - 24, f"recipe line too wide: {ln!r}"
        add(text(x, y + 2 + lh * i, ln, size, c, rot=-.4))
    return y - 22 + h


def checkbox(x, y, size=22):
    add(rect(x, y - size + 4, size, size, INK, 2.0, 3))


# ------------------------------------------------------------------ storyboard rows
VARX = 640                                        # x of the variable column; storyboard() moves it left for a wide row of cells


def cell_geometry(n, avail=520, x0=84):
    if n > 7:
        avail = 470
    pitch = min(66, avail / max(n, 1))
    bw = pitch - 8
    return x0, pitch, bw


def draw_cells(y, cells, bh, geom, hi=None, cross=(), frame=None, dim=(), idx=True, idx_from=0, color_map=None):
    x0, pitch, bw = geom
    hi, color_map = hi or {}, color_map or {}
    for i, v in enumerate(cells):
        x = x0 + i * pitch
        fill = FILL.get(hi.get(i))
        add(rect(x, y, bw, bh, GREY if i in dim else INK, 2.0, 3.0, fill=fill))
        s = str(v)
        size = 24 if len(s) <= 2 else (20 if len(s) <= 4 else 15)
        while tw(asc(s), size) > bw - 6 and size > 9:
            size -= 1
        c = color_map.get(i, RED if i in cross else (GREY if i in dim else INK))
        add(text(x + bw / 2, y + bh / 2 + size * .36, s, size, c, "middle"))
        if i in cross:
            add(strike(x + bw * .2, y + bh * .78, bw * .6, RED, 2.2, bh * .5))
        if idx:
            add(text(x + bw / 2, y + bh + 15, str(i + idx_from), 12, RED, "middle", opacity=.85))
    if frame:
        l, r = frame
        xl, xr = x0 + l * pitch - 6, x0 + r * pitch + bw + 6
        for _ in range(2):
            add(rect(xl, y - 7, xr - xl, bh + 14, TEAL, 2.8, 5, wob=1.5))


def draw_ptrs(y, ptr, geom, bh):
    x0, pitch, bw = geom
    by = {}
    for lab, i in ptr.items():
        by.setdefault(i, []).append(lab)
    for i, labs in by.items():
        cx = x0 + i * pitch + bw / 2
        add(arrow(cx, y + bh + 31, cx, y + bh + 18, 0, TEAL, 2.0, 7))
        add(text(cx, y + bh + 46, ",".join(labs), 15, TEAL, "middle"))


def draw_state(y, states, geom_x=84, maxw=530):
    """states = [(label, [items])]; item = 'text' or ('text', 'g'|'r'|'t'|'y') for a coloured box. returns y below"""
    for label, items in states:
        x = geom_x
        add(text(x, y + 20, label, 15, PENCIL))
        x += tw(label, 15) + 12
        if not items:
            add(text(x, y + 20, "(empty)", 15, GREY))
        for it in items:
            t, fl = (it, None) if isinstance(it, str) else it
            t = asc(t)
            w = max(tw(t, 17) + 16, 30)
            if x + w > geom_x + maxw:
                add(text(x, y + 20, "...", 17, PENCIL))
                break
            add(rect(x, y, w, 26, INK, 1.8, 2.2, fill=FILL.get(fl)))
            add(text(x + w / 2, y + 19, t, 17, RED if fl == "r" else INK, "middle"))
            x += w + 8
        y += 33
    return y


class VarTracker:
    def __init__(self):
        self.last = {}

    def draw(self, x, y, vars_):
        for k, v in vars_.items():
            s = str(v)
            name = f"{k} ="
            add(text(x, y, name, 17, INK, rot=-.5))
            vx = x + tw(name, 17) + 8
            old = self.last.get(k)
            if old is not None and str(old) != s:
                add(text(vx, y, str(old), 17, PENCIL))
                add(strike(vx, y, tw(asc(str(old)), 17), RED, 2.0, 11))
                vx += tw(asc(str(old)), 17) + 12
            add(text(vx, y, s, 17, INK, rot=j(1)))
            self.last[k] = v
            y += 26


def step_row(y, n_step, step, base_cells, geom, bh, tracker):
    cells = step.get("cells", base_cells)
    add(text(60, y + bh / 2 + 6, str(n_step), 20, RED, "middle", rot=-3))
    draw_cells(y, cells, bh, geom, step.get("hi"), step.get("cross", ()), step.get("frame"), step.get("dim", ()), step.get("idx", True),
               color_map=step.get("colors"))
    yy = y + bh + 24
    if step.get("ptr"):
        draw_ptrs(y, step["ptr"], geom, bh)
        yy = y + bh + 52
    if step.get("state"):
        yy = draw_state(yy, step["state"])
    if step.get("vars"):
        tracker.draw(VARX, y + 22, step["vars"])
    if step.get("note"):
        for l in wrap(step["note"], 17, 640):
            add(text(84, yy + 14, l, 17, PENCIL, rot=-.3))
            yy += 22
        yy += 2
    return yy + 8


def row_height_plan(steps):
    """pick the box height so that all rows fit the page"""
    return 38 if len(steps) <= 5 else 32


# ------------------------------------------------------------------ the pages
def page_card(S):
    c = S["card"]
    title(S["name"], f"topic {S['n']:02d}", "the card to read, then cover and rewrite")
    # mantra
    yy = 168
    lines = wrap(c["mantra"], 23, 660)
    add(highlighter(48, yy - 24, 690, 30 * len(lines) + 16, "#fff176", .55, -.5))
    for l in lines:
        add(text(62, yy, l, 23, INK, weight="bold", rot=-.4))
        yy += 30
    top = yy + 34
    # recipe (left)
    add(text(60, top, "THE MECHANISM", 15, PENCIL))
    end = codebox(70, top + 40, c["recipe"], 18, 26, 400)
    need(end, "recipe")
    add(text(60, end + 34, c["cost"], 17, TEAL, rot=-.5))
    # right column
    x, y = 500, top
    add(text(x, y, "THE 3 QUESTIONS", 15, PENCIL))
    y += 30
    for i, q in enumerate(c["questions"], 1):
        add(text(x, y, f"{i}", 19, RED))
        for l in wrap(q, 18, 232):
            add(text(x + 20, y, l, 18, INK, rot=-.3))
            y += 24
        y += 6
    y += 8
    add(text(x, y, "SPOT IT", 15, PENCIL))
    y += 30
    for t in c["triggers"]:
        w = min(tw(asc(t), 17) + 24, 262)
        add(ellipse(x + w / 2, y - 6, w / 2 + 4, 17, TEAL, 2.0))
        add(text(x + w / 2, y, t, 17, TEAL, "middle"))
        y += 40
    y += 4
    add(text(x, y, "NOT WHEN", 15, RED))
    y += 28
    for t in c["donts"]:
        add(text(x, y, "x", 18, RED))
        for l in wrap(t, 17, 232):
            add(text(x + 20, y, l, 17, INK, rot=-.3))
            y += 23
        y += 5
    need(y, "right column")
    # bug checklist
    by = max(end + 90, y + 30)
    add(text(60, by, "PITFALLS I NAME BEFORE I SAY \"DONE\"", 15, PENCIL))
    by += 34
    for b in c["bugs"]:
        checkbox(64, by)
        ls = wrap(b, 18, 640)
        for l in ls:
            add(text(100, by, l, 18, INK, rot=-.3))
            by += 24
        by += 8
    need(by, "bug list")


def storyboard(S, spec, blank_mode):
    steps = spec["steps"]
    bh = row_height_plan(steps)
    base = spec["cells"]
    global VARX
    VARX = 566 if len(base) > 7 else 640
    geom = cell_geometry(len(base))
    y = 168
    add(text(58, y, spec["input_label"], 19, INK, rot=-.4))
    y += 44
    tracker = VarTracker()
    for n, st in enumerate(steps, 1):
        y = blank_row(y, n, st, base, geom, bh) if blank_mode else step_row(y, n, st, base, geom, bh, tracker)
        need(y, "storyboard")
    return y


def blank_row(y, n, st, base, geom, bh):
    add(text(60, y + bh / 2 + 6, str(n), 20, RED, "middle", rot=-3))
    draw_cells(y, st.get("cells", base), bh, geom, idx=st.get("idx", True))
    yy = y + bh + 24
    vy = y + 22
    for k in (st.get("vars") or {}):
        add(text(VARX, vy, f"{k} =", 17, PENCIL))
        bx = VARX + tw(f"{k} =", 17) + 8
        add(blank(bx, vy, max(24, min(60, W - 12 - bx))))                      # a long variable name must not push the line off the page
        vy += 26
    if st.get("ptr"):
        lab = "pointers: " + ", ".join(st["ptr"].keys()) + " = "
        add(text(84, yy + 14, lab, 15, PENCIL))
        add(blank(84 + tw(lab, 15) + 4, yy + 14, 150))
        yy += 26
    for label, _ in st.get("state") or []:
        add(text(84, yy + 16, f"{label}:", 15, PENCIL))
        add(rect(84 + tw(label + ":", 15) + 10, yy - 2, 360, 26, GREY, 1.4, 3, extra='stroke-dasharray="5 4"'))
        yy += 32
    add(text(84, yy + 14, "what happens:", 15, PENCIL))
    add(blank(84 + tw("what happens:", 15) + 6, yy + 14, 380))
    return yy + 38


def page_worked(S):
    wk = S["worked"]
    assert tw(asc("result: " + wk["answer"]), 24) <= 600, "the worked result is too long for its line (the ellipse would leave the page): shorten it"
    title(wk["title"], "worked trace", wk["subtitle"])
    y = storyboard(S, wk, False) + 34
    add(highlighter(54, y - 20, tw(asc("result: " + wk["answer"]), 24) + 40, 38, "#fff176", .6, -.6))
    add(text(70, y + 6, "result: " + wk["answer"], 24, INK, weight="bold"))
    add(ellipse(70 + tw(asc("result: " + wk["answer"]), 24) / 2 + 8, y - 2, tw(asc("result: " + wk["answer"]), 24) / 2 + 30, 26, RED, 2.8))
    add(sparkle(70 + tw(asc("result: " + wk["answer"]), 24) + 56, y - 16))
    need(y + 40, "worked result")


def page_practice(S):
    pr = S["practice"]
    title(pr["title"], "your turn", pr["subtitle"])
    y = storyboard(S, pr, True)
    add(text(60, y + 12, "my result:", 20, INK))
    add(blank(60 + tw("my result:", 20) + 10, y + 12, 220))
    # upside-down result at the very bottom
    ans = "result: " + pr["answer"]
    add(f'<text x="{W - 70}" y="{H - 38}" font-size="14" fill="{GREY}" text-anchor="start" xml:space="preserve" transform="rotate(180 {W - 70} {H - 42})">{esc(asc(ans))}</text>')
    need(y + 40, "practice")


def page_key(S):
    pr, say = S["practice"], S["say"]
    title("Answer key + say it out loud", "answer key", pr["title"] + "  " + pr["subtitle"])
    y = 168
    add(text(58, y, "THE TRACE, STEP BY STEP", 15, PENCIL))
    y += 30
    for n, st in enumerate(pr["steps"], 1):
        lines = wrap(st.get("key") or st.get("note", ""), 17, 650)       # `key` = the short version of the note, so that the key page stays on one page
        add(text(58, y, str(n), 19, RED, "middle", rot=-3))
        for l in lines:
            add(text(76, y, l, 17, INK, rot=-.3))
            y += 23
        y += 6
    add(highlighter(54, y - 14, tw(asc("result: " + pr["answer"]), 22) + 34, 34, "#fff176", .6, -.6))
    add(text(66, y + 8, "result: " + pr["answer"], 22, INK, weight="bold"))
    y += 56
    add(text(58, y, "SAY IT IN THE INTERVIEW (5 steps)", 15, PENCIL))
    y += 30
    script = [(str(i), f"{label}: {body}") for i, (label, body) in enumerate(say, 1)]
    assert len(script) == 5, "the interview answer has exactly 5 steps: say = [(label, text) x 5]"
    for k, s in script:
        add(text(58, y, k, 19, RED, "middle", rot=-3))
        for l in wrap(s, 17, 650):
            add(text(76, y, l, 17, INK, rot=-.3))
            y += 23
        y += 6
    y += 14
    add(text(58, y, "SPACED REPETITION  (tick when done)", 15, PENCIL))
    y += 30
    for i, d in enumerate(["day 0", "day 1", "day 3", "day 7", "day 21"]):
        x = 64 + i * 140
        add(rect(x, y - 22, 26, 26, INK, 2.0, 3))
        add(text(x + 38, y, d, 18, INK))
    y += 36
    add(text(58, y, "MY LADDER: tick each one when I can answer it cold, out loud (M = must, C = core, S = stretch)", 15, PENCIL))
    y += 30
    ladder = S["ladder"]
    half = (len(ladder) + 1) // 2                                       # two columns, read top to bottom: the first half left, the rest right
    ends = []
    for col, items in enumerate((ladder[:half], ladder[half:])):
        x, yy = 62 + col * 366, y
        for tier, question in items:
            assert tier in ("M", "C", "S"), f"ladder tier must be M, C or S, got {tier!r}"
            checkbox(x, yy, 18)
            add(text(x + 28, yy - 2, tier, 14, {"M": GREEN, "C": INK, "S": "#8a3fb0"}[tier]))
            for l in wrap(question, 15, 292):
                add(text(x + 48, yy - 2, l, 15, INK, rot=-.3))
                yy += 18
            yy += 11
        ends.append(yy)
    y = max(ends)
    need(y, "answer key")


# ------------------------------------------------------------------ assemble
def svg_page(fn, S, name):
    global OUTBUF
    OUTBUF = []
    add(paper())
    fn(S)
    body = "".join(OUTBUF)
    return (f'<svg viewBox="0 0 {W} {H}" width="210mm" height="297mm" xmlns="http://www.w3.org/2000/svg" '
            f'font-family="{FONT_CSS}" role="img" aria-label="{esc(name)}">{body}</svg>')


def build(S):
    pages = [("recipe card", page_card), ("worked example", page_worked), ("your turn", page_practice), ("answer key", page_key)]
    parts = [f'<!doctype html><html lang="en"><head><meta charset="utf-8"><title>{esc(S["name"])} dry-run sheet</title>'
             '<meta name="viewport" content="width=device-width, initial-scale=1"><style>'
             '@page { size: 210mm 297mm; margin: 0; } * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }'
             f'html, body {{ margin: 0; background: #e9e9ec; font-family: {FONT_CSS}; }}'
             '.page { width: 210mm; height: 297mm; margin: 0 auto 6mm; background: #fff; break-after: page; page-break-after: always; overflow: hidden; }'
             '.page:last-of-type { break-after: auto; page-break-after: auto; margin-bottom: 0; } .page svg { display: block; }'
             '@media print { html, body { background: #fff; } .page { margin: 0; } }'
             '@media screen { body { padding: 6mm 0; } }'
             'body.one { padding: 0 !important; background: #fff; } body.one .page { display: none; margin: 0; } body.one .page:target { display: block; }'
             '</style></head><body' + (' class="one"' if SHOT else '') + '>']
    for i, (name, fn) in enumerate(pages, 1):
        parts.append(f'<section class="page" id="p{i}">{svg_page(fn, S, name)}</section>')
    parts.append("</body></html>")
    return "\n".join(parts)


if __name__ == "__main__":
    spec_path = os.environ["DRY_SPEC"]
    ns = {}
    exec(compile(io.open(spec_path, encoding="utf-8").read(), spec_path, "exec"), ns)
    S = ns["SPEC"]
    io.open(OUT, "w", encoding="utf-8", newline="\n").write(build(S))
    print("wrote", OUT)

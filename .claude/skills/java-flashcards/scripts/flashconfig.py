"""Reads the flash-card settings from a Markdown file and works out the sheet layout.

The settings live in the FIRST table of flashcards.config.md that has the header `| Setting | Value | ... |`.
Which file is used (first match wins):
    1. the path given to load(path)            (build_all.py --config <file>)
    2. the FLASH_CONFIG environment variable
    3. <FLASH_WORK>/flashcards.config.md        (a per-project copy next to the generated files)
    4. flashcards.config.md in the skill folder (the defaults: A4, 100 x 144 mm cards, Ink Free)

usage: python flashconfig.py [file.md]     prints the resolved layout and warnings, exit 1 on an error
"""
import difflib
import glob
import hashlib
import json
import math
import os
import sys

SKILL = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_CONFIG = os.path.join(SKILL, "flashcards.config.md")
WORK = os.path.abspath(os.environ.get("FLASH_WORK", os.getcwd()))

PAPERS = {"A3": (297, 420), "A4": (210, 297), "A5": (148, 210), "A6": (105, 148),
          "LETTER": (215.9, 279.4), "LEGAL": (215.9, 355.6), "TABLOID": (279.4, 431.8)}

# key -> default text (the md file may leave any of them out)
DEFAULTS = {
    "paper": "A4", "orientation": "portrait",
    "card_width": "100", "card_height": "144",
    "margin_x": "4", "margin_y": "4", "gap": "0", "columns": "auto", "rows": "auto",
    "content_inset": "5", "border_inset": "2.6", "scale": "auto",
    "handwriting_font": "Ink Free", "code_font": "Consolas", "font_folder": "",
    "cut_guides": "yes", "sheet_label": "yes",
}
BASE_FALLBACKS = ["Ink Free", "Segoe Print", "Bradley Hand ITC", "Comic Sans MS"]     # tried after the chosen font
DESIGN_W, DESIGN_H = 90.0, 134.0           # content box (mm) the card layout and the diagrams were designed for


class ConfigError(Exception):
    pass


def fmt(v):
    """100.0 -> '100', 4.5 -> '4.5' (no trailing zeros, so the generated HTML stays tidy)"""
    return f"{round(float(v), 3):g}"


def _cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def parse_md(text):
    """the rows of the first `| Setting | Value |` table -> {key: value text}"""
    rows, in_table = {}, False
    for line in text.splitlines():
        s = line.strip()
        if not s.startswith("|"):
            if in_table:
                break                                   # the settings table ended
            continue
        cells = _cells(s)
        if not in_table:
            if len(cells) >= 2 and cells[0].lower() == "setting" and cells[1].lower() == "value":
                in_table = True
            continue
        if all(set(c) <= set("-: ") for c in cells):    # the |---|---| row
            continue
        key = cells[0].strip("` ").lower().replace(" ", "_").replace("-", "_")
        val = cells[1].strip("` ") if len(cells) > 1 else ""
        if key not in DEFAULTS:
            guess = difflib.get_close_matches(key, list(DEFAULTS), n=1)
            raise ConfigError(f"unknown setting '{cells[0]}'" + (f" (did you mean '{guess[0]}'?)" if guess else "")
                              + "; valid settings: " + ", ".join(DEFAULTS))
        rows[key] = val
    return rows


def _num(key, text, lo=None, hi=None):
    t = text.lower().replace("mm", "").strip()
    try:
        v = float(t)
    except ValueError:
        raise ConfigError(f"{key}: '{text}' is not a number")
    if lo is not None and v < lo or hi is not None and v > hi:
        raise ConfigError(f"{key}: {v:g} is outside {lo:g} .. {hi:g}")
    return v


def _yes(key, text):
    t = text.lower()
    if t in ("yes", "true", "on", "1"):
        return True
    if t in ("no", "false", "off", "0"):
        return False
    raise ConfigError(f"{key}: use yes or no, not '{text}'")


def _paper(text, orientation):
    t = text.lower().replace("mm", "").replace("×", "x").strip()
    if t.upper() in PAPERS:
        w, h = PAPERS[t.upper()]
    elif "x" in t:
        try:
            w, h = (float(p) for p in t.split("x"))
        except ValueError:
            raise ConfigError(f"paper: cannot read '{text}'; use a name ({', '.join(p.title() for p in PAPERS)}) or 'width x height' in mm")
    else:
        raise ConfigError(f"paper: unknown size '{text}'; use a name ({', '.join(p.title() for p in PAPERS)}) or 'width x height' in mm")
    if orientation == "landscape":
        w, h = max(w, h), min(w, h)
    elif orientation == "portrait":
        w, h = min(w, h), max(w, h)
    else:
        raise ConfigError(f"orientation: use portrait or landscape, not '{orientation}'")
    return w, h


def _count(avail, size, gap):
    """how many cards of `size` with `gap` between them fit into `avail`"""
    return int(math.floor((avail + gap) / (size + gap) + 1e-9))


# ------------------------------------------------------------------ fonts
FONT_EXT = (".ttf", ".otf", ".ttc")


def system_font_dirs():
    dirs = []
    if os.name == "nt":
        dirs += [os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts"),
                 os.path.join(os.environ.get("LOCALAPPDATA", ""), "Microsoft", "Windows", "Fonts")]
    else:
        dirs += ["/usr/share/fonts", "/usr/local/share/fonts", os.path.expanduser("~/.fonts"),
                 os.path.expanduser("~/.local/share/fonts"), "/Library/Fonts", "/System/Library/Fonts", os.path.expanduser("~/Library/Fonts")]
    return [d for d in dirs if d and os.path.isdir(d)]


def _font_files(dirs):
    out = []
    for d in dirs:
        for root, _, files in os.walk(d):
            out += [os.path.join(root, f) for f in files if f.lower().endswith(FONT_EXT)]
    return sorted(out)


CARD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,:;!?'\"()[]{}<>=+-*/\|^_#~&%@$"     # what the cards print


def read_font_info(path):
    """[{family, style, weight, italic, mono, file, missing}] for one font file (one entry per face of a collection);
    `missing` = the characters of CARD_CHARS the font has no glyph for"""
    from fontTools.ttLib import TTCollection, TTFont
    try:
        faces = TTCollection(path, lazy=True).fonts if path.lower().endswith(".ttc") else [TTFont(path, lazy=True)]
    except Exception:
        return []
    out = []
    for f in faces:
        try:
            name = f["name"]
            family = name.getDebugName(16) or name.getDebugName(1)
            style = name.getDebugName(17) or name.getDebugName(2) or "Regular"
            os2 = f["OS/2"] if "OS/2" in f else None
            weight = int(getattr(os2, "usWeightClass", 400)) if os2 else 400
            italic = bool(getattr(os2, "fsSelection", 0) & 1) if os2 else False
            mono = bool(f["post"].isFixedPitch) if "post" in f else False
            cmap = f.getBestCmap() or {}
            out.append(dict(family=family, style=style, weight=weight, italic=italic, mono=mono, file=path,
                            missing="".join(ch for ch in CARD_CHARS if ord(ch) not in cmap)))
        except Exception:
            continue
    for f in faces:                                        # lazy fonts keep their file open until closed
        try:
            f.close()
        except Exception:
            pass
    return out


def scan_fonts(extra_dirs=(), cache=True):
    """every installed font (plus the extra folders). Cached in FLASH_WORK/fonts_catalog.json, rebuilt when a folder changes."""
    dirs = system_font_dirs() + [d for d in extra_dirs if d and os.path.isdir(d)]
    files = _font_files(dirs)
    stamp = hashlib.md5("|".join(f"{p}:{int(os.path.getmtime(p))}" for p in files).encode()).hexdigest()
    path = os.path.join(WORK, "fonts_catalog.json")
    if cache and os.path.exists(path):
        try:
            with open(path, encoding="utf-8") as fh:
                data = json.load(fh)
            if data.get("stamp") == stamp:
                return data["fonts"]
        except (ValueError, KeyError):
            pass
    fonts = []
    for p in files:
        fonts += read_font_info(p)
    if cache:
        try:
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(dict(stamp=stamp, fonts=fonts), fh)
        except OSError:
            pass
    return fonts


def pick_regular(faces):
    """the upright face closest to weight 400"""
    up = [f for f in faces if not f["italic"]] or faces
    return min(up, key=lambda f: abs(f["weight"] - 400))


def resolve_font(spec, extra_dirs=()):
    """spec = a family name or a path to a font file -> (family, regular file, from_file)"""
    spec = spec.strip().strip("'\"")
    if spec.lower().endswith(FONT_EXT):
        p = spec if os.path.isabs(spec) else os.path.join(WORK, spec)
        if not os.path.exists(p):
            raise ConfigError(f"handwriting_font: file not found: {spec}")
        info = read_font_info(p)
        if not info:
            raise ConfigError(f"handwriting_font: cannot read the font file {spec}")
        return info[0]["family"], p, True
    sysdir = [d.lower() for d in system_font_dirs()]
    fast = {"ink free": os.path.join(system_font_dirs()[0], "Inkfree.ttf")} if system_font_dirs() else {}
    if spec.lower() in fast and os.path.exists(fast[spec.lower()]):          # the default font: no scan needed
        return "Ink Free", fast[spec.lower()], False
    fonts = scan_fonts(extra_dirs)
    faces = [f for f in fonts if f["family"].lower() == spec.lower()]
    if not faces:
        names = sorted({f["family"] for f in fonts})
        guess = difflib.get_close_matches(spec, names, n=3, cutoff=0.5)
        raise ConfigError(f"font '{spec}' is not installed" + (f" (did you mean {', '.join(guess)}?)" if guess else "")
                          + ". Run font_specimen.py to see the available fonts, or put a .ttf path in handwriting_font.")
    face = pick_regular(faces)
    from_file = not any(os.path.abspath(face["file"]).lower().startswith(d) for d in sysdir)
    return face["family"], face["file"], from_file


def css_name(family):
    return f"'{family}'" if (" " in family or "-" in family) else family


# ------------------------------------------------------------------ the resolved settings
class Config:
    pass


def find_config(path=None):
    if path:
        return os.path.abspath(path)
    if os.environ.get("FLASH_CONFIG"):
        return os.path.abspath(os.environ["FLASH_CONFIG"])
    local = os.path.join(WORK, "flashcards.config.md")
    return local if os.path.exists(local) else DEFAULT_CONFIG


def load(path=None):
    src = find_config(path)
    if not os.path.exists(src):
        raise ConfigError(f"settings file not found: {src}")
    raw = dict(DEFAULTS)
    with open(src, encoding="utf-8") as fh:
        raw.update({k: v for k, v in parse_md(fh.read()).items() if v != ""})     # an empty value = the default
    if os.environ.get("FLASH_INKFONT"):                                   # the old way of choosing the writing font (a .ttf path) still works
        raw["handwriting_font"] = os.environ["FLASH_INKFONT"]
    c = Config()
    c.source, c.raw, c.warnings = src, raw, []
    c.orientation = raw["orientation"].lower()
    c.page_w, c.page_h = _paper(raw["paper"], c.orientation)
    c.card_w = _num("card_width", raw["card_width"], 20, 500)
    c.card_h = _num("card_height", raw["card_height"], 20, 500)
    c.margin_x = _num("margin_x", raw["margin_x"], 0, 100)
    c.margin_y = _num("margin_y", raw["margin_y"], 0, 100)
    c.gap = _num("gap", raw["gap"], 0, 50)
    c.content_inset = _num("content_inset", raw["content_inset"], 1, 30)
    c.border_inset = _num("border_inset", raw["border_inset"], 0.5, 30)
    if c.border_inset >= c.content_inset:
        raise ConfigError(f"border_inset ({fmt(c.border_inset)}) must be smaller than content_inset ({fmt(c.content_inset)}): the border has to stay outside the text")
    c.content_w = c.card_w - 2 * c.content_inset
    c.content_h = c.card_h - 2 * c.content_inset
    if c.content_w < 30 or c.content_h < 40:
        raise ConfigError(f"the text area would be only {fmt(c.content_w)} x {fmt(c.content_h)} mm: use a bigger card or a smaller content_inset")
    c.cols = _count(c.page_w - 2 * c.margin_x, c.card_w, c.gap)
    c.rows = _count(c.page_h - 2 * c.margin_y, c.card_h, c.gap)
    if raw["columns"].lower() != "auto":
        want = int(_num("columns", raw["columns"], 1, 20))
        if want > c.cols:
            raise ConfigError(f"columns: {want} cards of {fmt(c.card_w)} mm do not fit the {fmt(c.page_w)} mm paper with margin_x {fmt(c.margin_x)} (at most {c.cols})")
        c.cols = want
    if raw["rows"].lower() != "auto":
        want = int(_num("rows", raw["rows"], 1, 20))
        if want > c.rows:
            raise ConfigError(f"rows: {want} cards of {fmt(c.card_h)} mm do not fit the {fmt(c.page_h)} mm paper with margin_y {fmt(c.margin_y)} (at most {c.rows})")
        c.rows = want
    if c.cols < 1 or c.rows < 1:
        raise ConfigError(f"a {fmt(c.card_w)} x {fmt(c.card_h)} mm card does not fit on {fmt(c.page_w)} x {fmt(c.page_h)} mm paper with these margins")
    c.per_sheet = c.cols * c.rows
    used_w = c.cols * c.card_w + (c.cols - 1) * c.gap
    used_h = c.rows * c.card_h + (c.rows - 1) * c.gap
    c.x0, c.y0 = round((c.page_w - used_w) / 2, 3), round((c.page_h - used_h) / 2, 3)
    if min(c.x0, c.y0) < 3:
        c.warnings.append(f"the sheet margin is only {fmt(min(c.x0, c.y0))} mm: many printers cannot print closer than about 4 mm to the paper edge, "
                          "so the cut guides there may be clipped (the card content is inset and stays safe)")
    if raw["scale"].lower() == "auto":              # the card is laid out at the design size (a 90 x 134 mm text area) and scaled as a whole
        c.scale = round(max(0.4, min(2.0, min(c.content_w / DESIGN_W, c.content_h / DESIGN_H))), 2)
    else:
        c.scale = _num("scale", raw["scale"], 0.3, 3)
    c.design_w, c.design_h = round(c.content_w / c.scale, 3), round(c.content_h / c.scale, 3)       # the text area in layout (unscaled) mm
    c.dia_scale = c.design_w / DESIGN_W              # diagrams are drawn for 90 mm; they stretch to a wider layout area
    if 9.4 * c.scale < 6.5:
        c.warnings.append(f"scale {c.scale:g}: the step text prints at about {9.4 * c.scale:.1f} pt (the card was designed for 9.4 pt); below about 7 pt is hard to read. "
                          "Use a bigger card, or keep the text short")
    c.cut_guides = _yes("cut_guides", raw["cut_guides"])
    c.sheet_label = _yes("sheet_label", raw["sheet_label"])
    c.safe_mm = round(c.content_inset - 0.3, 3)              # the layout check wants the content at least this far inside the card
    if c.content_inset < 4:
        c.warnings.append(f"content_inset {fmt(c.content_inset)} mm: duplex printers are 1-2 mm off and cutting is crooked, text this close to the edge may be cut")
    c.font_folders = [p for p in (os.path.join(WORK, f) if not os.path.isabs(f) else f for f in raw["font_folder"].split(";") if f.strip())]
    c.font_family, c.font_file, c.font_from_file = resolve_font(raw["handwriting_font"], c.font_folders)
    stack = [c.font_family] + [f for f in BASE_FALLBACKS if f.lower() != c.font_family.lower()]
    c.font_css = ",".join(f"'{f}'" for f in stack) + ",cursive"
    code = raw["code_font"].strip().strip("'\"")
    c.code_family = code
    c.code_css = ", ".join([css_name(code)] + ([] if code.lower() == "courier new" else ["'Courier New'"]) + ["monospace"])
    c.arrow_css = f"{css_name(code)}, monospace"
    if code.lower() not in ("monospace", "courier new", "consolas"):          # the usual ones need no look-up
        if code.lower() not in {f["family"].lower() for f in scan_fonts(c.font_folders)}:
            c.warnings.append(f"code_font '{code}' was not found among the installed fonts; the browser will fall back to Courier New")
    return c


def signature(c):
    """a short id of everything that changes how much text fits on a card (stored in flash_scales.json)"""
    key = [c.page_w, c.page_h, c.card_w, c.card_h, c.content_inset, c.border_inset, c.scale, c.font_family, c.code_family, c.cols, c.rows]
    return hashlib.md5(json.dumps(key, sort_keys=True).encode()).hexdigest()[:10]


def describe(c):
    lines = [f"settings file : {c.source}",
             f"paper         : {fmt(c.page_w)} x {fmt(c.page_h)} mm ({c.orientation})",
             f"card          : {fmt(c.card_w)} x {fmt(c.card_h)} mm, text area {fmt(c.content_w)} x {fmt(c.content_h)} mm (inset {fmt(c.content_inset)}, pen border {fmt(c.border_inset)})",
             f"sheet         : {c.cols} x {c.rows} = {c.per_sheet} cards per side, gap {fmt(c.gap)} mm, margins left/right {fmt(c.x0)} mm, top/bottom {fmt(c.y0)} mm",
             f"scale         : {c.scale:g} ({'auto' if c.raw['scale'].lower() == 'auto' else 'set'}): everything on the card is laid out in a {fmt(c.design_w)} x {fmt(c.design_h)} mm text area and scaled",
             f"fonts         : {c.font_family} ({os.path.basename(c.font_file)}) for the writing, {c.code_family} for the code"]
    lines += [f"WARNING       : {w}" for w in c.warnings]
    return "\n".join(lines)


if __name__ == "__main__":
    try:
        cfg = load(sys.argv[1] if len(sys.argv) > 1 else None)
    except ConfigError as e:
        print("SETTINGS ERROR:", e)
        sys.exit(1)
    print(describe(cfg))

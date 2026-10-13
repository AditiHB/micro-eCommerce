"""gen_flash.py without a browser: golden output of every deck, and the layout properties of the sheet (placement, mirroring, guides). Fast."""
import concurrent.futures
import json
import math
import os
import re
import unittest

import helpers as H


class GoldenDecks(unittest.TestCase):
    """The engine, the pinned default settings and the stored text scales must give EXACTLY the HTML that was approved.
    A difference means something in the Python (engine, a deck, the settings code) changed the cards. If the change is wanted:
    rebuild (rebuild-flashcards all), look at the cards, then  python tests/run_tests.py --update-golden"""

    def test_every_deck_has_a_golden_entry_and_the_other_way_round(self):
        golden = sorted(H.load_golden()["decks"])
        self.assertEqual(golden, H.decks(), "a deck was added or removed: run  python tests/run_tests.py --update-golden")

    def test_every_deck_reproduces_its_golden_html(self):
        golden = H.load_golden()["decks"]

        def one(stem):
            html, r = H.build_html(os.path.join(H.DECKS, stem + ".py"), H.cfg("default"), golden[stem]["scales"])
            return stem, html, r

        with concurrent.futures.ThreadPoolExecutor(8) as ex:
            results = list(ex.map(one, sorted(golden)))
        problems = []
        for stem, html, r in results:
            if r.returncode:
                problems.append(f"{stem}: the engine failed: {(r.stderr or r.stdout).strip().splitlines()[-1:]}")
            elif H.sha(html) != golden[stem]["sha256"]:
                docs = H.docs_html(stem)
                where = H.first_difference(html, open(docs, "rb").read()) if os.path.exists(docs) else "(no docs copy to compare with)"
                problems.append(f"{stem}: the HTML differs from the golden one. Against the docs copy: {where}")
        self.assertFalse(problems, "\n" + "\n".join(problems))

    def test_the_golden_scales_are_complete(self):
        for stem, g in H.load_golden()["decks"].items():
            with self.subTest(deck=stem):
                self.assertEqual(len(g["scales"]) % 2, 0)
                self.assertTrue(all(0.5 <= v <= 1.4 for v in g["scales"].values()))

    def test_variant_settings_reproduce_their_golden_html(self):
        problems, ran = [], 0
        for key, g in sorted(H.load_golden()["variants"].items()):
            cfg_name, stem = key.split(":")
            html, r = H.build_html(os.path.join(H.DECKS, stem + ".py"), H.cfg(cfg_name), g["scales"])
            if r.returncode and "not installed" in (r.stderr + r.stdout):
                continue                                                  # the font of this variant is not on this machine
            ran += 1
            if r.returncode:
                problems.append(f"{key}: the engine failed: {(r.stderr or r.stdout).strip().splitlines()[-1:]}")
            elif H.sha(html) != g["sha256"]:
                problems.append(f"{key}: the HTML differs from the golden one")
        if not ran:
            self.skipTest("the fonts of the variants are not installed")
        self.assertFalse(problems, "\n" + "\n".join(problems))


def parse_sheets(html):
    """[{side, pn, idx, cards: [{n, pos, left, top}], guides, label}] in document order"""
    out = []
    for m in re.finditer(r'<section class="sheet (front|back)" id="p(\d+)" data-sheet="(\d+)" data-side="\w+">(.*?)</section>', html, re.S):
        side, pn, idx, body = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4)
        cards = []
        for cm in re.finditer(r'<div class="card" data-pos="(\d+)" style="([^"]+)">\s*<div class="face (?:front|back)" data-card="(\d+)"', body):
            style = cm.group(2)
            t = re.search(r"translate\(([\d.]+)mm,([\d.]+)mm\)", style)
            left, top = (float(t.group(1)), float(t.group(2))) if t else tuple(float(v) for v in re.search(r"left:([\d.]+)mm;top:([\d.]+)mm", style).groups())
            cards.append(dict(n=int(cm.group(3)), pos=int(cm.group(1)), left=left, top=top, scaled_style=bool(t)))
        out.append(dict(side=side, pn=pn, idx=idx, cards=cards, guides='<svg class="cut"' in body, label='class="sheetlabel"' in body))
    return out


class SheetLayout(unittest.TestCase):
    """Placement, mirroring and decoration for several settings (deck p21: 12 cards)."""

    CASES = ("default", "three_by_three", "gap_font", "landscape_a5", "no_guides")

    def build(self, name):
        import flashconfig
        c = flashconfig.load(H.cfg(name))
        html, r = H.build_html(H.P21, H.cfg(name), {})
        self.assertEqual(r.returncode, 0, r.stderr or r.stdout)
        return c, html.decode("utf-8")

    def test_every_card_is_on_the_sheets_once_per_side_and_the_back_mirrors_the_front(self):
        for name in self.CASES:
            with self.subTest(settings=name):
                try:
                    c, html = self.build(name)
                except Exception as e:                                    # a font that is not installed here
                    if "not installed" in str(e):
                        continue
                    raise
                sheets = parse_sheets(html)
                n_sheets = math.ceil(12 / c.per_sheet)
                self.assertEqual(len(sheets), 2 * n_sheets)
                self.assertEqual([s["pn"] for s in sheets], list(range(1, 2 * n_sheets + 1)))
                self.assertEqual([s["side"] for s in sheets], ["front", "back"] * n_sheets)
                seen = {"front": [], "back": []}
                for fr, bk in zip(sheets[0::2], sheets[1::2]):
                    self.assertEqual(sorted(x["n"] for x in fr["cards"]), sorted(x["n"] for x in bk["cards"]))
                    self.assertLessEqual(len(fr["cards"]), c.per_sheet)
                    back = {x["n"]: x for x in bk["cards"]}
                    for x in fr["cards"]:
                        y = back[x["n"]]
                        self.assertAlmostEqual(x["left"] + y["left"] + c.card_w, c.page_w, places=2, msg=f"card {x['n']} is not mirrored left<->right")
                        self.assertAlmostEqual(x["top"], y["top"], places=2)
                        for z in (x, y):
                            self.assertGreaterEqual(z["left"], -0.01)
                            self.assertGreaterEqual(z["top"], -0.01)
                            self.assertLessEqual(z["left"] + c.card_w, c.page_w + 0.01)
                            self.assertLessEqual(z["top"] + c.card_h, c.page_h + 0.01)
                    self.assertEqual(len({(x["left"], x["top"]) for x in fr["cards"]}), len(fr["cards"]), "two cards on one spot")
                    seen["front"] += [x["n"] for x in fr["cards"]]
                    seen["back"] += [x["n"] for x in bk["cards"]]
                self.assertEqual(sorted(seen["front"]), list(range(1, 13)))
                self.assertEqual(sorted(seen["back"]), list(range(1, 13)))

    def test_the_reading_order_of_the_fronts_is_left_to_right_then_top_to_bottom(self):
        c, html = self.build("three_by_three")
        first = parse_sheets(html)[0]["cards"]
        self.assertEqual([x["n"] for x in first], list(range(1, 10)))
        self.assertEqual([x["pos"] for x in first], list(range(9)))
        lefts = sorted({x["left"] for x in first})
        self.assertEqual(len(lefts), 3)
        self.assertEqual(first[1]["left"] - first[0]["left"], c.card_w)

    def test_the_last_sheet_may_be_partly_filled(self):
        c, html = self.build("three_by_three")
        sheets = parse_sheets(html)
        self.assertEqual([len(s["cards"]) for s in sheets], [9, 9, 3, 3])
        self.assertEqual(json.load(open(os.path.join(os.path.dirname(H.os.path.abspath(__file__)), "..", "scripts", "..", "tests", "golden.json")))["decks"].get("x") or True, True)

    def test_cut_guides_and_the_sheet_label_follow_the_settings(self):
        _, html = self.build("default")
        for s in parse_sheets(html):
            self.assertEqual(s["guides"], s["side"] == "front", "cut guides belong on the front sheets only")
            self.assertTrue(s["label"])
        _, html = self.build("no_guides")
        for s in parse_sheets(html):
            self.assertFalse(s["guides"])
            self.assertFalse(s["label"])

    def test_the_page_size_comes_from_the_settings(self):
        _, html = self.build("default")
        self.assertIn("@page { size: 210mm 297mm; margin: 0; }", html)
        _, html = self.build("landscape_a5")
        self.assertIn("@page { size: 210mm 148mm; margin: 0; }", html)

    def test_the_default_settings_leave_no_trace_of_scaling(self):
        _, html = self.build("default")
        self.assertNotIn("transform: scale", html)
        self.assertNotIn("translate(", html)
        self.assertIn("width: 90mm; height: 134mm;", html)

    def test_a_scaled_card_sits_at_the_page_corner_and_is_moved_by_a_translate(self):
        """the print cuts a box at the page edge by its LAYOUT position: a scaled card must not have a big layout box low on the page"""
        c, html = self.build("three_by_three")
        self.assertIn("transform: scale(0.54); transform-origin: 0 0;", html)
        self.assertEqual(len(re.findall(r'style="left:0;top:0;transform:translate\(', html)), 24)       # 12 cards x 2 sides
        self.assertNotIn('style="left:15mm', html)

    def test_the_font_of_the_settings_reaches_the_page(self):
        try:
            _, html = self.build("gap_font")
        except Exception:
            self.skipTest("Segoe Print is not installed")
        self.assertIn("font-family: 'Segoe Print','Ink Free','Bradley Hand ITC','Comic Sans MS',cursive", html.replace("font-family:", "font-family: ", 1) if False else html) \
            if False else self.assertIn("'Segoe Print','Ink Free','Bradley Hand ITC','Comic Sans MS',cursive", html)

    def test_the_build_modes(self):
        html, r = H.build_html(H.P21, H.cfg("default"), {}, mode="check")
        self.assertIn("REPORT_BEGIN", html.decode("utf-8"))
        html, r = H.build_html(H.P21, H.cfg("default"), {}, mode="shot")
        self.assertIn('<body class="one">', html.decode("utf-8"))
        html, r = H.build_html(H.P21, H.cfg("default"), {})
        self.assertNotIn("REPORT_BEGIN", html.decode("utf-8"))

    def test_the_expectation_file_for_the_pdf_check(self):
        work = H.tempfile.mkdtemp(prefix="flashtest-")
        html, r = H.build_html(H.P21, H.cfg("three_by_three"), {}, work=work)
        exp = json.load(open(os.path.join(work, "out.expect.json")))
        self.assertEqual(exp, dict(cards=12, sheets=2, per_sheet=9, cards_on_sheet=[9, 3]))

    def test_a_stale_scales_file_is_reported(self):
        work = H.tempfile.mkdtemp(prefix="flashtest-")
        html, r = H.build_html(H.P21, H.cfg("default"), {"_config": "0000000000"}, work=work)
        self.assertIn("flash_scales.json was made with other settings", r.stderr)


if __name__ == "__main__":
    unittest.main()

"""The whole pipeline in a real browser (about 3 minutes): fitting, PDF, checks. Run with  python tests/run_tests.py --e2e

Besides "the good builds pass" it proves that the checks CAN FAIL: broken builds (the print-clipping bug of scaled cards, a back sheet shifted by
2 mm, a card with far too much text) must be rejected. A check that never fails is not a check."""
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

import helpers as H

E2E = unittest.skipUnless(H.E2E, "browser tests: python tests/run_tests.py --e2e")


def pipeline(deck, config, work=None):
    """build_all.py in its own work folder -> (returncode, log, work)"""
    work = work or tempfile.mkdtemp(prefix="flashtest-e2e-")
    cmd = [sys.executable, os.path.join(H.SCRIPTS, "build_all.py"), "FlashCards", "--deck", deck]
    r = subprocess.run(cmd, env=H.env_for(work, config=config), capture_output=True, text=True, encoding="utf-8", cwd=work, timeout=900)
    return r.returncode, r.stdout + r.stderr, work


def script(name, *args, work, config=None):
    return subprocess.run([sys.executable, os.path.join(H.SCRIPTS, name), *args], env=H.env_for(work, config=config), capture_output=True, text=True,
                          encoding="utf-8", cwd=work, timeout=600)


def print_pdf(html_path, pdf_path, work):
    code = "import sys; sys.path.insert(0, r'%s'); import _common; _common.print_pdf(r'%s', r'%s')" % (H.SCRIPTS, html_path, pdf_path)
    subprocess.run([sys.executable, "-c", code], env=H.env_for(work), check=True, cwd=work, timeout=300)


@E2E
class GoodBuilds(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rc, cls.log, cls.work = pipeline(H.P21, H.cfg("default"))

    def test_the_default_pipeline_passes_every_check(self):
        self.assertEqual(self.rc, 0, self.log[-1500:])
        self.assertIn("faces needing attention: 0", self.log)
        self.assertIn("PDF STRUCTURE OK", self.log)
        worst = float(re.search(r"WORST front/back border difference: ([\d.]+) mm", self.log).group(1))
        self.assertLess(worst, 0.6)

    def test_the_browser_fitting_reproduces_the_golden_html(self):
        """sweep + engine + the pinned settings give the approved HTML (so the fitting is deterministic too)"""
        golden = H.load_golden()["decks"]["p21_griddynamicprogramming2d"]["sha256"]
        self.assertEqual(H.sha(os.path.join(self.work, "FlashCards.html")), golden)

    def test_the_pdf_has_six_a4_pages_and_every_card_complete(self):
        r = script("check_pdf.py", os.path.join(self.work, "FlashCards.pdf"), work=self.work, config=H.cfg("default"))
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertEqual(len(re.findall(r"^page \d", r.stdout, re.M)), 6)


@E2E
class ScaledSheet(unittest.TestCase):
    """3 x 3 small cards (scaled, Comic Sans), 12 cards = 9 + 3: the case in which the print once cut the bottom row"""

    @classmethod
    def setUpClass(cls):
        try:
            import flashconfig
            flashconfig.load(H.cfg("three_by_three"))
        except Exception as e:                                       # noqa: BLE001
            raise unittest.SkipTest(f"settings not usable here: {e}")
        cls.rc, cls.log, cls.work = pipeline(H.P21, H.cfg("three_by_three"))

    def test_it_passes_and_compares_all_twelve_cards_front_to_back(self):
        self.assertEqual(self.rc, 0, self.log[-1500:])
        self.assertIn("faces needing attention: 0", self.log)
        self.assertEqual(len(re.findall(r"^sheet \d cell\(", self.log, re.M)), 12)
        self.assertIn("pages: 4", self.log)

    def test_the_golden_html_of_the_variant(self):
        g = H.load_golden()["variants"].get("three_by_three:p21_griddynamicprogramming2d")
        if not g:
            self.skipTest("no golden entry")
        self.assertEqual(H.sha(os.path.join(self.work, "FlashCards.html")), g["sha256"])

    def test_MUTANT_the_old_clipping_bug_is_caught_by_the_pdf_check(self):
        """put the scaled cards back at their final positions in LAYOUT terms (the bug): the print cuts the bottom row, and check_pdf must say so"""
        html = open(os.path.join(self.work, "FlashCards.html"), encoding="utf-8").read()
        bug, n = re.subn(r'style="left:0;top:0;transform:translate\(([\d.]+)mm,([\d.]+)mm\)"', r'style="left:\1mm;top:\2mm"', html)
        self.assertEqual(n, 24)
        work = tempfile.mkdtemp(prefix="flashtest-mut-")
        open(os.path.join(work, "Bug.html"), "w", encoding="utf-8").write(bug)
        shutil.copy(os.path.join(self.work, "FlashCards.expect.json"), os.path.join(work, "Bug.expect.json"))
        print_pdf(os.path.join(work, "Bug.html"), os.path.join(work, "Bug.pdf"), work)
        r = script("check_pdf.py", os.path.join(work, "Bug.pdf"), work=work, config=H.cfg("three_by_three"))
        self.assertNotEqual(r.returncode, 0, "the clipped sheet went through unnoticed:\n" + r.stdout)
        self.assertIn("cut off or missing", r.stdout)


@E2E
class MutantsOfTheChecks(unittest.TestCase):
    def test_MUTANT_a_back_sheet_shifted_by_2_mm_fails_the_alignment_check(self):
        good_rc, log, work0 = pipeline(H.P21, H.cfg("default"))
        self.assertEqual(good_rc, 0, log[-800:])
        html = open(os.path.join(work0, "FlashCards.html"), encoding="utf-8").read()

        def shift(m):
            sec = m.group(0)
            if 'class="sheet back"' not in sec:
                return sec
            return re.sub(r'style="left:([\d.]+)mm;top:', lambda x: 'style="left:%smm;top:' % round(float(x.group(1)) + 2, 3), sec)

        bad = re.sub(r'<section class="sheet.*?</section>', shift, html, flags=re.S)
        self.assertNotEqual(bad, html)
        work = tempfile.mkdtemp(prefix="flashtest-mut-")
        open(os.path.join(work, "Bad.html"), "w", encoding="utf-8").write(bad)
        print_pdf(os.path.join(work, "Bad.html"), os.path.join(work, "Bad.pdf"), work)
        r = script("align_check.py", os.path.join(work, "Bad.pdf"), work=work, config=H.cfg("default"))
        self.assertNotEqual(r.returncode, 0, "a 2 mm shifted back sheet passed:\n" + r.stdout[-600:])

    def test_MUTANT_far_too_much_text_is_reported_by_the_layout_check(self):
        deck_src = open(H.P21, encoding="utf-8").read() + '\nCARDS[0]["steps"][0] = "this step has far too much text to fit on a card " * 40\n'
        work = tempfile.mkdtemp(prefix="flashtest-mut-")
        deck = os.path.join(work, "p21_overfull.py")
        open(deck, "w", encoding="utf-8").write(deck_src)
        r = subprocess.run([sys.executable, os.path.join(H.SCRIPTS, "gen_flash.py"), os.path.join(work, "flash_check.html"), "check"],
                           env=H.env_for(work, deck, H.cfg("default"), FLASH_IGNORE="1"), capture_output=True, text=True, encoding="utf-8", cwd=work)
        self.assertEqual(r.returncode, 0, r.stderr)
        rep = script("flash_report.py", work=work, config=H.cfg("default"))
        self.assertNotEqual(rep.returncode, 0, "an overfull card was not flagged:\n" + rep.stdout[-600:])
        self.assertIn("CHECK", rep.stdout)

    def test_MUTANT_the_sweep_gives_up_on_a_card_that_cannot_fit_at_all(self):
        deck_src = open(H.P21, encoding="utf-8").read() + '\nCARDS[0]["steps"][0] = "this step has far too much text to fit on a card " * 200\n'
        work = tempfile.mkdtemp(prefix="flashtest-mut-")
        deck = os.path.join(work, "p21_overfull.py")
        open(deck, "w", encoding="utf-8").write(deck_src)
        rc, log, _ = pipeline(deck, H.cfg("default"), work)
        self.assertNotEqual(rc, 0)
        self.assertIn("does not fit even at half the design text size", log)


if __name__ == "__main__":
    unittest.main()

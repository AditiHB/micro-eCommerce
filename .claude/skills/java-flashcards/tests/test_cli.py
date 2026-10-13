"""The terminal entry points: rebuild_cards.py (pattern selection, --list, errors), font_specimen.py, the wrappers. Fast, no browser."""
import os
import subprocess
import sys
import tempfile
import unittest

import helpers as H


def rebuild_module():
    try:
        import rebuild_cards
    except SystemExit as e:                                  # no Edge / Chrome on this machine
        raise unittest.SkipTest(str(e))
    return rebuild_cards


def run(*args, timeout=120):
    return subprocess.run([sys.executable, os.path.join(H.SCRIPTS, *args[0].split("/")), *args[1:]], capture_output=True, text=True,
                          encoding="utf-8", timeout=timeout, env=H.env_for(tempfile.mkdtemp(prefix="flashtest-")))


class SelectingPatterns(unittest.TestCase):
    def setUp(self):
        self.m = rebuild_module()
        self.decks = self.m.all_decks()

    def names(self, *specs):
        return [d["stem"] for d in self.m.select(list(specs), self.decks)]

    def mains(self, names):
        return [n for n in names if not n.endswith(("_lc75", "_top150"))]

    def test_all_decks_are_found_and_numbered(self):
        main = [d for d in self.decks if not d["kind"]]
        self.assertGreaterEqual(len(main), 21)
        self.assertEqual([d["n"] for d in main], list(range(1, len(main) + 1)), "pattern numbers must be 1, 2, 3 ... without a gap")
        self.assertEqual([d["stem"] for d in self.decks], H.decks())
        for d in self.decks:
            self.assertEqual(d["base"], d["stem"][:-len(d["kind"]) - 1] if d["kind"] else d["stem"])
            self.assertEqual(d["prefix"], d["stem"][:3] + ("_" + d["kind"] if d["kind"] else ""))
            self.assertEqual(d["lc75"], d["kind"] == "lc75")

    def test_by_number_prefix_name_and_range(self):
        self.assertEqual(self.mains(self.names("21")), ["p21_griddynamicprogramming2d"])
        self.assertEqual(self.mains(self.names("p21")), ["p21_griddynamicprogramming2d"])
        self.assertEqual(self.mains(self.names("3")), ["p03_slidingwindow"])
        self.assertEqual(self.mains(self.names("slidingwindow")), ["p03_slidingwindow"])
        self.assertEqual(self.mains(self.names("p03_slidingwindow")), ["p03_slidingwindow"])
        self.assertEqual(self.mains(self.names("1-3")), ["p01_hashingandfrequencymap", "p02_twopointers", "p03_slidingwindow"])

    def test_several_selections_keep_their_order_without_duplicates(self):
        self.assertEqual(self.mains(self.names("5", "3", "5")), ["p05_binarysearchonindex", "p03_slidingwindow"])
        self.assertEqual(len(self.names("5", "5")), len(self.names("5")))
        self.assertEqual(len(self.names("all")), len(self.decks))
        self.assertEqual(len(self.names("all", "3")), len(self.decks))

    def test_unknown_and_ambiguous_selections_stop_with_a_message(self):
        for bad in ("99", "nonsense", "dynamicprogramming", "0-0"):          # the last two: two patterns / none
            with self.subTest(spec=bad), self.assertRaises(SystemExit):
                self.m.select([bad], self.decks)
        with self.assertRaises(SystemExit):
            self.m.select([], self.decks)

    def test_lc75_decks_are_selected_with_their_pattern_or_on_their_own(self):
        """synthetic decks: p02 has a main deck and a LeetCode 75 deck, p03 only a main deck"""
        fake = [dict(n=2, stem="p02_twopointers", base="p02_twopointers", lc75=False, deck="a", prefix="p02"),
                dict(n=2, stem="p02_twopointers_lc75", base="p02_twopointers", lc75=True, deck="b", prefix="p02_lc75"),
                dict(n=3, stem="p03_slidingwindow", base="p03_slidingwindow", lc75=False, deck="c", prefix="p03")]

        def pick(*specs):
            return [d["stem"] for d in self.m.select(list(specs), fake)]
        self.assertEqual(pick("2"), ["p02_twopointers", "p02_twopointers_lc75"])
        self.assertEqual(pick("2:lc75"), ["p02_twopointers_lc75"])
        self.assertEqual(pick("2:main"), ["p02_twopointers"])
        self.assertEqual(pick("lc75"), ["p02_twopointers_lc75"])
        self.assertEqual(pick("twopointers"), ["p02_twopointers", "p02_twopointers_lc75"])
        self.assertEqual(pick("p02_twopointers_lc75"), ["p02_twopointers_lc75"])
        self.assertEqual(pick("1-3"), ["p02_twopointers", "p02_twopointers_lc75", "p03_slidingwindow"])
        self.assertEqual(pick("all"), [d["stem"] for d in fake])
        with self.assertRaises(SystemExit):
            self.m.select(["3:lc75"], fake)
        d = fake[1]
        self.assertTrue(self.m.docs_dir(d).endswith(os.path.join("src", "p02_twopointers", "lc75", "docs")))
        self.assertTrue(self.m.docs_dir(fake[0]).endswith(os.path.join("src", "p02_twopointers", "docs")))

    def test_top150_decks_are_a_second_set_of_extras(self):
        """synthetic decks: p02 has a main deck, a LeetCode 75 deck and a Top 150 deck, p03 a main deck and a Top 150 deck"""
        fake = [dict(n=2, stem="p02_twopointers", base="p02_twopointers", kind=None, lc75=False, deck="a", prefix="p02"),
                dict(n=2, stem="p02_twopointers_lc75", base="p02_twopointers", kind="lc75", lc75=True, deck="b", prefix="p02_lc75"),
                dict(n=2, stem="p02_twopointers_top150", base="p02_twopointers", kind="top150", lc75=False, deck="c", prefix="p02_top150"),
                dict(n=3, stem="p03_slidingwindow", base="p03_slidingwindow", kind=None, lc75=False, deck="d", prefix="p03"),
                dict(n=3, stem="p03_slidingwindow_top150", base="p03_slidingwindow", kind="top150", lc75=False, deck="e", prefix="p03_top150")]

        def pick(*specs):
            return [d["stem"] for d in self.m.select(list(specs), fake)]
        self.assertEqual(pick("2"), ["p02_twopointers", "p02_twopointers_lc75", "p02_twopointers_top150"])
        self.assertEqual(pick("2:top150"), ["p02_twopointers_top150"])
        self.assertEqual(pick("2:lc75"), ["p02_twopointers_lc75"])
        self.assertEqual(pick("3:main"), ["p03_slidingwindow"])
        self.assertEqual(pick("top150"), ["p02_twopointers_top150", "p03_slidingwindow_top150"])
        self.assertEqual(pick("lc75"), ["p02_twopointers_lc75"])
        self.assertEqual(pick("slidingwindow"), ["p03_slidingwindow", "p03_slidingwindow_top150"])
        self.assertTrue(self.m.docs_dir(fake[2]).endswith(os.path.join("src", "p02_twopointers", "top150", "docs")))
        self.assertEqual(self.m.kind_of(dict(lc75=True)), "lc75")                  # decks made before the kinds existed
        self.assertIsNone(self.m.kind_of(dict(lc75=False)))
        with self.assertRaises(SystemExit):
            self.m.select(["3:lc75"], fake)

    def test_the_docs_folder_of_a_pattern(self):
        d = [x for x in self.decks if x["n"] == 21][0]
        self.assertTrue(self.m.docs_dir(d).endswith(os.path.join("src", "p21_griddynamicprogramming2d", "docs")))
        self.assertTrue(os.path.isdir(self.m.docs_dir(d)))

    def test_every_deck_has_a_package_with_a_docs_folder(self):
        for d in self.decks:
            with self.subTest(deck=d["stem"]):
                self.assertTrue(os.path.isdir(self.m.docs_dir(d)))


class Combining(unittest.TestCase):
    """`all` also packs the cards of every deck onto shared sheets (no empty slots): only for a request for everything"""

    def setUp(self):
        self.m = rebuild_module()
        self.decks = self.m.all_decks()

    def test_only_a_request_for_everything_combines(self):
        self.assertTrue(self.m.covers_all(self.m.select(["all"], self.decks), self.decks))
        self.assertTrue(self.m.covers_all(self.m.select(["1-99"], self.decks), self.decks))
        for spec in (["1-7"], ["13"], ["top150"], ["lc75"], ["2:lc75"], ["21", "22"]):
            with self.subTest(spec=spec):
                self.assertFalse(self.m.covers_all(self.m.select(spec, self.decks), self.decks))
        self.assertFalse(self.m.covers_all([], []))

    def test_where_the_pdf_of_a_built_deck_is(self):
        import argparse
        d = [x for x in self.decks if x["n"] == 22][0]
        work, out = tempfile.mkdtemp(), tempfile.mkdtemp()
        a = argparse.Namespace(out=None, work=work, no_copy=False)
        self.assertEqual(self.m.built_pdf(d, a), os.path.join(self.m.docs_dir(d), "p22_FlashCards.pdf"))
        a.out = out                                                       # --out DIR: the deck PDFs are in that one folder, but only once they were built there
        self.assertEqual(self.m.built_pdf(d, a), os.path.join(work, d["stem"], "FlashCards.pdf"))
        open(os.path.join(out, "p22_FlashCards.pdf"), "w").close()
        self.assertEqual(self.m.built_pdf(d, a), os.path.join(out, "p22_FlashCards.pdf"))

    def test_the_help_names_the_feature(self):
        r = run("rebuild_cards.py", "--help")
        self.assertIn("--no-combine", r.stdout)
        self.assertIn("FlashCards_All.pdf", r.stdout)


class Commands(unittest.TestCase):
    def test_list(self):
        r = run("rebuild_cards.py", "--list")
        self.assertEqual(r.returncode, 0, r.stderr)
        rows = [l for l in r.stdout.splitlines() if l[:2].strip().isdigit()]
        self.assertEqual(len(rows), len(H.decks()))
        self.assertIn("p21_griddynamicprogramming2d", r.stdout)

    def test_help_shows_the_usage(self):
        r = run("rebuild_cards.py", "--help")
        self.assertEqual(r.returncode, 0)
        for word in ("--out", "--config", "--skip-unchanged", "--jobs", "all"):
            self.assertIn(word, r.stdout)

    def test_a_bad_selection_exits_with_an_error_and_builds_nothing(self):
        r = run("rebuild_cards.py", "99")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("does not name exactly one pattern", r.stdout + r.stderr)

    def test_a_settings_mistake_stops_before_any_build(self):
        bad = os.path.join(tempfile.mkdtemp(prefix="flashtest-"), "bad.config.md")
        with open(bad, "w") as f:
            f.write("| Setting | Value | x |\n|---|---|---|\n| margn_x | 6 | x |\n")
        r = run("rebuild_cards.py", "21", "--config", bad, "--no-copy")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("SETTINGS ERROR", r.stdout + r.stderr)

    def test_the_wrappers_exist_and_call_the_script(self):
        cmd = open(os.path.join(H.REPO, "rebuild-flashcards.cmd")).read()
        sh = open(os.path.join(H.REPO, "rebuild-flashcards.sh")).read()
        self.assertIn("rebuild_cards.py", cmd)
        self.assertIn("rebuild_cards.py", sh)


class FontSpecimen(unittest.TestCase):
    def test_the_specimen_pages_are_written_and_mark_the_font_in_use(self):
        work = tempfile.mkdtemp(prefix="flashtest-")
        r = subprocess.run([sys.executable, os.path.join(H.SCRIPTS, "font_specimen.py"), "--no-png"], capture_output=True, text=True,
                           encoding="utf-8", env=H.env_for(work, config=H.cfg("default")), cwd=work)
        self.assertEqual(r.returncode, 0, r.stderr)
        for name in ("fonts_handwriting.html", "fonts_text.html", "fonts_code.html", "fonts.html"):
            self.assertTrue(os.path.exists(os.path.join(work, name)), name)
        hand = open(os.path.join(work, "fonts_handwriting.html"), encoding="utf-8").read()
        self.assertIn("Ink Free", hand)
        self.assertIn("in use now", hand)
        self.assertIn("handwriting_font | Ink Free", hand)
        code = open(os.path.join(work, "fonts_code.html"), encoding="utf-8").read()
        self.assertIn("Consolas", code)
        self.assertIn("code_font | Consolas", code)


if __name__ == "__main__":
    unittest.main()

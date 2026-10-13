"""The book (make_book.py): every dry-run sheet and every card in one PDF. Packing, page numbers and the printed result. Fast, no browser;
the tests that build a PDF need PyMuPDF (rebuild-flashcards --install-deps) and are skipped without it."""
import argparse
import math
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

import helpers as H


def book():
    import make_book
    return make_book


def need_pymupdf():
    try:
        return book().pymupdf()
    except SystemExit as e:
        raise unittest.SkipTest(str(e))


def ns(**kw):
    base = dict(patterns=["22-24"], dry_layout="full", order="pattern", only="both", out=tempfile.mkdtemp(prefix="booktest-"), name=None, no_cover=False,
                config=None, verify=True, list=False)
    base.update(kw)
    return argparse.Namespace(**base)


def geometry():
    return book().Geometry(2, 2, 100, 144, 0, 5.0, 4.5, 210, 297)


class Packing(unittest.TestCase):
    def test_every_sheet_is_full_but_the_last(self):
        M = book()
        sheets = M.pack([12, 7, 4], 4)
        self.assertEqual([len(s) for s in sheets], [4, 4, 4, 4, 4, 3])
        self.assertEqual(sheets[0], [(0, 0), (0, 1), (0, 2), (0, 3)])
        self.assertEqual(sheets[3][0], (1, 0))                              # the 13th card is the first card of the second deck: decks run into each other
        flat = [c for s in sheets for c in s]
        self.assertEqual(flat, [(d, i) for d, n in enumerate([12, 7, 4]) for i in range(n)])

    def test_nothing_is_lost_or_doubled(self):
        M = book()
        for counts in ([], [1], [3, 1], [4, 4], [5, 2, 2, 7], [0, 5, 0]):
            flat = [c for s in M.pack(counts, 4) for c in s]
            self.assertEqual(len(flat), sum(counts), counts)
            self.assertEqual(len(set(flat)), len(flat), counts)

    def test_the_back_of_a_card_sits_in_the_mirrored_column(self):
        g = geometry()
        self.assertEqual([g.slot(k) for k in range(4)], [(0, 0), (1, 0), (0, 1), (1, 1)])
        self.assertEqual([g.back_slot(k) for k in range(4)], [(1, 0), (0, 0), (1, 1), (0, 1)])

    def test_a_card_is_found_where_its_own_deck_printed_it(self):
        M = book()
        self.assertEqual([M.source_slot(i, 4) for i in (0, 3, 4, 11)], [(0, 0), (0, 3), (1, 0), (2, 3)])

    def test_two_up_puts_the_answer_key_on_the_back_of_the_page_you_write_on(self):
        M = book()
        self.assertEqual(M.two_up_sides(4), [(1, 2), (0, 3)])             # front: worked example + your turn; back: recipe + answer key
        for q in (1, 2, 3, 5, 27):
            pages = [p for side in M.two_up_sides(q) for p in side]
            self.assertEqual(sorted(pages), list(range(q)), q)
        self.assertEqual(M.dry_sides(4, "full"), 4)
        self.assertEqual(M.dry_sides(4, "2up"), 2)
        self.assertEqual(M.dry_sides(27, "2up"), 14)

    def test_the_paper_count_of_printing_every_pdf_on_its_own(self):
        M = book()
        self.assertEqual(M.separate_sheets([4, 4], [12, 2, 5], 4), 2 + 2 + 3 + 1 + 2)
        self.assertEqual(math.ceil(sum([12, 2, 5]) / 4), 5)               # packed: 19 cards = 5 sheets


class PageNumbers(unittest.TestCase):
    def layout(self, part1, cards, toc_lines, cover=True, layout="full"):
        M = book()
        deck = type("D", (), dict(count=cards))()
        lay = M.Layout([dict(pages=part1)] if part1 else [], [deck] if cards else [], ns(no_cover=not cover, dry_layout=layout), geometry())
        lay.finish(toc_lines)
        return lay

    def test_the_cards_always_start_on_a_front_side(self):
        for part1 in range(0, 9):
            for cover in (True, False):
                for toc in (1, 54, 55, 110, 111):
                    with self.subTest(part1=part1, cover=cover, toc=toc):
                        lay = self.layout(part1, 9, toc, cover)
                        self.assertEqual(lay.part2_start % 2, 1, "card fronts must be on odd pages: the back of a sheet is the next even page")
                        self.assertEqual(lay.total, lay.front + part1 + lay.part2)
                        self.assertEqual(lay.total % 2, 0)

    def test_the_contents_may_take_two_pages(self):
        self.assertEqual(self.layout(4, 4, 54).toc_pages, 1)
        self.assertEqual(self.layout(4, 4, 55).toc_pages, 2)
        self.assertEqual(self.layout(4, 4, 90, cover=False).toc_pages, 0)

    def test_two_up_halves_the_sheet_pages(self):
        self.assertEqual(self.layout(4, 0, 10, layout="2up").part1, 2)
        self.assertEqual(self.layout(4, 0, 10, layout="full").part1, 4)


class Printed(unittest.TestCase):
    def setUp(self):
        self.m = need_pymupdf()

    def test_the_book_of_three_patterns(self):
        M = book()
        a = ns()
        rc, path = M.build(a, log=lambda *x: None)
        self.assertEqual(rc, 0)
        doc = self.m.open(path)
        g = M.Geometry.from_config(None)
        dry, decks = M.collect(a.patterns, "both", "pattern", g, M.load_ladder(), lambda *x: None)
        lay, _ = M.plan(dry, decks, a, g)
        self.assertEqual(len(doc), lay.total)
        self.assertEqual(len(doc) % 2, 0)
        self.assertEqual(sum(d.count for d in decks), 14)
        self.assertEqual(lay.part2, 2 * math.ceil(14 / 4))                # 8 PDFs, 14 cards: 4 sheets, not one sheet per deck
        titles = [t for _, t, _ in doc.get_toc()]
        self.assertEqual(titles[:3], ["Cover", "Contents", "Part 1: dry-run sheets"])
        self.assertIn("Part 2: flash cards", titles)
        front = doc[lay.part2_start - 1].get_text()
        self.assertIn("FRONT 1/4", front)
        self.assertIn("BACK 1/4", doc[lay.part2_start].get_text())
        first = decks[0].doc[0].get_text("text", clip=g.rect(0, 0, 0))
        self.assertTrue(first.strip())
        self.assertEqual(first, doc[lay.part2_start - 1].get_text("text", clip=g.rect(0, 0, 0)), "slot 0 of the first card sheet holds the first card of the first deck")
        doc.close()

    def test_every_face_of_the_book_is_the_face_of_its_source(self):
        M = book()
        a = ns(patterns=["13", "22-23"], only="cards")
        g = M.Geometry.from_config(None)
        dry, decks = M.collect(a.patterns, "cards", "pattern", g, M.load_ladder(), lambda *x: None)
        lay = M.Layout(dry, decks, a, g)
        lay.finish(0)
        out = self.m.open()
        placed = M.add_card_sheets(out, decks, lay, g, (decks[0].doc[0].rect.width, decks[0].doc[0].rect.height))
        path = os.path.join(a.out, "faces.pdf")
        out.save(path)
        self.assertEqual(len(placed), 2 * sum(d.count for d in decks))
        worst, bad = M.verify(path, decks, placed)
        self.assertEqual(bad, [], "a face of the book differs from its source")
        self.assertLess(worst, 0.2)
        r = self.m
        displaced = [(p, r.Rect(d.x0 + 3, d.y0, d.x1 + 3, d.y1), di, s, sr) for p, d, di, s, sr in placed]       # MUTANT: every face read 3 pt to the right
        worst2, bad2 = M.verify(path, decks, displaced)
        self.assertGreater(len(bad2), len(placed) // 2, "the check must notice a displaced face")
        self.assertGreater(worst2, 5 * M.VERIFY_MAX_DIFF)

    def test_only_cards_without_a_cover(self):
        M = book()
        a = ns(patterns=["13:top150", "22"], only="cards", no_cover=True)
        rc, path = M.build(a, log=lambda *x: None)
        doc = self.m.open(path)
        self.assertEqual(len(doc), 2)                                      # 2 + 2 cards: one sheet, front and back
        self.assertIn("FRONT 1/1", doc[0].get_text())
        self.assertIn("BACK 1/1", doc[1].get_text())
        doc.close()

    def test_two_up_takes_half_the_pages_for_the_sheets(self):
        M = book()
        full = self.m.open(M.build(ns(patterns=["22-24"], only="sheets", no_cover=True), log=lambda *x: None)[1])
        n_full = len(full)
        full.close()
        two = self.m.open(M.build(ns(patterns=["22-24"], only="sheets", no_cover=True, dry_layout="2up", name="two.pdf"), log=lambda *x: None)[1])
        self.assertEqual(n_full, 12)
        self.assertEqual(len(two), 6)
        self.assertLess(two[0].rect.width, two[0].rect.height)             # still portrait A4: one flip setting for the whole book
        two.close()

    def test_the_combined_cards_of_an_all_request_use_the_given_pdfs_and_one_output_folder(self):
        M = book()
        out, src = tempfile.mkdtemp(prefix="combined-"), tempfile.mkdtemp(prefix="decks-")
        copies = {}
        for d in M.DI.select(["22-24"], M.DI.all_decks()):          # the deck PDFs may be anywhere (--out DIR, the work folder): pdf_of says where
            copies[d["stem"]] = os.path.join(src, d["stem"] + ".pdf")
            shutil.copyfile(os.path.join(M.DI.docs_dir(d), f"{d['prefix']}_FlashCards.pdf"), copies[d["stem"]])
        r = M.combine_cards(lambda d: copies[d["stem"]], out, None, log=lambda *x: None, selectors=["22-24"])
        self.assertEqual((r["cards"], r["decks"]), (14, 5))
        self.assertEqual((r["card_sheets"], r["card_sheets_separate"]), (4, 5))       # 5 decks take 5 sheets one by one, 14 cards packed need 4
        self.assertEqual(sorted(os.listdir(out)), ["FlashCards_All.pdf", "FlashCards_All_contents.md"])
        doc = self.m.open(os.path.join(out, "FlashCards_All.pdf"))
        self.assertEqual(len(doc), 8)
        self.assertIn("FRONT 1/4", doc[0].get_text())                       # no cover, no contents page: page 1 is already a front side
        self.assertIn("BACK 4/4", doc[7].get_text())
        sheet4 = M.Geometry.from_config(None)
        self.assertTrue(doc[6].get_text("text", clip=sheet4.rect(0, 0, 0)).strip())      # the last sheet holds the last 2 cards ...
        self.assertTrue(doc[6].get_text("text", clip=sheet4.rect(1, 0, 0)).strip())
        self.assertFalse(doc[6].get_text("text", clip=sheet4.rect(0, 1, 0)).strip())     # ... and nothing else
        text = open(os.path.join(out, "FlashCards_All_contents.md"), encoding="utf-8").read()
        self.assertIn("| p22 Queue, main deck | 2 |", text)
        self.assertIn("saves 1", text)
        doc.close()

    def test_a_pdf_built_with_other_settings_is_refused(self):
        M = book()
        g = M.Geometry(2, 2, 90, 130, 0, 10.0, 8.0, 210, 297)             # other card size and margins than the ones the PDFs were built with
        path = os.path.join(H.REPO, "src", "p22_queue", "docs", "p22_FlashCards.pdf")
        with self.assertRaises(SystemExit):
            M.check_geometry(self.m.open(path), g, path)
        M.check_geometry(self.m.open(path), M.Geometry.from_config(None), path)       # and accepted with the right ones


class Commands(unittest.TestCase):
    def test_help_and_list(self):
        script = os.path.join(H.SCRIPTS, "make_book.py")
        r = subprocess.run([sys.executable, script, "--help"], capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("--dry-layout", r.stdout)
        need_pymupdf()
        r = subprocess.run([sys.executable, script, "--list", "22"], capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(r.returncode, 0, r.stderr + r.stdout)
        self.assertIn("p22_queue", r.stdout)
        self.assertIn("book:", r.stdout)

    def test_rebuild_cards_hands_over_without_a_browser(self):
        need_pymupdf()
        env = dict(os.environ, EDGE="C:\\no\\such\\browser.exe")          # the book must not need Edge / Chrome
        r = subprocess.run([sys.executable, os.path.join(H.SCRIPTS, "rebuild_cards.py"), "--book", "--list", "22"], capture_output=True, text=True, encoding="utf-8", env=env)
        self.assertEqual(r.returncode, 0, r.stderr + r.stdout)
        self.assertIn("book:", r.stdout)


if __name__ == "__main__":
    unittest.main()

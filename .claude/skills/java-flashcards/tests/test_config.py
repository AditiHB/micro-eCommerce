"""flashconfig.py: the settings file -> paper, cards per sheet, margins, scale, fonts. Fast, no browser."""
import os
import tempfile
import unittest

import helpers  # noqa: F401  (puts scripts/ on sys.path and the work folder into the environment below)

os.environ.setdefault("FLASH_WORK", tempfile.mkdtemp(prefix="flashtest-work-"))
import flashconfig  # noqa: E402


def load(*rows, head="| Setting | Value | What |\n|---|---|---|\n"):
    """settings from table rows given as 'key | value'"""
    fd, path = tempfile.mkstemp(suffix=".config.md")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(head + "".join(f"| {r} | x |\n" for r in rows))
    try:
        return flashconfig.load(path)
    finally:
        os.remove(path)


class Defaults(unittest.TestCase):
    def test_default_layout_is_the_classic_2x2_a4_sheet(self):
        c = flashconfig.load(helpers.cfg("default"))
        self.assertEqual((c.page_w, c.page_h), (210, 297))
        self.assertEqual((c.card_w, c.card_h), (100, 144))
        self.assertEqual((c.cols, c.rows, c.per_sheet), (2, 2, 4))
        self.assertEqual((c.x0, c.y0), (5, 4.5))                 # centred: (210 - 200) / 2 and (297 - 288) / 2
        self.assertEqual((c.content_w, c.content_h), (90, 134))
        self.assertEqual((c.content_inset, c.border_inset), (5, 2.6))
        self.assertEqual(c.scale, 1)
        self.assertEqual((c.design_w, c.design_h), (90, 134))
        self.assertEqual(c.safe_mm, 4.7)
        self.assertEqual(c.font_family, "Ink Free")
        self.assertEqual(c.font_css, "'Ink Free','Segoe Print','Bradley Hand ITC','Comic Sans MS',cursive")
        self.assertEqual(c.code_css, "Consolas, 'Courier New', monospace")
        self.assertEqual(c.arrow_css, "Consolas, monospace")
        self.assertEqual(c.warnings, [])

    def test_the_live_settings_file_is_valid(self):
        c = flashconfig.load()                                    # skill default, FLASH_CONFIG is not set by the tests
        self.assertGreaterEqual(c.per_sheet, 1)

    def test_an_empty_value_means_the_default(self):
        self.assertEqual(load("card_width |").card_w, 100)


class Layout(unittest.TestCase):
    def test_small_cards_make_a_3x3_sheet_centred_on_the_paper(self):
        c = load("card_width | 60", "card_height | 80", "content_inset | 4", "border_inset | 2")
        self.assertEqual((c.cols, c.rows, c.per_sheet), (3, 3, 9))
        self.assertEqual((c.x0, c.y0), (15, 28.5))
        self.assertAlmostEqual(c.x0 * 2 + 3 * 60, 210)

    def test_gap_reduces_how_many_cards_fit(self):
        self.assertEqual(load("card_width | 100", "gap | 0").cols, 2)
        self.assertEqual(load("card_width | 100", "gap | 3").cols, 1)           # 100 + 3 + 100 = 203 > 202 (210 - 2 x 4)
        self.assertEqual(load("card_width | 99", "gap | 3").cols, 2)            # 99 + 3 + 99 = 201 <= 202

    def test_a_gap_is_part_of_the_centred_block(self):
        c = load("card_width | 95", "card_height | 135", "gap | 2", "margin_x | 6", "margin_y | 8", "border_inset | 2.4")
        self.assertEqual((c.cols, c.rows), (2, 2))
        self.assertEqual((c.x0, c.y0), (9, 12.5))                  # (210 - 192) / 2, (297 - 272) / 2

    def test_landscape_and_named_paper(self):
        c = load("paper | A5", "orientation | landscape", "card_width | 70", "card_height | 100", "gap | 3")
        self.assertEqual((c.page_w, c.page_h), (210, 148))
        self.assertEqual((c.cols, c.rows), (2, 1))

    def test_custom_paper_size(self):
        c = load("paper | 200 x 300")
        self.assertEqual((c.page_w, c.page_h), (200, 300))
        self.assertEqual(load("paper | 200x300").page_w, 200)

    def test_forced_columns_and_rows_can_only_reduce(self):
        self.assertEqual(load("columns | 1").per_sheet, 2)
        self.assertEqual(load("columns | 1", "rows | 1").per_sheet, 1)
        with self.assertRaisesRegex(flashconfig.ConfigError, "do not fit"):
            load("columns | 5")

    def test_a_card_that_does_not_fit_the_paper(self):
        with self.assertRaisesRegex(flashconfig.ConfigError, "does not fit"):
            load("card_width | 300")

    def test_back_sheet_mirror_needs_a_centred_block(self):
        for rows in ([], ["card_width | 60", "card_height | 80"], ["gap | 3", "card_width | 70", "card_height | 100"]):
            c = load(*rows)
            used_w = c.cols * c.card_w + (c.cols - 1) * c.gap
            self.assertAlmostEqual(c.x0, c.page_w - c.x0 - used_w, places=2)    # equal left and right margins


class Scale(unittest.TestCase):
    def test_auto_scale_follows_the_text_area(self):
        self.assertEqual(load("card_width | 63", "card_height | 88").scale, 0.58)       # min(53 / 90, 78 / 134)
        self.assertEqual(load().scale, 1)

    def test_a_given_scale_wins_and_the_design_area_follows(self):
        c = load("scale | 0.8")
        self.assertEqual(c.scale, 0.8)
        self.assertAlmostEqual(c.design_w, 112.5)
        self.assertAlmostEqual(c.dia_scale, 112.5 / 90)

    def test_tiny_text_warns(self):
        self.assertTrue(any("hard to read" in w for w in load("card_width | 63", "card_height | 88").warnings))

    def test_signature_changes_with_the_size_and_the_font_but_not_with_cosmetics(self):
        base = flashconfig.signature(load())
        self.assertEqual(base, flashconfig.signature(load("cut_guides | no", "sheet_label | no")))
        self.assertNotEqual(base, flashconfig.signature(load("card_width | 90")))
        try:
            other_font = flashconfig.signature(load("handwriting_font | Segoe Print"))
        except flashconfig.ConfigError:
            self.skipTest("Segoe Print is not installed")
        self.assertNotEqual(base, other_font)


class Mistakes(unittest.TestCase):
    def test_a_typo_in_a_setting_name_is_reported_with_a_suggestion(self):
        with self.assertRaisesRegex(flashconfig.ConfigError, r"margn_x.*did you mean 'margin_x'"):
            load("margn_x | 6")

    def test_bad_values(self):
        for row, pattern in (("card_height | tall", "not a number"), ("cut_guides | maybe", "yes or no"), ("paper | Foolscap", "unknown size"),
                             ("orientation | sideways", "orientation"), ("card_width | 5", "outside")):
            with self.subTest(row=row), self.assertRaisesRegex(flashconfig.ConfigError, pattern):
                load(row)

    def test_the_pen_border_must_stay_outside_the_text(self):
        with self.assertRaisesRegex(flashconfig.ConfigError, "must be smaller than content_inset"):
            load("border_inset | 6")

    def test_a_font_that_is_not_installed(self):
        with self.assertRaisesRegex(flashconfig.ConfigError, "not installed"):
            load("handwriting_font | No Such Font Anywhere")

    def test_a_font_file_path(self):
        ttf = os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts", "Inkfree.ttf")
        if not os.path.exists(ttf):
            self.skipTest("Ink Free is not installed")
        c = load(f"handwriting_font | {ttf}")
        self.assertEqual(c.font_family, "Ink Free")
        self.assertTrue(c.font_from_file)

    def test_only_the_first_settings_table_is_read(self):
        head = "| Setting | Value | What |\n|---|---|---|\n| card_width | 90 | x |\n\nprose\n\n| Other | table |\n|---|---|\n| nonsense | 1 |\n"
        fd, path = tempfile.mkstemp(suffix=".config.md")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(head)
        try:
            self.assertEqual(flashconfig.load(path).card_w, 90)
        finally:
            os.remove(path)

    def test_small_margins_and_insets_warn(self):
        self.assertTrue(any("sheet margin" in w for w in load("margin_x | 0", "margin_y | 0", "card_width | 105", "card_height | 148").warnings))
        self.assertTrue(any("content_inset" in w for w in load("content_inset | 3", "border_inset | 2").warnings))


if __name__ == "__main__":
    unittest.main()

"""Regression tests of the flash-card tooling (standard library only).

usage (from anywhere; rebuild_cards.py --test is a short name for this):
  python .claude/skills/java-flashcards/tests/run_tests.py                    fast tests, no browser (about 15 s)
  python .../run_tests.py --e2e                                               + the whole pipeline in a real browser, incl. broken builds (about 4 min)
  python .../run_tests.py --update-golden                                     approve the current output of the decks as the new golden output
  python .../run_tests.py test_config.Layout -v                               one test class / method, verbose

What is checked
  test_config   flashconfig.py: paper, cards per sheet, margins, scale, fonts, every kind of settings mistake
  test_engine   the HTML of ALL decks is byte-identical to the golden output (golden.json holds a hash and the fitted text scales per deck);
                placement, mirroring, partly filled sheets, cut guides, page size, scaling for several pinned settings (tests/configs/), on a fixture deck
  test_cli      rebuild_cards.py (deck selection, --list, errors) and font_specimen.py
  test_e2e      (--e2e) fitting + PDF + the checks in a browser, and MUTANTS: the old print-clipping bug, a shifted back sheet, an overfull card
                must all be REJECTED by the checks

When a golden test fails, the Python changed what the cards look like. If that is intended: rebuild (rebuild_cards.py all), look at
the cards, then run --update-golden and commit golden.json together with the change.
"""
import json
import os
import sys
import time
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

VARIANTS = [("three_by_three", "layout12"), ("gap_font", "layout12")]          # settings variants are tested on the fixture deck, not on real content


def update_golden():
    import helpers as H
    old = H.load_golden()
    new = {"note": "approved output of the flash-card engine; regenerate with tests/run_tests.py --update-golden", "decks": {}, "variants": {}}
    for stem in H.decks():
        docs = H.docs_html(stem)
        if not os.path.exists(docs):
            raise SystemExit(f"no published HTML for {stem}: build it first (python scripts/rebuild_cards.py {int(stem[1:3])})")
        new["decks"][stem] = dict(sha256=H.sha(docs), scales=H.scales_from_html(docs))
    os.environ["FLASH_TEST_E2E"] = "1"
    import test_e2e
    import flashconfig
    for cfg_name, stem in VARIANTS:
        key = f"{cfg_name}:{stem}"
        try:
            flashconfig.load(H.cfg(cfg_name))
        except Exception as e:                                         # noqa: BLE001  (a font that is not installed here)
            print(f"keeping the old golden entry of {key}: {e}")
            if key in old.get("variants", {}):
                new["variants"][key] = old["variants"][key]
            continue
        print(f"building the variant {key} in the browser ...")
        rc, log, work = test_e2e.pipeline(H.deck_path(stem), H.cfg(cfg_name))
        if rc:
            raise SystemExit(f"the variant {key} does not build:\n{log[-1500:]}")
        scales = json.load(open(os.path.join(work, "flash_scales.json")))
        scales.pop("_config", None)
        new["variants"][key] = dict(sha256=H.sha(os.path.join(work, "FlashCards.html")), scales=scales)
    changed = [k for k in new["decks"] if old.get("decks", {}).get(k, {}).get("sha256") != new["decks"][k]["sha256"]]
    changed += [k for k in new["variants"] if old.get("variants", {}).get(k, {}).get("sha256") != new["variants"][k]["sha256"]]
    with open(H.GOLDEN, "w", encoding="utf-8") as f:
        json.dump(new, f, indent=1, sort_keys=True)
        f.write("\n")
    print(f"golden.json written: {len(new['decks'])} decks, {len(new['variants'])} variants; changed: {', '.join(changed) if changed else 'nothing'}")


def main(argv):
    if "--update-golden" in argv:
        update_golden()
        return 0
    if "--e2e" in argv:
        os.environ["FLASH_TEST_E2E"] = "1"
    verbose = "-v" in argv
    names = [a for a in argv if not a.startswith("-")]
    loader = unittest.TestLoader()
    suite = loader.loadTestsFromNames(names) if names else loader.discover(HERE, pattern="test_*.py", top_level_dir=HERE)
    t0 = time.time()
    result = unittest.TextTestRunner(verbosity=2 if verbose else 1).run(suite)
    skipped = len(result.skipped)
    print(f"\n{result.testsRun} tests, {len(result.failures)} failed, {len(result.errors)} errors, {skipped} skipped, {time.time() - t0:.0f} s"
          + ("" if os.environ.get("FLASH_TEST_E2E") == "1" else "   (browser tests were not run: add --e2e)"))
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

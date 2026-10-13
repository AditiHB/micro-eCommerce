# Flash card settings

This file decides **how big the cards are, how they sit on the paper, and which fonts they use**.
Change a value in the **Value** column, save, and rebuild (`python scripts/build_all.py <name> --deck decks/<deck>.py`).
Everything is in millimetres unless it says otherwise. Nothing else needs editing: the card text, the diagrams and the
checks (no overflow, safe margins, every back lines up with its front) all follow these numbers.

The defaults: A4 paper, 100 x 144 mm cards, 2 x 2 per sheet, Ink Free.

## Where this file is read from

First match wins: `build_all.py --config <file>` / the `FLASH_CONFIG` environment variable / a `flashcards.config.md`
next to the generated files (`FLASH_WORK`) / this file. To try a different size for one project without touching the
defaults, copy this file into the work folder and edit the copy.

## The settings

Only the first table is read. Leave a value empty to use the default. Unknown setting names are reported as errors
(a typo never fails silently).

| Setting | Value | What it does |
|---|---|---|
| paper | A4 | Paper size: `A3`, `A4`, `A5`, `A6`, `Letter`, `Legal`, `Tabloid`, or your own `width x height` in mm, e.g. `210 x 297` |
| orientation | portrait | `portrait` or `landscape` (the paper is turned, the cards are not) |
| card_width | 100 | Width of one card |
| card_height | 144 | Height of one card |
| margin_x | 4 | Smallest empty strip at the left and right paper edge. Most printers cannot print closer than about 4 mm. The cards are centred, so the real margin is usually a bit bigger (the build prints it) |
| margin_y | 4 | The same for the top and bottom paper edge |
| gap | 0 | Space between neighbouring cards. `0` = the cards touch and you cut once between two cards |
| columns | auto | Cards per row. `auto` = as many as fit; a number forces fewer |
| rows | auto | Cards per column. `auto` = as many as fit; a number forces fewer |
| content_inset | 5 | Distance from the card edge to the text. Duplex printers are 1-2 mm off and cutting is crooked, so keep this at 4 or more |
| border_inset | 2.6 | Distance from the card edge to the pen-drawn border. Must be smaller than `content_inset` |
| scale | auto | How big everything on the card is drawn (text, boxes, diagrams together). `auto` follows the card size: `1` for the default 100 x 144 mm card, smaller for a smaller card. Or a number such as `0.8`. The cards are designed for a 90 x 134 mm text area; the step text prints at 9.4 pt x scale |
| handwriting_font | Ink Free | The writing font: the family name of an installed font, or the path to a `.ttf` / `.otf` file. Run `python scripts/font_specimen.py` to see how every installed font looks |
| code_font | Consolas | Monospace font for the code box |
| font_folder | | Extra folder(s) with font files to offer, separated by `;` (for fonts you downloaded; relative paths start in the work folder) |
| cut_guides | yes | Light dashed cut lines on the fronts: `yes` or `no` |
| sheet_label | yes | The small "FRONT 1/3" label in the top margin: `yes` or `no` |

## How the sheet is worked out

1. The number of cards per row is `floor((paper width - 2 x margin_x + gap) / (card width + gap))`, the same for the rows.
2. The block of cards is **centred** on the paper, so the left and right margins are equal and the back sheet (printed mirrored,
   "flip on the long edge") lands exactly behind the front sheet.
3. A deck does not have to fill the last sheet: missing cards leave an empty slot.

A card is always laid out as if its text area were about 90 x 134 mm and then scaled as a whole (`scale`), so a smaller card
looks like the default card made smaller, with every proportion kept. The build tells you the size the step text will print at and warns
below about 7 pt. Each card is still fitted on its own (`build_all.py` shrinks or grows its text a little); if a card is too full even
at half size the build stops and names it, so you can shorten its text.

Regenerate the cards after a change, without Claude: `python .claude/skills/java-flashcards/scripts/rebuild_cards.py 2` (one deck) or `... all`
in the repository root; `--config my.config.md` tries another settings file for one run.

Check a file without building anything: `python scripts/flashconfig.py path/to/flashcards.config.md` prints the cards per sheet,
the real margins and any warning.

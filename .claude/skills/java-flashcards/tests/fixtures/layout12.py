"""Test fixture: 12 plain cards for the layout tests (placement, mirroring, scaling, partly filled sheets). Not part of the curriculum, so changing a real deck
never disturbs the layout tests. exec()d inside gen_flash.py like every deck."""

DECK_TITLE = "Layout fixture"
DECK_LABEL = "Layout fixture"


def _dia():
    d = canvas(64)
    flow(d, ["client", "service", "database"], 10, fills={1: "#fff3a8"}, under={0: ("request", PENCIL), 2: ("row", PENCIL)})
    return d


CARDS = [dict(
    n=i, tier=("MUST", "CORE", "STRETCH")[i % 3], ref="TEST", ask="EXPLAIN",
    title=f"Fixture card {i}", short=f"Fixture {i}",
    q=f"Question {i}: what does this fixture card test, and what would break if it were missing?",
    ex_draw=["a", "b", "c"], ex=[("given", "observed", "why")],
    kind="FIXTURE  |  layout", keep="keep: one thing", rule="one rule", spot="a signal, another signal",
    dia=_dia,
    steps=["The first step.", "The second step with <b>bold</b> text and a <u>highlight</u>.", "The third step."],
    code=["int a = 1;", "int b = a + 1;"],
    cost="cost: none", twins="nothing", trap="A trap sentence that is long enough to wrap onto a second line of the card.",
) for i in range(1, 13)]

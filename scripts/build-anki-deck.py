#!/usr/bin/env python3
"""Builds one importable Anki deck (.apkg) from the Q&A pairs in docs/interview/.

Every file under docs/interview/<topic>/ (except README.md, which is navigation prose, not
content) follows an identical, mechanical structure:

    ### Q: <question, possibly wrapping onto a second line>

    **A:** <answer, may contain markdown: code fences, lists, links>

This script parses that structure directly (no change to the source docs) and packages the
result as one .apkg with one subdeck per topic (Docker, Kafka, Kubernetes, Microservices,
Spring Boot), so importing it into Anki is a single action.

Usage:
    pip install -r scripts/requirements-anki.txt
    python scripts/build-anki-deck.py

Output: scripts/out/micro-ecommerce-interview-deck.apkg (gitignored - this is a generated
artifact, like everything else under target/, not something this repo commits).
"""
import random
import re
from pathlib import Path

import genanki
import markdown

REPO_ROOT = Path(__file__).resolve().parent.parent
INTERVIEW_DIR = REPO_ROOT / "docs" / "interview"
OUTPUT_DIR = REPO_ROOT / "scripts" / "out"
OUTPUT_FILE = OUTPUT_DIR / "micro-ecommerce-interview-deck.apkg"

# Folder name -> display name used in the deck hierarchy ("micro-eCommerce::<Topic>").
TOPICS = {
    "docker": "Docker",
    "kafka": "Kafka",
    "kubernetes": "Kubernetes",
    "microservices": "Microservices",
    "spring-boot": "Spring Boot",
}

QUESTION_MARKER = re.compile(r"^### Q:\s*", re.MULTILINE)
ANSWER_MARKER = re.compile(r"\*\*A:\*\*\s*")

MARKDOWN_EXTENSIONS = ["fenced_code"]


def stable_id(seed: str) -> int:
    """A genanki model/deck id that is the same on every run for the same seed string, so
    re-running this script does not create duplicate decks/notes on re-import."""
    return random.Random(seed).randrange(1 << 30, 1 << 31)


def parse_qa_pairs(text: str) -> list[tuple[str, str]]:
    """Splits one file's content into (question, answer) pairs. The text before the first
    '### Q:' (the file's title) is not a pair and is discarded."""
    blocks = QUESTION_MARKER.split(text)[1:]
    pairs = []
    for block in blocks:
        match = ANSWER_MARKER.search(block)
        if not match:
            continue
        question = block[: match.start()].strip()
        answer = block[match.end() :].strip()
        pairs.append((question, answer))
    return pairs


def to_html(markdown_text: str) -> str:
    return markdown.markdown(markdown_text, extensions=MARKDOWN_EXTENSIONS)


def build_model() -> genanki.Model:
    return genanki.Model(
        stable_id("micro-eCommerce interview deck - basic model v1"),
        "micro-eCommerce Interview Q&A",
        fields=[{"name": "Question"}, {"name": "Answer"}],
        templates=[
            {
                "name": "Card 1",
                "qfmt": "{{Question}}",
                "afmt": '{{FrontSide}}<hr id="answer">{{Answer}}',
            }
        ],
        css="""
            .card { font-family: Helvetica, Arial, sans-serif; font-size: 18px; text-align: left; }
            pre, code { background: #f4f4f4; padding: 2px 4px; border-radius: 3px; }
            pre { padding: 8px; overflow-x: auto; }
        """,
    )


def build_deck(topic_key: str, display_name: str, model: genanki.Model) -> tuple[genanki.Deck, int]:
    deck_name = f"micro-eCommerce::{display_name}"
    deck = genanki.Deck(stable_id(deck_name), deck_name)

    topic_dir = INTERVIEW_DIR / topic_key
    count = 0
    for md_file in sorted(topic_dir.glob("*.md")):
        if md_file.name == "README.md":
            continue
        text = md_file.read_text(encoding="utf-8")
        for question, answer in parse_qa_pairs(text):
            deck.add_note(genanki.Note(model=model, fields=[to_html(question), to_html(answer)]))
            count += 1
    return deck, count


def main() -> None:
    model = build_model()
    decks = []
    total = 0
    print("Cards generated per topic:")
    for topic_key, display_name in TOPICS.items():
        deck, count = build_deck(topic_key, display_name, model)
        decks.append(deck)
        total += count
        print(f"  {display_name:<14} {count}")
    print(f"  {'TOTAL':<14} {total}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    genanki.Package(decks).write_to_file(str(OUTPUT_FILE))
    print(f"\nWrote {OUTPUT_FILE}")


if __name__ == "__main__":
    main()

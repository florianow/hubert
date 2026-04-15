#!/usr/bin/env python3
"""
Extract preposition fill-in-the-blank sentences from the local anki_french repo.

Reads YAML cards from anki_french/cards/, finds sentences where the marked
word (*...*) is the target preposition, and outputs prepositions.json.

Usage:
    python3 scripts/extract_prepositions.py
"""
import json
import re
import sys
import random
from collections import Counter
from pathlib import Path

CARDS_DIR = Path("anki_french/cards")
OUT_PATH = Path("app/src/main/assets/prepositions.json")

# (filename, preposition, distractors)
PREPOSITION_CARDS = [
    ("0002_de.yml",    "de",    ["à", "par", "en"]),
    ("0004_à.yml",     "à",     ["de", "par", "en"]),
    ("0007_en.yml",    "en",    ["dans", "à", "de"]),
    ("0010_pour.yml",  "pour",  ["à", "de", "en"]),
    ("0011_dans.yml",  "dans",  ["en", "à", "sur"]),
    ("0016_sur.yml",   "sur",   ["dans", "à", "sous"]),
    ("0021_par.yml",   "par",   ["pour", "de", "à"]),
    ("0023_avec.yml",  "avec",  ["sans", "de", "pour"]),
    ("0040_avant.yml", "avant", ["après", "dans", "dès"]),
    ("0055_entre.yml", "entre", ["dans", "à", "de"]),
    ("0071_sans.yml",  "sans",  ["avec", "de", "à"]),
    ("0082_après.yml", "après", ["avant", "depuis", "dès"]),
    ("0121_contre.yml","contre",["pour", "à", "de"]),
    ("0122_sous.yml",  "sous",  ["sur", "dans", "à"]),
    ("0182_vers.yml",  "vers",  ["dans", "à", "de"]),
    ("0206_chez.yml",  "chez",  ["à", "dans", "en"]),
]


def parse_beispielsaetze(yaml_text: str) -> list[tuple[str, str]]:
    m = re.search(r"Beispielsätze:\s*\|-?\n(.*?)(?=\n\S|\Z)", yaml_text, re.DOTALL)
    if not m:
        return []

    lines = [line.lstrip() for line in m.group(1).splitlines()]
    pairs = []
    i = 0
    while i < len(lines):
        fr = lines[i].strip()
        if not fr:
            i += 1
            continue
        de = lines[i + 1].strip() if i + 1 < len(lines) else ""
        if fr and de:
            pairs.append((fr, de))
        i += 2
    return pairs


def extract(filename: str, prep: str, distractors: list[str]) -> list[dict]:
    path = CARDS_DIR / filename
    if not path.exists():
        print(f"  WARN: {path} not found", file=sys.stderr)
        return []

    yaml_text = path.read_text(encoding="utf-8")
    pairs = parse_beispielsaetze(yaml_text)
    results = []
    seen = set()

    pattern = re.compile(r"\*(" + re.escape(prep) + r"(?:')?)\*", re.IGNORECASE)

    for fr_raw, de_raw in pairs:
        if not pattern.search(fr_raw):
            continue

        fr_clean = pattern.sub("___", fr_raw)
        fr_clean = re.sub(r"\*([^*]+)\*", r"\1", fr_clean).strip()
        de_clean = re.sub(r"\*([^*]+)\*", r"\1", de_raw).strip()

        if fr_clean in seen or len(fr_clean) < 10:
            continue
        if fr_clean.count("___") != 1:
            continue

        seen.add(fr_clean)
        results.append({
            "fr": fr_clean,
            "de": de_clean,
            "answer": prep,
            "distractors": distractors,
        })

    print(f"  {filename:25s} → {len(results):3d} sentences", file=sys.stderr)
    return results


def main():
    if not CARDS_DIR.exists():
        print(f"ERROR: {CARDS_DIR} not found. Run from the hubert repo root.", file=sys.stderr)
        sys.exit(1)

    all_questions = []
    for filename, prep, distractors in PREPOSITION_CARDS:
        all_questions.extend(extract(filename, prep, distractors))

    # Deduplicate
    seen = set()
    unique = [q for q in all_questions if not seen.add(q["fr"]) and q["fr"] not in seen]
    # Fix: simpler dedup
    seen2 = set()
    unique2 = []
    for q in all_questions:
        if q["fr"] not in seen2:
            seen2.add(q["fr"])
            unique2.append(q)

    random.seed(42)
    random.shuffle(unique2)

    OUT_PATH.write_text(json.dumps(unique2, ensure_ascii=False, indent=2), encoding="utf-8")

    counts = Counter(q["answer"] for q in unique2)
    print(f"\nWrote {len(unique2)} questions to {OUT_PATH}", file=sys.stderr)
    for prep, count in sorted(counts.items(), key=lambda x: -x[1]):
        print(f"  {prep:8s} {count}", file=sys.stderr)


if __name__ == "__main__":
    main()

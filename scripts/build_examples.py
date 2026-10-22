"""Build app/src/main/assets/examples.json from the anki_french cards.

All example sentence pairs per word (up to MAX_PER_WORD), keyed by frequency rank, with the
deck's *target* markers kept in both languages — used by Dites! (Anki-style cards: sentence
counter, target underlined, Lückenspiel). sentences.json stays as it is for the other games.
"""
import glob
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CARDS = ROOT / "anki_french/cards"
OUT = ROOT / "app/src/main/assets/examples.json"
MAX_PER_WORD = 20


def examples(path):
    lines = path.read_text(encoding="utf-8").split("\n")
    start = next((i for i, l in enumerate(lines) if l.startswith("Beispielsätze:")), None)
    if start is None:
        return []
    block = []
    for line in lines[start + 1:]:
        if line and not line.startswith(" ") and not line.startswith("#"):
            break
        if line.strip() and not line.strip().startswith("#"):
            block.append(line.strip())
    pairs = []
    for fr, de in zip(block[0::2], block[1::2]):
        # Drop HTML the deck sometimes uses inside sentences
        fr = re.sub(r"<[^>]+>", "", fr).strip()
        de = re.sub(r"<[^>]+>", "", de).strip()
        if fr and de:
            pairs.append({"fr": fr, "de": de})
    return pairs[:MAX_PER_WORD]


def main():
    data = {}
    for path in sorted(glob.glob(str(CARDS / "*.yml"))):
        rank = int(Path(path).name.split("_")[0])
        pairs = examples(Path(path))
        if pairs:
            data[str(rank)] = pairs
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    total = sum(len(v) for v in data.values())
    print(f"{len(data)} words, {total} sentences, {OUT.stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()

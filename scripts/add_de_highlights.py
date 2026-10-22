"""Add the German highlight ("deBlank") to app/src/main/assets/sentences.json.

The anki_french cards mark the target word in both languages (*word*); convert_vocab.py kept
only the French one (as "blank"). Dites! underlines the German target like the Anki card, so
this script restores it from anki_french/cards/*.yml, matching sentences by French text.
Idempotent: re-running only fills missing values.
"""
import glob
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SENTENCES = ROOT / "app/src/main/assets/sentences.json"
CARDS = ROOT / "anki_french/cards"


def sentence_pairs(path):
    """(fr_clean, de_marked_word) pairs from the Beispielsätze block of one card."""
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
        m = re.search(r"\*([^*]+)\*", de)
        if m:
            pairs.append((fr.replace("*", ""), m.group(1)))
    return pairs


def main():
    data = json.loads(SENTENCES.read_text(encoding="utf-8"))
    highlights = {}
    for path in glob.glob(str(CARDS / "*.yml")):
        rank = str(int(Path(path).name.split("_")[0]))
        for fr, de_word in sentence_pairs(Path(path)):
            highlights[(rank, fr)] = de_word
    added = 0
    for rank, entries in data.items():
        for entry in entries:
            word = highlights.get((rank, entry["fr"]))
            if word and "deBlank" not in entry:
                entry["deBlank"] = word
                added += 1
    SENTENCES.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    total = sum(len(v) for v in data.values())
    print(f"added {added}, with deBlank {sum(1 for v in data.values() for e in v if 'deBlank' in e)}/{total}")


if __name__ == "__main__":
    main()

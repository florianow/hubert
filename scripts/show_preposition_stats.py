#!/usr/bin/env python3
"""Show preposition distribution in prepositions.json."""
import json
from collections import Counter
from pathlib import Path

data = json.loads(Path("app/src/main/assets/prepositions.json").read_text())
counts = Counter(q["answer"] for q in data)

print(f"Total: {len(data)}\n")
for prep, count in sorted(counts.items(), key=lambda x: -x[1]):
    print(f"  {prep:8s} {count}")

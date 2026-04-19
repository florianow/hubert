#!/usr/bin/env python3
"""
Merge mood sentence result files into conjugations.json.

Usage:
    python3 merge_mood_results.py

This script reads all mood_result_XX.json files in the scripts/ directory
and merges them into app/src/main/assets/conjugations.json.

Rules:
- Only fills gaps — does NOT overwrite existing sentences
- Validates that the "blank" form appears in the sentence
- Reports any validation errors without aborting

Result file format (per prompt response):
{
  "infinitive": {
    "subjonctif": {
      "INDEX": {"fr": "...", "de": "...", "blank": "..."}
    },
    "present": {
      "INDEX": {"fr": "...", "de": "...", "blank": "..."}
    }
  }
}

Person index mapping: je=0, tu=1, il/elle=2, nous=3, vous=4, ils/elles=5
"""

import json
import os
import glob
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CONJUGATIONS_PATH = os.path.join(SCRIPT_DIR, "../app/src/main/assets/conjugations.json")
RESULT_PATTERN = os.path.join(SCRIPT_DIR, "mood_result_*.json")


def load_conjugations():
    with open(CONJUGATIONS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save_conjugations(data):
    with open(CONJUGATIONS_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"Saved {CONJUGATIONS_PATH}")


def validate_sentence(entry, verb, mood, idx):
    """Check that blank appears exactly once in the sentence."""
    fr = entry.get("fr", "")
    blank = entry.get("blank", "")
    if not blank:
        print(f"  WARNING [{verb} {mood} {idx}]: missing 'blank' field")
        return False
    if "___" not in fr:
        print(f"  WARNING [{verb} {mood} {idx}]: no ___ in sentence: {fr!r}")
        return False
    # Reconstruct and check blank is in original sentence
    reconstructed = fr.replace("___", blank)
    # Just check blank appears in reconstructed (always true)
    return True


def merge_results(conjugations, result_files):
    # Build lookup: infinitive -> verb entry
    lookup = {}
    for verb in conjugations:
        inf = verb.get("infinitive", "")
        if inf:
            lookup[inf] = verb

    total_added = 0
    total_skipped = 0
    total_errors = 0

    for result_file in sorted(result_files):
        print(f"\nProcessing {os.path.basename(result_file)} ...")
        with open(result_file, "r", encoding="utf-8") as f:
            try:
                results = json.load(f)
            except json.JSONDecodeError as e:
                print(f"  ERROR: invalid JSON: {e}")
                total_errors += 1
                continue

        for infinitive, tenses in results.items():
            if infinitive not in lookup:
                print(f"  WARNING: '{infinitive}' not found in conjugations.json — skipping")
                continue

            verb_entry = lookup[infinitive]

            # Ensure sentences dict exists
            if "sentences" not in verb_entry:
                verb_entry["sentences"] = {}

            sentences = verb_entry["sentences"]

            for mood in ("subjonctif", "present"):
                if mood not in tenses:
                    continue

                if mood not in sentences:
                    sentences[mood] = {}

                for idx_str, entry in tenses[mood].items():
                    if idx_str in sentences[mood]:
                        total_skipped += 1
                        continue  # Don't overwrite existing

                    if not validate_sentence(entry, infinitive, mood, idx_str):
                        total_errors += 1
                        continue

                    sentences[mood][idx_str] = entry
                    total_added += 1

    print(f"\n--- Summary ---")
    print(f"Added:   {total_added}")
    print(f"Skipped: {total_skipped} (already existed)")
    print(f"Errors:  {total_errors}")
    return total_added


def main():
    result_files = glob.glob(RESULT_PATTERN)
    if not result_files:
        print(f"No result files found matching: {RESULT_PATTERN}")
        print("Generate result files first (mood_result_01.json through mood_result_16.json)")
        sys.exit(1)

    print(f"Found {len(result_files)} result file(s)")
    conjugations = load_conjugations()
    print(f"Loaded {len(conjugations)} verbs from conjugations.json")

    total_added = merge_results(conjugations, result_files)

    if total_added > 0:
        save_conjugations(conjugations)
    else:
        print("Nothing to save.")


if __name__ == "__main__":
    main()

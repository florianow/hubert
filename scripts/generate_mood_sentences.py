#!/usr/bin/env python3
"""
Generate French mood sentences (subjonctif vs présent indicatif) for the Conjuguez! game.

For verb+person combinations where the two moods produce different forms AND no sentence
exists yet in conjugations.json, this script calls Claude to generate example sentences:
  - Subjonctif: sentence with a trigger phrase (il faut que, je veux que, etc.)
  - Présent:    factual indicative sentence

The output is merged directly into conjugations.json.

Usage:
  python3 scripts/generate_mood_sentences.py [--rank-limit N] [--dry-run]

  --rank-limit N   Only process the top N verbs by frequency rank (default: all)
  --dry-run        Print what would be generated without calling the API
"""

import argparse
import json
import re
import sys
import time
from pathlib import Path

import anthropic

CONJUGATIONS_PATH = Path(__file__).parent.parent / "app/src/main/assets/conjugations.json"
CHECKPOINT_PATH = Path(__file__).parent / "mood_sentences_checkpoint.json"

PERSON_LABELS = ["je", "tu", "il/elle", "nous", "vous", "ils/elles"]
BATCH_SIZE = 15  # verbs per API call


def find_missing_combos(data: list, rank_limit: int | None) -> list:
    """Find verb+person combos that are eligible for mood questions but lack sentences."""
    missing = []
    for verb in data:
        rank = verb.get("rank", 9999)
        if rank_limit and rank > rank_limit:
            continue

        p_forms = verb["tenses"].get("present", [])
        s_forms = verb["tenses"].get("subjonctif", [])
        if not p_forms or not s_forms:
            continue

        sentences = verb.get("sentences") or {}
        sub_sents = sentences.get("subjonctif", {})
        pres_sents = sentences.get("present", {})

        for idx in range(min(len(p_forms), len(s_forms))):
            pf = p_forms[idx]
            sf = s_forms[idx]
            if not pf or not sf or pf.lower() == sf.lower():
                continue

            need_sub = str(idx) not in sub_sents
            need_pres = str(idx) not in pres_sents
            if need_sub or need_pres:
                missing.append({
                    "infinitive": verb["infinitive"],
                    "german": verb["german"],
                    "rank": rank,
                    "person_idx": idx,
                    "person": PERSON_LABELS[idx],
                    "subjonctif_form": sf,
                    "present_form": pf,
                    "need_sub": need_sub,
                    "need_pres": need_pres,
                })
    return missing


def build_prompt(batch: list) -> str:
    lines = []
    for item in batch:
        needs = []
        if item["need_sub"]:
            needs.append(f"subjonctif={item['subjonctif_form']}")
        if item["need_pres"]:
            needs.append(f"present={item['present_form']}")
        person = item["person"]
        # Handle je → j' elision in trigger phrases
        if person == "je":
            que_person = "que je"
        elif person in ("il/elle", "ils/elles"):
            que_person = "qu'" + person
        else:
            que_person = "que " + person
        lines.append(
            f"- {item['infinitive']} ({item['german']}): {person} | "
            f"NEED: {', '.join(needs)} | "
            f"trigger hint: \"Il faut {que_person} [_{item['subjonctif_form']}_]\" or similar"
        )

    verb_list = "\n".join(lines)

    return f"""Generate French example sentences for a language learning app.

For each verb+person combination below, generate the requested sentence types:

SUBJONCTIF sentences: Use a natural trigger phrase. Vary the trigger — choose from:
  il faut que, je veux que, je souhaite que, il est important que, il est possible que,
  je suis content/triste/surpris que, j'ai peur que, bien que, pour que, avant que,
  à moins que, il est nécessaire que, nous voulons que

PRÉSENT sentences: A simple factual statement in present tense (no trigger phrase needed).

Rules:
- The conjugated form must appear EXACTLY as given (watch elision: je → j' before vowels)
- Replace the conjugated form in the sentence with ___ (three underscores)
- Keep sentences short and natural (5-12 words)
- The "blank" field must be the exact conjugated form (the word replacing ___)
- German translation should be natural German

Verbs:
{verb_list}

Return ONLY valid JSON with this exact structure (no markdown, no explanation):
{{
  "être": {{
    "subjonctif": {{
      "3": {{"fr": "Il faut que nous ___ là à l'heure.", "de": "Wir müssen pünktlich da sein.", "blank": "soyons"}}
    }},
    "present": {{
      "3": {{"fr": "Nous ___ toujours ensemble.", "de": "Wir sind immer zusammen.", "blank": "sommes"}}
    }}
  }}
}}

Only include the tenses/persons you were asked to generate. Use the infinitive as the top-level key."""


def call_api(client: anthropic.Anthropic, batch: list) -> dict:
    prompt = build_prompt(batch)
    response = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=4096,
        messages=[{"role": "user", "content": prompt}],
    )
    text = response.content[0].text.strip()
    # Strip markdown code fences if present
    text = re.sub(r"^```(?:json)?\s*", "", text)
    text = re.sub(r"\s*```$", "", text)
    return json.loads(text)


def validate_sentence(entry: dict, expected_blank: str) -> bool:
    """Check that the blank field matches what's in the sentence."""
    fr = entry.get("fr", "")
    blank = entry.get("blank", "")
    if not fr or not blank or not entry.get("de"):
        return False
    if "___" not in fr:
        return False
    if blank.lower() != expected_blank.lower():
        return False
    return True


def merge_results(data: list, generated: dict) -> int:
    """Merge generated sentences into conjugations data in-place. Returns count merged."""
    count = 0
    verb_index = {verb["infinitive"]: verb for verb in data}

    for infinitive, tenses in generated.items():
        verb = verb_index.get(infinitive)
        if not verb:
            print(f"  WARN: verb '{infinitive}' not found in data, skipping")
            continue

        if "sentences" not in verb or verb["sentences"] is None:
            verb["sentences"] = {}

        for tense, persons in tenses.items():
            if tense not in verb["sentences"]:
                verb["sentences"][tense] = {}
            for person_idx_str, entry in persons.items():
                # Determine expected blank from verb tense data
                tense_key = "subjonctif" if tense == "subjonctif" else "present"
                forms = verb["tenses"].get(tense_key, [])
                try:
                    idx = int(person_idx_str)
                    expected = forms[idx] if idx < len(forms) else ""
                except (ValueError, IndexError):
                    expected = ""

                if not validate_sentence(entry, expected):
                    print(f"  WARN: invalid sentence for {infinitive}/{tense}/{person_idx_str}: {entry}")
                    continue

                # Only fill gaps — don't overwrite existing sentences
                if person_idx_str not in verb["sentences"].get(tense, {}):
                    verb["sentences"][tense][person_idx_str] = entry
                    count += 1

    return count


def load_checkpoint() -> dict:
    if CHECKPOINT_PATH.exists():
        return json.loads(CHECKPOINT_PATH.read_text())
    return {}


def save_checkpoint(checkpoint: dict):
    CHECKPOINT_PATH.write_text(json.dumps(checkpoint, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rank-limit", type=int, default=None,
                        help="Only process top N verbs by rank")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print batches without calling API")
    args = parser.parse_args()

    print("Loading conjugations.json...")
    data = json.loads(CONJUGATIONS_PATH.read_text())

    missing = find_missing_combos(data, args.rank_limit)
    print(f"Found {len(missing)} combos needing generation")

    if not missing:
        print("Nothing to generate.")
        return

    # Load checkpoint (already-generated infinitives)
    checkpoint = load_checkpoint()
    done_verbs = set(checkpoint.keys())
    missing = [m for m in missing if m["infinitive"] not in done_verbs]
    print(f"{len(missing)} combos remaining (after checkpoint)")

    if args.dry_run:
        # Group by verb and show first few batches
        groups = {}
        for m in missing[:BATCH_SIZE * 3]:
            groups.setdefault(m["infinitive"], []).append(m)
        batch = list(groups.values())[:BATCH_SIZE]
        flat = [item for group in batch for item in group]
        print("\n--- Sample prompt ---")
        print(build_prompt(flat[:10]))
        return

    client = anthropic.Anthropic()

    # Group combos by verb, then batch groups of BATCH_SIZE verbs
    verb_groups: dict[str, list] = {}
    for m in missing:
        verb_groups.setdefault(m["infinitive"], []).append(m)

    verbs = list(verb_groups.keys())
    total_batches = (len(verbs) + BATCH_SIZE - 1) // BATCH_SIZE
    total_merged = 0

    print(f"Processing {len(verbs)} verbs in {total_batches} batches of {BATCH_SIZE}...")

    for batch_num, start in enumerate(range(0, len(verbs), BATCH_SIZE), 1):
        batch_verbs = verbs[start:start + BATCH_SIZE]
        batch = [item for v in batch_verbs for item in verb_groups[v]]

        print(f"  Batch {batch_num}/{total_batches}: {', '.join(batch_verbs[:5])}{'...' if len(batch_verbs) > 5 else ''}", end=" ", flush=True)

        retries = 0
        while retries < 3:
            try:
                generated = call_api(client, batch)
                break
            except (json.JSONDecodeError, anthropic.APIError) as e:
                retries += 1
                if retries >= 3:
                    print(f"FAILED after 3 retries: {e}")
                    generated = {}
                    break
                print(f"retry {retries}...", end=" ", flush=True)
                time.sleep(2 ** retries)

        merged = merge_results(data, generated)
        total_merged += merged
        print(f"→ {merged} sentences added")

        # Update checkpoint
        for v in batch_verbs:
            checkpoint[v] = True
        save_checkpoint(checkpoint)

        # Small delay to be polite to the API
        time.sleep(0.3)

    print(f"\nWriting updated conjugations.json ({total_merged} new sentences)...")
    CONJUGATIONS_PATH.write_text(
        json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    )

    # Clean up checkpoint on success
    if CHECKPOINT_PATH.exists():
        CHECKPOINT_PATH.unlink()

    print("Done.")


if __name__ == "__main__":
    main()

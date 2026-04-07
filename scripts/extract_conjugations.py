#!/usr/bin/env python3
"""
Extract conjugation data from the local Anki collection database.

Reads the "Französisch 5000" deck from the local Anki installation,
parses the Konjugation HTML field for each verb, and extracts all
conjugated forms per tense per person.

Also matches example sentences from the YAML cards to conjugated forms
so the game can show real sentences when available.

Outputs:
  - conjugations.json  Verb conjugation data for the Conjuguez! game mode

Source: ~/Library/Application Support/Anki2/Benutzer 1/collection.anki2
"""
import os
import json
import re
import sqlite3
import sys
from html.parser import HTMLParser


# ---------------------------------------------------------------------------
# 1. Parse conjugation HTML tables from Anki
# ---------------------------------------------------------------------------

TENSE_CODES = {
    'P': 'present',
    'IT': 'imparfait',
    'F': 'futur',
    'C': 'conditionnel',
    'S': 'subjonctif',
    'PS': 'passe_simple',
    'IF': 'imperatif',
}

# Tenses used in the game (skip PC/Gérondif/Subj.imp — PC needs auxiliary
# construction which is a full sentence, not a single word form)
GAME_TENSES = {'P', 'IT', 'F', 'C', 'S', 'PS', 'IF'}

PERSON_LABELS = ['je', 'tu', 'il/elle', 'nous', 'vous', 'ils/elles']

TENSE_DISPLAY = {
    'present': 'Présent',
    'imparfait': 'Imparfait',
    'futur': 'Futur simple',
    'conditionnel': 'Conditionnel',
    'subjonctif': 'Subjonctif',
    'passe_simple': 'Passé simple',
    'imperatif': 'Impératif',
}


class ConjugationParser(HTMLParser):
    """Parse an Anki Konjugation HTML table into structured data."""

    def __init__(self):
        super().__init__()
        self.tenses = {}  # tense_code -> [6 forms or fewer]
        self.only_third_person = False
        self.reflexive = False
        self.auxiliary = ''
        self.word = ''

        self._current_tense = None
        self._current_row_forms = []
        self._in_td = False
        self._td_attrs = {}
        self._colspan = 1
        self._skip_cells = 0  # number of non-form cells to skip (tense name, stem)

    def handle_starttag(self, tag, attrs):
        attrs_dict = dict(attrs)

        if tag == 'table':
            self.only_third_person = attrs_dict.get('data-only-third-person', 'false') == 'true'
            self.reflexive = attrs_dict.get('data-reflexive', 'false') == 'true'
            self.auxiliary = attrs_dict.get('data-aux', '')
            self.word = attrs_dict.get('data-word', '')

        elif tag == 'tr':
            tense_code = attrs_dict.get('data-tense', '')
            if tense_code in GAME_TENSES:
                self._current_tense = tense_code
                self._current_row_forms = []
                self._skip_cells = 0
            else:
                self._current_tense = None

        elif tag == 'td' and self._current_tense is not None:
            self._in_td = True
            self._td_attrs = attrs_dict
            self._colspan = int(attrs_dict.get('colspan', '1'))

            td_class = attrs_dict.get('class', '')
            if 'tense' in td_class and 'tense_stem' not in td_class:
                # This is the tense label cell, skip it
                self._skip_cells = 1
            elif 'tense_stem' in td_class:
                # This is the stem cell, skip it
                self._skip_cells = 1

    def handle_endtag(self, tag):
        if tag == 'td' and self._in_td and self._current_tense is not None:
            self._in_td = False

            if self._skip_cells > 0:
                self._skip_cells = 0
                return

            # Extract the full form from data-full attribute
            form = self._td_attrs.get('data-full', '').strip()

            if self._colspan > 1:
                # colspan cell (e.g., participe passé) — replicate for all persons
                for _ in range(self._colspan):
                    self._current_row_forms.append(form)
            else:
                self._current_row_forms.append(form)

            self._td_attrs = {}

        elif tag == 'tr' and self._current_tense is not None:
            if self._current_row_forms:
                # Ensure we have exactly 6 forms (je, tu, il, nous, vous, ils)
                forms = self._current_row_forms[:6]
                while len(forms) < 6:
                    forms.append('')
                self.tenses[self._current_tense] = forms
            self._current_tense = None
            self._current_row_forms = []

    def handle_data(self, data):
        pass  # We use data-full attributes, not cell text


def parse_conjugation_html(html):
    """Parse conjugation HTML and return structured data."""
    parser = ConjugationParser()
    parser.feed(html)
    return parser


# ---------------------------------------------------------------------------
# 2. Match example sentences to conjugated forms
# ---------------------------------------------------------------------------

def load_sentences_from_yaml(cards_dir):
    """Load example sentences from YAML card files.
    Returns {rank: [(fr_sentence, de_sentence, highlighted_word), ...]}"""
    sentences = {}

    if not os.path.isdir(cards_dir):
        print(f"  Warning: {cards_dir} not found, skipping sentence matching.")
        return sentences

    for filename in sorted(os.listdir(cards_dir)):
        if not filename.endswith('.yml'):
            continue

        filepath = os.path.join(cards_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        # Extract rank
        rank_match = re.search(r'^Rang:\s*(\d+)', content, re.MULTILINE)
        if not rank_match:
            continue
        rank = int(rank_match.group(1))

        # Extract Beispielsätze block
        block_match = re.search(r'^Beispielsätze:\s*\|-?\s*\n(.*?)(?=^\w|\Z)',
                                content, re.MULTILINE | re.DOTALL)
        if not block_match:
            continue

        block = block_match.group(1)
        lines = [l.strip() for l in block.split('\n') if l.strip() and not l.strip().startswith('#')]

        # Parse pairs of FR/DE lines
        sents = []
        i = 0
        while i + 1 < len(lines):
            fr_line = lines[i]
            de_line = lines[i + 1]
            i += 2

            # Extract highlighted word
            m = re.search(r'\*([^*]+)\*', fr_line)
            if m:
                highlighted = m.group(1)
                fr_clean = fr_line.replace('*', '')
                de_clean = de_line.replace('*', '')
                sents.append((fr_clean, de_clean, highlighted))

        if sents:
            sentences[rank] = sents

    return sentences


def match_sentences_to_forms(verb_rank, conjugation_forms, yaml_sentences):
    """Find example sentences where the highlighted word matches a conjugation form.

    Returns dict of matches: {tense_code: {person_index: {fr, de, blank}}}
    Only returns exact matches — no fuzzy matching, no generation.
    """
    matches = {}
    sents = yaml_sentences.get(verb_rank, [])
    if not sents:
        return matches

    # Build a lookup: form -> [(tense_code, person_index), ...]
    form_lookup = {}
    for tense_code, forms in conjugation_forms.items():
        for person_idx, form in enumerate(forms):
            if form:
                key = form.lower()
                form_lookup.setdefault(key, []).append((tense_code, person_idx))

    for fr_sent, de_sent, highlighted in sents:
        key = highlighted.lower().strip()
        if key in form_lookup:
            for tense_code, person_idx in form_lookup[key]:
                if tense_code not in matches:
                    matches[tense_code] = {}
                if person_idx not in matches[tense_code]:
                    matches[tense_code][person_idx] = {
                        'fr': fr_sent,
                        'de': de_sent,
                        'blank': highlighted
                    }

    return matches


# ---------------------------------------------------------------------------
# 3. Main
# ---------------------------------------------------------------------------

def main():
    anki_db = os.path.expanduser(
        '~/Library/Application Support/Anki2/Benutzer 1/collection.anki2'
    )
    cards_dir = '/tmp/anki_french/cards'
    assets_dir = 'app/src/main/assets'

    if not os.path.isfile(anki_db):
        print(f"Error: Anki database not found at {anki_db}")
        print("Make sure Anki is installed and the Französisch 5000 deck is imported.")
        sys.exit(1)

    # --- Load from Anki DB ---
    print(f"Reading Anki database: {anki_db}")
    conn = sqlite3.connect(anki_db)
    # Anki uses a custom 'unicase' collation — provide a dummy so queries work
    conn.create_collation('unicase', lambda a, b: (a.lower() > b.lower()) - (a.lower() < b.lower()))
    cur = conn.cursor()

    # Find the "French Word" note type
    cur.execute("SELECT id FROM notetypes WHERE name = 'French Word'")
    row = cur.fetchone()
    if not row:
        print("Error: 'French Word' note type not found in Anki database.")
        sys.exit(1)
    notetype_id = row[0]

    # Get field order to confirm Konjugation is at index 10
    cur.execute("SELECT name, ord FROM fields WHERE ntid = ? ORDER BY ord", (notetype_id,))
    field_names = {row[1]: row[0] for row in cur.fetchall()}
    print(f"  Fields: {field_names}")

    konj_idx = None
    for idx, name in field_names.items():
        if name == 'Konjugation':
            konj_idx = idx
            break

    if konj_idx is None:
        print("Error: 'Konjugation' field not found.")
        sys.exit(1)

    # Field indices we need
    rang_idx = 0      # Rang
    wort_idx = 1      # Wort
    wortart_idx = 4   # Wortart
    def_idx = 6       # Definition

    # --- Load example sentences from YAML ---
    print(f"Loading example sentences from {cards_dir}...")
    yaml_sentences = load_sentences_from_yaml(cards_dir)
    print(f"  Loaded sentences for {len(yaml_sentences)} words")

    # --- Parse all verbs ---
    print("Parsing conjugation data...")
    cur.execute("SELECT flds FROM notes WHERE mid = ?", (notetype_id,))

    conjugations = []
    skipped_no_conj = 0
    skipped_third_only = 0
    total_sentence_matches = 0

    for (flds,) in cur.fetchall():
        fields = flds.split('\x1f')
        if len(fields) <= konj_idx:
            continue

        konj_html = fields[konj_idx].strip()
        if not konj_html:
            skipped_no_conj += 1
            continue

        # Parse rank
        try:
            rank = int(fields[rang_idx])
        except (ValueError, IndexError):
            continue

        word = fields[wort_idx]
        wortart = fields[wortart_idx] if len(fields) > wortart_idx else ''
        german = fields[def_idx] if len(fields) > def_idx else ''

        # Clean German definition (simple version)
        german_clean = german
        if ';' in german_clean:
            german_clean = german_clean.split(';')[0].strip()
        if ',' in german_clean and '(' not in german_clean:
            german_clean = german_clean.split(',')[0].strip()
        german_clean = re.sub(r'<[^>]+>', ' ', german_clean).strip()
        german_clean = re.sub(r'\(→\)\s*', '', german_clean).strip()
        german_clean = re.sub(r'\(←\)\s*', '', german_clean).strip()

        # Parse conjugation HTML
        parsed = parse_conjugation_html(konj_html)

        # Skip impersonal verbs (only third person) — not useful for the game
        if parsed.only_third_person:
            skipped_third_only += 1
            continue

        # Build tense data — only include tenses where we have forms
        tenses = {}
        for tense_code in GAME_TENSES:
            if tense_code not in parsed.tenses:
                continue
            forms = parsed.tenses[tense_code]
            tense_name = TENSE_CODES.get(tense_code)
            if not tense_name:
                continue

            # Skip if all forms are empty
            if not any(f for f in forms):
                continue

            tenses[tense_name] = forms

        if not tenses:
            continue

        # Match sentences
        sentence_matches = match_sentences_to_forms(rank, parsed.tenses, yaml_sentences)
        sentences = {}
        for tense_code, person_matches in sentence_matches.items():
            tense_name = TENSE_CODES.get(tense_code)
            if tense_name:
                sentences[tense_name] = {}
                for person_idx, sent_data in person_matches.items():
                    sentences[tense_name][str(person_idx)] = sent_data
                    total_sentence_matches += 1

        entry = {
            'rank': rank,
            'infinitive': word,
            'german': german_clean,
            'tenses': tenses,
        }

        if sentences:
            entry['sentences'] = sentences

        conjugations.append(entry)

    conn.close()

    # Sort by rank
    conjugations.sort(key=lambda c: c['rank'])

    # --- Write output ---
    os.makedirs(assets_dir, exist_ok=True)
    out_path = os.path.join(assets_dir, 'conjugations.json')
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(conjugations, f, ensure_ascii=False, indent=None, separators=(',', ':'))

    file_size = os.path.getsize(out_path) / 1024

    # --- Stats ---
    print(f"\n{'='*60}")
    print(f"conjugations.json: {len(conjugations)} verbs, {file_size:.1f} KB")
    print(f"  Skipped (no conjugation): {skipped_no_conj}")
    print(f"  Skipped (impersonal/3rd person only): {skipped_third_only}")
    print(f"  Sentence matches: {total_sentence_matches}")

    # Tense coverage
    print(f"\nTense coverage:")
    for tense_name, display_name in TENSE_DISPLAY.items():
        count = sum(1 for c in conjugations if tense_name in c['tenses'])
        print(f"  {display_name}: {count} verbs")

    # Sentence coverage
    verbs_with_sents = sum(1 for c in conjugations if 'sentences' in c)
    print(f"\nVerbs with sentence matches: {verbs_with_sents}")

    # Samples
    print(f"\nSamples:")
    for entry in conjugations[:3]:
        print(f"  #{entry['rank']} {entry['infinitive']} ({entry['german']})")
        for tense, forms in list(entry['tenses'].items())[:2]:
            print(f"    {tense}: {forms}")
        if 'sentences' in entry:
            for tense, persons in list(entry['sentences'].items())[:1]:
                for pidx, sent in list(persons.items())[:1]:
                    print(f"    Sentence ({tense}, person {pidx}): \"{sent['fr']}\"")


if __name__ == '__main__':
    main()

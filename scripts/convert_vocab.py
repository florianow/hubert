#!/usr/bin/env python3
"""
Convert anki_french YAML cards + grammar HTML to JSON assets for the Hubert app.

Outputs:
  - vocab.json       Enriched word data (rank, french, german, gender, ipa, pos, categories)
  - categories.json  Category name -> list of ranks (for Gap Fill distractors)
  - sentences.json   Rank -> list of {fr, de, blank} example sentences (for Gap Fill)
"""
import os
import json
import re
import sys
from html.parser import HTMLParser


# ---------------------------------------------------------------------------
# 1. Parse thematic category HTML files  (grammar/99 Vokabeln/*.html)
# ---------------------------------------------------------------------------

class CategoryParser(HTMLParser):
    """Extract rank numbers from a thematic vocabulary HTML file."""

    def __init__(self):
        super().__init__()
        self.ranks = []
        self._in_condensed = False

    def handle_starttag(self, tag, attrs):
        attrs_dict = dict(attrs)
        if tag == 'td' and attrs_dict.get('class', '') == 'condensed':
            self._in_condensed = True

    def handle_data(self, data):
        if self._in_condensed:
            m = re.match(r'#(\d+)', data.strip())
            if m:
                self.ranks.append(int(m.group(1)))
            self._in_condensed = False


def parse_categories(grammar_dir):
    """Parse all 99 Vokabeln HTML files into {category_name: [ranks]}."""
    vocab_dir = os.path.join(grammar_dir, '99 Vokabeln')
    if not os.path.isdir(vocab_dir):
        print(f"Warning: {vocab_dir} not found, skipping categories.")
        return {}

    categories = {}
    for filename in sorted(os.listdir(vocab_dir)):
        if not filename.endswith('.html'):
            continue
        # e.g. "12 Tiere.html" -> "Tiere"
        name = re.sub(r'^\d+\s+', '', filename).replace('.html', '')

        filepath = os.path.join(vocab_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            html = f.read()

        parser = CategoryParser()
        parser.feed(html)

        if parser.ranks:
            categories[name] = sorted(set(parser.ranks))

    return categories


def build_rank_to_categories(categories):
    """Invert: rank -> [category names]."""
    rank_cats = {}
    for cat_name, ranks in categories.items():
        for rank in ranks:
            rank_cats.setdefault(rank, []).append(cat_name)
    return rank_cats


# ---------------------------------------------------------------------------
# 2. Parse YAML card files
# ---------------------------------------------------------------------------

def unquote(val):
    """Remove surrounding quotes from a YAML value."""
    val = val.strip()
    if (val.startswith("'") and val.endswith("'")) or \
       (val.startswith('"') and val.endswith('"')):
        val = val[1:-1]
    return val


def detect_gender(wortart, wort_mit_artikel):
    """Detect grammatical gender from Wortart and Wort mit Artikel.
    Returns 'm', 'f', or None."""
    # Check Wortart first
    if 'nf' in wortart:
        return 'f'
    if 'nm' in wortart:
        return 'm'
    # Fallback: check article
    art = wort_mit_artikel.lower().strip()
    if art.startswith('le ') or art.startswith("l'") or art.startswith('un '):
        return 'm'
    if art.startswith('la ') or art.startswith('une '):
        return 'f'
    return None


def parse_sentences(lines, start_idx):
    """Parse Beispielsätze block from YAML lines starting at start_idx.
    Returns list of (french_sentence, german_sentence, blank_word)."""
    sentences = []
    i = start_idx
    while i < len(lines):
        line = lines[i]
        # Stop if we hit another YAML key (not indented, not blank, not comment)
        if line and not line.startswith(' ') and not line.startswith('#') and ':' in line:
            break
        i += 1

    # Re-parse: collect pairs of non-empty lines
    block_lines = []
    for j in range(start_idx, i):
        stripped = lines[j].strip()
        if stripped and not stripped.startswith('#'):
            block_lines.append(stripped)

    # Pairs: FR line, DE line
    idx = 0
    while idx + 1 < len(block_lines):
        fr_line = block_lines[idx]
        de_line = block_lines[idx + 1]
        idx += 2

        # Extract the *highlighted* word from French sentence
        m = re.search(r'\*([^*]+)\*', fr_line)
        if m:
            blank_word = m.group(1)
            # Clean asterisks for display
            fr_clean = fr_line.replace('*', '')
            de_clean = de_line.replace('*', '')
            sentences.append({
                'fr': fr_clean,
                'de': de_clean,
                'blank': blank_word
            })

    return sentences


def parse_card(filepath):
    """Parse a single YAML card file. Returns dict with all fields."""
    with open(filepath, 'r', encoding='utf-8') as f:
        raw_lines = f.read().split('\n')

    card = {
        'rank': None,
        'french': None,
        'german': None,
        'wortart': '',
        'wort_mit_artikel': '',
        'ipa': '',
        'gender': None,
        'sentences': []
    }

    for i, line in enumerate(raw_lines):
        stripped = line.strip()
        if stripped.startswith('#') or not stripped:
            continue

        if stripped.startswith('Rang:'):
            try:
                card['rank'] = int(stripped.split(':', 1)[1].strip())
            except ValueError:
                pass
        elif stripped.startswith('Wort:') and not stripped.startswith('Wortart:') and not stripped.startswith('Wort mit'):
            card['french'] = unquote(stripped.split(':', 1)[1])
        elif stripped.startswith('Wortart:'):
            card['wortart'] = unquote(stripped.split(':', 1)[1])
        elif stripped.startswith('Wort mit Artikel:'):
            card['wort_mit_artikel'] = unquote(stripped.split(':', 1)[1])
        elif stripped.startswith('IPA:'):
            ipa = unquote(stripped.split(':', 1)[1])
            # Remove surrounding backslashes
            ipa = ipa.strip('\\')
            card['ipa'] = ipa
        elif stripped.startswith('Definition:'):
            card['german'] = unquote(stripped.split(':', 1)[1])
        elif stripped.startswith('Beispielsätze:'):
            card['sentences'] = parse_sentences(raw_lines, i + 1)

    # Detect gender
    card['gender'] = detect_gender(card['wortart'], card['wort_mit_artikel'])

    return card


def clean_german(german):
    """Clean up German definition for game display."""
    clean = german

    # Remove HTML tags like <br>
    clean = re.sub(r'<[^>]+>', ' ', clean)

    # Handle arrow-separated meanings: "(←) X (→) Y" -> take Y (main meaning)
    arrow_match = re.search(r'\(→\)\s*(.+)', clean)
    if arrow_match:
        clean = arrow_match.group(1).strip()
    elif '(←)' in clean:
        clean = re.sub(r'\(←\)\s*', '', clean).strip()

    # Take first meaning if semicolon-separated
    if ';' in clean:
        clean = clean.split(';')[0].strip()

    # Take first meaning if comma-separated (but not inside parentheses)
    if ',' in clean and '(' not in clean:
        clean = clean.split(',')[0].strip()

    # Remove bracket annotations like [Relativpronomen]
    clean = re.sub(r'\[.*?\]', '', clean).strip()

    # Remove parenthesized annotations like (-e/r/s)
    clean = re.sub(r'\(-[a-z/]+\)', '', clean).strip()

    if not clean:
        clean = german.split(',')[0].split(';')[0].strip()

    return clean


# ---------------------------------------------------------------------------
# 3. Main
# ---------------------------------------------------------------------------

def main():
    cards_dir = '/tmp/anki_french/cards'
    grammar_dir = '/tmp/anki_french/grammar'
    assets_dir = 'app/src/main/assets'

    if not os.path.isdir(cards_dir):
        print(f"Error: {cards_dir} not found.")
        print("Clone the repo first: git clone https://github.com/jacbz/anki_french /tmp/anki_french")
        sys.exit(1)

    # --- Parse thematic categories ---
    print("Parsing thematic categories...")
    categories = parse_categories(grammar_dir)
    rank_to_cats = build_rank_to_categories(categories)
    print(f"  Found {len(categories)} categories covering {len(rank_to_cats)} words")

    # --- Parse all cards ---
    print("Parsing cards...")
    words = []
    all_sentences = {}
    skipped = 0
    noun_count = 0
    sentence_count = 0

    files = sorted(os.listdir(cards_dir))
    for filename in files:
        if not filename.endswith('.yml'):
            continue

        filepath = os.path.join(cards_dir, filename)
        card = parse_card(filepath)

        if not card['french'] or not card['german'] or card['german'] in ("''", ''):
            skipped += 1
            continue

        # Skip single-char words that are too vague
        if len(card['french']) < 2 and len(clean_german(card['german'])) < 2:
            skipped += 1
            continue

        rank = card['rank'] or 0
        cleaned = clean_german(card['german'])

        # Build vocab entry
        entry = {
            'rank': rank,
            'french': card['french'],
            'german': cleaned,
        }

        # Add gender if it's a noun
        if card['gender']:
            entry['gender'] = card['gender']
            noun_count += 1

        # Add IPA if available
        if card['ipa']:
            entry['ipa'] = card['ipa']

        # Add part of speech
        if card['wortart']:
            entry['pos'] = card['wortart']

        # Add categories from thematic HTML
        cats = rank_to_cats.get(rank, [])
        if cats:
            entry['categories'] = cats

        words.append(entry)

        # Store sentences separately (only for words with categories,
        # since Gap Fill needs category-aware distractors)
        if card['sentences'] and cats:
            # Keep max 5 sentences per word to limit file size
            all_sentences[str(rank)] = card['sentences'][:5]
            sentence_count += len(card['sentences'][:5])

    # Sort by rank
    words.sort(key=lambda w: w['rank'])

    # --- Write outputs ---
    os.makedirs(assets_dir, exist_ok=True)

    # 1. vocab.json (enriched)
    vocab_path = os.path.join(assets_dir, 'vocab.json')
    with open(vocab_path, 'w', encoding='utf-8') as f:
        json.dump(words, f, ensure_ascii=False, indent=None, separators=(',', ':'))
    print(f"\nvocab.json: {len(words)} words, {os.path.getsize(vocab_path) / 1024:.1f} KB")
    print(f"  {noun_count} nouns with gender")

    # 2. categories.json
    cat_path = os.path.join(assets_dir, 'categories.json')
    with open(cat_path, 'w', encoding='utf-8') as f:
        json.dump(categories, f, ensure_ascii=False, indent=None, separators=(',', ':'))
    print(f"categories.json: {len(categories)} categories, {os.path.getsize(cat_path) / 1024:.1f} KB")
    for name, ranks in sorted(categories.items()):
        print(f"  {name}: {len(ranks)} words")

    # 3. sentences.json
    sent_path = os.path.join(assets_dir, 'sentences.json')
    with open(sent_path, 'w', encoding='utf-8') as f:
        json.dump(all_sentences, f, ensure_ascii=False, indent=None, separators=(',', ':'))
    words_with_sents = len(all_sentences)
    print(f"sentences.json: {words_with_sents} words with sentences, {sentence_count} total, {os.path.getsize(sent_path) / 1024:.1f} KB")

    # Print samples
    print("\nSamples:")
    for w in words[:5]:
        cats = w.get('categories', [])
        g = w.get('gender', '-')
        print(f"  #{w['rank']}: {w['french']} = {w['german']} (gender={g}, cats={cats})")

    # Print a sentence sample
    for rank_str, sents in list(all_sentences.items())[:3]:
        s = sents[0]
        print(f"  Sentence #{rank_str}: \"{s['fr']}\" [blank: {s['blank']}]")


if __name__ == '__main__':
    main()

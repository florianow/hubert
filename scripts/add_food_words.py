#!/usr/bin/env python3
"""Add missing fruit and vegetable words to vocab.json and categories.json."""
import json, os

ASSETS = 'app/src/main/assets'

FRUITS = [
    ("poire",        "f", "Birne",          "pwaʁ"),
    ("cerise",       "f", "Kirsche",         "səʁiz"),
    ("banane",       "f", "Banane",          "banan"),
    ("citron",       "m", "Zitrone",         "sitʁɔ̃"),
    ("fraise",       "f", "Erdbeere",        "fʁɛz"),
    ("raisin",       "m", "Weintraube",      "ʁezɛ̃"),
    ("melon",        "m", "Melone",          "məlɔ̃"),
    ("prune",        "f", "Pflaume",         "pʁyn"),
    ("mangue",       "f", "Mango",           "mɑ̃ɡ"),
    ("ananas",       "m", "Ananas",          "anana"),
    ("abricot",      "m", "Aprikose",        "abʁiko"),
    ("framboise",    "f", "Himbeere",        "fʁɑ̃bwaz"),
    ("myrtille",     "f", "Heidelbeere",     "miʁtij"),
    ("noix",         "f", "Walnuss",         "nwa"),
    ("noisette",     "f", "Haselnuss",       "nwazɛt"),
    ("amande",       "f", "Mandel",          "amɑ̃d"),
    ("figue",        "f", "Feige",           "fiɡ"),
    ("kiwi",         "m", "Kiwi",            "kiwi"),
    ("pastèque",     "f", "Wassermelone",    "pastɛk"),
    ("pamplemousse", "m", "Grapefruit",      "pɑ̃pləmus"),
    ("pêche",        "f", "Pfirsich",        "pɛʃ"),
    ("orange",       "f", "Orange",          "ɔʁɑ̃ʒ"),
]

VEGETABLES = [
    ("carotte",       "f", "Karotte",        "kaʁɔt"),
    ("tomate",        "f", "Tomate",         "tɔmat"),
    ("concombre",     "m", "Gurke",          "kɔ̃kɔ̃bʁ"),
    ("oignon",        "m", "Zwiebel",        "ɔɲɔ̃"),
    ("ail",           "m", "Knoblauch",      "aj"),
    ("salade",        "f", "Salat",          "salad"),
    ("laitue",        "f", "Kopfsalat",      "lɛty"),
    ("épinard",       "m", "Spinat",         "epinar"),
    ("brocoli",       "m", "Brokkoli",       "bʁɔkɔli"),
    ("chou-fleur",    "m", "Blumenkohl",     "ʃuflœʁ"),
    ("pomme de terre","f", "Kartoffel",      "pɔmdətɛʁ"),
    ("poivron",       "m", "Paprika",        "pwavʁɔ̃"),
    ("petit pois",    "m", "Erbse",          "pətipwa"),
    ("haricot",       "m", "Bohne",          "aʁiko"),
    ("maïs",          "m", "Mais",           "mais"),
    ("champignon",    "m", "Pilz",           "ʃɑ̃piɲɔ̃"),
    ("courgette",     "f", "Zucchini",       "kuʁʒɛt"),
    ("aubergine",     "f", "Aubergine",      "obɛʁʒin"),
    ("poireau",       "m", "Lauch",          "pwaʁo"),
    ("céleri",        "m", "Sellerie",       "selʁi"),
    ("radis",         "m", "Radieschen",     "ʁadi"),
    ("chou",          "m", "Kohl",           "ʃu"),
    ("navet",         "m", "Rübe",           "navɛ"),
    ("asperge",       "f", "Spargel",        "aspɛʁʒ"),
    ("artichaut",     "m", "Artischocke",    "aʁtiʃo"),
    ("betterave",     "f", "Rote Bete",      "bɛtʁav"),
    ("fenouil",       "m", "Fenchel",        "fənuj"),
    ("poivron rouge", "m", "roter Paprika",  "pwavʁɔ̃ ʁuʒ"),
    ("courgette",     "f", "Zucchini",       "kuʁʒɛt"),
]

def main():
    vocab_path = os.path.join(ASSETS, 'vocab.json')
    cat_path   = os.path.join(ASSETS, 'categories.json')

    with open(vocab_path, encoding='utf-8') as f:
        words = json.load(f)
    with open(cat_path, encoding='utf-8') as f:
        cats = json.load(f)

    existing_french = {w['french'] for w in words}
    next_rank = max(w['rank'] for w in words) + 1

    new_words = []
    new_ranks = []

    # pêche and orange exist but mean something else — we add them with correct food meaning
    # but only if not already there as food
    skip_existing = {'pêche', 'orange'}  # already in deck with other meaning

    all_entries = [(fr, g, de, ipa, 'Obst') for fr, g, de, ipa in FRUITS] + \
                  [(fr, g, de, ipa, 'Gemüse') for fr, g, de, ipa in VEGETABLES]

    for fr, gender, de, ipa, _ in all_entries:
        if fr in existing_french and fr not in skip_existing:
            continue  # already present with correct meaning
        if fr in skip_existing and fr in existing_french:
            # update existing entry to also include the food category
            for w in words:
                if w['french'] == fr:
                    cats_list = w.get('categories', [])
                    if 'Essen und Trinken' not in cats_list:
                        cats_list.append('Essen und Trinken')
                        w['categories'] = sorted(cats_list)
                    if 'Obst und Gemüse' not in cats_list:
                        cats_list.append('Obst und Gemüse')
                        w['categories'] = sorted(cats_list)
            continue

        entry = {
            'rank': next_rank,
            'french': fr,
            'german': de,
            'gender': gender,
            'ipa': ipa,
            'pos': f'n{gender}',
            'categories': ['Essen und Trinken', 'Obst und Gemüse']
        }
        new_words.append(entry)
        new_ranks.append(next_rank)
        next_rank += 1

    words.extend(new_words)

    # Update categories
    food_cat = set(cats.get('Essen und Trinken', []))
    obst_cat = set(cats.get('Obst und Gemüse', []))

    for r in new_ranks:
        food_cat.add(r)
        obst_cat.add(r)

    # also add existing pêche/orange ranks to food
    for w in words:
        if w['french'] in skip_existing:
            food_cat.add(w['rank'])
            obst_cat.add(w['rank'])

    cats['Essen und Trinken'] = sorted(food_cat)
    cats['Obst und Gemüse']   = sorted(obst_cat)
    cats = dict(sorted(cats.items()))

    with open(vocab_path, 'w', encoding='utf-8') as f:
        json.dump(words, f, ensure_ascii=False, indent=2)
    with open(cat_path, 'w', encoding='utf-8') as f:
        json.dump(cats, f, ensure_ascii=False, indent=2)

    print(f"Hinzugefügt: {len(new_words)} neue Wörter (Ränge {next_rank - len(new_words)}–{next_rank-1})")
    print(f"Essen und Trinken: {len(cats['Essen und Trinken'])} Wörter")
    print(f"Obst und Gemüse:   {len(cats['Obst und Gemüse'])} Wörter")
    for w in new_words:
        print(f"  #{w['rank']} {w['french']:20s} = {w['german']}")

if __name__ == '__main__':
    main()

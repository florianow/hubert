#!/usr/bin/env python3
"""Add shopping vocabulary category to vocab.json and categories.json."""
import json, os

ASSETS = 'app/src/main/assets'
CAT = 'Einkaufen'

# (french, gender m/f/None, german, ipa)
# gender None = no gender (verbs, adjectives)
NEW_WORDS = [
    # Geschäfte
    ("supermarché",          "m", "Supermarkt",              "sypɛʁmaʁʃe"),
    ("hypermarché",          "m", "Hypermarkt",              "ipɛʁmaʁʃe"),
    ("grande surface",       "f", "Großmarkt",               "ɡʁɑ̃d syʁfas"),
    ("centre commercial",    "m", "Einkaufszentrum",         "sɑ̃tʁ kɔmɛʁsjal"),
    ("épicerie",             "f", "Lebensmittelladen",       "episʁi"),
    ("supérette",            "f", "Kleinsupermarkt",         "sypɛʁɛt"),
    ("boucherie",            "f", "Metzgerei",               "buʃʁi"),
    ("charcuterie",          "f", "Wurst- und Feinkostladen","ʃaʁkytʁi"),
    ("traiteur",             "m", "Feinkosthändler",         "tʁɛtœʁ"),
    ("boulangerie",          "f", "Bäckerei",                "bulɑ̃ʒʁi"),
    ("pâtisserie",           "f", "Konditorei",              "pɑtisʁi"),
    ("poissonnerie",         "f", "Fischgeschäft",           "pwasɔnʁi"),
    ("primeur",              "m", "Obst- und Gemüsehändler", "pʁimœʁ"),
    ("bureau de tabac",      "m", "Tabakladen",              "byʁo də taba"),
    ("kiosque",              "m", "Kiosk",                   "kjɔsk"),
    ("mercerie",             "f", "Kurzwarengeschäft",       "mɛʁsʁi"),
    ("cordonnier",           "m", "Schuster",                "kɔʁdɔnje"),
    ("papeterie",            "f", "Schreibwarengeschäft",    "papɛtʁi"),
    ("librairie",            "f", "Buchhandlung",            "libʁɛʁi"),
    ("droguerie",            "f", "Drogerie",                "dʁɔɡʁi"),
    ("quincaillerie",        "f", "Eisenwarenhandlung",      "kɛ̃kajʁi"),
    ("pressing",             "m", "Reinigung",               "pʁɛsiŋ"),
    ("bijouterie",           "f", "Juweliergeschäft",        "biʒutʁi"),
    ("fleuriste",            "f", "Blumenladen",             "flœʁist"),
    ("parfumerie",           "f", "Parfümerie",              "paʁfymʁi"),
    ("opticien",             "m", "Optiker",                 "ɔptisjɛ̃"),
    ("boutique",             "f", "Boutique",                "butik"),
    # Im Geschäft
    ("étalage",              "m", "Auslage",                 "etalɑʒ"),
    ("vitrine",              "f", "Schaufenster",            "vitʁin"),
    ("allée",                "f", "Gang",                    "ale"),
    ("étagère",              "f", "Regal",                   "etaʒɛʁ"),
    ("rayon",                "m", "Abteilung",               "ʁɛjɔ̃"),
    ("panier",               "m", "Einkaufskorb",            "panje"),
    ("chariot",              "m", "Einkaufswagen",           "ʃaʁjo"),
    ("caisse",               "f", "Kasse",                   "kɛs"),
    ("comptoir",             "m", "Tresen",                  "kɔ̃twaʁ"),
    ("cabine d'essayage",    "f", "Umkleidekabine",          "kabin desɛjaʒ"),
    ("vendeur",              "m", "Verkäufer",               "vɑ̃dœʁ"),
    ("vendeuse",             "f", "Verkäuferin",             "vɑ̃døz"),
    ("escalator",            "m", "Rolltreppe",              "ɛskalatɔʁ"),
    ("étiquette",            "f", "Preisschild",             "etikɛt"),
    ("catalogue",            "m", "Katalog",                 "katalɔɡ"),
    ("solde",                "m", "Schlussverkauf",          "sɔld"),
    ("promotion",            "f", "Sonderangebot",           "pʁɔmosjɔ̃"),
    ("bon d'achat",          "m", "Gutschein",               "bɔ̃ daʃa"),
    ("carte fidélité",       "f", "Treuekarte",              "kaʁt fidelite"),
    # Geld & Zahlung
    ("espèces",              "f", "Bargeld",                 "ɛspɛs"),
    ("carte bancaire",       "f", "Bankkarte",               "kaʁt bɑ̃kɛʁ"),
    ("chèque",               "m", "Scheck",                  "ʃɛk"),
    ("acompte",              "m", "Anzahlung",               "akɔ̃t"),
    ("devis",                "m", "Kostenvoranschlag",       "dəvi"),
    ("virement bancaire",    "m", "Banküberweisung",         "viʁmɑ̃ bɑ̃kɛʁ"),
    ("distributeur",        "m",  "Geldautomat",             "distʁibytoer"),
    ("aubaine",              "f", "Schnäppchen",             "obɛn"),
    # Reklamation & Service
    ("garantie",             "f", "Garantie",                "ɡaʁɑ̃ti"),
    ("facture",              "f", "Rechnung",                "faktyʁ"),
    ("ticket de caisse",     "m", "Kassenbon",               "tikɛ də kɛs"),
    ("livraison",            "f", "Lieferung",               "livʁɛzɔ̃"),
    ("livreur",              "m", "Lieferant",               "livʁœʁ"),
    ("remboursement",        "m", "Rückerstattung",          "ʁɑ̃buʁsəmɑ̃"),
    ("dédommagement",        "m", "Entschädigung",           "dedɔmaʒmɑ̃"),
    ("service après-vente",  "m", "Kundendienst",            "sɛʁvis apʁɛvɑ̃t"),
    # Verben
    ("acheter",              None, "kaufen",                 "aʃte"),
    ("vendre",               None, "verkaufen",              "vɑ̃dʁ"),
    ("dépenser",             None, "ausgeben",               "depɑ̃se"),
    ("économiser",           None, "sparen",                 "ekɔnɔmize"),
    ("emprunter",            None, "leihen/borgen",          "ɑ̃pʁœ̃te"),
    ("rembourser",           None, "zurückzahlen",           "ʁɑ̃buʁse"),
    ("choisir",              None, "wählen/auswählen",       "ʃwaziʁ"),
    ("comparer",             None, "vergleichen",            "kɔ̃paʁe"),
    ("consommer",            None, "konsumieren",            "kɔ̃sɔme"),
    ("faire la queue",       None, "anstehen",               "fɛʁ la kø"),
    ("faire du shopping",    None, "einkaufen gehen",        "fɛʁ dy ʃɔpiŋ"),
    ("faire les courses",    None, "Besorgungen machen",     "fɛʁ le kuʁs"),
]

# French words that probably already exist in vocab.json
EXISTING_TAGS = [
    "acheter", "vendre", "payer", "coûter", "dépenser", "économiser",
    "emprunter", "choisir", "prix", "argent", "banque", "pharmacie",
    "marché", "carte", "compte", "promotion", "garantie", "facture",
    "livraison", "remboursement", "comparer", "consommer", "boutique",
    "catalogue", "solde", "vendeur",
]

def main():
    vocab_path = os.path.join(ASSETS, 'vocab.json')
    cat_path   = os.path.join(ASSETS, 'categories.json')

    with open(vocab_path, encoding='utf-8') as f:
        words = json.load(f)
    with open(cat_path, encoding='utf-8') as f:
        cats = json.load(f)

    fr_to_word = {w['french']: w for w in words}
    next_rank = max(w['rank'] for w in words) + 1
    cat_ranks = set(cats.get(CAT, []))
    added = []
    tagged = []

    # Tag existing words
    for fr in EXISTING_TAGS:
        if fr in fr_to_word:
            w = fr_to_word[fr]
            existing = w.get('categories', [])
            if CAT not in existing:
                w['categories'] = sorted(existing + [CAT])
                tagged.append(fr)
            cat_ranks.add(w['rank'])

    # Add new words
    for fr, gender, de, ipa in NEW_WORDS:
        if fr in fr_to_word:
            w = fr_to_word[fr]
            existing = w.get('categories', [])
            if CAT not in existing:
                w['categories'] = sorted(existing + [CAT])
                tagged.append(fr)
            cat_ranks.add(w['rank'])
        else:
            entry = {'rank': next_rank, 'french': fr, 'german': de, 'ipa': ipa, 'categories': [CAT]}
            if gender:
                entry['gender'] = gender
                entry['pos'] = f'n{gender}'
            else:
                entry['pos'] = 'vt'
            words.append(entry)
            fr_to_word[fr] = entry
            cat_ranks.add(next_rank)
            added.append((next_rank, fr, de))
            next_rank += 1

    cats[CAT] = sorted(cat_ranks)
    cats = dict(sorted(cats.items()))

    with open(vocab_path, 'w', encoding='utf-8') as f:
        json.dump(words, f, ensure_ascii=False, indent=2)
    with open(cat_path, 'w', encoding='utf-8') as f:
        json.dump(cats, f, ensure_ascii=False, indent=2)

    print(f"Kategorie '{CAT}': {len(cats[CAT])} Wörter")
    print(f"Bestehende getaggt: {len(tagged)} → {tagged}")
    print(f"Neu hinzugefügt: {len(added)}")
    for rank, fr, de in added:
        print(f"  #{rank} {fr:30s} = {de}")

if __name__ == '__main__':
    main()

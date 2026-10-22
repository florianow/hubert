#!/usr/bin/env python3
"""
Builds the Parcours (learning path) for Hubert:
- app/src/main/assets/parcours.json: the units (étapes), A1 → B2
- app/src/main/assets/grammar/<id>.html: the anki_french grammar notes each unit teaches

Each unit = one grammar point (lesson from anki_french/grammar, by jacbz) + a theme of the
day (Hubert vocab category) + the grammar game that practises it (with its focus).
Run from the repo root: python3 scripts/build_parcours.py
"""
import json
import os
import re
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRAMMAR = os.path.join(ROOT, "anki_french", "grammar")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")

# (level, title, theme, [grammar notes], game, focus)
# game: conjugation | preposition | relative_pronoun | agreement | numbers | gender_snap
UNITS = [
    ("A1", "Artikel & Geschlecht", "Familie",
     ["03 Artikel/1 Der bestimmte Artikel", "03 Artikel/2 Der unbestimmte Artikel", "04 Substantive/01 Das Geschlecht der Substantive"],
     "gender_snap", []),
    ("A1", "Das Présent", "Haus und Wohnung",
     ["10 Zeitformen und Modi/01 Présent", "09 Verben/02 Die Verben auf -er"],
     "conjugation", ["present"]),
    ("A1", "Zahlen", "Einkaufen",
     ["22 Zahlen und Zeitangaben/1 Zahlen"],
     "numbers", ["NUMBERS_100", "NUMBERS_1000"]),
    ("A1", "Uhrzeit & Datum", "Zeit",
     ["22 Zahlen und Zeitangaben/5 Zeitangaben", "22 Zahlen und Zeitangaben/4 Datumsangaben"],
     "numbers", ["TIME", "DATES", "DAYS_MONTHS"]),
    ("A1", "Präpositionen des Ortes", "Stadt und Reise",
     ["16 Präpositionen/1 Präpositionen des Ortes"],
     "preposition", ["à", "en", "dans", "sur", "chez", "au", "aux", "de"]),
    ("A1", "Adjektive: Stellung & Angleichung", "Emotionen und Charakter",
     ["05 Adjektive/1 Die Stellung des Adjektivs", "05 Adjektive/3 Adjektive in der Femininform", "05 Adjektive/2 Adjektive im Plural"],
     "gender_snap", []),
    ("A2", "avoir & être", "Kleidung",
     ["09 Verben/07 Die Hilfsverben avoir und être", "09 Verben/03 Die Verben auf -ir"],
     "conjugation", ["present"]),
    ("A2", "Passé composé", "Essen und Trinken",
     ["10 Zeitformen und Modi/04 Passé composé"],
     "conjugation", ["passe_compose", "present"]),
    ("A2", "Passé composé mit être", "Transport",
     ["11 Partizip/3 Participe passé"],
     "agreement", ["ETRE"]),
    ("A2", "Der Imperativ", "Körper und Gesundheit",
     ["10 Zeitformen und Modi/10 Impératif"],
     "conjugation", ["imperatif", "present"]),
    ("A2", "Präpositionen der Zeit", "Sport",
     ["16 Präpositionen/2 Präpositionen der Zeit"],
     "preposition", ["avant", "après", "depuis", "pendant", "en", "dans"]),
    ("A2", "Relativsätze mit qui & que", "Gesellschaft",
     ["14 Relativsätze/1 Relativsätze mit qui", "14 Relativsätze/2 Relativsätze mit que"],
     "relative_pronoun", ["qui", "que"]),
    ("A2", "Reflexive Verben", "Status und Beziehungen",
     ["09 Verben/09 Reflexive Verben", "07 Pronomen/05 Die Reflexivpronomen"],
     "agreement", ["REFLEXIVE", "ETRE"]),
    ("A2", "Infinitiv mit à und de", "Bildung und Wissenschaft",
     ["13 Ergänzung des Verbs/11 Der Infinitiv mit à", "13 Ergänzung des Verbs/12 Der Infinitiv mit de"],
     "preposition", ["à", "de"]),
    ("A2", "Modale Präpositionen", "Technologie und Medien",
     ["16 Präpositionen/3 Modale Präpositionen"],
     "preposition", ["avec", "sans", "pour", "par", "contre", "selon"]),
    ("A2", "Ordnungszahlen & große Zahlen", "Geld und Finanzen",
     ["22 Zahlen und Zeitangaben/2 Ordnungszahlen", "22 Zahlen und Zeitangaben/3 Bruchzahlen"],
     "numbers", ["NUMBERS_BIG", "DATES"]),
    ("B1", "Imparfait", "Natur",
     ["10 Zeitformen und Modi/06 Imparfait"],
     "conjugation", ["imparfait", "passe_compose"]),
    ("B1", "Futur simple", "Wetter",
     ["10 Zeitformen und Modi/08 Futur simple"],
     "conjugation", ["futur", "present"]),
    ("B1", "Conditionnel & si-Sätze", "Kunst und Kultur",
     ["10 Zeitformen und Modi/11 Conditionnel", "15 Bedingungssätze/1 Bedingungssätze mit si"],
     "conjugation", ["conditionnel", "imparfait", "futur"]),
    ("B1", "Relativsätze mit dont & où", "Recht",
     ["14 Relativsätze/3 Relativsätze mit dont", "14 Relativsätze/5 Relativsätze mit ou"],
     "relative_pronoun", ["qui", "que", "dont", "où"]),
    ("B1", "Verben mit à und de", "Kommunikationsverben",
     ["13 Ergänzung des Verbs/03 Verben mit à", "13 Ergänzung des Verbs/04 Verben mit de", "13 Ergänzung des Verbs/05 Verben mit à und de"],
     "preposition", ["à", "de", "au", "aux", "du", "des"]),
    ("B1", "Angleichung mit avoir", "Arbeit und Wirtschaft",
     ["11 Partizip/3 Participe passé"],
     "agreement", ["AVOIR"]),
    ("B1", "Verben mit sur, pour, contre, en", "Politik und Staat",
     ["13 Ergänzung des Verbs/06 Verben mit pour", "13 Ergänzung des Verbs/07 Verben mit contre", "13 Ergänzung des Verbs/08 Verben mit sur", "13 Ergänzung des Verbs/09 Verben mit en"],
     "preposition", ["sur", "pour", "contre", "en"]),
    ("B2", "Subjonctif", "Denken und Wissen",
     ["10 Zeitformen und Modi/12 Subjonctif"],
     "conjugation", ["subjonctif", "mood_question"]),
    ("B2", "ce qui, ce que, ce dont", "Berufe",
     ["14 Relativsätze/6 Relativsätze mit ce que"],
     "relative_pronoun", ["ce qui", "ce que", "ce dont", "qui", "que"]),
    ("B2", "lequel & Co.", "Bildung und Wissenschaft",
     ["14 Relativsätze/4 Relativsätze mit lequel"],
     "relative_pronoun", ["lequel", "laquelle", "lesquels", "lesquelles", "auquel", "auxquels", "duquel", "desquels"]),
    ("B2", "Zeitenfolge (indirekte Rede)", "Kommunikationsverben",
     ["20 Indirekte Rede/1 Die indirekte Rede", "20 Indirekte Rede/3 Die Zeitenfolge in der indirekten Rede"],
     "conjugation", ["imparfait", "conditionnel", "passe_compose"]),
]

MIN_NOUNS = 12      # MenuDuJourViewModel.MIN_CATEGORY_NOUNS
MIN_SENTENCES = 12  # MenuDuJourViewModel.MIN_CATEGORY_SENTENCES


def eligible_themes():
    vocab = {w["rank"]: w for w in json.load(open(os.path.join(ASSETS, "vocab.json")))}
    sentences = {int(k) for k in json.load(open(os.path.join(ASSETS, "sentences.json")))}
    cats = json.load(open(os.path.join(ASSETS, "categories.json")))
    ok = set()
    for cat, ranks in cats.items():
        nouns = sum(1 for r in ranks if vocab.get(r, {}).get("gender") and vocab[r].get("pos") in ("nm", "nf"))
        sent = sum(1 for r in ranks if r in sentences)
        if nouns >= MIN_NOUNS and sent >= MIN_SENTENCES:
            ok.add(cat)
    return ok


def slug(path):
    name = path.split("/")[-1]
    name = re.sub(r"^\d+\s+", "", name).lower()
    for a, b in (("ä", "ae"), ("ö", "oe"), ("ü", "ue"), ("ß", "ss"), ("é", "e"), ("è", "e"), ("à", "a"), ("ç", "c")):
        name = name.replace(a, b)
    return re.sub(r"[^a-z0-9]+", "_", name).strip("_")


def main():
    themes = eligible_themes()
    out_dir = os.path.join(ASSETS, "grammar")
    shutil.rmtree(out_dir, ignore_errors=True)
    os.makedirs(out_dir)
    units = []
    for index, (level, title, theme, notes, game, focus) in enumerate(UNITS, start=1):
        if theme not in themes:
            raise SystemExit(f"Theme '{theme}' of unit {index} has too few nouns/sentences for a menu")
        lessons = []
        for note in notes:
            src = os.path.join(GRAMMAR, note + ".html")
            html = open(src, encoding="utf-8").read()
            note_title = re.search(r'class="section-title"[^>]*>(.*?)</div>', html, re.S).group(1)
            note_title = re.sub(r"<[^>]+>", "", note_title).strip()
            file = slug(note) + ".html"
            shutil.copyfile(src, os.path.join(out_dir, file))
            lessons.append({"title": note_title, "file": "grammar/" + file})
        units.append({
            "id": index,
            "level": level,
            "title": title,
            "theme": theme,
            "lessons": lessons,
            "game": game,
            "focus": focus,
        })
    with open(os.path.join(ASSETS, "parcours.json"), "w", encoding="utf-8") as f:
        json.dump(units, f, ensure_ascii=False, indent=1)
    print(f"{len(units)} units, {len(os.listdir(out_dir))} lessons")


if __name__ == "__main__":
    main()

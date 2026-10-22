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

# Parlez! per unit (same order as UNITS): theme, situation, Hubert's role, opening line and
# the grammar goal Hubert steers the conversation towards (French, goes into the prompt)
PARLEZ = [
    ("Ma famille", "Stell deine Familie vor: Wer gehört dazu, was machen sie?", "",
     "Salut ! Parle-moi un peu de ta famille. Tu as des frères et sœurs ?",
     "Fais parler le joueur de personnes et de choses avec beaucoup de noms et leur article (le, la, un, une, les)."),
    ("Chez moi", "Beschreibe deine Wohnung und was du dort jeden Tag machst.", "",
     "Bonjour ! Tu habites où ? Décris-moi ton appartement !",
     "Pose des questions sur les habitudes quotidiennes pour que le joueur utilise le présent (je fais, je prends, je vais)."),
    ("Au marché", "Du kaufst auf dem Markt ein: Mengen und Preise.", "Tu es marchand(e) de fruits et légumes au marché.",
     "Bonjour ! Qu'est-ce qu'il vous faut aujourd'hui ? Les tomates sont à trois euros le kilo.",
     "Parle de quantités et de prix pour que le joueur utilise beaucoup de nombres."),
    ("Prendre rendez-vous", "Vereinbare einen Arzttermin: Tag, Datum, Uhrzeit.", "Tu es la secrétaire d'un cabinet médical.",
     "Cabinet du docteur Martin, bonjour ! Vous voulez prendre rendez-vous ?",
     "Demande le jour, la date et l'heure ; propose des créneaux (le mardi douze, à quinze heures trente)."),
    ("Demander le chemin", "Du suchst in einer fremden Stadt den Weg.", "Tu es un passant sympathique qui connaît bien la ville.",
     "Bonjour ! Vous cherchez quelque chose ? Je connais bien le quartier.",
     "Fais utiliser les prépositions de lieu : à, en, dans, sur, chez, au (aller à la gare, en France, chez le médecin)."),
    ("Décrire quelqu'un", "Beschreibe Menschen, die dir wichtig sind: Aussehen und Charakter.", "",
     "Dis-moi, comment est ton meilleur ami ? Il est comment ?",
     "Fais décrire des personnes avec des adjectifs (accord masculin/féminin et pluriel, place de l'adjectif)."),
    ("Dans une boutique", "Du kaufst Kleidung: Größe, Farbe, Anprobe.", "Tu es vendeur / vendeuse dans une boutique de vêtements.",
     "Bonjour ! Vous cherchez quelque chose de particulier ? Vous faites quelle taille ?",
     "Fais utiliser avoir et être (j'ai besoin de, je suis grand, c'est trop petit, j'ai froid)."),
    ("Le repas d'hier", "Erzähl, was du gestern gegessen und getrunken hast.", "",
     "Salut ! Qu'est-ce que tu as mangé hier soir ?",
     "Pose des questions sur le passé pour que le joueur utilise le passé composé avec avoir (j'ai mangé, j'ai bu, j'ai pris)."),
    ("Mon dernier voyage", "Erzähl von deiner letzten Reise: Wohin, wie, wann angekommen?", "",
     "Alors, tu es parti en voyage récemment ? Tu es allé où ?",
     "Fais raconter un trajet au passé composé avec être (je suis parti, je suis arrivé, nous sommes allés) avec l'accord du participe."),
    ("Un ami malade", "Hubert ist krank. Gib ihm Ratschläge – im Imperativ.", "Tu es un ami malade qui demande des conseils.",
     "Oh là là, j'ai mal à la tête et je tousse depuis hier… Qu'est-ce que je dois faire ?",
     "Demande des conseils pour que le joueur utilise l'impératif (bois, reste au lit, prends un médicament)."),
    ("Le sport et moi", "Erzähl, welchen Sport du machst – seit wann, wie lange, wann.", "",
     "Tu fais du sport ? Depuis quand ?",
     "Fais utiliser les prépositions de temps : depuis, pendant, avant, après, en, dans (depuis deux ans, pendant une heure)."),
    ("Les gens que j'admire", "Erzähl von Menschen, die du bewunderst.", "",
     "Parle-moi d'une personne que tu admires. C'est quelqu'un qui fait quoi ?",
     "Pousse le joueur à faire des phrases relatives avec qui et que (une amie qui…, le livre que…)."),
    ("Ma routine", "Dein Tagesablauf und wie du mit anderen auskommst.", "",
     "À quelle heure tu te lèves le matin ? Et tu t'entends bien avec tes collègues ?",
     "Fais utiliser des verbes pronominaux (se lever, se dépêcher, s'entendre, se rencontrer), au présent et au passé composé."),
    ("Apprendre", "Was willst du lernen, was hast du angefangen, was ist dir gelungen?", "",
     "Qu'est-ce que tu as envie d'apprendre cette année ? Tu as déjà commencé ?",
     "Fais utiliser des verbes + à/de + infinitif (commencer à, essayer de, avoir envie de, réussir à, arrêter de)."),
    ("Mon téléphone et moi", "Wofür nutzt du Handy und Internet – und ginge es ohne?", "",
     "Tu pourrais vivre sans ton téléphone ? Tu l'utilises pour quoi ?",
     "Fais utiliser avec, sans, pour, par, contre, selon."),
    ("Mon budget", "Rede über Ausgaben, Preise und Anteile deines Budgets.", "",
     "Combien tu dépenses par mois pour le logement ? C'est le premier poste de ton budget ?",
     "Fais utiliser de grands nombres, des fractions et des ordinaux (le premier, la moitié, un tiers, mille deux cents euros)."),
    ("Quand j'étais petit", "Erzähl von deiner Kindheit: Wie war es, was hast du immer gemacht?", "",
     "Quand tu étais enfant, tu passais tes vacances où ? C'était comment ?",
     "Fais décrire des souvenirs et des habitudes passées à l'imparfait (j'allais, il faisait beau, on jouait)."),
    ("Mes projets", "Wetter fürs Wochenende und deine Pläne für die Zukunft.", "",
     "Quel temps fera-t-il ce week-end chez toi ? Et qu'est-ce que tu feras ?",
     "Fais parler de l'avenir avec le futur simple (il fera beau, je partirai, nous verrons)."),
    ("Si j'étais artiste", "Was würdest du tun, wenn …? Hypothesen rund um Kunst und Kultur.", "",
     "Si tu avais beaucoup de temps libre, quel art est-ce que tu pratiquerais ?",
     "Pose des questions hypothétiques pour faire utiliser si + imparfait et le conditionnel (si j'avais…, je ferais…)."),
    ("Un fait divers", "Erzähl von einem Fall aus den Nachrichten: Wo, wer, worum ging es?", "",
     "Tu as entendu parler de cette affaire dont tout le monde parle ? Raconte-moi ce que tu sais.",
     "Pousse le joueur à utiliser dont et où (la ville où…, l'homme dont on parle…)."),
    ("Une conversation importante", "Erzähl von einem wichtigen Gespräch: mit wem, worüber, worum gebeten?", "",
     "Tu te souviens de la dernière conversation importante que tu as eue ? Tu as parlé à qui ?",
     "Fais utiliser des verbes avec à et de (parler à/de, demander à, se souvenir de, répondre à, s'occuper de)."),
    ("Ma semaine de travail", "Was hast du diese Woche erledigt – und was ist daraus geworden?", "",
     "Les dossiers que tu as traités cette semaine, ils étaient compliqués ?",
     "Fais utiliser le passé composé avec un complément placé avant (la lettre que j'ai écrite, je les ai finies)."),
    ("Un débat", "Bist du dafür oder dagegen? Diskutiere über Politik.", "",
     "Tu es pour ou contre le vote à seize ans ? Pourquoi ?",
     "Fais utiliser être pour/contre, compter sur, croire en, voter pour, en avoir assez."),
    ("Opinions et souhaits", "Was muss passieren, was wünschst du dir, woran zweifelst du?", "",
     "Qu'est-ce qu'il faut que tu fasses cette semaine ? Et qu'est-ce que tu veux que je fasse pour toi ?",
     "Provoque le subjonctif (il faut que, je veux que, bien que, je doute que, avant que)."),
    ("Mon métier idéal", "Was zählt für dich im Beruf, was brauchst du, was gefällt dir?", "",
     "Ce qui compte le plus pour toi dans un travail, c'est quoi ?",
     "Fais utiliser ce qui, ce que, ce dont (ce qui me plaît, ce dont j'ai besoin)."),
    ("Mes études", "Erzähl von Schule, Studium und den Fächern, für die du brennst.", "",
     "Quelle est la matière pour laquelle tu étais le plus doué à l'école ?",
     "Fais utiliser lequel, laquelle, auquel, duquel après une préposition (la raison pour laquelle, le projet auquel je pense)."),
    ("Rapporter une conversation", "Gib wieder, was jemand zu dir gesagt oder dich gefragt hat.", "",
     "Qu'est-ce que ton chef t'a dit à la dernière réunion ? Il a dit qu'il était content ?",
     "Fais rapporter des paroles au discours indirect (il a dit que…, il m'a demandé si…) avec la concordance des temps."),
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
    if len(PARLEZ) != len(UNITS):
        raise SystemExit(f"PARLEZ has {len(PARLEZ)} entries for {len(UNITS)} units")
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
            "parlez": dict(zip(("themeFr", "scenarioDe", "hubertRoleFr", "starterFr", "grammarFr"), PARLEZ[index - 1])),
        })
    with open(os.path.join(ASSETS, "parcours.json"), "w", encoding="utf-8") as f:
        json.dump(units, f, ensure_ascii=False, indent=1)
    print(f"{len(units)} units, {len(os.listdir(out_dir))} lessons")


if __name__ == "__main__":
    main()

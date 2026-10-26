"""
Exercises for the Parcours étapes (used by build_parcours.py).

Every exercise is a gap with choices: a sentence with "___", the right answer and three wrong
ones that test exactly the étape's grammar (other tenses of the same verb, the other
prepositions of the étape, the other forms of the adjective, …).

Sources, in this order:
1. the example sentences of the étape's lessons — the anki_french grammar notes underline
   the grammar point (<u>…</u>), which becomes the gap;
2. Hubert's pools for the same grammar: conjugation sentences (conjugations.json),
   prepositions.json, relative_pronouns.json, nouns with their gender (vocab.json).
Numbers and participle agreement are filled up in the app by the Nombrez!/Accordez! generators.
Distractor word forms must occur in the example corpus, so no invented words are offered.
"""
import html as htmllib
import json
import os
import random
import re

MAX_EXERCISES = 40
MIN_EXERCISES = 25

TENSE_DE = {
    "present": "Présent", "imparfait": "Imparfait", "futur": "Futur simple",
    "conditionnel": "Conditionnel", "subjonctif": "Subjonctif", "passe_compose": "Passé composé",
    "imperatif": "Impératif", "passe_simple": "Passé simple",
}
SIMPLE_TENSES = ["present", "imparfait", "futur", "conditionnel", "subjonctif"]
PERSONS = ["je", "tu", "il/elle", "nous", "vous", "ils/elles"]
AVOIR = ["ai", "as", "a", "avons", "avez", "ont"]
ETRE = ["suis", "es", "est", "sommes", "êtes", "sont"]

DEFINITE = ["le", "la", "l'", "les"]
INDEFINITE = ["un", "une", "des"]
PREPOSITIONS = [
    "à", "au", "aux", "de", "du", "des", "d'", "en", "dans", "sur", "sous", "chez", "par", "pour",
    "avec", "sans", "contre", "selon", "avant", "après", "depuis", "pendant", "il y a", "vers",
    "entre", "devant", "derrière", "jusqu'à", "dès",
]
RELATIVES = [
    "qui", "que", "qu'", "dont", "où", "ce qui", "ce que", "ce qu'", "ce dont", "lequel", "laquelle",
    "lesquels", "lesquelles", "auquel", "auxquels", "auxquelles", "à laquelle", "duquel", "desquels",
    "de laquelle",
]
# endings (m, f, m pl, f pl), longest first
ADJ_PATTERNS = [
    ("eau", "elle", "eaux", "elles"), ("eux", "euse", "eux", "euses"), ("if", "ive", "ifs", "ives"),
    ("er", "ère", "ers", "ères"), ("el", "elle", "els", "elles"), ("en", "enne", "ens", "ennes"),
    ("on", "onne", "ons", "onnes"), ("al", "ale", "aux", "ales"), ("e", "e", "es", "es"),
    ("", "e", "s", "es"),
]


def kind_of(unit):
    game, title = unit["game"], unit["title"]
    if game == "gender_snap":
        return "article" if title.startswith("Artikel") else "adjective"
    return {
        "conjugation": "conjugation", "preposition": "preposition", "relative_pronoun": "relative",
        "agreement": "agreement", "numbers": "numbers",
    }[game]


def clean(text):
    text = re.sub(r"<br\s*/?>", " ", text)
    text = re.sub(r"<(?!/?u\b)[^>]+>", "", text)
    return re.sub(r"\s+", " ", htmllib.unescape(text)).strip()


def strip_u(text):
    return re.sub(r"</?u[^>]*>", "", text)


WORD = "[\\wàâäçéèêëîïôöùûüœæÀÂÇÉÈÊÎÔÛ'-]"


def lesson_examples(html):
    """(fr with <u>, de, wrong answers) of a grammar note; the "wrong" example sentences right
    after a right one (crossed out in the lesson) give their underlined part as a distractor."""
    out = []
    title = re.search(r'class="section-title"[^>]*>(.*?)</div>', html, re.S)
    title = "📖 " + re.sub(r"<[^>]+>", "", title.group(1)).strip() if title else ""
    for m in re.finditer(r'<div class="(fr[^"]*)"[^>]*>(.*?)</div>(?:\s*<div class="de spoiler"[^>]*>(.*?)</div>)?', html, re.S):
        cls, fr, de = m.group(1), clean(m.group(2)), strip_u(clean(m.group(3) or ""))
        if "wrong" in cls.split():
            if out:
                out[-1][2].extend(a for _, a in gaps(fr))
            continue
        if de:
            out.append((fr, de, [], title))
    return out


def gaps(fr):
    """Every underlined part as its own gap: (sentence with ___, answer). An underline inside a
    word (vendu<u>e</u>) takes the whole word."""
    plain_parts = list(re.finditer(r"<u[^>]*>(.*?)</u>", fr))
    for m in plain_parts:
        before, after = strip_u(fr[:m.start()]), strip_u(fr[m.end():])
        inner = strip_u(m.group(1))
        left = re.search(WORD.replace("'", "") + "*$", before).group(0)
        right = re.match(WORD.replace("'", "") + "*", after).group(0)
        answer = (left + inner + right).strip()
        yield before[: len(before) - len(left)] + "___" + after[len(right):], answer


class Corpus:
    def __init__(self, assets):
        examples = json.load(open(os.path.join(assets, "examples.json"), encoding="utf-8"))
        words = set()
        for items in examples.values():
            for e in items:
                words.update(re.findall(r"[a-zàâäçéèêëîïôöùûüœæ'-]+", e["fr"].lower().replace("*", "")))
        self.words = words
        self.conj = json.load(open(os.path.join(assets, "conjugations.json"), encoding="utf-8"))
        self.conj.sort(key=lambda v: v["rank"])
        self.forms = {}  # form → [(verb, tense, person)]
        for v in self.conj:
            for tense, forms in v["tenses"].items():
                for person, form in enumerate(forms):
                    if form:
                        self.forms.setdefault(form, []).append((v, tense, person))
        self.prepositions = json.load(open(os.path.join(assets, "prepositions.json"), encoding="utf-8"))
        self.relatives = json.load(open(os.path.join(assets, "relative_pronouns.json"), encoding="utf-8"))
        self.vocab = json.load(open(os.path.join(assets, "vocab.json"), encoding="utf-8"))

    def known(self, word):
        return word.lower() in self.words


def exercise(fr, de, answer, wrong, rule, source):
    wrong = [w for w in dict.fromkeys(wrong) if w and w != answer][:3]
    if len(wrong) < 2 or "___" not in fr:
        return None
    return {"fr": fr, "de": de, "answer": answer, "wrong": wrong, "rule": rule, "source": source}


# ---- distractors per grammar point ---------------------------------------------------------

def pick(options, answer, rng, n=3):
    rest = [o for o in options if o != answer]
    rng.shuffle(rest)
    return rest[:n]


def adjective_forms(word, corpus):
    for m, f, mp, fp in ADJ_PATTERNS:
        for own in (m, f, mp, fp):
            if own and not word.endswith(own):
                continue
            base = word[: len(word) - len(own)] if own else word
            forms = [base + e for e in (m, f, mp, fp)]
            if word in forms:
                known = [x for x in dict.fromkeys(forms) if x != word and corpus.known(x)]
                if len(known) >= 2:
                    return known
    return []


def verb_wrong(answer, focus, corpus, rng):
    """Wrong forms for a conjugated verb from a lesson: same verb and person, other tenses."""
    words = answer.split()
    if len(words) == 1:
        hits = corpus.forms.get(answer, [])
        order = ["present", "imparfait", "futur", "conditionnel", "subjonctif", "imperatif", "passe_simple"]
        hits.sort(key=lambda h: (focus.index(h[1]) if h[1] in focus else 99, order.index(h[1]) if h[1] in order else 9, h[0]["rank"]))
        for verb, tense, person in hits:
            if tense == "passe_compose":
                continue
            others = [t for t in focus if t != tense and t in SIMPLE_TENSES + ["imperatif"]]
            others += [t for t in SIMPLE_TENSES if t not in others and t != tense]
            wrong = [verb["tenses"][t][person] for t in others if verb["tenses"].get(t) and verb["tenses"][t][person]]
            wrong = [w for w in dict.fromkeys(wrong) if w != answer]
            if len(wrong) >= 2:
                return wrong[:3], f"{TENSE_DE.get(tense, tense)} von {verb['infinitive']}"
        return None
    if len(words) == 2 and words[0] in AVOIR + ETRE:
        aux, participle = words
        person = (AVOIR if aux in AVOIR else ETRE).index(aux)
        swapped = (ETRE if aux in AVOIR else AVOIR)[person]
        hits = []
        for base in (participle, participle[:-1], participle[:-2]):
            hits = [h for h in corpus.forms.get(base, []) if h[1] == "passe_compose"]
            if hits:
                break
        verb = hits[0][0] if hits else None
        wrong = [f"{swapped} {participle}"]
        if verb:
            wrong.append(f"{aux} {verb['infinitive']}")
            imparfait = verb["tenses"].get("imparfait")
            if imparfait and imparfait[person]:
                wrong.append(imparfait[person])
        wrong += [f"{aux} {v}" for v in adjective_forms(participle, corpus)]
        rule = f"Passé composé von {verb['infinitive']} mit {'être' if aux in ETRE else 'avoir'}" if verb else "Passé composé"
        return wrong[:3], rule
    return None


def is_participle(word, corpus):
    return any(h[1] == "passe_compose" for base in (word, word[:-1], word[:-2]) for h in corpus.forms.get(base, []))


def participle_wrong(answer, corpus):
    words = answer.split()
    if len(words) < 2 or not is_participle(words[-1].lower(), corpus):
        return None
    head, participle = " ".join(words[:-1]), words[-1]
    variants = adjective_forms(participle, corpus)
    wrong = [f"{head} {v}" for v in variants]
    last_aux = words[-2]
    if last_aux in AVOIR + ETRE and not head.startswith(("s'", "se ", "me ", "m'", "te ", "t'", "nous ", "vous ")):
        person = (AVOIR if last_aux in AVOIR else ETRE).index(last_aux)
        swapped = (ETRE if last_aux in AVOIR else AVOIR)[person]
        wrong.insert(0, " ".join(words[:-2] + [swapped, participle]))
    return wrong if len(wrong) >= 2 else None


# ---- per étape ----------------------------------------------------------------------------

def from_lessons(unit, kind, examples, corpus, rng):
    out = []
    focus = unit["focus"]
    for fr_u, de, lesson_wrong, lesson_title in examples:
        for fr, answer in gaps(fr_u):
            if not answer or "(" in answer or len(answer) > 30 or len(answer) < 2:
                continue
            low = answer.lower()
            # Verb + preposition (continue à, se prononcer sur): the gap is the preposition
            if kind == "preposition" and " " in low and low.split()[-1] in PREPOSITIONS:
                verb = answer[: answer.rindex(" ")]
                fr = fr.replace("___", verb + " ___", 1)
                lesson_wrong = [w.split()[-1] for w in lesson_wrong if " " in w]
                answer = answer.split()[-1]
                low = answer.lower()
            item = None
            if kind == "article" and low in DEFINITE + INDEFINITE:
                group = DEFINITE if low in DEFINITE else INDEFINITE + ["de"]
                item = exercise(fr, de, answer, pick(group, low, rng), lesson_title, "lesson")
            elif kind == "adjective" and " " not in answer:
                wrong = lesson_wrong + adjective_forms(low, corpus)
                if wrong:
                    item = exercise(fr, de, answer, wrong, lesson_title, "lesson")
            elif kind == "preposition" and low in PREPOSITIONS:
                near = ["à", "de", "pour", "en", "par", "sur", "dans", "avec", "chez", "sans"]
                pool = [p for p in focus if p != low] + [p for p in near if p not in focus]
                lesson_wrong = [w for w in lesson_wrong if w.lower() in PREPOSITIONS + ["comme"]]
                wrong = lesson_wrong + pick(pool[:max(2, len(focus) - 1)], low, rng, 2) + pick(pool[len(focus):], low, rng, 2)
                item = exercise(fr, de, answer, wrong, lesson_title, "lesson")
            elif kind == "relative" and low in RELATIVES:
                pool = [p for p in focus if p != low] + ["qui", "que", "dont", "où"]
                item = exercise(fr, de, answer, pick(list(dict.fromkeys(pool)), low, rng), lesson_title, "lesson")
            elif kind == "conjugation":
                found = verb_wrong(low, focus, corpus, rng)
                if found:
                    wrong = found[0]
                    if answer[0].isupper():
                        wrong = [w[0].upper() + w[1:] for w in wrong]
                    item = exercise(fr, de, answer, lesson_wrong + wrong, found[1], "lesson")
            elif kind == "agreement":
                wrong = participle_wrong(answer, corpus) if " " in answer else (
                    adjective_forms(low, corpus) if is_participle(low, corpus) else None)
                if wrong and answer[0].isupper():
                    wrong = [w[0].upper() + w[1:] for w in wrong]
                if wrong:
                    wrong = lesson_wrong + wrong
                if wrong:
                    item = exercise(fr, de, answer, wrong, lesson_title, "lesson")
            if item:
                out.append(item)
    return out


def blank_sentence(fr, word):
    m = re.search(r"(?<![\wàâäçéèêëîïôöùûüœæ])" + re.escape(word) + r"(?![\wàâäçéèêëîïôöùûüœæ])", fr)
    return fr[:m.start()] + "___" + fr[m.end():] if m else None


# Verbs an étape's pool is limited to (the lesson is about them)
POOL_VERBS = {
    "Das Présent": lambda v: v["infinitive"].endswith("er") and v["infinitive"] != "aller",
    "avoir & être": lambda v: v["infinitive"] in ("avoir", "être") or (v["infinitive"].endswith("ir") and v["rank"] < 1500),
}
SUBJECT_PERSON = [("je ", 0), ("j'", 0), ("tu ", 1), ("nous ", 3), ("vous ", 4)]


CLITICS = ("me", "te", "se", "le", "la", "les", "lui", "leur", "y", "en", "ne", "nous", "vous")


def guess_person(before, answer, forms):
    """Person of the gap from the forms that match and the subject pronoun in front of it."""
    candidates = [i for i, f in enumerate(forms) if f == answer]
    if len(candidates) == 1:
        return candidates[0]
    words = re.findall(r"[a-zàâäçéèêëîïôöùûüœæ]+'?", before.lower())
    while words:
        last = words[-1]
        for pronoun, person in (("je", 0), ("j'", 0), ("tu", 1), ("nous", 3), ("vous", 4)):
            if last == pronoun and person in candidates:
                return person
        if last.rstrip("'") in CLITICS or last in ("m'", "t'", "s'", "l'", "n'"):
            words.pop()
            continue
        break
    for person in (2, 5, 0, 1, 3, 4):
        if person in candidates:
            return person
    return None


def conjugation_pool(unit, corpus, rng, need):
    tenses = [t for t in unit["focus"] if t in TENSE_DE]
    allowed = POOL_VERBS.get(unit["title"], lambda v: True)
    by_tense = {}
    for tense in tenses:
        for verb in corpus.conj[:800]:
            if not allowed(verb):
                continue
            t = verb["tenses"]
            for _, s in sorted((verb.get("sentences") or {}).get(tense, {}).items()):
                answer = s["blank"]
                fr = blank_sentence(s["fr"], answer)
                if not fr or tense not in t:
                    continue
                before = fr[: fr.index("___")]
                if tense == "imperatif" and before.strip(" \"«-").lower() not in ("", "ne", "n'"):
                    continue  # an imperative starts the sentence
                if tense == "subjonctif" and not re.search(r"\bqu(e\b|')", before.lower()):
                    continue  # only clear subjonctif contexts
                if tense == "passe_compose":
                    person = 2
                    aux = before.split()[-1].lower() if before.split() else ""
                    for forms in (AVOIR, ETRE):
                        if aux in forms:
                            person = forms.index(aux)
                    if "present" not in t or "imparfait" not in t:
                        continue
                    wrong = [verb["infinitive"], t["present"][person], t["imparfait"][person]]
                elif tense == "imperatif":
                    if "present" not in t or "subjonctif" not in t:
                        continue
                    person = guess_person(before, answer, t[tense])
                    if person is None:
                        continue
                    wrong = [t["present"][person], verb["infinitive"], t["subjonctif"][person]]
                else:
                    person = guess_person(before, answer, t[tense])
                    if person is None:
                        continue
                    others = [o for o in tenses + SIMPLE_TENSES if o != tense and o in t and o not in ("imperatif",)]
                    wrong = [t[o][person] for o in dict.fromkeys(others)]
                    if tense == "subjonctif" and t["present"][person] == answer:
                        continue  # same form as the présent: not telling
                rule = f"{TENSE_DE[tense]} von {verb['infinitive']} ({PERSONS[person]})"
                if fr[:3] == "___" and answer[:1].islower():
                    answer, wrong = answer[0].upper() + answer[1:], [w[:1].upper() + w[1:] for w in wrong]
                item = exercise(fr, s["de"], answer, wrong, rule, "pool")
                if item:
                    by_tense.setdefault(tense, []).append(item)
                break  # one sentence per verb and tense
    for items in by_tense.values():
        rng.shuffle(items)
    # The étape's main tense gets two thirds, the others share the rest
    mixed = []
    main, others = tenses[0], tenses[1:]
    # Contrast tenses (e.g. the présent next to the passé composé) only where the étape is about
    # choosing between several tenses
    main_share = need if len(tenses) < 3 else need // 2
    mixed += by_tense.get(main, [])[:main_share]
    i = 0
    while len(mixed) < need and any(by_tense.get(o) for o in others):
        o = others[i % len(others)]
        if by_tense.get(o):
            mixed.append(by_tense[o].pop())
        i += 1
    mixed += by_tense.get(main, [])[main_share:main_share + need - len(mixed)]
    rng.shuffle(mixed)
    return mixed


def answer_pool(items, focus, rng, need):
    chosen = [i for i in items if i["answer"] in focus]
    rng.shuffle(chosen)
    out = []
    for i in chosen[:need]:
        item = exercise(i["fr"], i["de"], i["answer"], i["distractors"], i.get("explanation") or "", "pool")
        if item:
            out.append(item)
    return out


def article_pool(unit, corpus, rng, need):
    out = []
    nouns = [w for w in corpus.vocab if w.get("gender") in ("m", "f") and w.get("pos") in ("nm", "nf")
             and " " not in w["french"] and not w["french"][0].lower() in "h"]
    for w in nouns[:400]:
        noun = w["french"]
        vowel = noun[0].lower() in "aeiouéèêâîôû"
        answer = "l'" if vowel else ("le" if w["gender"] == "m" else "la")
        fr = f"___{noun}" if vowel else f"___ {noun}"
        rule = f"{noun} ist {'männlich' if w['gender'] == 'm' else 'weiblich'}" + (" — vor Vokal: l'" if vowel else "")
        item = exercise(fr, w["german"], answer, pick(DEFINITE, answer, rng), rule, "pool")
        if item:
            out.append(item)
    rng.shuffle(out)
    return out[:need]


def build(unit, lesson_htmls, corpus, seed):
    rng = random.Random(seed)
    kind = kind_of(unit)
    examples = [e for html in lesson_htmls for e in lesson_examples(html)]
    items = from_lessons(unit, kind, examples, corpus, rng)
    seen = set()
    items = [i for i in items if not (i["fr"], i["answer"]) in seen and not seen.add((i["fr"], i["answer"]))]
    need = MAX_EXERCISES - len(items)
    if need > 0:
        if kind == "conjugation":
            items += conjugation_pool(unit, corpus, rng, need)
        elif kind == "preposition":
            items += answer_pool(corpus.prepositions, unit["focus"], rng, need)
        elif kind == "relative":
            items += answer_pool(corpus.relatives, unit["focus"], rng, need)
        elif kind == "article":
            items += article_pool(unit, corpus, rng, need)
    return kind, items[:MAX_EXERCISES]

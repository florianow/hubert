# Game Ideas for Hubert

Future game mode ideas for the French-German vocabulary learning app.

## Data Sources

The app currently has 4 data files in `app/src/main/assets/`:

| File | Content | Size |
|------|---------|------|
| `vocab.json` | 5,000 words (rank, french, german, ipa, pos, gender, categories) | 548 KB |
| `sentences.json` | 9,940 example sentences keyed by word rank (fr, de, blank) | 1.45 MB |
| `conjugations.json` | 1,182 verbs with 8-tense conjugation tables + 15,173 matched sentences | 3.16 MB |
| `categories.json` | Thematic category → word rank mappings | 12 KB |

All sentence blanks target the **vocabulary word**, not grammar elements. Most game
ideas below would require re-blanking or entirely new datasets.

## Feasibility Summary

| # | Game | Data Status | Effort |
|---|------|-------------|--------|
| 1 | Relativisez! | Needs new dataset | High |
| 2 | Démontrez! | Partially sufficient (simple forms only) | Medium |
| 3 | Possédez! | Needs new dataset | High |
| 4 | **Préposez!** | **Sufficient (~4,500 sentences)** | **Low** |
| 5 | Partagez! | Partially sufficient (needs disambiguation) | Medium |
| 6 | **Subjonctiez!** | **Sufficient (1,182 verbs + conjugation tables)** | **Low** |
| 7 | Remplacez! | Partially sufficient (~1,268 sentences, noisy) | Medium |
| 8 | **Accordez!** | **Sufficient (42 être verbs + 6,492 PC sentences)** | **Low** |
| 9 | Négativez! | Sufficient for common patterns, scarce for rare ones | Medium |
| 10 | Déjouez! | Needs new dataset | High |

---

## 1. Relativisez! — Pronoms relatifs

Fill in the correct relative pronoun: **qui, que, dont, où, lequel, laquelle, lesquels, lesquelles, auquel, auxquels, duquel, desquels**.

**Example questions:**
- La fille ___ parle est ma sœur. → **qui**
- Le livre ___ j'ai lu est intéressant. → **que**
- L'homme ___ je parle est mon prof. → **dont**
- La ville ___ je suis né est petite. → **où**
- La table sur ___ j'ai posé le livre. → **laquelle**

**Key forms:** qui (subject), que (direct object), dont (de + relative), où (place/time), lequel/laquelle/lesquels/lesquelles (after prepositions), contractions auquel/auxquels/duquel/desquels.

### Data Feasibility: NEEDS NEW DATASET

| Pronoun | Sentences found |
|---------|---------------:|
| qui (relative) | ~200-300 |
| que/qu' (ambiguous with conjunction) | ~1,400 total, ~200-400 true relative |
| où (relative) | ~50 |
| dont | **4** |
| lequel/laquelle | **5** |
| lesquels/lesquelles | **0** |
| auquel/auxquels/duquel/desquels | **0** |

The rare but important forms (dont, lequel family, contractions) have near-zero coverage.
All existing sentence blanks target vocabulary words, not relative pronouns.
A dedicated `relative_pronouns.json` with ~200+ curated sentences (10-20 per form) is needed.

---

## 2. Démontrez! — Pronoms démonstratifs

Choose the correct demonstrative pronoun: **celui, celle, ceux, celles, ce, ceci, cela/ça**.

**Example questions:**
- J'aime cette robe, mais je préfère ___. → **celle-là**
- Ces livres sont à Paul, ___ sont à Marie. → **ceux-là**
- ___ qui travaille dur réussira. → **Celui**
- ___ est incroyable! → **C'**

**Key forms:** celui/celle/ceux/celles (+ de, + qui/que, + -ci/-là), ce/c' (before être), ceci/cela/ça.

### Data Feasibility: PARTIALLY SUFFICIENT

| Form | Sentences found |
|------|---------------:|
| cela | 108 |
| ça | 182 |
| ce (as pronoun) | ~616 |
| celui | 7 |
| celle | 9 |
| ceux | 13 |
| celles | 8 |
| ceci | 7 |
| celui-ci/là | 2 |
| celle-ci/là | 5 |
| ceux-ci/là, celles-ci/là | 0 |

Simple forms (cela, ça, ce) have ~900+ sentences — enough for a scoped game. But the compound
forms (celui-ci, celle-là, etc.) have near-zero coverage. All blanks need repositioning.
Either scope the game to simple forms only, or generate ~100+ sentences for compound forms.

---

## 3. Possédez! — Pronoms possessifs

Select the correct possessive pronoun: **le mien, la mienne, les miens, les miennes, le tien, la tienne, le sien, la sienne, le nôtre, la nôtre, le vôtre, la vôtre, le leur, la leur**.

**Example questions:**
- C'est ton stylo? Oui, c'est ___. → **le mien**
- Vos enfants sont sages, ___ sont terribles. → **les nôtres**
- Sa voiture est rouge, ___ est bleue. → **la mienne**
- Leurs idées sont bonnes mais ___ sont meilleures. → **les nôtres**

**Key forms:** 6 persons × 4 gender/number combinations = 24 forms. Agree with the possessed noun, not the possessor.

### Data Feasibility: NEEDS NEW DATASET

Only **10 sentences** found across all data, covering just 5 of 21 forms. 16 forms have zero
hits. Possessive pronouns are rare in the vocabulary-oriented corpus.
A purpose-built dataset of ~200+ sentences (10+ per form) is needed.

---

## 4. Préposez! — Prépositions (verbes + à/de)

Choose the correct preposition for verb + preposition constructions: **à, de, en, dans, par, pour, sur, avec, sans, chez**.

**Example questions:**
- Il commence ___ pleuvoir. → **à**
- Elle a décidé ___ partir. → **de**
- Je pense ___ toi. → **à**
- Il rêve ___ voyager. → **de**
- Nous nous intéressons ___ l'art. → **à**

**Key forms:** Verbs taking à (penser à, commencer à, réussir à...), verbs taking de (décider de, essayer de, rêver de...), verbs with no preposition (regarder, écouter, chercher...).

### Data Feasibility: SUFFICIENT ✓

| Pattern | Sentences found |
|---------|---------------:|
| verb + de | 1,934 |
| verb + à | 1,252 |
| verb + dans | 716 |
| verb + en | 599 |
| verb + pour | 464 |
| verb + sur | 430 |
| verb + par | 238 |
| verb + avec | 161 |
| **Total** | **~4,500+** (after removing noise) |

Top combos: commence à (22), manque de (27), arrête de (23), réussi à (16), décidé de (20).
All blanks need repositioning to target the preposition. Some false positives exist where noun
homographs match verb forms (~20% noise), but the volume is more than enough.
**Strongest candidate of all 10 ideas — ready to build.**

---

## 5. Partagez! — Articles partitifs

Fill in the correct partitive or article: **du, de la, de l', des, de/d'**.

**Example questions:**
- Je mange ___ pain. → **du**
- Il n'y a pas ___ lait. → **de**
- Elle boit ___ eau. → **de l'**
- J'ai beaucoup ___ amis. → **d'**
- Nous avons ___ chance. → **de la**

**Key forms:** du (masc. sing.), de la (fem. sing.), de l' (before vowel), des (plural). After negation → de/d'. After quantity expressions (beaucoup, peu, trop, assez) → de/d'.

### Data Feasibility: PARTIALLY SUFFICIENT

| Pattern | Sentences found |
|---------|---------------:|
| du | 1,691 |
| des | 2,163 |
| de la | 925 |
| de l' | 578 |
| pas de / pas d' | 129 |
| beaucoup de/d' | 214 |
| peu de/d' | 12 |
| trop de | 19 |
| **Raw total** | **~5,735** |

**Core problem: ambiguity.** Most `du`/`des` hits are contractions (de + le/les), not true
partitives. Sampling shows only ~15-25% are genuine partitives, yielding ~850-1,400 usable
sentences. The quantity expressions (beaucoup de, pas de) are clean and unambiguous (~350+).
Needs either NLP-based filtering or manual curation to separate partitives from contractions.

---

## 6. Subjonctiez! — Subjonctif ou indicatif?

Decide whether the subordinate clause requires the **subjunctive** or **indicative** mood, then choose the correct verb form.

**Example questions:**
- Je veux que tu ___ (venir). → **viennes** (subjonctif)
- Je sais que tu ___ (venir). → **viens** (indicatif)
- Il faut que nous ___ (faire). → **fassions** (subjonctif)
- Je pense qu'il ___ (avoir) raison. → **a** (indicatif)
- Bien qu'il ___ (pleuvoir), je sors. → **pleuve** (subjonctif)

**Key triggers:** Subjunctive after: vouloir que, il faut que, bien que, pour que, avant que, à moins que, douter que. Indicative after: savoir que, penser que (affirmative), espérer que, après que.

### Data Feasibility: SUFFICIENT ✓

| Metric | Count |
|--------|------:|
| Verbs with subjonctif conjugation data | 1,182 (100%) |
| Subjonctif-tagged sentences in conjugations.json | 2,598 |
| Verbs with distinct subjonctif vs present forms | 244 (e.g. est→soit, a→ait, peut→puisse) |
| Sentences containing subjunctive triggers | 107 |

**Questions are generatable from conjugation tables:** pair a trigger phrase with a verb and
ask the player to choose subjonctif or indicatif. The 244 verbs with distinct forms provide
the most interesting questions. The 2,598 pre-made subjonctif sentences already have the
conjugated verb as the blank — ready to use. The 107 naturally occurring trigger sentences
add realistic context.

---

## 7. Remplacez! — Pronoms compléments

Replace the underlined noun with the correct object pronoun(s) and place them in the right order: **me, te, le, la, les, lui, leur, y, en, nous, vous**.

**Example questions:**
- Je donne le livre à Marie. → **Je le lui donne.**
- Elle va au marché. → **Elle y va.**
- Nous mangeons des pommes. → **Nous en mangeons.**
- Il me donne les clés. → **Il me les donne.**

**Key forms:** Word order before verb: me/te/nous/vous → le/la/les → lui/leur → y → en. Imperative affirmative reverses order.

### Data Feasibility: PARTIALLY SUFFICIENT

| Pattern | Sentences found |
|---------|---------------:|
| me/m' (object) | 1,288 |
| te/t' (object) | 399 |
| se/s' (reflexive) | 361 |
| l'a/l'ai/l'ont | 280 |
| en (pronoun) | 218 |
| lui (indirect object) | 155 |
| y (pronoun) | 129 |
| leur (indirect object) | 25 |
| **Unique sentences** | **~1,268** |

Volume is decent but all blanks need repositioning. Significant noise: `en` includes gerund
markers ("en admirant"), `le/la/les` overlap with articles, `se/s'` are reflexive rather than
object pronouns. Needs a preprocessing script with heuristic filtering. No new dataset needed,
but significant data transformation required.

---

## 8. Accordez! — Accords du participe passé

Choose the correctly agreed past participle form.

**Example questions:**
- Les fleurs que j'ai ___ (acheter). → **achetées**
- Elle est ___ (partir). → **partie**
- Ils se sont ___ (laver) les mains. → **lavé** (no agreement, COD after)
- La lettre qu'il a ___ (envoyer). → **envoyée**

**Key rules:** With être: agree with subject. With avoir: agree with preceding direct object. Reflexive verbs: agree with reflexive pronoun if it's the direct object.

### Data Feasibility: SUFFICIENT ✓

| Metric | Count |
|--------|------:|
| Verbs with auxiliary "être" | 42 |
| Verbs with auxiliary "avoir" | 1,140 |
| Passé composé tagged sentences | 6,492 |
| …of which for être verbs | 180 |
| Sentences with visible PP agreement (-ée, -és, -ées) | ~727 |

**Questions are generatable from verb data:** present a sentence and ask the player to choose
the correct agreement (e.g. "Elle est arrivé / arrivée / arrivés / arrivées"). The 42 être
verbs with 180 tagged PC sentences provide a solid base. For avoir + preceding direct object
(the harder rule), the ~280 `l'a/les a` sentences from the pronoun analysis can be used.
The conjugation tables provide all past participle forms needed.

---

## 9. Négativez! — La négation

Complete the sentence with the correct negation structure: **ne...pas, ne...plus, ne...jamais, ne...rien, ne...personne, ne...que, ne...aucun(e), ne...ni...ni, ne...guère**.

**Example questions:**
- Je ___ mange ___ de viande. (no longer) → **ne ... plus**
- Il ___ a ___ dans le frigo. (nothing) → **n' ... rien**
- Elle ___ connaît ___ ici. (nobody) → **ne ... personne**
- Nous ___ avons ___ trois euros. (only) → **n' ... que**
- Tu ___ fumes ___? (never) → **ne ... jamais**

**Key forms:** 9 negation patterns. Word order with pronouns and compound tenses. Special cases: personne ne..., rien ne... (as subject).

### Data Feasibility: SUFFICIENT (common patterns), PARTIALLY for rare ones

| Pattern | Sentences found |
|---------|---------------:|
| ne...pas / n'...pas | 1,165 |
| ne...plus / n'...plus | 172 |
| ne...jamais / n'...jamais | 103 |
| ne...rien / n'...rien | 97 |
| ne...que / n'...que | ~57 |
| ne...aucun / n'...aucun | 53 |
| ne...personne / n'...personne | **12** |
| ne...ni...ni | **5** |
| **Total unique** | **~917** |

170 sentences already have the negation word as the blank. The remaining ~747 need re-blanking.
Distribution is heavily skewed: ne...pas dominates (1,165) while ne...personne (12) and
ne...ni...ni (5) are too scarce for standalone practice. Could supplement rare patterns
with generated sentences, or scope the game to the 6 common patterns.

---

## 10. Déjouez! — Faux amis (French-German false friends)

Given a French sentence with a highlighted word, choose the correct German translation (not the false friend).

**Example questions:**
- Il a une bonne **note**. → **Note/Zensur** (not Notiz)
- Elle porte une **robe** élégante. → **Kleid** (not Robe)
- C'est une **histoire** intéressante. → **Geschichte** (not Historie, though related)
- Le **chef** est en réunion. → **Chef/Leiter** (actually a true cognate!)
- J'ai besoin d'une **carte**. → **Karte** (true cognate) vs. La **marche** était longue. → **Wanderung/Gang** (not Marsch in the military sense)

**Key pairs:** bras/Arm (not BH), robe/Kleid (not Robe), figure/Gesicht (not Figur), blesser/verletzen (not blessen), formation/Ausbildung (not Formation), journal/Zeitung (not Journal), sympathique/nett (not sympathisch in the German sense).

### Data Feasibility: NEEDS NEW DATASET

Only ~10 genuine French-German false friend pairs found in vocab.json:

| French | Meaning | German lookalike | German meaning |
|--------|---------|------------------|----------------|
| robe | dress | Robe | gown |
| figure | face | Figur | body shape |
| sale | dirty | Sale | sale (English) |
| stage | internship | Stage | stage (English) |
| glace | ice cream/mirror | Glas | glass |
| phrase | sentence | Phrase | empty talk |
| magasin | shop | Magazin | magazine |
| regard | look/glance | Regard | respect (archaic) |
| coin | corner | Coin | coin (English) |
| grave | serious | Grab | grave/tomb |

A proper faux amis game needs a curated list of 50-100+ pairs with the French word, its true
meaning, the German lookalike, and the German word's actual meaning. The vocabulary data
provides the French side but contains no false-friend metadata. Entirely separate dataset needed.

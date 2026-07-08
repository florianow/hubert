#!/usr/bin/env python3
"""
Update categories.json and vocab.json with new/extended categories.
Works purely from the existing asset files — no Anki repo needed.

Run from the repo root:
  python3 scripts/update_categories.py
"""
import json, os

ASSETS = 'app/src/main/assets'

# ---------------------------------------------------------------------------
# New categories and extensions.
# Each entry: category_name -> list of French words that belong there.
# Words are looked up in vocab.json to get their rank.
# Words may appear in multiple categories.
# ---------------------------------------------------------------------------

ADDITIONS = {

    # ── Extend existing ────────────────────────────────────────────────────

    'Familie': [
        'mère', 'père', 'fils', 'fille', 'frère', 'sœur', 'mari', 'femme',
        'bébé', 'adulte', 'neveu', 'nièce', 'oncle', 'tante', 'cousin',
        'cousine', 'grand-mère', 'grand-père', 'époux', 'épouse', 'veuf',
        'veuve', 'jumeau', 'fiancé', 'fiancée', 'parrain', 'marraine',
    ],

    'Natur': [
        'mer', 'océan', 'lac', 'rivière', 'fleuve', 'montagne', 'forêt',
        'plage', 'île', 'désert', 'campagne', 'prairie', 'vallée', 'colline',
        'rocher', 'pierre', 'sable', 'terre', 'sol', 'eau', 'feu', 'air',
        'ciel', 'nuage', 'soleil', 'lune', 'étoile', 'vent', 'pluie',
        'neige', 'glace', 'fleur', 'arbre', 'feuille', 'racine', 'graine',
        'herbe', 'plante', 'bois', 'saisir',
    ],

    'Essen und Trinken': [
        'manger', 'boire', 'cuisiner', 'préparer', 'goûter', 'faim', 'soif',
        'repas', 'petit-déjeuner', 'déjeuner', 'dîner', 'recette', 'cuisine',
        'ingrédient', 'plat', 'menu', 'saveur', 'goût', 'épice', 'sel',
        'sucre', 'huile', 'beurre', 'farine', 'lait', 'fromage', 'yaourt',
        'œuf', 'pain', 'gâteau', 'dessert', 'soupe', 'sauce', 'légume',
        'fruit', 'viande', 'poisson', 'poulet', 'bœuf', 'porc', 'agneau',
        'pomme', 'orange', 'banane', 'fraise', 'raisin', 'tomate', 'carotte',
        'salade', 'riz', 'pâte', 'café', 'thé', 'jus', 'vin', 'bière',
        'eau minérale', 'assiette', 'verre', 'couteau', 'fourchette',
        'cuillère', 'saisonnier',
    ],

    'Körper und Gesundheit': [
        'vivre', 'mourir', 'naître', 'grandir', 'vieillir', 'guérir',
        'souffrir', 'dormir', 'se reposer', 'respirer', 'manger', 'boire',
        'marcher', 'courir', 'tomber', 'blesser', 'opérer', 'examiner',
        'traiter', 'soigner',
    ],

    'Zeit': [
        'minute', 'seconde', 'heure', 'jour', 'semaine', 'mois', 'an',
        'siècle', 'millénaire', 'époque', 'ère', 'période', 'saison',
        'printemps', 'été', 'automne', 'hiver', 'matin', 'midi', 'soir',
        'nuit', 'hier', 'aujourd\'hui', 'demain', 'maintenant', 'alors',
        'bientôt', 'tôt', 'tard', 'souvent', 'parfois', 'toujours', 'jamais',
        'longtemps', 'récent', 'ancien', 'futur', 'passé', 'présent',
        'immédiat', 'actuel', 'prochain', 'dernier', 'calendrier', 'date',
        'horaire', 'durée', 'délai', 'instant', 'moment', 'début', 'fin',
    ],

    'Sport': [
        'jouer', 'gagner', 'perdre', 'marquer', 'entraîner', 'courir',
        'nager', 'sauter', 'lancer', 'attraper', 'frapper', 'dribbler',
        'champion', 'compétition', 'tournoi', 'match', 'équipe', 'joueur',
        'entraîneur', 'arbitre', 'stade', 'terrain', 'piscine', 'gymnase',
        'médaille', 'trophée', 'record', 'performance', 'résultat',
        'victoire', 'défaite', 'score', 'point', 'ballon', 'balle',
        'raquette', 'vélo', 'ski', 'natation', 'football', 'tennis',
        'basket', 'rugby', 'athlétisme', 'cyclisme', 'boxe',
    ],

    'Stadt und Reise': [
        'voyager', 'visiter', 'explorer', 'partir', 'arriver', 'rentrer',
        'loger', 'réserver', 'conduire', 'voler', 'naviguer',
        'touriste', 'voyage', 'tourisme', 'destination', 'itinéraire',
        'frontière', 'douane', 'passeport', 'visa', 'billet', 'carte',
        'plan', 'guide', 'hôtel', 'chambre', 'auberge', 'camping',
        'aéroport', 'gare', 'port', 'rue', 'avenue', 'boulevard', 'place',
        'quartier', 'centre-ville', 'banlieue', 'capitale', 'région',
        'musée', 'monument', 'cathédrale', 'château', 'parc', 'jardin',
        'magasin', 'marché', 'pharmacie', 'hôpital', 'mairie', 'banque',
        'poste', 'commissariat',
    ],

    'Emotionen und Charakter': [
        'aimer', 'adorer', 'détester', 'craindre', 'souffrir', 'pleurer',
        'rire', 'sourire', 'crier', 'calmer', 'exciter', 'surprendre',
        'décevoir', 'satisfaire', 'inquiéter', 'rassurer', 'irriter',
        'énerver', 'consoler', 'encourager', 'inspirer', 'motiver',
        'hésiter', 'regretter', 'pardonner', 'remercier',
    ],

    'Kommunikationsverben': [
        'parler', 'dire', 'écrire', 'lire', 'écouter', 'entendre',
        'répondre', 'demander', 'poser', 'annoncer', 'expliquer',
        'décrire', 'raconter', 'préciser', 'mentionner', 'indiquer',
        'signaler', 'avertir', 'informer', 'communiquer', 'transmettre',
        'envoyer', 'recevoir', 'lancer', 'publier', 'diffuser', 'partager',
        'discuter', 'débattre', 'négocier', 'convaincre', 'persuader',
        'promettre', 'accepter', 'refuser', 'confirmer', 'nier',
        'aborder', 'souligner', 'résumer', 'conclure', 'citer',
    ],

    'Arbeit und Wirtschaft': [
        'travailler', 'employer', 'embaucher', 'licencier', 'diriger',
        'gérer', 'administrer', 'organiser', 'planifier', 'produire',
        'fabriquer', 'créer', 'développer', 'améliorer', 'innover',
        'investir', 'financer', 'budgéter', 'économiser', 'dépenser',
        'gagner', 'perdre', 'profiter', 'bénéficier', 'rentabiliser',
        'collaborer', 'coopérer', 'concurrencer', 'négocier', 'conclure',
        'signer', 'présenter', 'réunir', 'former', 'évaluer',
        'entreprise', 'société', 'compagnie', 'firme', 'start-up',
        'industrie', 'secteur', 'domaine', 'branche', 'filière',
        'employé', 'patron', 'directeur', 'manager', 'cadre', 'ouvrier',
        'contrat', 'salaire', 'prime', 'retraite', 'syndicat', 'grève',
        'chômage', 'emploi', 'poste', 'carrière', 'formation', 'stage',
    ],

    # ── New categories ─────────────────────────────────────────────────────

    'Grundverben': [
        'faire', 'mettre', 'donner', 'voir', 'trouver', 'rendre', 'tenir',
        'montrer', 'continuer', 'laisser', 'garder', 'finir', 'commencer',
        'essayer', 'utiliser', 'changer', 'représenter', 'sembler', 'paraître',
        'exister', 'appartenir', 'constituer', 'concerner', 'former',
        'obtenir', 'permettre', 'remettre', 'reprendre', 'retrouver',
        'prendre', 'aller', 'venir', 'partir', 'entrer', 'sortir', 'rester',
        'passer', 'tourner', 'revenir', 'retourner', 'traverser', 'suivre',
        'mener', 'conduire', 'porter', 'apporter', 'emporter', 'retirer',
        'ouvrir', 'fermer', 'lever', 'poser', 'placer', 'installer',
        'construire', 'établir', 'créer', 'détruire', 'supprimer',
        'ajouter', 'enlever', 'remplacer', 'choisir', 'décider', 'agir',
        'réussir', 'rater', 'terminer', 'résoudre', 'valoir', 'coûter',
        'compter', 'mesurer', 'peser', 'occuper', 'remplir', 'vider',
    ],

    'Denken und Wissen': [
        'penser', 'croire', 'connaître', 'comprendre', 'savoir', 'imaginer',
        'oublier', 'se souvenir', 'supposer', 'estimer', 'considérer',
        'réfléchir', 'analyser', 'étudier', 'apprendre', 'enseigner',
        'comprendre', 'expliquer', 'interpréter', 'juger', 'évaluer',
        'comparer', 'distinguer', 'reconnaître', 'identifier', 'découvrir',
        'inventer', 'créer', 'planifier', 'prévoir', 'anticiper',
        'remarquer', 'observer', 'noter', 'constater', 'vérifier',
        'douter', 'hésiter', 'certifier', 'confirmer', 'prouver',
        'rechercher', 'trouver', 'résoudre', 'conclure', 'décider',
        'intelligence', 'connaissance', 'savoir', 'idée', 'concept',
        'théorie', 'hypothèse', 'analyse', 'réflexion', 'pensée',
        'opinion', 'avis', 'point de vue', 'conviction', 'croyance',
        'mémoire', 'souvenir', 'imagination', 'créativité', 'intuition',
    ],

    'Sprache und Text': [
        'parler', 'dire', 'écrire', 'lire', 'écouter', 'répondre', 'demander',
        'parole', 'langage', 'langue', 'texte', 'phrase', 'mot', 'lettre',
        'vocabulaire', 'grammaire', 'orthographe', 'prononciation', 'accent',
        'discours', 'argument', 'commentaire', 'article', 'rapport',
        'document', 'livre', 'roman', 'poème', 'journal', 'magazine',
        'histoire', 'conte', 'récit', 'message', 'email', 'note',
        'traduction', 'interprétation', 'signification', 'sens', 'définition',
        'expression', 'terme', 'concept', 'symbole', 'signe',
        'question', 'réponse', 'conversation', 'dialogue', 'interview',
        'citation', 'titre', 'sujet', 'thème', 'chapitre', 'paragraphe',
    ],

    'Geld und Finanzen': [
        'payer', 'coûter', 'valoir', 'acheter', 'vendre', 'investir',
        'économiser', 'dépenser', 'emprunter', 'prêter', 'rembourser',
        'financer', 'budgéter', 'rentabiliser', 'profiter', 'perdre',
        'prix', 'coût', 'tarif', 'facture', 'reçu', 'bon de commande',
        'argent', 'monnaie', 'billet', 'pièce', 'euro', 'dollar',
        'salaire', 'revenu', 'bénéfice', 'profit', 'perte', 'dette',
        'crédit', 'prêt', 'hypothèque', 'intérêt', 'taux', 'inflation',
        'budget', 'économie', 'marché', 'bourse', 'action', 'banque',
        'compte', 'épargne', 'assurance', 'taxe', 'impôt', 'TVA',
        'achat', 'vente', 'commerce', 'transaction', 'échange',
        'riche', 'pauvre', 'cher', 'bon marché', 'gratuit', 'remise',
    ],

    'Politik und Staat': [
        'voter', 'élire', 'gouverner', 'diriger', 'administrer',
        'légiférer', 'débattre', 'négocier', 'réformer', 'réglementer',
        'national', 'international', 'civil', 'public', 'officiel',
        'gouvernement', 'état', 'nation', 'pays', 'territoire',
        'président', 'premier ministre', 'ministre', 'député', 'sénateur',
        'parlement', 'assemblée', 'sénat', 'conseil', 'commission',
        'parti', 'opposition', 'coalition', 'majorité', 'minorité',
        'élection', 'campagne', 'candidat', 'vote', 'scrutin', 'résultat',
        'loi', 'décret', 'constitution', 'règlement', 'accord', 'traité',
        'diplomatie', 'ambassade', 'consul', 'frontière', 'souveraineté',
        'démocratie', 'liberté', 'droits', 'citoyen', 'bürger',
        'police', 'armée', 'sécurité', 'défense', 'guerre', 'paix',
        'parlementaire', 'législatif', 'judiciaire', 'exécutif',
    ],

    'Wohnen und Alltag': [
        'vivre', 'habiter', 'loger', 'dormir', 'manger', 'cuisiner',
        'nettoyer', 'ranger', 'laver', 'repasser', 'réparer', 'bricoler',
        'acheter', 'payer', 'économiser', 'dépenser',
        'maison', 'appartement', 'logement', 'domicile', 'résidence',
        'chambre', 'salon', 'cuisine', 'salle de bain', 'toilettes',
        'jardin', 'terrasse', 'balcon', 'cave', 'grenier', 'garage',
        'meuble', 'table', 'chaise', 'lit', 'canapé', 'armoire',
        'lampe', 'fenêtre', 'porte', 'mur', 'plafond', 'sol', 'tapis',
        'télévision', 'ordinateur', 'téléphone', 'réfrigérateur',
        'machine', 'appareil', 'outil', 'clé', 'serrure',
        'loyer', 'charges', 'propriétaire', 'locataire', 'voisin',
        'quartier', 'immeuble', 'étage', 'ascenseur', 'escalier',
    ],

    'Gefühle und Stimmung': [
        'aimer', 'adorer', 'détester', 'craindre', 'espérer', 'regretter',
        'ressentir', 'éprouver', 'exprimer', 'manifester', 'montrer',
        'heureux', 'triste', 'content', 'satisfait', 'déçu', 'surpris',
        'inquiet', 'anxieux', 'stressé', 'calme', 'serein', 'zen',
        'fâché', 'en colère', 'furieux', 'irrité', 'agacé', 'frustré',
        'amoureux', 'jaloux', 'nostalgique', 'mélancolique', 'déprimé',
        'enthousiaste', 'motivé', 'passionné', 'curieux', 'intéressé',
        'fatigué', 'épuisé', 'ennuyé', 'bored', 'excité', 'nerveux',
        'joie', 'bonheur', 'tristesse', 'peur', 'colère', 'surprise',
        'amour', 'haine', 'jalousie', 'nostalgie', 'espoir', 'désespoir',
        'confiance', 'doute', 'honte', 'fierté', 'culpabilité', 'soulagement',
    ],

    'Schule und Bildung': [
        'apprendre', 'étudier', 'enseigner', 'expliquer', 'comprendre',
        'mémoriser', 'répéter', 'réviser', 'pratiquer', 'tester',
        'évaluer', 'noter', 'corriger', 'réussir', 'échouer', 'passer',
        'école', 'collège', 'lycée', 'université', 'faculté', 'classe',
        'salle de classe', 'laboratoire', 'bibliothèque', 'gymnase',
        'élève', 'étudiant', 'professeur', 'enseignant', 'directeur',
        'cours', 'leçon', 'matière', 'programme', 'emploi du temps',
        'devoir', 'exercice', 'examen', 'test', 'concours', 'diplôme',
        'baccalauréat', 'licence', 'master', 'doctorat', 'thèse',
        'manuel', 'cahier', 'livre', 'dictionnaire', 'calculatrice',
        'crayon', 'stylo', 'feuille', 'tableau', 'écran', 'projecteur',
        'note', 'résultat', 'bulletin', 'certificat', 'mention',
        'mathématiques', 'sciences', 'histoire', 'géographie', 'langues',
        'physique', 'chimie', 'biologie', 'philosophie', 'littérature',
    ],

    'Medien und Kultur': [
        'lire', 'écrire', 'publier', 'diffuser', 'émettre', 'recevoir',
        'regarder', 'écouter', 'voir', 'entendre', 'télécharger', 'partager',
        'médias', 'presse', 'journal', 'magazine', 'livre', 'roman',
        'télévision', 'radio', 'internet', 'réseaux sociaux', 'site web',
        'film', 'cinéma', 'série', 'émission', 'documentaire', 'reportage',
        'musique', 'chanson', 'concert', 'festival', 'spectacle',
        'théâtre', 'opéra', 'ballet', 'danse', 'exposition', 'galerie',
        'journaliste', 'rédacteur', 'auteur', 'écrivain', 'artiste',
        'acteur', 'chanteur', 'musicien', 'réalisateur', 'photographe',
        'publicité', 'annonce', 'campagne', 'message', 'information',
        'nouvelle', 'actualité', 'événement', 'fait', 'réalité',
    ],

    'Redewendungen und Ausdrücke': [
        'bien sûr', 'en fait', 'par exemple', 'c\'est-à-dire', 'en effet',
        'au contraire', 'cependant', 'pourtant', 'néanmoins', 'toutefois',
        'alors', 'donc', 'ainsi', 'aussi', 'encore', 'déjà', 'enfin',
        'surtout', 'seulement', 'même', 'aussi', 'très', 'trop', 'assez',
        'peu', 'beaucoup', 'plus', 'moins', 'autant', 'tant', 'tellement',
        'ici', 'là', 'ailleurs', 'partout', 'nulle part', 'quelque part',
        'maintenant', 'bientôt', 'toujours', 'jamais', 'parfois', 'souvent',
        'd\'abord', 'ensuite', 'finalement', 'enfin', 'au début', 'à la fin',
        'bien', 'mal', 'mieux', 'moins bien', 'vite', 'lentement',
    ],
}

# ---------------------------------------------------------------------------

def main():
    vocab_path = os.path.join(ASSETS, 'vocab.json')
    cat_path = os.path.join(ASSETS, 'categories.json')

    with open(vocab_path, encoding='utf-8') as f:
        words = json.load(f)
    with open(cat_path, encoding='utf-8') as f:
        categories = json.load(f)

    # Build lookup: french -> rank
    fr_to_rank = {w['french']: w['rank'] for w in words}

    added_total = 0
    not_found = []

    for cat_name, french_words in ADDITIONS.items():
        existing_ranks = set(categories.get(cat_name, []))
        new_ranks = set(existing_ranks)

        for fr in french_words:
            if fr in fr_to_rank:
                new_ranks.add(fr_to_rank[fr])
            else:
                not_found.append((cat_name, fr))

        added = len(new_ranks) - len(existing_ranks)
        added_total += added
        categories[cat_name] = sorted(new_ranks)
        status = "NEW" if cat_name not in categories or existing_ranks == set() else "EXT"
        print(f"  [{status}] {cat_name}: {len(existing_ranks)} → {len(new_ranks)} (+{added})")

    # Sort categories alphabetically
    categories = dict(sorted(categories.items()))

    # Rebuild rank->categories index
    rank_to_cats: dict[int, list[str]] = {}
    for cat_name, ranks in categories.items():
        for rank in ranks:
            rank_to_cats.setdefault(rank, []).append(cat_name)

    # Update vocab.json categories field
    changed_words = 0
    for w in words:
        old_cats = sorted(w.get('categories', []))
        new_cats = sorted(rank_to_cats.get(w['rank'], []))
        if old_cats != new_cats:
            if new_cats:
                w['categories'] = new_cats
            elif 'categories' in w:
                del w['categories']
            changed_words += 1

    # Write back
    with open(cat_path, 'w', encoding='utf-8') as f:
        json.dump(categories, f, ensure_ascii=False, indent=2)
    with open(vocab_path, 'w', encoding='utf-8') as f:
        json.dump(words, f, ensure_ascii=False, indent=2)

    # Stats
    with_cat = sum(1 for w in words if w.get('categories'))
    print(f"\nErgebnis:")
    print(f"  {len(categories)} Kategorien")
    print(f"  {with_cat}/{len(words)} Wörter kategorisiert ({with_cat/len(words)*100:.0f}%)")
    print(f"  {changed_words} vocab.json Einträge aktualisiert")
    print(f"  +{added_total} Zuordnungen hinzugefügt")
    if not_found:
        print(f"\nNicht gefunden ({len(not_found)}):")
        for cat, fr in not_found[:20]:
            print(f"  [{cat}] '{fr}'")


if __name__ == '__main__':
    main()

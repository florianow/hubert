"""Extend app/src/main/assets/relative_pronouns.json with curated Relativisez! questions.

Each entry: (French sentence with ___, German translation, answer, optional cue for the
explanation). Distractors and explanations are derived per answer form. Rules followed:
- lequel family only with things (with persons "avec qui" would also be correct)
- no que/ce que directly before il/elle/on (the solution would read "que il")
- duquel/desquels only after prepositional phrases (près de, au bord de …) where dont is wrong
Existing questions are kept; duplicates (same French sentence) are skipped.
"""
import json
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "app/src/main/assets/relative_pronouns.json"

DISTRACTORS = {
    "qui": ["que", "dont", "où"],
    "que": ["qui", "dont", "où"],
    "dont": ["qui", "que", "où"],
    "où": ["qui", "que", "dont"],
    "lequel": ["laquelle", "lesquels", "lesquelles"],
    "laquelle": ["lequel", "lesquels", "lesquelles"],
    "lesquels": ["lequel", "laquelle", "lesquelles"],
    "lesquelles": ["lequel", "laquelle", "lesquels"],
    "auquel": ["auxquels", "duquel", "desquels"],
    "auxquels": ["auquel", "duquel", "desquels"],
    "duquel": ["dont", "auquel", "desquels"],
    "desquels": ["duquel", "dont", "auxquels"],
    "ce qui": ["ce que", "ce dont", "qui"],
    "ce que": ["ce qui", "ce dont", "que"],
    "ce dont": ["ce que", "ce qui", "dont"],
}

def explanation(answer, cue):
    return {
        "qui": "qui ist Subjekt des Relativsatzes – direkt danach folgt das Verb.",
        "que": "que ist direktes Objekt – der Relativsatz hat ein eigenes Subjekt.",
        "dont": f"{cue} → dont ersetzt de + Ergänzung." if cue else "dont ersetzt de + Ergänzung.",
        "où": "où steht für einen Ort oder einen Zeitpunkt.",
        "lequel": "Nach Präposition bei Sachen: lequel – männlich Singular wie das Bezugswort.",
        "laquelle": "Nach Präposition bei Sachen: laquelle – weiblich Singular wie das Bezugswort.",
        "lesquels": "Nach Präposition bei Sachen: lesquels – männlich Plural wie das Bezugswort.",
        "lesquelles": "Nach Präposition bei Sachen: lesquelles – weiblich Plural wie das Bezugswort.",
        "auquel": f"{cue} → à + lequel = auquel." if cue else "à + lequel = auquel.",
        "auxquels": f"{cue} → à + lesquels = auxquels." if cue else "à + lesquels = auxquels.",
        "duquel": f"{cue} + lequel = duquel – nach einer Wendung mit de ist dont nicht möglich.",
        "desquels": f"{cue} + lesquels = desquels – nach einer Wendung mit de ist dont nicht möglich.",
        "ce qui": "Kein Bezugswort und Subjekt → ce qui.",
        "ce que": "Kein Bezugswort und direktes Objekt → ce que.",
        "ce dont": f"Kein Bezugswort und {cue} → ce dont." if cue else "Kein Bezugswort und Ergänzung mit de → ce dont.",
    }[answer]

Q = [
    # ── qui ──────────────────────────────────────────────────────────────
    ("Le garçon ___ chante est mon cousin.", "Der Junge, der singt, ist mein Cousin.", "qui"),
    ("J'ai une collègue ___ vient de Lyon.", "Ich habe eine Kollegin, die aus Lyon kommt.", "qui"),
    ("Le bus ___ va à la gare est en retard.", "Der Bus, der zum Bahnhof fährt, hat Verspätung.", "qui"),
    ("Prends le pull ___ est sur la chaise.", "Nimm den Pullover, der auf dem Stuhl liegt.", "qui"),
    ("C'est un film ___ fait peur.", "Das ist ein Film, der Angst macht.", "qui"),
    ("Les étudiants ___ ont réussi l'examen sont contents.", "Die Studenten, die die Prüfung bestanden haben, sind froh.", "qui"),
    ("Je connais une boulangerie ___ ouvre à six heures.", "Ich kenne eine Bäckerei, die um sechs Uhr öffnet.", "qui"),
    ("La femme ___ parle avec le directeur est avocate.", "Die Frau, die mit dem Direktor spricht, ist Anwältin.", "qui"),
    ("C'est moi ___ ai fait le gâteau.", "Ich bin es, der den Kuchen gebacken hat.", "qui"),
    ("Il y a un chat ___ dort sur le toit.", "Da ist eine Katze, die auf dem Dach schläft.", "qui"),
    ("Les fleurs ___ poussent dans le jardin sont belles.", "Die Blumen, die im Garten wachsen, sind schön.", "qui"),
    ("Nous cherchons quelqu'un ___ parle allemand.", "Wir suchen jemanden, der Deutsch spricht.", "qui"),
    ("Le restaurant ___ se trouve au coin de la rue est fermé.", "Das Restaurant, das an der Straßenecke liegt, ist geschlossen.", "qui"),
    ("J'aime les gens ___ disent la vérité.", "Ich mag Menschen, die die Wahrheit sagen.", "qui"),
    ("C'est une histoire ___ finit bien.", "Das ist eine Geschichte, die gut ausgeht.", "qui"),
    ("Le médecin ___ m'a soigné était très gentil.", "Der Arzt, der mich behandelt hat, war sehr nett.", "qui"),
    ("Voilà l'enfant ___ a perdu son ballon.", "Da ist das Kind, das seinen Ball verloren hat.", "qui"),
    ("Les voitures ___ consomment peu sont plus chères.", "Autos, die wenig verbrauchen, sind teurer.", "qui"),
    ("Le prof ___ enseigne l'histoire est malade.", "Der Lehrer, der Geschichte unterrichtet, ist krank.", "qui"),
    ("J'ai lu un article ___ parle du climat.", "Ich habe einen Artikel gelesen, der vom Klima handelt.", "qui"),
    ("La lampe ___ éclaire le salon est cassée.", "Die Lampe, die das Wohnzimmer beleuchtet, ist kaputt.", "qui"),
    ("Ce sont mes parents ___ m'ont offert ce vélo.", "Es sind meine Eltern, die mir dieses Fahrrad geschenkt haben.", "qui"),
    ("Il a un chien ___ aboie toute la nuit.", "Er hat einen Hund, der die ganze Nacht bellt.", "qui"),
    ("La personne ___ a appelé n'a pas laissé de message.", "Die Person, die angerufen hat, hat keine Nachricht hinterlassen.", "qui"),
    ("Le magasin ___ vend des vélos a fermé.", "Der Laden, der Fahrräder verkauft, hat zugemacht.", "qui"),

    # ── que ──────────────────────────────────────────────────────────────
    ("Le gâteau ___ tu as fait est délicieux.", "Der Kuchen, den du gebacken hast, ist köstlich.", "que"),
    ("La ville ___ nous avons visitée était magnifique.", "Die Stadt, die wir besucht haben, war wunderschön.", "que"),
    ("Le film ___ j'ai vu hier était trop long.", "Der Film, den ich gestern gesehen habe, war zu lang.", "que"),
    ("C'est une question ___ je me pose souvent.", "Das ist eine Frage, die ich mir oft stelle.", "que"),
    ("Les amis ___ j'ai invités arrivent ce soir.", "Die Freunde, die ich eingeladen habe, kommen heute Abend.", "que"),
    ("La voiture ___ mon père conduit est neuve.", "Das Auto, das mein Vater fährt, ist neu.", "que"),
    ("Voici le cadeau ___ j'ai acheté pour toi.", "Hier ist das Geschenk, das ich für dich gekauft habe.", "que"),
    ("L'homme ___ tu vois là-bas est mon oncle.", "Der Mann, den du dort drüben siehst, ist mein Onkel.", "que"),
    ("La musique ___ ma sœur écoute est trop forte.", "Die Musik, die meine Schwester hört, ist zu laut.", "que"),
    ("Les photos ___ vous avez prises sont superbes.", "Die Fotos, die ihr gemacht habt, sind großartig.", "que"),
    ("C'est le professeur ___ tous les élèves adorent.", "Das ist der Lehrer, den alle Schüler lieben.", "que"),
    ("Le plat ___ je préfère est le couscous.", "Das Gericht, das ich am liebsten mag, ist Couscous.", "que"),
    ("La langue ___ Paul apprend est difficile.", "Die Sprache, die Paul lernt, ist schwierig.", "que"),
    ("Les chaussures ___ j'ai achetées sont trop petites.", "Die Schuhe, die ich gekauft habe, sind zu klein.", "que"),
    ("Le livre ___ tu m'as prêté est passionnant.", "Das Buch, das du mir geliehen hast, ist spannend.", "que"),
    ("La maison ___ mes parents ont construite est immense.", "Das Haus, das meine Eltern gebaut haben, ist riesig.", "que"),
    ("C'est un homme ___ je respecte beaucoup.", "Das ist ein Mann, den ich sehr respektiere.", "que"),
    ("Les vacances ___ nous avons passées en Italie étaient géniales.", "Die Ferien, die wir in Italien verbracht haben, waren toll.", "que"),
    ("Le mot ___ tu cherches est « bibliothèque ».", "Das Wort, das du suchst, ist „bibliothèque“.", "que"),
    ("L'appartement ___ Julie loue est au cinquième étage.", "Die Wohnung, die Julie mietet, liegt im fünften Stock.", "que"),
    ("Le message ___ j'ai reçu était bizarre.", "Die Nachricht, die ich bekommen habe, war seltsam.", "que"),
    ("La chanson ___ tu chantes est triste.", "Das Lied, das du singst, ist traurig.", "que"),
    ("Les fruits ___ tu as achetés sont mûrs.", "Das Obst, das du gekauft hast, ist reif.", "que"),
    ("C'est la plus belle plage ___ je connaisse.", "Das ist der schönste Strand, den ich kenne.", "que"),
    ("Le café ___ je bois le matin est très fort.", "Der Kaffee, den ich morgens trinke, ist sehr stark.", "que"),

    # ── dont ─────────────────────────────────────────────────────────────
    ("C'est le livre ___ je t'ai parlé.", "Das ist das Buch, von dem ich dir erzählt habe.", "dont", "parler de"),
    ("Voici l'outil ___ j'ai besoin.", "Hier ist das Werkzeug, das ich brauche.", "dont", "avoir besoin de"),
    ("La ville ___ il est originaire est petite.", "Die Stadt, aus der er stammt, ist klein.", "dont", "être originaire de"),
    ("C'est un résultat ___ nous sommes fiers.", "Das ist ein Ergebnis, auf das wir stolz sind.", "dont", "être fier de"),
    ("L'homme ___ la voiture est rouge habite ici.", "Der Mann, dessen Auto rot ist, wohnt hier.", "dont", "la voiture de l'homme"),
    ("Le film ___ tout le monde parle sort demain.", "Der Film, über den alle reden, kommt morgen heraus.", "dont", "parler de"),
    ("C'est une chose ___ j'ai peur.", "Das ist etwas, wovor ich Angst habe.", "dont", "avoir peur de"),
    ("Les enfants ___ elle s'occupe sont adorables.", "Die Kinder, um die sie sich kümmert, sind entzückend.", "dont", "s'occuper de"),
    ("Le projet ___ je suis responsable est en retard.", "Das Projekt, für das ich verantwortlich bin, ist im Verzug.", "dont", "être responsable de"),
    ("C'est une maladie ___ on guérit facilement.", "Das ist eine Krankheit, von der man leicht geheilt wird.", "dont", "guérir de"),
    ("Le cadeau ___ j'ai envie coûte trop cher.", "Das Geschenk, das ich mir wünsche, ist zu teuer.", "dont", "avoir envie de"),
    ("Une amie ___ le frère est pilote vient dîner.", "Eine Freundin, deren Bruder Pilot ist, kommt zum Abendessen.", "dont", "le frère de l'amie"),
    ("C'est un travail ___ il est très content.", "Das ist eine Arbeit, mit der er sehr zufrieden ist.", "dont", "être content de"),
    ("Les difficultés ___ il se plaint sont réelles.", "Die Schwierigkeiten, über die er sich beklagt, sind echt.", "dont", "se plaindre de"),
    ("C'est une région ___ je me souviens bien.", "Das ist eine Gegend, an die ich mich gut erinnere.", "dont", "se souvenir de"),
    ("L'accident ___ il a été témoin était terrible.", "Der Unfall, dessen Zeuge er war, war schrecklich.", "dont", "être témoin de"),
    ("C'est un auteur ___ j'ai lu tous les livres.", "Das ist ein Autor, von dem ich alle Bücher gelesen habe.", "dont", "les livres de l'auteur"),
    ("La maison ___ les fenêtres sont bleues est à vendre.", "Das Haus, dessen Fenster blau sind, steht zum Verkauf.", "dont", "les fenêtres de la maison"),
    ("C'est la seule chose ___ je suis sûr.", "Das ist das Einzige, dessen ich mir sicher bin.", "dont", "être sûr de"),
    ("Le chien ___ elle a peur est pourtant gentil.", "Der Hund, vor dem sie Angst hat, ist doch lieb.", "dont", "avoir peur de"),
    ("Les étudiants, ___ trois sont étrangers, ont réussi.", "Die Studenten, von denen drei Ausländer sind, haben bestanden.", "dont", "trois des étudiants"),
    ("C'est un sujet ___ je ne sais rien.", "Das ist ein Thema, über das ich nichts weiß.", "dont", "ne rien savoir de"),
    ("Le collègue ___ je me méfie travaille ici depuis peu.", "Der Kollege, dem ich misstraue, arbeitet erst seit kurzem hier.", "dont", "se méfier de"),
    ("C'est le voyage ___ nous rêvons depuis des années.", "Das ist die Reise, von der wir seit Jahren träumen.", "dont", "rêver de"),
    ("Le restaurant ___ on dit tant de bien a ouvert hier.", "Das Restaurant, über das man so viel Gutes sagt, hat gestern eröffnet.", "dont", "dire du bien de"),

    # ── où ───────────────────────────────────────────────────────────────
    ("La maison ___ j'ai grandi est à vendre.", "Das Haus, in dem ich aufgewachsen bin, steht zum Verkauf.", "où"),
    ("Le jour ___ il est né, il neigeait.", "An dem Tag, an dem er geboren wurde, schneite es.", "où"),
    ("Je connais un café ___ on mange bien.", "Ich kenne ein Café, wo man gut isst.", "où"),
    ("C'est l'année ___ nous nous sommes mariés.", "Das ist das Jahr, in dem wir geheiratet haben.", "où"),
    ("La plage ___ nous allons est très calme.", "Der Strand, an den wir gehen, ist sehr ruhig.", "où"),
    ("Au moment ___ il est entré, tout le monde s'est tu.", "In dem Moment, als er hereinkam, verstummten alle.", "où"),
    ("Le pays ___ elle vit est très froid.", "Das Land, in dem sie lebt, ist sehr kalt.", "où"),
    ("C'est le bureau ___ je travaille.", "Das ist das Büro, in dem ich arbeite.", "où"),
    ("Au moment ___ je partais, le téléphone a sonné.", "Gerade als ich gehen wollte, klingelte das Telefon.", "où"),
    ("La rue ___ se trouve l'école est fermée.", "Die Straße, in der die Schule liegt, ist gesperrt.", "où"),
    ("Je ne connais pas la ville ___ il habite.", "Ich kenne die Stadt nicht, in der er wohnt.", "où"),
    ("C'est la période ___ il y a le plus de touristes.", "Das ist die Zeit, in der es die meisten Touristen gibt.", "où"),
    ("Le parc ___ je cours le dimanche est immense.", "Der Park, in dem ich sonntags laufe, ist riesig.", "où"),
    ("Le soir ___ je l'ai rencontrée, il pleuvait.", "An dem Abend, an dem ich sie kennenlernte, regnete es.", "où"),
    ("Voici la chambre ___ tu vas dormir.", "Hier ist das Zimmer, in dem du schlafen wirst.", "où"),
    ("L'hôpital ___ elle travaille est tout neuf.", "Das Krankenhaus, in dem sie arbeitet, ist ganz neu.", "où"),
    ("C'était l'époque ___ on écrivait encore des lettres.", "Das war die Zeit, in der man noch Briefe schrieb.", "où"),
    ("Le village ___ il a passé son enfance est minuscule.", "Das Dorf, in dem er seine Kindheit verbracht hat, ist winzig.", "où"),
    ("Le magasin ___ j'ai acheté ce manteau a fermé.", "Der Laden, in dem ich diesen Mantel gekauft habe, hat zugemacht.", "où"),
    ("Il y a des jours ___ tout va mal.", "Es gibt Tage, an denen alles schiefgeht.", "où"),
    ("La salle ___ a lieu le concert est pleine.", "Der Saal, in dem das Konzert stattfindet, ist voll.", "où"),
    ("Le siècle ___ nous vivons change vite.", "Das Jahrhundert, in dem wir leben, verändert sich schnell.", "où"),

    # ── lequel ───────────────────────────────────────────────────────────
    ("Le stylo avec ___ j'écris est bleu.", "Der Stift, mit dem ich schreibe, ist blau.", "lequel"),
    ("Le bureau sur ___ tu travailles est en désordre.", "Der Schreibtisch, an dem du arbeitest, ist unordentlich.", "lequel"),
    ("Le pont sous ___ passent les bateaux est très ancien.", "Die Brücke, unter der die Boote durchfahren, ist sehr alt.", "lequel"),
    ("C'est le projet pour ___ elle a travaillé jour et nuit.", "Das ist das Projekt, für das sie Tag und Nacht gearbeitet hat.", "lequel"),
    ("Le mur contre ___ il s'appuie est fragile.", "Die Mauer, an die er sich lehnt, ist brüchig.", "lequel"),
    ("Le parapluie sans ___ je ne sors jamais est cassé.", "Der Regenschirm, ohne den ich nie hinausgehe, ist kaputt.", "lequel"),
    ("C'est un sujet sur ___ nous ne sommes pas d'accord.", "Das ist ein Thema, bei dem wir uns nicht einig sind.", "lequel"),
    ("Le canapé sur ___ le chat dort est tout neuf.", "Das Sofa, auf dem die Katze schläft, ist ganz neu.", "lequel"),
    ("Le chemin par ___ nous sommes passés était boueux.", "Der Weg, über den wir gegangen sind, war schlammig.", "lequel"),
    ("C'est le film pendant ___ je me suis endormi.", "Das ist der Film, bei dem ich eingeschlafen bin.", "lequel"),
    ("Le contrat selon ___ il doit payer est clair.", "Der Vertrag, nach dem er zahlen muss, ist eindeutig.", "lequel"),
    ("L'arbre derrière ___ il se cache est énorme.", "Der Baum, hinter dem er sich versteckt, ist riesig.", "lequel"),
    ("Le motif pour ___ il a démissionné reste flou.", "Der Grund, aus dem er gekündigt hat, bleibt unklar.", "lequel"),
    ("C'est l'ordinateur avec ___ j'ai écrit ma thèse.", "Das ist der Computer, mit dem ich meine Abschlussarbeit geschrieben habe.", "lequel"),
    ("Le lit dans ___ je dors est trop petit.", "Das Bett, in dem ich schlafe, ist zu klein.", "lequel"),

    # ── laquelle ─────────────────────────────────────────────────────────
    ("La chaise sur ___ tu es assis est cassée.", "Der Stuhl, auf dem du sitzt, ist kaputt.", "laquelle"),
    ("La boîte dans ___ j'ai mis les clés a disparu.", "Die Schachtel, in die ich die Schlüssel gelegt habe, ist verschwunden.", "laquelle"),
    ("C'est une idée pour ___ je me bats.", "Das ist eine Idee, für die ich kämpfe.", "laquelle"),
    ("La fenêtre par ___ le voleur est entré était ouverte.", "Das Fenster, durch das der Dieb eingestiegen ist, war offen.", "laquelle"),
    ("La porte derrière ___ il attend est fermée.", "Die Tür, hinter der er wartet, ist geschlossen.", "laquelle"),
    ("C'est la solution sans ___ nous serions perdus.", "Das ist die Lösung, ohne die wir verloren wären.", "laquelle"),
    ("La valise dans ___ j'ai rangé mes affaires est lourde.", "Der Koffer, in den ich meine Sachen gepackt habe, ist schwer.", "laquelle"),
    ("La période pendant ___ il a vécu à Rome était heureuse.", "Die Zeit, in der er in Rom lebte, war glücklich.", "laquelle"),
    ("La règle selon ___ on doit arriver à l'heure est stricte.", "Die Regel, nach der man pünktlich sein muss, ist streng.", "laquelle"),
    ("C'est la raison pour ___ je suis en retard.", "Das ist der Grund, warum ich zu spät bin.", "laquelle"),
    ("La lampe sous ___ je lis est trop faible.", "Die Lampe, unter der ich lese, ist zu schwach.", "laquelle"),
    ("La statue devant ___ il pose est célèbre.", "Die Statue, vor der er posiert, ist berühmt.", "laquelle"),
    ("L'entreprise pour ___ elle travaille est suisse.", "Die Firma, für die sie arbeitet, ist schweizerisch.", "laquelle"),
    ("La méthode avec ___ il apprend est efficace.", "Die Methode, mit der er lernt, ist wirksam.", "laquelle"),
    ("La table autour de ___ nous dînons est ronde.", "Der Tisch, um den wir zu Abend essen, ist rund.", "laquelle"),

    # ── lesquels ─────────────────────────────────────────────────────────
    ("Les outils avec ___ il répare la voiture sont vieux.", "Die Werkzeuge, mit denen er das Auto repariert, sind alt.", "lesquels"),
    ("Les livres sans ___ je ne pourrais pas vivre sont ici.", "Die Bücher, ohne die ich nicht leben könnte, sind hier.", "lesquels"),
    ("Les sujets sur ___ ils débattent sont sensibles.", "Die Themen, über die sie debattieren, sind heikel.", "lesquels"),
    ("Les murs entre ___ nous vivons sont trop fins.", "Die Wände, zwischen denen wir leben, sind zu dünn.", "lesquels"),
    ("Les problèmes pour ___ il cherche une solution sont nombreux.", "Die Probleme, für die er eine Lösung sucht, sind zahlreich.", "lesquels"),
    ("Les arbres sous ___ nous déjeunons donnent de l'ombre.", "Die Bäume, unter denen wir zu Mittag essen, spenden Schatten.", "lesquels"),
    ("Les documents dans ___ j'ai trouvé l'erreur sont confidentiels.", "Die Dokumente, in denen ich den Fehler gefunden habe, sind vertraulich.", "lesquels"),
    ("Les critères selon ___ on choisit les candidats sont secrets.", "Die Kriterien, nach denen man die Kandidaten auswählt, sind geheim.", "lesquels"),
    ("Les jeux avec ___ les enfants jouent sont en bois.", "Die Spielsachen, mit denen die Kinder spielen, sind aus Holz.", "lesquels"),
    ("Les meubles parmi ___ il a trouvé ce trésor étaient anciens.", "Die Möbel, unter denen er diesen Schatz gefunden hat, waren alt.", "lesquels"),
    ("Les murs contre ___ on a posé les vélos sont sales.", "Die Mauern, an die man die Fahrräder gestellt hat, sind schmutzig.", "lesquels"),
    ("Les gants sans ___ je ne fais pas de ski sont perdus.", "Die Handschuhe, ohne die ich nicht Ski fahre, sind verloren.", "lesquels"),
    ("Les dossiers sur ___ elle travaille sont urgents.", "Die Akten, an denen sie arbeitet, sind dringend.", "lesquels"),
    ("Les chemins par ___ nous sommes rentrés étaient étroits.", "Die Wege, auf denen wir heimgegangen sind, waren eng.", "lesquels"),

    # ── lesquelles ───────────────────────────────────────────────────────
    ("Les chaises sur ___ nous sommes assis sont dures.", "Die Stühle, auf denen wir sitzen, sind hart.", "lesquelles"),
    ("Les conditions dans ___ ils travaillent sont difficiles.", "Die Bedingungen, unter denen sie arbeiten, sind schwierig.", "lesquelles"),
    ("Les idées pour ___ il se bat sont généreuses.", "Die Ideen, für die er kämpft, sind großzügig.", "lesquelles"),
    ("Les lunettes sans ___ je ne vois rien sont dans mon sac.", "Die Brille, ohne die ich nichts sehe, ist in meiner Tasche.", "lesquelles"),
    ("Les rues par ___ nous sommes passés étaient étroites.", "Die Straßen, durch die wir gegangen sind, waren eng.", "lesquelles"),
    ("Les questions sur ___ il a hésité étaient faciles.", "Die Fragen, bei denen er gezögert hat, waren einfach.", "lesquelles"),
    ("Les fenêtres derrière ___ il se cachait étaient sales.", "Die Fenster, hinter denen er sich versteckte, waren schmutzig.", "lesquelles"),
    ("Les règles selon ___ le jeu fonctionne sont simples.", "Die Regeln, nach denen das Spiel funktioniert, sind einfach.", "lesquelles"),
    ("Les entreprises pour ___ elle a travaillé sont connues.", "Die Firmen, für die sie gearbeitet hat, sind bekannt.", "lesquelles"),
    ("Les tables entre ___ le serveur passe sont trop serrées.", "Die Tische, zwischen denen der Kellner hindurchgeht, stehen zu eng.", "lesquelles"),
    ("Les montagnes parmi ___ se trouve le Mont Blanc sont magnifiques.", "Die Berge, zu denen der Mont Blanc gehört, sind herrlich.", "lesquelles"),
    ("Les valises dans ___ j'ai mis les cadeaux sont lourdes.", "Die Koffer, in die ich die Geschenke gelegt habe, sind schwer.", "lesquelles"),
    ("Les vacances pendant ___ il a plu tous les jours étaient décevantes.", "Die Ferien, in denen es jeden Tag geregnet hat, waren enttäuschend.", "lesquelles"),
    ("Les photos sur ___ on te voit sont floues.", "Die Fotos, auf denen man dich sieht, sind unscharf.", "lesquelles"),

    # ── auquel ───────────────────────────────────────────────────────────
    ("Le concours ___ je participe commence demain.", "Der Wettbewerb, an dem ich teilnehme, beginnt morgen.", "auquel", "participer à"),
    ("C'est un problème ___ je n'avais pas pensé.", "Das ist ein Problem, an das ich nicht gedacht hatte.", "auquel", "penser à"),
    ("Le poste ___ il a postulé est intéressant.", "Die Stelle, auf die er sich beworben hat, ist interessant.", "auquel", "postuler à"),
    ("Le jeu ___ les enfants jouent est nouveau.", "Das Spiel, das die Kinder spielen, ist neu.", "auquel", "jouer à"),
    ("Le club ___ elle appartient est très fermé.", "Der Club, dem sie angehört, ist sehr exklusiv.", "auquel", "appartenir à"),
    ("C'est un détail ___ je n'ai pas fait attention.", "Das ist ein Detail, auf das ich nicht geachtet habe.", "auquel", "faire attention à"),
    ("Le résultat ___ nous sommes arrivés est surprenant.", "Das Ergebnis, zu dem wir gekommen sind, ist überraschend.", "auquel", "arriver à"),
    ("Le cours ___ tu t'intéresses est complet.", "Der Kurs, für den du dich interessierst, ist ausgebucht.", "auquel", "s'intéresser à"),
    ("Le guichet ___ il faut s'adresser est fermé.", "Der Schalter, an den man sich wenden muss, ist geschlossen.", "auquel", "s'adresser à"),
    ("C'est un événement ___ j'assiste chaque année.", "Das ist eine Veranstaltung, die ich jedes Jahr besuche.", "auquel", "assister à"),
    ("C'est un changement ___ on doit s'habituer.", "Das ist eine Veränderung, an die man sich gewöhnen muss.", "auquel", "s'habituer à"),
    ("Le projet ___ elle tient le plus est celui-ci.", "Das Projekt, das ihr am meisten am Herzen liegt, ist dieses.", "auquel", "tenir à"),
    ("L'examen ___ je me prépare est difficile.", "Die Prüfung, auf die ich mich vorbereite, ist schwierig.", "auquel", "se préparer à"),
    ("Le message ___ tu n'as pas répondu était important.", "Die Nachricht, auf die du nicht geantwortet hast, war wichtig.", "auquel", "répondre à"),

    # ── auxquels ─────────────────────────────────────────────────────────
    ("Les jeux ___ nous jouons sont amusants.", "Die Spiele, die wir spielen, sind lustig.", "auxquels", "jouer à"),
    ("Les messages ___ je dois répondre sont urgents.", "Die Nachrichten, auf die ich antworten muss, sind dringend.", "auxquels", "répondre à"),
    ("Les clubs ___ il appartient sont sélectifs.", "Die Clubs, denen er angehört, sind exklusiv.", "auxquels", "appartenir à"),
    ("Les concerts ___ nous avons assisté étaient géniaux.", "Die Konzerte, die wir besucht haben, waren toll.", "auxquels", "assister à"),
    ("Les détails ___ tu fais attention sont importants.", "Die Details, auf die du achtest, sind wichtig.", "auxquels", "faire attention à"),
    ("Les sujets ___ elle s'intéresse sont variés.", "Die Themen, für die sie sich interessiert, sind vielfältig.", "auxquels", "s'intéresser à"),
    ("Les examens ___ ils se préparent sont difficiles.", "Die Prüfungen, auf die sie sich vorbereiten, sind schwer.", "auxquels", "se préparer à"),
    ("Les changements ___ nous devons nous habituer sont nombreux.", "Die Veränderungen, an die wir uns gewöhnen müssen, sind zahlreich.", "auxquels", "s'habituer à"),
    ("Les tournois ___ il a participé l'ont rendu célèbre.", "Die Turniere, an denen er teilgenommen hat, haben ihn berühmt gemacht.", "auxquels", "participer à"),
    ("Les postes ___ elle a postulé sont bien payés.", "Die Stellen, auf die sie sich beworben hat, sind gut bezahlt.", "auxquels", "postuler à"),
    ("Les objets ___ il tient le plus sont des souvenirs.", "Die Gegenstände, an denen er am meisten hängt, sind Andenken.", "auxquels", "tenir à"),
    ("Les problèmes ___ nous pensons sont sérieux.", "Die Probleme, an die wir denken, sind ernst.", "auxquels", "penser à"),

    # ── duquel ───────────────────────────────────────────────────────────
    ("Le parc près ___ nous habitons est calme.", "Der Park, in dessen Nähe wir wohnen, ist ruhig.", "duquel", "près de"),
    ("Le lac au bord ___ nous avons pique-niqué est magnifique.", "Der See, an dessen Ufer wir gepicknickt haben, ist herrlich.", "duquel", "au bord de"),
    ("Le bâtiment en face ___ se trouve la poste est moderne.", "Das Gebäude, gegenüber dem die Post liegt, ist modern.", "duquel", "en face de"),
    ("Le jardin au milieu ___ il y a une fontaine est public.", "Der Garten, in dessen Mitte ein Brunnen steht, ist öffentlich.", "duquel", "au milieu de"),
    ("L'arbre à l'ombre ___ je lis est un chêne.", "Der Baum, in dessen Schatten ich lese, ist eine Eiche.", "duquel", "à l'ombre de"),
    ("Le voyage au cours ___ ils se sont rencontrés était en Grèce.", "Die Reise, auf der sie sich kennengelernt haben, war in Griechenland.", "duquel", "au cours de"),
    ("Le château autour ___ on a construit le village est médiéval.", "Die Burg, um die herum das Dorf gebaut wurde, ist mittelalterlich.", "duquel", "autour de"),
    ("Le stade à côté ___ j'habite est bruyant.", "Das Stadion, neben dem ich wohne, ist laut.", "duquel", "à côté de"),
    ("Le bureau à l'intérieur ___ il travaille n'a pas de fenêtre.", "Das Büro, in dem er arbeitet, hat kein Fenster.", "duquel", "à l'intérieur de"),
    ("Le fleuve le long ___ nous marchons est la Seine.", "Der Fluss, an dem wir entlanggehen, ist die Seine.", "duquel", "le long de"),
    ("Le magasin en face ___ je t'attends est fermé.", "Der Laden, gegenüber dem ich auf dich warte, ist geschlossen.", "duquel", "en face de"),
    ("Le carrefour près ___ l'accident a eu lieu est dangereux.", "Die Kreuzung, in deren Nähe der Unfall passiert ist, ist gefährlich.", "duquel", "près de"),

    # ── desquels ─────────────────────────────────────────────────────────
    ("Les arbres au pied ___ poussent des champignons sont vieux.", "Die Bäume, an deren Fuß Pilze wachsen, sind alt.", "desquels", "au pied de"),
    ("Les magasins en face ___ il habite sont toujours pleins.", "Die Läden, gegenüber denen er wohnt, sind immer voll.", "desquels", "en face de"),
    ("Les immeubles à côté ___ on construit une école sont neufs.", "Die Gebäude, neben denen eine Schule gebaut wird, sind neu.", "desquels", "à côté de"),
    ("Les champs au milieu ___ se trouve la ferme sont immenses.", "Die Felder, in deren Mitte der Bauernhof liegt, sind riesig.", "desquels", "au milieu de"),
    ("Les projets au cours ___ j'ai beaucoup appris étaient difficiles.", "Die Projekte, bei denen ich viel gelernt habe, waren schwierig.", "desquels", "au cours de"),
    ("Les murs le long ___ nous marchons sont très hauts.", "Die Mauern, an denen wir entlanggehen, sind sehr hoch.", "desquels", "le long de"),
    ("Les bâtiments près ___ nous avons garé la voiture sont anciens.", "Die Gebäude, in deren Nähe wir das Auto geparkt haben, sind alt.", "desquels", "près de"),
    ("Les nuages au-dessus ___ vole l'avion sont gris.", "Die Wolken, über denen das Flugzeug fliegt, sind grau.", "desquels", "au-dessus de"),
    ("Les rochers au bord ___ on se baigne sont glissants.", "Die Felsen, an denen man badet, sind rutschig.", "desquels", "au bord de"),
    ("Les villages autour ___ il y a des vignes sont charmants.", "Die Dörfer, um die herum Weinberge liegen, sind reizend.", "desquels", "autour de"),
    ("Les événements à la suite ___ il a démissionné sont restés secrets.", "Die Ereignisse, nach denen er zurückgetreten ist, blieben geheim.", "desquels", "à la suite de"),
    ("Les jardins au fond ___ coule une rivière sont superbes.", "Die Gärten, an deren Ende ein Fluss fließt, sind herrlich.", "desquels", "au fond de"),

    # ── ce qui ───────────────────────────────────────────────────────────
    ("___ me plaît, c'est la mer.", "Was mir gefällt, ist das Meer.", "ce qui"),
    ("Je ne sais pas ___ est arrivé.", "Ich weiß nicht, was passiert ist.", "ce qui"),
    ("Dis-moi ___ ne va pas.", "Sag mir, was nicht stimmt.", "ce qui"),
    ("Il a oublié ___ était important.", "Er hat vergessen, was wichtig war.", "ce qui"),
    ("___ m'énerve, c'est le bruit.", "Was mich nervt, ist der Lärm.", "ce qui"),
    ("Raconte-moi ___ s'est passé hier soir.", "Erzähl mir, was gestern Abend passiert ist.", "ce qui"),
    ("Je comprends ___ te fait peur.", "Ich verstehe, was dir Angst macht.", "ce qui"),
    ("Elle fait toujours ___ lui plaît.", "Sie macht immer, was ihr gefällt.", "ce qui"),
    ("Prends ___ te semble utile.", "Nimm, was dir nützlich erscheint.", "ce qui"),
    ("Il est encore en retard, ___ arrive souvent.", "Er ist wieder zu spät, was oft vorkommt.", "ce qui"),
    ("On ne sait jamais ___ peut arriver.", "Man weiß nie, was passieren kann.", "ce qui"),
    ("___ compte, c'est la santé.", "Was zählt, ist die Gesundheit.", "ce qui"),
    ("Il a gagné, ___ a surpris tout le monde.", "Er hat gewonnen, was alle überrascht hat.", "ce qui"),
    ("Regarde ___ se trouve dans la boîte.", "Schau, was in der Schachtel ist.", "ce qui"),
    ("Je me demande ___ a causé la panne.", "Ich frage mich, was die Panne verursacht hat.", "ce qui"),
    ("Explique-moi ___ te dérange.", "Erklär mir, was dich stört.", "ce qui"),
    ("Tout ___ brille n'est pas or.", "Es ist nicht alles Gold, was glänzt.", "ce qui"),
    ("Choisis ___ te fait plaisir.", "Such dir aus, was dir Freude macht.", "ce qui"),

    # ── ce que ───────────────────────────────────────────────────────────
    ("___ je préfère, c'est le chocolat.", "Was ich am liebsten mag, ist Schokolade.", "ce que"),
    ("Dis-moi ___ tu veux.", "Sag mir, was du willst.", "ce que"),
    ("Je ne comprends pas ___ tu dis.", "Ich verstehe nicht, was du sagst.", "ce que"),
    ("Elle mange tout ___ sa grand-mère prépare.", "Sie isst alles, was ihre Großmutter kocht.", "ce que"),
    ("Je me demande ___ tu penses de mon idée.", "Ich frage mich, was du von meiner Idee hältst.", "ce que"),
    ("Montre-moi ___ tu as dessiné.", "Zeig mir, was du gezeichnet hast.", "ce que"),
    ("___ tu racontes est incroyable.", "Was du erzählst, ist unglaublich.", "ce que"),
    ("Il a fait exactement ___ je lui ai demandé.", "Er hat genau das gemacht, worum ich ihn gebeten habe.", "ce que"),
    ("Tu sais ___ j'aime chez toi ?", "Weißt du, was ich an dir mag?", "ce que"),
    ("___ nous avons vu était magnifique.", "Was wir gesehen haben, war wunderschön.", "ce que"),
    ("Je ne sais pas ___ je vais faire ce week-end.", "Ich weiß nicht, was ich am Wochenende machen werde.", "ce que"),
    ("Écoute ___ le professeur explique.", "Hör zu, was der Lehrer erklärt.", "ce que"),
    ("Il a perdu tout ___ son père lui avait laissé.", "Er hat alles verloren, was sein Vater ihm hinterlassen hatte.", "ce que"),
    ("C'est exactement ___ je pensais.", "Das ist genau das, was ich dachte.", "ce que"),
    ("Répète ___ tu viens de dire.", "Wiederhole, was du gerade gesagt hast.", "ce que"),
    ("Personne ne sait ___ Marie cherche.", "Niemand weiß, was Marie sucht.", "ce que"),
    ("___ je veux, c'est un peu de calme.", "Was ich will, ist ein bisschen Ruhe.", "ce que"),
    ("Achète ___ tu veux pour le dîner.", "Kauf, was du fürs Abendessen willst.", "ce que"),

    # ── ce dont ──────────────────────────────────────────────────────────
    ("C'est ___ j'ai besoin.", "Das ist das, was ich brauche.", "ce dont", "avoir besoin de"),
    ("Dis-moi ___ tu as envie.", "Sag mir, worauf du Lust hast.", "ce dont", "avoir envie de"),
    ("___ je me souviens, c'est son sourire.", "Woran ich mich erinnere, ist ihr Lächeln.", "ce dont", "se souvenir de"),
    ("Il ne sait pas ___ il parle.", "Er weiß nicht, wovon er redet.", "ce dont", "parler de"),
    ("___ elle a peur, c'est la solitude.", "Wovor sie Angst hat, ist die Einsamkeit.", "ce dont", "avoir peur de"),
    ("Voilà ___ je suis fier.", "Darauf bin ich stolz.", "ce dont", "être fier de"),
    ("Je ne sais pas ___ tu te plains.", "Ich weiß nicht, worüber du dich beklagst.", "ce dont", "se plaindre de"),
    ("Raconte-moi ___ tu rêves.", "Erzähl mir, wovon du träumst.", "ce dont", "rêver de"),
    ("___ il s'agit est très sérieux.", "Worum es geht, ist sehr ernst.", "ce dont", "il s'agit de"),
    ("Il a enfin obtenu ___ il rêvait.", "Er hat endlich bekommen, wovon er träumte.", "ce dont", "rêver de"),
    ("Prends ___ tu as besoin.", "Nimm, was du brauchst.", "ce dont", "avoir besoin de"),
    ("___ nous avons discuté reste entre nous.", "Worüber wir gesprochen haben, bleibt unter uns.", "ce dont", "discuter de"),
    ("C'est exactement ___ je me doutais.", "Das ist genau das, was ich vermutet hatte.", "ce dont", "se douter de"),
    ("Il oublie toujours ___ il est responsable.", "Er vergisst immer, wofür er verantwortlich ist.", "ce dont", "être responsable de"),
]

def main():
    data = json.loads(PATH.read_text(encoding="utf-8"))
    existing = {q["fr"] for q in data}
    added = 0
    for item in Q:
        fr, de, answer = item[0], item[1], item[2]
        cue = item[3] if len(item) > 3 else None
        assert fr.count("___") == 1, fr
        assert answer in DISTRACTORS, answer
        if fr in existing:
            continue
        data.append({
            "fr": fr,
            "de": de,
            "answer": answer,
            "distractors": DISTRACTORS[answer],
            "explanation": explanation(answer, cue),
        })
        existing.add(fr)
        added += 1
    # Append in the file's existing style (distractors on one line) to keep the diff readable
    new_entries = data[len(data) - added:]
    text = PATH.read_text(encoding="utf-8").rstrip()
    assert text.endswith("]")
    blocks = []
    for q in new_entries:
        blocks.append(
            "  {\n"
            f'    "fr": {json.dumps(q["fr"], ensure_ascii=False)},\n'
            f'    "de": {json.dumps(q["de"], ensure_ascii=False)},\n'
            f'    "answer": {json.dumps(q["answer"], ensure_ascii=False)},\n'
            f'    "distractors": {json.dumps(q["distractors"], ensure_ascii=False)},\n'
            f'    "explanation": {json.dumps(q["explanation"], ensure_ascii=False)}\n'
            "  }"
        )
    if blocks:
        text = text[:-1].rstrip() + ",\n" + ",\n".join(blocks) + "\n]"
    PATH.write_text(text + "\n", encoding="utf-8")
    json.loads(PATH.read_text(encoding="utf-8"))  # still valid JSON
    print(f"added {added}, total {len(data)}")

if __name__ == "__main__":
    main()

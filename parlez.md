# Parlez! — Konzeptentwurf für Hubert

**Game Mode #8: Freie Konversation mit KI-Bewertung**

> Rede 2 Minuten mit Hubert über ein Thema. Hubert hört zu, antwortet, und bewertet am Ende dein Gespräch.

---

## 1. Spielidee

Parlez! ist der erste **offene, generative** Spielmodus in Hubert. Statt vordefinierten Antworten
führt der Spieler ein echtes Gespräch auf Französisch. Hubert gibt ein Thema vor (z.B. "Au restaurant",
"Ton week-end", "Les vacances"), und der Spieler hat 2 Minuten, um sich auf Französisch zu unterhalten.

**Warum das in Hubert passt:** Alle bisherigen Modi testen isolierte Skills (Vokabeln, Gender, Konjugation,
Aussprache). Parlez! ist der "Endgegner" — hier muss alles zusammenkommen: Wortschatz, Grammatik,
Satzbau und Aussprache in Echtzeit.

### Ablauf

```
┌─────────────────────────────────────────────────────────┐
│  1. THEMA WÄHLEN                                        │
│     Spieler wählt oder bekommt ein Thema zugewiesen     │
│     (z.B. "Au marché" / "Chez le médecin")              │
├─────────────────────────────────────────────────────────┤
│  2. GESPRÄCH (2 Min Timer)                              │
│     Loop:                                               │
│       → Spieler spricht (Mikro-Button)                  │
│       → STT transkribiert                               │
│       → LLM generiert Antwort + spricht sie aus (TTS)   │
│       → Spieler antwortet erneut ...                    │
│     Timer läuft, Gesprächsverlauf wird gespeichert      │
├─────────────────────────────────────────────────────────┤
│  3. BEWERTUNG                                           │
│     LLM analysiert das gesamte Transkript und bewertet: │
│       • Vokabular (Vielfalt, Niveau)                    │
│       • Grammatik (Fehler, Korrektheit)                 │
│       • Kohärenz (roter Faden, Themenbezug)             │
│       • Aussprache (Azure Scores, falls verfügbar)      │
│     → Gesamtscore + detailliertes Feedback              │
│     → Fehler-Highlights mit Korrekturen                 │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Technische Architektur

### 2.1 Komponenten-Übersicht

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Android     │     │   Speech     │     │     LLM      │
│   App (UI)    │────▶│   Services   │────▶│   Backend    │
│              │     │              │     │              │
│ • Timer       │     │ • STT        │     │ • Konversa-  │
│ • Mikro-UI    │     │   (Whisper/  │     │   tionsführ. │
│ • Chat-Ansicht│     │    Azure)    │     │ • Bewertung  │
│ • Bewertungs- │     │ • TTS        │     │ • Fehler-    │
│   screen      │     │   (Android/  │     │   analyse    │
│ • Transkript  │     │    Azure)    │     │              │
└──────────────┘     └──────────────┘     └──────────────┘
```

### 2.2 LLM-Auswahl — Empfehlung

Gemini 2.5 Flash 

**Empfehlung: Zweistufiger Ansatz**

1. **Konversation (Echtzeit):** Groq mit Llama 3.1 8B oder Gemini 2.0 Flash
   - Latenz ist King — der Spieler wartet auf jede Antwort
   - Bei Voice-Konversation ist alles über 1s spürbar
   - Groq ist extrem schnell und hat ein kostenloses Tier
   - Gemini 2.0 Flash hat native Multimodal-Voice (kein separates STT nötig)

2. **Bewertung (nach dem Gespräch):** GPT-4o oder Claude 3.5 Sonnet
   - Hier ist Latenz egal (Spieler wartet auf Ergebnis-Screen)
   - Qualität der Analyse zählt mehr
   - Besseres Verständnis von Grammatikfehlern im Französischen

**Alternative: OpenAI Realtime API**
- Kombiniert STT + LLM + TTS in einem Stream
- Niedrigste wahrgenommene Latenz, natürlichstes Gespräch
- Teurer (~$0.06/min Audio), aber ideal für 2-Minuten-Sessions
- Bereits im Hubert-Ökosystem: Azure Speech Services ist ähnlich konfiguriert

**Alternative: Gemini Live / Gemini 2.0 Flash Multimodal**
- Kostenloser API-Zugang möglich
- Native Voice-to-Voice ohne separates STT
- Problem (das du ja kennst): Kontext geht leichter verloren
- Aber: mit einem guten System-Prompt und nur 2 Minuten Gespräch → machbar

### 2.3 Speech-to-Text

Du hast bereits Azure Speech Services für Prononcez! integriert. Optionen:

| Option                          | Pro                                           | Contra                              |
|---------------------------------|-----------------------------------------------|-------------------------------------|
| **Azure Speech (bestehend)**    | Bereits integriert, Pronunciation Assessment   | Latenz ~1-2s, kostet nach Free Tier |
| **OpenAI Whisper API**          | Sehr gute Französisch-Erkennung               | Keine Echtzeit, Batch-basiert       |
| **OpenAI Realtime API**         | STT+LLM+TTS in einem Stream                   | Teurer, neues SDK                   |
| **Google Speech-to-Text**       | Gute FR-Erkennung, Streaming möglich           | Separater API-Key                   |
| **On-Device (Whisper.cpp)**     | Offline, keine Kosten, keine Latenz            | Modellgröße ~40MB, CPU-intensiv     |

**Empfehlung:** Azure Speech Services weiterverwenden (ist schon in der App), aber das
Pronunciation Assessment nur für die Endbewertung nutzen, nicht für jede einzelne Äußerung.
Für die Live-Transkription während des Gesprächs reicht der reguläre STT-Modus.

### 2.4 Text-to-Speech

Android TTS (French locale) ist bereits in Hubert integriert und reicht für Huberts Antworten.
Für natürlichere Stimmen optional: Azure Neural TTS oder ElevenLabs (aber: Kosten + Latenz).

---

## 3. System-Prompt für die Konversation

```
Tu es Hubert, un assistant de conversation français amical et patient.

RÔLE: Tu aides un germanophone à pratiquer le français oral dans une
conversation de 2 minutes sur le thème: "{THEMA}".

RÈGLES DE CONVERSATION:
1. Parle UNIQUEMENT en français, niveau {NIVEAU} (A1/A2/B1/B2)
2. Pose des questions ouvertes pour encourager le joueur à parler davantage
3. Reste STRICTEMENT dans le thème donné — c'est ton fil rouge
4. Si le joueur fait une erreur grammaticale grave, corrige-la naturellement
   dans ta réponse (reformulation) sans interrompre le flux
5. Tes réponses doivent être COURTES (1-3 phrases max) pour laisser
   le joueur parler plus que toi
6. Adapte ton vocabulaire au niveau du joueur
7. Si le joueur utilise un mot allemand, donne-lui le mot français
   et continue la conversation

PERSONNALITÉ:
- Enthousiaste mais pas exagéré
- Curieux — pose des questions de suivi
- Encourage les détails ("Ah intéressant ! Et pourquoi ?")
- Utilise des expressions idiomatiques simples
- Ne traduis JAMAIS en allemand sauf si le joueur est complètement bloqué

FORMAT DE RÉPONSE:
Réponds uniquement avec ton texte de conversation. Pas de métadonnées,
pas d'annotations, pas de corrections explicites entre crochets.
```

### Niveau-Anpassung

| Niveau | Wortschatz         | Satzlänge    | Themen                           |
|--------|--------------------|--------------|----------------------------------|
| A1     | Basis (~500 Wörter)| 5-8 Wörter   | Se présenter, la famille, manger |
| A2     | Alltag (~1500)     | 8-12 Wörter  | Einkaufen, Reisen, Hobbys        |
| B1     | Erweitert (~3000)  | 12-18 Wörter | Arbeit, Nachrichten, Meinungen   |
| B2     | Fortgeschritten    | Unbegrenzt   | Gesellschaft, Kultur, Debatte    |

**Niveau-Erkennung:** Könnte automatisch aus den bisherigen Hubert-Statistiken abgeleitet werden.
Wenn der Spieler bei Trouvez!/Écrivez! regelmäßig hohe Scores hat → B1/B2 vorschlagen.

---

## 4. System-Prompt für die Bewertung

```
Tu es un professeur de français certifié. Évalue la performance d'un
germanophone dans une conversation de 2 minutes en français.

TRANSCRIPTION DU JOUEUR:
{SPIELER_NACHRICHTEN}

TRANSCRIPTION COMPLÈTE (avec les réponses de Hubert):
{VOLLES_TRANSKRIPT}

THÈME DE LA CONVERSATION: {THEMA}
NIVEAU CIBLE: {NIVEAU}

Évalue selon ces 5 critères (chacun sur 20 points, total sur 100):

1. VOCABULAIRE (20 pts)
   - Variété des mots utilisés (pas de répétition excessive)
   - Adéquation au thème
   - Utilisation de mots au-dessus du niveau de base
   Compte: nombre de mots uniques, mots thématiques, mots avancés

2. GRAMMAIRE (20 pts)
   - Conjugaisons correctes
   - Accords (genre, nombre)
   - Structure de phrase
   - Articles et prépositions
   Liste CHAQUE erreur grammaticale avec la correction

3. COHÉRENCE (20 pts)
   - Le joueur reste-t-il dans le thème ?
   - Y a-t-il un fil rouge dans la conversation ?
   - Les réponses sont-elles logiques et pertinentes ?

4. FLUIDITÉ (20 pts)
   - Longueur moyenne des réponses du joueur
   - Le joueur développe-t-il ses idées ou répond-il par un mot ?
   - Utilisation de connecteurs (mais, donc, parce que, ensuite...)

5. EFFORT & PRISE DE RISQUE (20 pts)
   - Le joueur essaie-t-il des structures complexes ?
   - Utilise-t-il des expressions idiomatiques ?
   - Sort-il de sa zone de confort ?

RÉPONDS EN JSON STRICT (pas de markdown, pas de backticks):
{
  "scores": {
    "vocabulaire": { "score": 0-20, "commentaire": "..." },
    "grammaire": { "score": 0-20, "commentaire": "..." },
    "coherence": { "score": 0-20, "commentaire": "..." },
    "fluidite": { "score": 0-20, "commentaire": "..." },
    "effort": { "score": 0-20, "commentaire": "..." }
  },
  "total": 0-100,
  "erreurs": [
    { "original": "ce que le joueur a dit", "correction": "forme correcte", "explication": "courte explication en allemand" }
  ],
  "mots_appris": ["liste", "de", "nouveaux", "mots", "utilisés"],
  "conseil": "Un conseil personnalisé pour progresser (en allemand)"
}
```

---

## 5. Themen-Pool

Themen könnten an die bestehenden 28 Kategorien in `categories.json` angelehnt sein:

### Einfach (A1-A2)
| Thema               | Beschreibung                        | Kategorie-Link        |
|----------------------|------------------------------------|-----------------------|
| Au café              | Getränk bestellen, Small Talk      | Essen und Trinken     |
| Ma famille           | Familie beschreiben                | Familie               |
| Les animaux          | Über Haustiere/Tiere reden         | Tiere                 |
| Les couleurs         | Farben beschreiben, Kleidung       | Farben, Kleidung      |
| Au supermarché       | Einkaufen, Preise, Mengen          | Essen und Trinken     |

### Mittel (B1)
| Thema               | Beschreibung                        | Kategorie-Link        |
|----------------------|------------------------------------|-----------------------|
| Mon travail          | Beruf beschreiben, Arbeitsalltag   | Arbeit                |
| Les vacances         | Reisepläne, Erlebnisse erzählen    | Reise                 |
| Chez le médecin      | Symptome beschreiben               | Gesundheit            |
| Le week-end dernier  | Vergangenheit erzählen (Passé C.)  | Freizeit              |
| La météo             | Wetter, Jahreszeiten, Pläne        | Wetter                |

### Fortgeschritten (B2)
| Thema               | Beschreibung                        |
|----------------------|------------------------------------|
| L'actualité          | Über Nachrichten diskutieren       |
| L'environnement      | Umwelt, Klimawandel                |
| La technologie       | KI, Social Media, Pro/Contra       |
| Un film récent       | Beschreiben, bewerten, empfehlen   |

### Datenformat (neue Datei: `parlez_topics.json`)

```json
[
  {
    "id": "cafe",
    "theme_fr": "Au café",
    "theme_de": "Im Café",
    "niveau": "A1",
    "description_de": "Bestelle ein Getränk und unterhalte dich mit dem Kellner",
    "starter_fr": "Bonjour ! Bienvenue au café. Qu'est-ce que vous désirez ?",
    "vocab_hints": ["commander", "l'addition", "un café crème", "s'il vous plaît"],
    "category_links": ["Essen und Trinken"]
  }
]
```

---

## 6. Scoring-System

### Integration in Huberts Scoring-Logik

Parlez! nutzt ein **punktebasiertes System** (wie Prononcez!), aber statt Start mit 10 Punkten
wird nach dem Gespräch ein Score von 0-100 vergeben.

```
Gesamtscore = Vocabulaire (20) + Grammaire (20) + Cohérence (20) 
            + Fluidité (20) + Effort (20)
```

### High-Score-Integration

- Neuer `gameType = "PARLEZ"` in der Room-Datenbank
- High-Score-Tabelle zeigt: Score, Thema, Datum
- "Hubert choisit!" berücksichtigt Parlez! bei der Spielzeit-Balance

### Statistik-Integration

- `WordAttempt`-Tracking: Neue Vokabeln, die der Spieler im Gespräch verwendet hat → als "geübt" markieren
- Fehler-Wörter aus der LLM-Bewertung → "Words I Struggle With" füttern
- Achievement-Ideen:
  - "Premier mot" — Erstes Parlez!-Gespräch abgeschlossen
  - "Bavard" — 5 Gespräche mit Score > 60
  - "Eloquent" — Ein Gespräch mit Score > 80
  - "Philosophe" — B2-Thema mit Score > 70
  - "Sans faute" — Gespräch mit 20/20 Grammatik

---

## 7. UI-Konzept (Jetpack Compose)

### 7.1 Themenauswahl-Screen

```
┌───────────────────────────────┐
│         🎙️ PARLEZ!            │
│                               │
│  Choisis un thème:            │
│                               │
│  ┌─────────────────────────┐  │
│  │ ☕ Au café          A1  │  │
│  └─────────────────────────┘  │
│  ┌─────────────────────────┐  │
│  │ 👨‍👩‍👧 Ma famille       A1  │  │
│  └─────────────────────────┘  │
│  ┌─────────────────────────┐  │
│  │ ✈️ Les vacances     B1  │  │
│  └─────────────────────────┘  │
│  ┌─────────────────────────┐  │
│  │ 🌍 L'environnement  B2  │  │
│  └─────────────────────────┘  │
│                               │
│  Niveau: [A1] [A2] [B1] [B2] │
└───────────────────────────────┘
```

### 7.2 Gesprächs-Screen

```
┌───────────────────────────────┐
│  Au café          ⏱️ 1:42     │
│───────────────────────────────│
│                               │
│  🤖 Bonjour ! Bienvenue au    │
│     café. Qu'est-ce que vous  │
│     désirez ?                 │
│                               │
│         Je voudrais un café   │
│         crème, s'il vous plaît│
│                     👤        │
│                               │
│  🤖 Excellent choix ! Avec    │
│     un croissant peut-être ?  │
│                               │
│───────────────────────────────│
│                               │
│         🎤 Appuie pour parler │
│        [     🔴 REC     ]     │
│                               │
└───────────────────────────────┘
```

### 7.3 Bewertungs-Screen

```
┌───────────────────────────────┐
│     🎙️ PARLEZ! — Résultat     │
│                               │
│         ⭐ 72 / 100           │
│                               │
│  Vocabulaire    ████████░░ 16 │
│  Grammaire      ██████░░░░ 12 │
│  Cohérence      █████████░ 18 │
│  Fluidité       ███████░░░ 14 │
│  Effort         ████████░░ 12 │
│                               │
│  ── Fehler ──                 │
│  ✗ "je suis allé au café"    │
│  ✓ "je suis allé au café" ✓  │
│    → OK! (Hier stimmte es)   │
│                               │
│  ✗ "il faut que je va"       │
│  ✓ "il faut que j'aille"     │
│    → Subjonctif nach          │
│      "il faut que"            │
│                               │
│  ── Neue Vokabeln ──         │
│  croissant · l'addition ·     │
│  commander · s'asseoir        │
│                               │
│  💡 Tipp: Versuche mehr       │
│  Konnektoren zu nutzen (mais, │
│  donc, parce que...)          │
│                               │
│  [🔄 Nochmal]  [🏠 Menü]     │
└───────────────────────────────┘
```

---

## 8. Implementierungsplan

### Phase 1: MVP (Minimal Viable "Parlez!")

| Schritt | Beschreibung                                          | Aufwand   |
|---------|------------------------------------------------------|-----------|
| 1       | `parlez_topics.json` erstellen (10 Themen)           | 1h        |
| 2       | API-Key-Management erweitern (LLM Key + Azure Key)   | 2h        |
| 3       | `ParlezViewModel.kt` — Timer, Chat-State, Recording  | 8h        |
| 4       | LLM-Client (REST) für Konversation                   | 4h        |
| 5       | `ParlezScreen.kt` — Chat-UI mit Mikro-Button         | 6h        |
| 6       | LLM-Bewertung nach Gespräch + JSON-Parsing           | 4h        |
| 7       | Bewertungs-Screen (Scores + Fehler)                  | 4h        |
| 8       | Room-DB erweitern (gameType PARLEZ)                  | 2h        |
| 9       | MenuScreen.kt anpassen (8. Karte)                    | 1h        |
| **Σ**   |                                                      | **~32h**  |

### Phase 2: Polish

| Schritt | Beschreibung                                          |
|---------|------------------------------------------------------|
| 10      | Azure Pronunciation Assessment für Einzelwörter      |
| 11      | Statistik-Integration (WordAttempt-Tracking)         |
| 12      | Achievements hinzufügen                              |
| 13      | "Hubert choisit!" Integration                        |
| 14      | Adaptive Niveau-Erkennung aus bestehenden Stats      |

### Phase 3: Nice-to-have

| Feature                | Beschreibung                                    |
|------------------------|-------------------------------------------------|
| Gesprächsverlauf       | Alte Gespräche ansehen und Fortschritt vergleich.|
| Streak-System          | Tägliche Konversations-Challenges               |
| Szenario-Ketten        | Zusammenhängende Gespräche über mehrere Sessions |
| Offline-Modus          | On-Device LLM (Phi-3-mini / Gemma 2B)          |

---

## 9. API-Key-Management

Hubert hat bereits ein Settings-Dialog für den Azure Speech API-Key (Prononcez!).
Parlez! braucht zusätzlich einen LLM-API-Key.

**Empfehlung:** Gleiche Settings-UI wie bei Prononcez! erweitern:

```
Settings
├── Azure Speech Services
│   ├── API Key: ••••••••
│   └── Region: northeurope
└── LLM Service (NEU)
    ├── Provider: [Groq ▾]  (Groq / OpenAI / Gemini)
    ├── API Key: ••••••••
    └── Model: llama-3.1-8b-instant
```

Gespeichert via Jetpack DataStore (wie bestehende Azure-Settings).

---

## 10. Kosten-Abschätzung pro Gespräch

| Komponente               | Geschätzte Kosten (2 Min Gespräch) |
|--------------------------|-----------------------------------|
| STT (Azure, bestehend)   | ~$0.01 (1 Min Audio ≈ $0.01)     |
| LLM Konversation (Groq)  | $0.00 (Free Tier: 14k req/Tag)   |
| LLM Bewertung (GPT-4o-m) | ~$0.002 (~2k Tokens)             |
| TTS (Android built-in)   | $0.00                             |
| **Gesamt**               | **~$0.01 pro Gespräch**          |

Mit Groq + Android TTS + Azure STT (Free Tier: 5h/Monat) kommt man auf
ca. **150 kostenlose Gespräche pro Monat**. Danach ~1 Cent pro Gespräch.

---

## 11. Offene Fragen & Entscheidungen

1. **Soll Hubert während des Gesprächs Fehler korrigieren?**
   - Option A: Nur am Ende (weniger Unterbrechung, natürlicherer Flow)
   - Option B: Subtil in der Antwort reformulieren (natürliche Korrektur)
   - Empfehlung: **Option B** — das ist wie ein nativer Sprecher es tun würde

2. **Push-to-Talk oder automatische Spracherkennung?**
   - Push-to-Talk ist einfacher und zuverlässiger
   - VAD (Voice Activity Detection) fühlt sich natürlicher an
   - Empfehlung: **Push-to-Talk** für MVP, VAD als spätere Option

3. **Soll die Bewertung auf Deutsch oder Französisch sein?**
   - Deutsch macht mehr Sinn für Erklärungen von Grammatikfehlern
   - Empfehlung: **Scores/Labels auf Französisch, Erklärungen auf Deutsch**

4. **Wie mit leeren Antworten / Stille umgehen?**
   - Wenn der Spieler 15s nichts sagt → Hubert ermutigt: "Tu veux continuer?"
   - Wenn 30s Stille → Timer-Strafe oder sanfter Hinweis

5. **Soll das der Konversations-LLM und der Bewertungs-LLM der gleiche sein?**
   - Gleicher: Einfacher, weniger API-Keys
   - Verschieden: Optimiert jeweils für Speed vs. Qualität
   - Empfehlung: **Gleicher Provider, verschiedene Modelle** (z.B. Groq Llama für Chat, dann GPT-4o-mini für Bewertung)

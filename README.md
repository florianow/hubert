<div align="center">
  <img src="logo.png" alt="Hubert Logo" width="120" height="120">
  <h1>Hubert</h1>
  <p><strong>Gamified French-German vocabulary trainer with 7 game modes + smart practice picker</strong></p>
  <p>
    <a href="#features">Features</a> •
    <a href="#game-modes">Game Modes</a> •
    <a href="#getting-started">Getting Started</a> •
    <a href="#project-structure">Project Structure</a> •
    <a href="#data-pipeline">Data Pipeline</a> •
    <a href="#contributing">Contributing</a> •
    <a href="#license">License</a>
  </p>
  <p>
    <img src="https://img.shields.io/badge/Android-8.0%2B-green?logo=android" alt="Android 8.0+">
    <img src="https://img.shields.io/badge/Kotlin-1.9-blue?logo=kotlin" alt="Kotlin">
    <img src="https://img.shields.io/badge/License-GPLv3-blue" alt="GPL v3 License">
    <img src="https://github.com/florianow/hubert/workflows/Android%20CI/badge.svg" alt="CI Status">
  </p>
</div>

---

## About

Hubert is a Duolingo-style Android app for learning French-German vocabulary through fast-paced mini-games. Built with Kotlin, Jetpack Compose, and Material Design 3, it features ~5000 vocabulary cards sourced from the [anki_french](https://github.com/jacbz/anki_french) Anki deck — including gender, IPA pronunciation, thematic categories, example sentences, and full verb conjugation tables across 8 tenses.

Two scoring systems keep things interesting:
- **Timer-based** (Trouvez!, Classez!, Compl\u00E9tez!, \u00C9crivez!, Conjuguez!): Countdown timer with time bonuses for correct answers and penalties for wrong ones.
- **Points-based** (Prononcez!): Start with 10 points. Earn +3 per correct answer (plus streak bonus), lose 5 for each mistake \u2014 game over when you hit 0.

French words and sentences are spoken aloud via Android's built-in Text-to-Speech engine, and Prononcez! uses Azure Speech Services to assess your pronunciation in real time.

## Features

- **7 Game Modes** — Trouvez!, Classez!, Compl\u00E9tez!, \u00C9crivez!, Conjuguez!, Prononcez!, Préposez!
- **Hubert choisit!** — Smart practice picker that launches the game mode you've played the least, encouraging balanced practice
- **5000 Vocabulary Cards** — sourced from a curated French Anki deck
- **IPA Phonetic Transcription** — shown alongside French words in Trouvez!, Classez!, and Conjuguez!
- **Pronunciation Assessment** — read French sentences aloud, scored by Azure Speech Services, with second-chance retry
- **Text-to-Speech** — hear correct French pronunciation after every correct answer
- **Streak System** — consecutive correct answers earn bonus points
- **High Scores** — per-game-type leaderboard stored locally
- **Statistics & Word Tracking** — per-word attempt tracking, "Words I Struggle With" section, achievements
- **28 Thematic Categories** — Tiere, Farben, Essen und Trinken, Familie, and more
- **3076 Nouns with Gender** — masculine/feminine data for Classez!
- **~2000 Example Sentences** — real French sentences with German translations for Compl\u00E9tez!
- **1182 Verbs with Conjugations** — full conjugation tables across 8 tenses for Conjuguez!
- **Adaptive Difficulty** — Prononcez! adjusts sentence length based on your streak; Conjuguez! error-weights tenses you struggle with
- **Tense Info with Limits** — Conjuguez! shows grammar info dialogs with conjugation tables; limited to 3 views per tense per round with timer pause
- **Manual Advance** — Conjuguez! and Prononcez! wait for you to tap "Next" instead of auto-advancing
- **Offline-first** — all data bundled as JSON assets, no network required (except Prononcez! which uses Azure Speech Services)
- **Material Design 3** — clean, modern UI with themed game colors

## Game Modes

### Hubert choisit!

A smart practice picker — tap to let Hubert choose which game you should play next. Hubert queries your total play time per game mode and launches the one you've practiced the least, encouraging balanced learning across all modes. A toast shows which game was selected.

### 1. Trouvez!

Match French words on the left with their German translations on the right. Four pairs are shown at a time — tap a French word, then tap its German match. Matched pairs are replaced immediately so the game flows continuously. IPA phonetic transcription is shown on each French card.

- **Scoring**: 100 pts per match + streak bonus
- **Timer**: 60s, +2s correct, -5s wrong

### 2. Classez!

A French noun appears on screen with its IPA transcription — tap whether it takes the masculine article (**le**) or the feminine article (**la**). Only pure nouns are included (no verbs, pronouns, or adjectives that happen to have a grammatical gender).

- **Scoring**: 100 pts per correct + streak bonus
- **Timer**: 60s, +2s correct, -5s wrong
- **Pool**: ~2100 nouns with known gender

### 3. Compl\u00E9tez!

A French sentence is shown with one word blanked out. The German translation is displayed as a hint. Pick the correct word from 4 choices — distractors are drawn from the same thematic category as the answer (e.g., if the answer is "chat", other options come from the "Tiere" category).

- **Scoring**: 150 pts per correct + streak bonus
- **Timer**: 60s, +2s correct, -5s wrong
- **Pool**: ~2000 words with example sentences

### 4. \u00C9crivez!

Hear a French word spoken via TTS, then type it. The German translation is shown as a hint. Accents are forgiven — "cafe" matches "caf\u00E9".

- **Scoring**: 200 pts per correct + streak bonus
- **Timer**: 60s, +2s correct, -5s wrong
- **Pool**: all 5000 words

### 5. Conjuguez!

A verb conjugation challenge. Before the game starts, you choose which tenses to practice (Présent, Imparfait, Futur, Conditionnel, Subjonctif, Passé simple, Impératif, Passé composé). During gameplay, tenses are picked randomly from your selection with error-weighted repetition — tenses you get wrong appear more frequently.

When a matching example sentence exists in the dataset, you see a French sentence with the verb blanked out, plus the German translation as context. Otherwise, a plain drill card shows the infinitive (with IPA transcription) and subject pronoun. Pick the correct conjugated form from 4 choices — distractors are other forms of the same verb (different tenses or persons). Pass\u00E9 compos\u00E9 has two question types that alternate: auxiliary selection (avoir/\u00EAtre) and verb form selection. Tap the tense name to see a grammar info dialog with German explanations, examples, and conjugation formation tables (limited to 3 views per tense per round — timer pauses while viewing, lock icon shown when exhausted). After answering, tap "Next" to advance manually.

- **Scoring**: Timer-based — 90s countdown, +5s correct, -5s wrong, 300s cap. 150 pts per correct + 30 per streak level
- **Pool**: 1182 verbs across 8 tenses (Pr\u00E9sent, Imparfait, Futur, Conditionnel, Subjonctif, Pass\u00E9 simple, Imp\u00E9ratif, Pass\u00E9 compos\u00E9)

### 7. Préposez!

A French sentence is shown with the preposition blanked out. The German translation is shown as a hint. Pick the correct preposition from 4 choices — covering à, de, en, dans, sur, avec, sans, par, pour, avant, après, entre, contre, sous, vers, chez.

- **Scoring**: 150 pts per correct + streak bonus
- **Timer**: 60s, +5s correct, -5s wrong
- **Pool**: 764 sentences extracted from [anki_french](https://github.com/jacbz/anki_french) preposition cards

### 6. Prononcez!

Read French sentences aloud and get scored on your pronunciation by Azure Speech Services. A reference sentence is displayed (French + German translation), you record yourself reading it, and Azure returns a pronunciation score with per-word accuracy feedback. Words you mispronounced are highlighted in the results. After each attempt, you can compare your pronunciation to the correct one — tap "Correct" to hear the sentence via TTS, or "Yours" to play back your own recording. Tap "Next" to advance manually.

Difficulty adapts to your streak: short sentences at first (Facile, <= 6 words), medium sentences as you improve (Moyen, 7-10 words), and long sentences at high streaks (Difficile, 11+ words).

**Second chance**: If you score 80-94 on your first attempt, you get a retry — tap "TRY AGAIN" to re-record the same sentence at a lower pass threshold of 85. Score below 80 and it's an immediate fail; score 95+ and it's an immediate pass.

- **Scoring**: Points-based — start with 10 pts, +3 correct + streak bonus, -5 wrong, game over at 0
- **Pass threshold**: PronScore >= 95 (first attempt), >= 85 (second attempt)
- **Retry range**: PronScore 80-94 on first attempt earns a second chance
- **Requires**: Azure Speech Services API key (configured in-app via settings dialog)
- **Game over stats**: Average/best/worst pronunciation scores, most mispronounced words

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: Hilt
- **Database**: Room (high scores, game sessions, word attempts)
- **Preferences**: Jetpack DataStore (Azure API settings)
- **JSON Parsing**: Gson
- **Text-to-Speech**: Android TTS (French locale)
- **Pronunciation Assessment**: Azure Speech Services REST API
- **Audio Recording**: Android AudioRecord (16 kHz, 16-bit, mono PCM → WAV)
- **Asynchronous**: Kotlin Coroutines & Flow
- **Build**: Gradle (Kotlin DSL)
- **CI/CD**: GitHub Actions (automatic APK builds on tag push)
- **Data Pipeline**: Python (YAML parsing, HTML scraping)

## Screenshots

<table>
  <tr>
    <td align="center"><img src="screenshots/01-menu.png" width="240"><br><b>Main Menu</b></td>
    <td align="center"><img src="screenshots/13-menu-games.png" width="240"><br><b>All 6 Game Modes</b></td>
    <td align="center"><img src="screenshots/02-word-match.png" width="240"><br><b>Trouvez!</b></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/03-word-match-correct.png" width="240"><br><b>Trouvez! — Correct</b></td>
    <td align="center"><img src="screenshots/04-le-ou-la-baguette.png" width="240"><br><b>Classez!</b></td>
    <td align="center"><img src="screenshots/05-gap-fill.png" width="240"><br><b>Compl\u00E9tez!</b></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/14-conjuguez-tenses.png" width="240"><br><b>Conjuguez! — Tense Selection</b></td>
    <td align="center"><img src="screenshots/15-conjuguez-gameplay.png" width="240"><br><b>Conjuguez! — Gameplay</b></td>
    <td align="center"><img src="screenshots/09-prononcez-ready.png" width="240"><br><b>Prononcez! — Ready</b></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/10-prononcez-recording.png" width="240"><br><b>Prononcez! — Recording</b></td>
    <td align="center"><img src="screenshots/11-prononcez-correct.png" width="240"><br><b>Prononcez! — Correct (100)</b></td>
    <td align="center"><img src="screenshots/08-prononcez-wrong.png" width="240"><br><b>Prononcez! — Wrong (65)</b></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/12-prononcez-retry.png" width="240"><br><b>Prononcez! — Second Chance</b></td>
    <td align="center"><img src="screenshots/07-statistics-detail.png" width="240"><br><b>Statistics & Score Trend</b></td>
    <td align="center"><img src="screenshots/06-statistics-achievements.png" width="240"><br><b>Achievements</b></td>
  </tr>
</table>

## Getting Started

### Download Pre-built APK

1. Go to [Releases](https://github.com/florianow/hubert/releases)
2. Download the latest `hubert-*.apk`
3. Transfer to your Android device
4. Enable "Install from Unknown Sources" in Settings
5. Open the APK and install

### Prerequisites (for building from source)

- Android Studio (latest version recommended)
- Android SDK 26+ (Android 8.0 Oreo or higher)
- JDK 17 or higher
- Python 3 (only needed to regenerate vocabulary data)

### Building the App

#### Option 1: Using Android Studio (Recommended)

1. Clone the repository:
```bash
git clone https://github.com/florianow/hubert.git
cd hubert
```

2. Open the project in Android Studio:
   - File → Open → Select the project folder
   - Wait for Gradle sync to complete
   - Click Run or press `Shift+F10`

#### Option 2: Using Command Line

**On macOS:**

```bash
git clone https://github.com/florianow/hubert.git
cd hubert

# Build the debug APK (using Android Studio's bundled JDK)
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

**On Linux/Windows:**

```bash
./gradlew assembleDebug
```

**Build Output:**
- The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

#### Option 3: Install to Connected Device/Emulator

```bash
./gradlew installDebug
```

## Data Pipeline

The vocabulary data is sourced from the [anki_french](https://github.com/jacbz/anki_french) repository and converted into JSON asset files by Python scripts. Conjugation data is extracted from the Anki deck's `.apkg` database.

### Running the Vocabulary Pipeline

```bash
python3 scripts/convert_vocab.py
```

This downloads the YAML card data and parses the thematic category HTML files, producing:

| File | Contents | Size |
|------|----------|------|
| `app/src/main/assets/vocab.json` | 5000 words with rank, french, german, gender, IPA, part of speech, categories | ~535 KB |
| `app/src/main/assets/categories.json` | 28 thematic categories → list of word rank numbers | ~12 KB |
| `app/src/main/assets/sentences.json` | ~2000 words → up to 5 example sentences each (French + German + blank word) | ~1.4 MB |

### Running the Conjugation Pipeline

```bash
python3 scripts/extract_conjugations.py
```

This reads the local Anki database (requires the "Französisch 5000" deck imported in Anki), parses HTML conjugation tables, matches example sentences to conjugated forms, and produces:

| File | Contents | Size |
|------|----------|------|
| `app/src/main/assets/conjugations.json` | 1182 verbs with conjugation forms across 8 tenses + matched example sentences | ~2 MB |

### Data Fields

Each vocabulary word includes:

- **rank** — frequency rank (1–5000)
- **french** — French word
- **german** — German translation
- **gender** — `"m"` (masculine) or `"f"` (feminine), null if not a noun
- **ipa** — IPA pronunciation
- **pos** — part of speech (`nm`, `nf`, `vt`, `vi`, `adj`, `adv`, etc.)
- **categories** — list of thematic categories the word belongs to

## Project Structure

```
app/src/main/java/com/hubert/
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt          # Room database (v4, high scores + sessions + word attempts)
│   │   ├── HighScoreDao.kt         # Per-game-type score queries
│   │   ├── GameSessionDao.kt       # Game session history queries
│   │   └── WordAttemptDao.kt       # Per-word attempt tracking queries
│   ├── model/
│   │   ├── VocabWord.kt            # VocabWord, SentenceEntry, ConjugationVerb data classes
│   │   ├── HighScore.kt            # Room entity with gameType field
│   │   ├── GameSession.kt          # Room entity for game session history
│   │   └── WordAttempt.kt          # Room entity for per-word attempt tracking
│   └── repository/
│       ├── VocabRepository.kt      # Vocab, categories, sentences, conjugations, IPA lookup
│       ├── HighScoreRepository.kt  # Save/query scores by game type
│       └── StatisticsRepository.kt # Game sessions, word attempts, achievements
├── di/
│   └── DatabaseModule.kt           # Hilt module (Room + TTS singletons)
├── ui/
│   ├── screens/
│   │   ├── MenuScreen.kt           # Main menu with Hubert choisit! + 7 game mode cards
│   │   ├── GameScreen.kt           # Trouvez! game UI (with IPA on French cards)
│   │   ├── GenderSnapScreen.kt     # Classez! game UI (with IPA)
│   │   ├── GapFillScreen.kt        # Compl\u00E9tez! game UI
│   │   ├── SpellingBeeScreen.kt    # \u00C9crivez! game UI
│   │   ├── ConjugationScreen.kt    # Conjuguez! game UI (with IPA, timer, info limits)
│   │   ├── PronunciationScreen.kt  # Prononcez! game UI (recording, word-level feedback, retry)
│   │   ├── PrepositionScreen.kt    # Préposez! game UI
│   │   ├── GameOverScreen.kt       # Generic game over screen (with answer history detail)
│   │   ├── StatisticsScreen.kt     # Statistics, achievements, words I struggle with
│   │   └── HighScoresScreen.kt     # Top 10 high scores list
│   └── theme/
│       ├── Color.kt                # FrenchBlue, GermanGold, AccentPurple, etc.
│       └── Theme.kt                # Material 3 theme configuration
├── utils/
│   ├── FrenchTts.kt                # Text-to-Speech wrapper (French locale)
│   ├── AzurePronunciationApi.kt    # Azure Speech Services REST client
│   └── AudioRecorder.kt            # PCM audio capture → WAV byte array
├── viewmodel/
│   ├── GameViewModel.kt            # Trouvez! game logic
│   ├── GenderSnapViewModel.kt      # Classez! game logic
│   ├── GapFillViewModel.kt         # Compl\u00E9tez! game logic
│   ├── SpellingBeeViewModel.kt     # \u00C9crivez! game logic
│   ├── ConjugationViewModel.kt     # Conjuguez! game logic (8 tenses, timer, IPA, info limits)
│   ├── PronunciationViewModel.kt   # Prononcez! game logic (Azure, adaptive difficulty, second chance)
│   ├── PrepositionViewModel.kt     # Préposez! game logic
│   ├── StatisticsViewModel.kt      # Statistics and achievements
│   └── AnswerRecord.kt             # Per-question answer log for detail views
├── HubertApplication.kt            # Hilt application class
└── MainActivity.kt                 # Entry point, screen navigation

scripts/
├── convert_vocab.py                # Data pipeline: YAML + HTML → JSON assets
├── extract_conjugations.py         # Conjugation pipeline: Anki DB → conjugations.json
└── extract_prepositions.py         # Preposition pipeline: anki_french YAML → prepositions.json

app/src/main/assets/
├── vocab.json                      # 5000 vocabulary words
├── categories.json                 # 28 thematic categories
├── sentences.json                  # ~2000 words with example sentences
├── conjugations.json               # 1182 verbs with conjugation tables + sentence matches
└── prepositions.json               # 764 preposition fill-in-the-blank sentences (16 prepositions)
```

## Architecture

```mermaid
graph TD
    UI["<b>UI Layer</b><br/>MenuScreen \u00B7 GameScreen \u00B7 GenderSnapScreen<br/>GapFillScreen \u00B7 SpellingBeeScreen<br/>ConjugationScreen \u00B7 PronunciationScreen<br/>GameOverScreen \u00B7 StatisticsScreen"]
    VM["<b>ViewModel Layer</b><br/>GameViewModel · GenderSnapViewModel<br/>GapFillViewModel · SpellingBeeViewModel<br/>ConjugationViewModel · PronunciationViewModel<br/>PrepositionViewModel · StatisticsViewModel<br/><i>(timer/points, scoring, streak, game state)</i>"]
    REPO["<b>Repository / API Layer</b><br/>VocabRepository · HighScoreRepository<br/>StatisticsRepository<br/>AzurePronunciationApi · AudioRecorder"]
    DATA["<b>Data Layer</b><br/>vocab.json · categories.json · sentences.json · conjugations.json<br/>Room SQLite (high_scores, game_sessions, word_attempts)<br/>Azure Speech Services REST API"]

    UI -- "observes StateFlow" --> VM
    VM -- "calls" --> REPO
    REPO -- "reads / writes" --> DATA
```

**Data flow:** User taps answer → UI calls ViewModel → ViewModel checks answer, updates score/timer/streak → StateFlow emits new state → Compose redraws UI.

## Troubleshooting

### Build fails with "Unable to locate a Java Runtime"

Use Android Studio's bundled JDK:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

### Build fails with "SDK location not found"

Create `local.properties` in the project root:
```properties
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk
```

### No French speech / TTS not working

- Make sure a French TTS voice is installed: Settings → Accessibility → Text-to-Speech → Install French language pack
- Check device volume is not muted

### Database errors after updating

The app uses `fallbackToDestructiveMigration()` — if the database schema changes, it will be recreated automatically. High scores will be lost on schema updates during development.

### Prononcez! — "Assessment failed" error

- Make sure you have a working internet connection — Prononcez! requires network access to Azure Speech Services
- Check that your Azure Speech API key and region are correctly configured (tap the settings icon on the Prononcez! screen)
- You can get a free Azure Speech Services key at [Azure Portal](https://portal.azure.com/) (free tier: 5 hours/month)

### Prononcez! — recording not working

- Grant the microphone permission when prompted (Settings → Apps → Hubert → Permissions → Microphone)
- Make sure no other app is using the microphone

## Contributing

Contributions are welcome!

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Ideas for Contributions

- Add more game modes (sentence ordering, listening comprehension, etc.)
- Difficulty levels (filter by word frequency rank)
- Daily challenges / spaced repetition
- Dark mode support
- Localization (English hints instead of German)
- Sound effects and animations
- Accessibility improvements

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Vocabulary and conjugation data from [anki_french](https://github.com/jacbz/anki_french) by jacbz
- Built with Kotlin and Jetpack Compose

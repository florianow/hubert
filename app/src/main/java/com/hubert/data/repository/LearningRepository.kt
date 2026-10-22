package com.hubert.data.repository

import com.hubert.data.local.GameSessionDao
import com.hubert.data.local.WordAttemptDao
import com.hubert.data.local.WordProgressDao
import com.hubert.data.model.WordProgress
import com.hubert.utils.ReviewStrength
import com.hubert.utils.SpacedRepetition
import com.hubert.viewmodel.AnswerRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the learning path for menus, statistics and the weekly report. */
data class LearningOverview(
    val known: Int,        // "sicher": interval ≥ 3 weeks
    val learning: Int,     // introduced but not yet known
    val unseen: Int,       // not introduced yet
    val dueToday: Int
) {
    /**
     * Rough CEFR estimate from the number of words known for good (common rules of thumb:
     * A1 ≈ 500, A2 ≈ 1000, B1 ≈ 2000, B2 ≈ 4000 words). Vocabulary only, not grammar.
     */
    val estimatedLevel: String get() = when {
        known < 250 -> "Einstieg"
        known < 500 -> "A1"
        known < 1000 -> "A2"
        known < 2000 -> "B1"
        known < 4000 -> "B2"
        else -> "C1"
    }
}

/**
 * Spaced-repetition state per vocab word. Every vocab-game answer (Trouvez!, Classez!,
 * Complétez!, Écrivez!) is fed in via [recordAnswers]; on first start the existing answer
 * history is replayed once so the player keeps their progress.
 */
@Singleton
class LearningRepository @Inject constructor(
    private val progressDao: WordProgressDao,
    private val attemptDao: WordAttemptDao,
    private val gameSessionDao: GameSessionDao,
    private val vocabRepository: VocabRepository
) {
    private val mutex = Mutex()

    companion object {
        const val NEW_WORDS_PER_DAY = 10
        /** After the one-time import, overdue words are spread over this many days. */
        const val SEED_SPREAD_DAYS = 14
    }

    fun today(): Long = LocalDate.now().toEpochDay()

    suspend fun recordAnswers(gameType: String, answers: List<AnswerRecord>) {
        val strength = ReviewStrength.forGameType(gameType) ?: return
        val relevant = answers.filter { it.rank >= 0 }
        if (relevant.isEmpty()) return
        val today = today()
        mutex.withLock {
            val state = progressDao.getByRanks(relevant.map { it.rank }.distinct())
                .associateBy { it.rank }
                .toMutableMap()
            for (answer in relevant) {
                state[answer.rank] = SpacedRepetition.review(state[answer.rank], answer.rank, answer.isCorrect, strength, today)
            }
            progressDao.upsertAll(state.values.toList())
        }
    }

    /**
     * One-time import: replay all recorded vocab answers chronologically. Runs only while
     * the progress table is empty, so it never overwrites live data.
     */
    suspend fun seedFromHistoryIfNeeded() {
        mutex.withLock {
            if (progressDao.count() > 0) return
            val rankByFrench = vocabRepository.getAllWords().associate { it.french to it.rank }
            val zone = ZoneId.systemDefault()
            val state = mutableMapOf<Int, WordProgress>()
            for (attempt in attemptDao.getVocabAttemptsInOrder()) {
                val strength = ReviewStrength.forGameType(attempt.gameType) ?: continue
                // Écrivez!/Complétez! log the French word as answer, Trouvez!/Classez! as question
                val french = if (attempt.gameType == "spelling_bee" || attempt.gameType == "gap_fill")
                    attempt.correctAnswer else attempt.question
                val rank = rankByFrench[french] ?: continue
                val day = Instant.ofEpochMilli(attempt.timestamp).atZone(zone).toLocalDate().toEpochDay()
                state[rank] = SpacedRepetition.review(state[rank], rank, attempt.isCorrect, strength, day)
            }
            if (state.isEmpty()) return
            // A history of random practice leaves most words overdue at once. Spread the
            // backlog over the next two weeks, most frequent words first.
            val today = today()
            val overdue = state.values.filter { it.dueDay <= today }.sortedBy { it.rank }
            val perDay = (overdue.size + SEED_SPREAD_DAYS - 1) / SEED_SPREAD_DAYS
            overdue.forEachIndexed { index, p ->
                state[p.rank] = p.copy(dueDay = today + if (perDay == 0) 0 else index / perDay)
            }
            progressDao.upsertAll(state.values.toList())
        }
    }

    // ── Word selection for games and menus ──────────────────────────────

    suspend fun dueRanks(limit: Int): List<Int> = progressDao.dueRanks(today(), limit)

    suspend fun dueProgress(limit: Int): List<WordProgress> = progressDao.dueProgress(today(), limit)

    /** Words introduced today (new words of the day). */
    suspend fun introducedToday(): List<Int> = progressDao.introducedOn(today())

    /**
     * Next new words in frequency order, so that at most [NEW_WORDS_PER_DAY] are introduced
     * per day. Words that already have a learning state are skipped.
     */
    suspend fun wordsToIntroduce(): List<Int> {
        val remaining = NEW_WORDS_PER_DAY - progressDao.introducedOn(today()).size
        if (remaining <= 0) return emptyList()
        val seen = progressDao.allRanks().toSet()
        return vocabRepository.getAllWords()
            .asSequence()
            .map { it.rank }
            .filter { it !in seen }
            .sorted()
            .take(remaining)
            .toList()
    }

    /**
     * Put words on the learning path. New ones are due right away; words the player already
     * knows ("Kenne ich schon") are checked again in a week.
     */
    suspend fun introduce(newRanks: List<Int>, alreadyKnown: Set<Int>) {
        if (newRanks.isEmpty()) return
        val today = today()
        mutex.withLock {
            val existing = progressDao.getByRanks(newRanks).map { it.rank }.toSet()
            val rows = newRanks.filter { it !in existing }.map { rank ->
                if (rank in alreadyKnown) WordProgress(
                    rank = rank, intervalDays = 7.0, ease = SpacedRepetition.START_EASE, dueDay = today + 7,
                    reps = 1, lapses = 0, lastReviewDay = today, introducedDay = today
                ) else WordProgress(
                    rank = rank, intervalDays = 0.0, ease = SpacedRepetition.START_EASE, dueDay = today,
                    reps = 0, lapses = 0, lastReviewDay = -1, introducedDay = today
                )
            }
            progressDao.upsertAll(rows)
        }
    }

    /**
     * Words for a free practice round: due first, then today's new words, then a few
     * already learned ones. Only words on the learning path — nothing unseen.
     */
    suspend fun practiceRanks(limit: Int = 40): List<Int> {
        val due = progressDao.dueRanks(today(), limit)
        val fresh = introducedToday()
        val filler = progressDao.randomNotDue(today(), limit)
        return (due + fresh + filler).distinct().take(limit)
    }

    /** Words that entered the learning path in [fromDay, toDay). */
    suspend fun introducedBetween(fromDay: Long, toDay: Long): Int = progressDao.countIntroducedBetween(fromDay, toDay)

    /** Whether any round was played today (used to skip the daily reminder). */
    suspend fun practicedToday(): Boolean {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return gameSessionDao.getSessionsBetween(start, end).isNotEmpty()
    }

    suspend fun overview(): LearningOverview {
        val introduced = progressDao.count()
        val known = progressDao.countKnown(WordProgress.KNOWN_INTERVAL_DAYS)
        return LearningOverview(
            known = known,
            learning = introduced - known,
            unseen = (vocabRepository.getAllWords().size - introduced).coerceAtLeast(0),
            dueToday = progressDao.countDue(today())
        )
    }
}

package com.hubert.data.repository

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
)

/**
 * Spaced-repetition state per vocab word. Every vocab-game answer (Trouvez!, Classez!,
 * Complétez!, Écrivez!) is fed in via [recordAnswers]; on first start the existing answer
 * history is replayed once so the player keeps their progress.
 */
@Singleton
class LearningRepository @Inject constructor(
    private val progressDao: WordProgressDao,
    private val attemptDao: WordAttemptDao,
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

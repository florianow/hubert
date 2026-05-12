package com.hubert.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pinnedDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "trouvez_pinned_words")

@Singleton
class PinnedWordsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_PINNED_RANKS = stringPreferencesKey("pinned_ranks")
        private val KEY_MASTERED_WORDS = stringPreferencesKey("mastered_words")
        const val MASTERY_THRESHOLD = 50
    }

    val pinnedRanks: Flow<Set<Int>> = context.pinnedDataStore.data.map { prefs ->
        prefs[KEY_PINNED_RANKS]
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    val masteredWords: Flow<Set<String>> = context.pinnedDataStore.data.map { prefs ->
        prefs[KEY_MASTERED_WORDS]
            ?.split("||")
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun togglePin(rank: Int) {
        context.pinnedDataStore.edit { prefs ->
            val current = prefs[KEY_PINNED_RANKS]
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?.toMutableSet()
                ?: mutableSetOf()
            if (rank in current) current.remove(rank) else current.add(rank)
            prefs[KEY_PINNED_RANKS] = current.joinToString(",")
        }
    }

    suspend fun markMastered(french: String) {
        context.pinnedDataStore.edit { prefs ->
            val current = prefs[KEY_MASTERED_WORDS]
                ?.split("||")
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()
            current.add(french)
            prefs[KEY_MASTERED_WORDS] = current.joinToString("||")
        }
    }
}

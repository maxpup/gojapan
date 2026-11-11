package com.gojapan.feature.library.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.gojapan.feature.review.domain.TravelMemoryState
import com.gojapan.feature.review.domain.TravelReviewResult
import com.gojapan.feature.review.domain.TravelReviewScheduler

data class TravelLibraryState(
    val favoriteIds: Set<String> = emptySet(),
    val reviewIds: Set<String> = emptySet(),
    val frequentSearchIds: Set<String> = emptySet(),
    val hiddenFrequentSearchIds: Set<String> = emptySet(),
    val memory: Map<String, TravelMemoryState> = emptyMap(),
    val dueReviewIds: Set<String> = emptySet(),
    val frequentWrongIds: Set<String> = emptySet(),
    val hiddenFrequentWrongIds: Set<String> = emptySet(),
    val completedLessonIds: Set<String> = emptySet(),
    val learnedExpressionIds: Set<String> = emptySet(),
    val lessonCheckpoint: com.gojapan.feature.lesson.domain.LessonCheckpoint? = null,
    val lessonBestScores: Map<String, Int> = emptyMap(),
)

class TravelLibraryPreferences(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val reviewScheduler: TravelReviewScheduler = TravelReviewScheduler(),
) {
    val state: Flow<TravelLibraryState> = dataStore.data.map { values ->
        val recent = SearchHistoryCodec.decode(values[SEARCH_HISTORY].orEmpty())
            .mapValues { (_, timestamps) -> SearchHistoryCodec.recent(timestamps, clock.millis()) }
        val hidden = values[HIDDEN_FREQUENT].orEmpty()
        val memory = TravelMemoryCodec.decode(values[MEMORY].orEmpty())
        val enrolled = values[REVIEW_IDS].orEmpty()
        val answerHistory = AnswerHistoryCodec.decode(values[ANSWER_HISTORY].orEmpty())
        val hiddenWrong = values[HIDDEN_WRONG].orEmpty()
        val lessonScores = LessonScoreCodec.decode(values[LESSON_SCORES].orEmpty())
        TravelLibraryState(
            favoriteIds = values[FAVORITES].orEmpty(),
            reviewIds = enrolled,
            frequentSearchIds = SearchHistoryCodec.frequentIds(recent, FREQUENT_THRESHOLD) - hidden,
            hiddenFrequentSearchIds = hidden,
            memory = memory,
            dueReviewIds = enrolled.filterTo(mutableSetOf()) { id ->
                (memory[id]?.nextReviewAt ?: Long.MIN_VALUE) <= clock.millis()
            },
            frequentWrongIds = AnswerHistoryCodec.frequentWrongIds(answerHistory) - hiddenWrong,
            hiddenFrequentWrongIds = hiddenWrong,
            completedLessonIds = values[COMPLETED_LESSONS].orEmpty(),
            lessonBestScores = lessonScores,
            learnedExpressionIds = values[LEARNED_EXPRESSIONS].orEmpty() + answerHistory.keys,
            lessonCheckpoint = com.gojapan.feature.lesson.domain.LessonCheckpointCodec.decode(values[LESSON_CHECKPOINT].orEmpty()),
        )
    }

    suspend fun toggleFavorite(id: String) = updateSet(FAVORITES, id)

    suspend fun toggleReview(id: String) {
        dataStore.edit { values ->
            val existing = values[REVIEW_IDS].orEmpty()
            if (id in existing) {
                values[REVIEW_IDS] = existing - id
            } else {
                values[REVIEW_IDS] = existing + id
                val memory = TravelMemoryCodec.decode(values[MEMORY].orEmpty()).toMutableMap()
                memory.putIfAbsent(id, TravelMemoryState(id, nextReviewAt = clock.millis()))
                values[MEMORY] = TravelMemoryCodec.encode(memory)
            }
        }
    }

    suspend fun enrollReview(ids: Set<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { values ->
            values[REVIEW_IDS] = values[REVIEW_IDS].orEmpty() + ids
            val now = clock.millis()
            val memory = TravelMemoryCodec.decode(values[MEMORY].orEmpty()).toMutableMap()
            ids.forEach { id -> memory.putIfAbsent(id, TravelMemoryState(id, nextReviewAt = now)) }
            values[MEMORY] = TravelMemoryCodec.encode(memory)
        }
    }

    suspend fun recordReview(id: String, result: TravelReviewResult) {
        dataStore.edit { values ->
            require(id in values[REVIEW_IDS].orEmpty()) { "Expression must be enrolled before review" }
            val memory = TravelMemoryCodec.decode(values[MEMORY].orEmpty()).toMutableMap()
            val now = clock.millis()
            val old = memory[id] ?: TravelMemoryState(id, nextReviewAt = now)
            memory[id] = reviewScheduler.complete(old, result, now)
            values[MEMORY] = TravelMemoryCodec.encode(memory)
        }
    }

    suspend fun hideFrequentSearch(id: String) {
        dataStore.edit { values -> values[HIDDEN_FREQUENT] = values[HIDDEN_FREQUENT].orEmpty() + id }
    }

    suspend fun hideFrequentWrong(id: String) {
        dataStore.edit { values -> values[HIDDEN_WRONG] = values[HIDDEN_WRONG].orEmpty() + id }
    }

    suspend fun recordLessonResults(results: Map<String, List<Boolean>>) {
        if (results.isEmpty()) return
        dataStore.edit { values ->
            val history = AnswerHistoryCodec.decode(values[ANSWER_HISTORY].orEmpty()).toMutableMap()
            results.forEach { (id, answers) -> history[id] = (history[id].orEmpty() + answers).takeLast(5) }
            values[ANSWER_HISTORY] = AnswerHistoryCodec.encode(history)
            values[LEARNED_EXPRESSIONS] = values[LEARNED_EXPRESSIONS].orEmpty() + results.keys
        }
    }

    suspend fun recordLessonCompletion(lessonId: String, score: Int, passed: Boolean) {
        require(lessonId.isNotBlank() && score in 0..100)
        dataStore.edit { values ->
            val scores = LessonScoreCodec.decode(values[LESSON_SCORES].orEmpty()).toMutableMap()
            scores[lessonId] = maxOf(scores[lessonId] ?: 0, score)
            values[LESSON_SCORES] = LessonScoreCodec.encode(scores)
            if (com.gojapan.feature.lesson.domain.LessonCheckpointCodec.decode(values[LESSON_CHECKPOINT].orEmpty())?.lessonId == lessonId) {
                values.remove(LESSON_CHECKPOINT)
            }
            if (passed) values[COMPLETED_LESSONS] = values[COMPLETED_LESSONS].orEmpty() + lessonId
        }
    }

    /** Commit results, review enrollment, score and checkpoint removal as one durable transaction. */
    suspend fun finishLessonSession(
        lessonId: String,
        sessionId: String,
        results: Map<String, List<Boolean>>,
        score: Int,
        passed: Boolean,
        reinforcement: Boolean,
    ) {
        require(sessionId.isNotBlank() && lessonId.isNotBlank() && score in 0..100)
        dataStore.edit { values ->
            val receipts = values[FINISHED_SESSIONS].orEmpty()
            if (sessionId in receipts) return@edit
            val ids = results.keys
            values[REVIEW_IDS] = values[REVIEW_IDS].orEmpty() + ids
            val memory = TravelMemoryCodec.decode(values[MEMORY].orEmpty()).toMutableMap()
            ids.forEach { id -> memory.putIfAbsent(id, TravelMemoryState(id, nextReviewAt = clock.millis())) }
            values[MEMORY] = TravelMemoryCodec.encode(memory)
            val history = AnswerHistoryCodec.decode(values[ANSWER_HISTORY].orEmpty()).toMutableMap()
            results.forEach { (id, answers) -> history[id] = (history[id].orEmpty() + answers).takeLast(5) }
            values[ANSWER_HISTORY] = AnswerHistoryCodec.encode(history)
            values[LEARNED_EXPRESSIONS] = values[LEARNED_EXPRESSIONS].orEmpty() + ids
            if (!reinforcement) {
                val scores = LessonScoreCodec.decode(values[LESSON_SCORES].orEmpty()).toMutableMap()
                scores[lessonId] = maxOf(scores[lessonId] ?: 0, score)
                values[LESSON_SCORES] = LessonScoreCodec.encode(scores)
                if (passed) values[COMPLETED_LESSONS] = values[COMPLETED_LESSONS].orEmpty() + lessonId
            }
            val checkpoint = com.gojapan.feature.lesson.domain.LessonCheckpointCodec.decode(values[LESSON_CHECKPOINT].orEmpty())
            if (checkpoint?.sessionId == sessionId) values.remove(LESSON_CHECKPOINT)
            values[FINISHED_SESSIONS] = receipts.toList().takeLast(63).toSet() + sessionId
        }
    }

    suspend fun saveLessonCheckpoint(checkpoint: com.gojapan.feature.lesson.domain.LessonCheckpoint) {
        dataStore.edit { values ->
            if (checkpoint.sessionId !in values[FINISHED_SESSIONS].orEmpty()) {
                values[LESSON_CHECKPOINT] = com.gojapan.feature.lesson.domain.LessonCheckpointCodec.encode(checkpoint)
            }
        }
    }

    suspend fun recordSearchResult(id: String) {
        dataStore.edit { values ->
            val now = clock.millis()
            val all = SearchHistoryCodec.decode(values[SEARCH_HISTORY].orEmpty()).toMutableMap()
            all[id] = SearchHistoryCodec.recent(all[id].orEmpty(), now) + now
            values[SEARCH_HISTORY] = SearchHistoryCodec.encode(all)
        }
    }

    private suspend fun updateSet(key: Preferences.Key<Set<String>>, id: String) {
        dataStore.edit { values ->
            val existing = values[key].orEmpty()
            values[key] = if (id in existing) existing - id else existing + id
        }
    }

    private companion object {
        const val FREQUENT_THRESHOLD = 2
        val FINISHED_SESSIONS = stringSetPreferencesKey("finished_lesson_sessions_v1")
        val LESSON_CHECKPOINT = stringPreferencesKey("lesson_checkpoint_v1")
        val LEARNED_EXPRESSIONS = stringSetPreferencesKey("learned_expression_ids")
        val FAVORITES = stringSetPreferencesKey("favorite_expression_ids")
        val REVIEW_IDS = stringSetPreferencesKey("review_expression_ids")
        val HIDDEN_FREQUENT = stringSetPreferencesKey("hidden_frequent_search_ids")
        val SEARCH_HISTORY = stringPreferencesKey("search_history_v1")
        val MEMORY = stringPreferencesKey("travel_memory_v1")
        val ANSWER_HISTORY = stringPreferencesKey("travel_answer_history_v1")
        val HIDDEN_WRONG = stringSetPreferencesKey("hidden_frequent_wrong_ids")
        val COMPLETED_LESSONS = stringSetPreferencesKey("completed_lesson_ids")
        val LESSON_SCORES = stringPreferencesKey("lesson_best_scores_v1")
    }
}

object LessonScoreCodec {
    fun encode(scores: Map<String, Int>): String = scores.entries.sortedBy { it.key }
        .joinToString("\n") { (id, score) -> "$id:${score.coerceIn(0, 100)}" }
    fun decode(raw: String): Map<String, Int> = raw.lineSequence().mapNotNull { line ->
        val id = line.substringBefore(':').takeIf(String::isNotBlank) ?: return@mapNotNull null
        val score = line.substringAfter(':', "").toIntOrNull()?.coerceIn(0, 100) ?: return@mapNotNull null
        id to score
    }.toMap()
}

object AnswerHistoryCodec {
    fun frequentWrongIds(history: Map<String, List<Boolean>>): Set<String> =
        history.filterValues { results -> results.takeLast(5).count { !it } >= 2 }.keys

    fun encode(history: Map<String, List<Boolean>>): String = history.entries.sortedBy { it.key }
        .joinToString("\n") { (id, results) -> "$id:${results.takeLast(5).joinToString(",") { if (it) "1" else "0" }}" }

    fun decode(raw: String): Map<String, List<Boolean>> = raw.lineSequence().mapNotNull { line ->
        val id = line.substringBefore(':').takeIf(String::isNotBlank) ?: return@mapNotNull null
        val results = line.substringAfter(':', "").split(',').mapNotNull {
            when (it) { "1" -> true; "0" -> false; else -> null }
        }.takeLast(5)
        if (results.isEmpty()) null else id to results
    }.toMap()
}

object TravelMemoryCodec {
    fun encode(memory: Map<String, TravelMemoryState>): String = memory.values.sortedBy { it.expressionId }
        .joinToString("\n") { state ->
            listOf(
                state.expressionId,
                state.level,
                state.correctCount,
                state.wrongCount,
                state.lastReviewedAt ?: "",
                state.nextReviewAt,
            ).joinToString("|")
        }

    fun decode(raw: String): Map<String, TravelMemoryState> = raw.lineSequence().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 6) return@mapNotNull null
        val id = parts[0].takeIf(String::isNotBlank) ?: return@mapNotNull null
        val state = TravelMemoryState(
            expressionId = id,
            level = parts[1].toIntOrNull() ?: return@mapNotNull null,
            correctCount = parts[2].toIntOrNull() ?: return@mapNotNull null,
            wrongCount = parts[3].toIntOrNull() ?: return@mapNotNull null,
            lastReviewedAt = parts[4].takeIf(String::isNotEmpty)?.toLongOrNull(),
            nextReviewAt = parts[5].toLongOrNull() ?: return@mapNotNull null,
        )
        id to state
    }.toMap()
}

object SearchHistoryCodec {
    private val window = Duration.ofDays(7).toMillis()

    fun recent(timestamps: List<Long>, now: Long): List<Long> =
        timestamps.filter { it in (now - window)..now }.sorted()

    fun frequentIds(history: Map<String, List<Long>>, threshold: Int = 2): Set<String> =
        history.filterValues { it.size >= threshold }.keys

    fun encode(history: Map<String, List<Long>>): String = history.entries
        .sortedBy { it.key }
        .joinToString("\n") { (id, timestamps) -> "$id:${timestamps.joinToString(",")}" }

    fun decode(raw: String): Map<String, List<Long>> = raw.lineSequence().mapNotNull { line ->
        val id = line.substringBefore(':').takeIf(String::isNotBlank) ?: return@mapNotNull null
        val timestamps = line.substringAfter(':', "").split(',').mapNotNull(String::toLongOrNull)
        id to timestamps
    }.toMap()
}

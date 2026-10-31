package com.gojapan.feature.kanalink.data.repository

import androidx.room.withTransaction
import com.gojapan.feature.kanalink.data.local.KanaLinkDatabase
import com.gojapan.feature.kanalink.data.local.KanaProgressEntity
import com.gojapan.feature.kanalink.data.local.SessionItemEntity
import com.gojapan.feature.kanalink.data.local.StudySessionEntity
import com.gojapan.feature.kanalink.domain.model.ItemEvidence
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.KanaProgress
import com.gojapan.feature.kanalink.domain.model.PairMode
import com.gojapan.feature.kanalink.domain.model.ReviewResult
import com.gojapan.feature.kanalink.domain.model.RoundSource
import com.gojapan.feature.kanalink.domain.scheduler.MemoryScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

data class CompleteRoundCommand(
    val sessionId: String,
    val completedAt: Long,
    val localDate: String,
    val timezoneId: String,
    val durationSeconds: Int,
    val mode: PairMode,
    val source: RoundSource,
    val bestCombo: Int,
    val wrongAttempts: Int,
    val items: List<Pair<KanaItem, ItemEvidence>>,
)

data class CompletedItem(
    val kanaId: KanaId,
    val seenCount: Int,
    val levelAfter: Int,
    val nextReviewAt: Long,
)

data class CompleteRoundResult(
    val sessionId: String,
    val inserted: Boolean,
    val items: List<CompletedItem> = emptyList(),
)

data class HomeStats(
    val progress: Map<KanaId, KanaProgress>,
    val sessions: Int,
    val learningDays: Int,
    val dueCount: Int,
)

data class ReportItem(
    val kanaId: KanaId,
    val appearances: Int,
    val word: String,
    val meaning: String,
    val manualMastered: Boolean,
)

data class LearningReport(
    val rounds: Int,
    val distinctKana: Int,
    val accuracyPercent: Int,
    val durationSeconds: Int,
    val previousRounds: Int,
    val items: List<ReportItem>,
)

data class KanaExport(
    val progress: List<KanaProgressEntity>,
    val sessions: List<StudySessionEntity>,
    val sessionItems: List<SessionItemEntity>,
)

interface LearningRepository {
    fun observeProgress(): Flow<Map<KanaId, KanaProgress>>
    fun observeHomeStats(now: Long): Flow<HomeStats>
    suspend fun completeRound(command: CompleteRoundCommand): CompleteRoundResult
    suspend fun markMastered(ids: Set<KanaId>, at: Long)
    suspend fun getReport(start: Long, end: Long, previousStart: Long): LearningReport
    suspend fun exportData(): KanaExport
    suspend fun clearHistory()
}

class RoomLearningRepository(
    private val database: KanaLinkDatabase,
    private val scheduler: MemoryScheduler,
) : LearningRepository {
    override fun observeProgress(): Flow<Map<KanaId, KanaProgress>> =
        database.progressDao().observeAll().map { list -> list.associate { it.toDomain().let { p -> p.kanaId to p } } }

    override fun observeHomeStats(now: Long): Flow<HomeStats> = combine(
        observeProgress(),
        database.sessionDao().observeCount(),
        database.sessionDao().observeLearningDays(),
        database.progressDao().observeDueCount(now),
    ) { progress, sessions, days, due -> HomeStats(progress, sessions, days, due) }

    override suspend fun completeRound(command: CompleteRoundCommand): CompleteRoundResult = database.withTransaction {
        val sessions = database.sessionDao()
        if (sessions.get(command.sessionId) != null) {
            val storedItems = database.sessionItemDao().forSession(command.sessionId)
            val storedProgress = database.progressDao().getByIds(storedItems.map { it.kanaId }.toSet()).associateBy { it.kanaId }
            return@withTransaction CompleteRoundResult(
                command.sessionId,
                false,
                storedItems.map { item ->
                    CompletedItem(
                        KanaId(item.kanaId),
                        storedProgress[item.kanaId]?.seenCount ?: 0,
                        item.levelAfter,
                        item.nextReviewAt,
                    )
                },
            )
        }
        require(command.items.isNotEmpty() && command.items.size <= 5)
        require(command.items.map { it.first.id }.distinct().size == command.items.size)

        val ids = command.items.map { it.first.id.value }.toSet()
        val old = database.progressDao().getByIds(ids).associateBy { it.kanaId }
        val progressUpdates = mutableListOf<KanaProgressEntity>()
        val sessionItems = mutableListOf<SessionItemEntity>()
        command.items.forEach { (kana, evidence) ->
            require(kana.id == evidence.kanaId)
            val before = old[kana.id.value]
            val oldLevel = before?.level ?: -1
            val update = scheduler.complete(oldLevel, evidence, command.completedAt)
            progressUpdates += KanaProgressEntity(
                kanaId = kana.id.value,
                seenCount = (before?.seenCount ?: 0) + 1,
                firstTryCorrectCount = (before?.firstTryCorrectCount ?: 0) + if (evidence.firstTryCorrect) 1 else 0,
                wrongCount = (before?.wrongCount ?: 0) + evidence.wrongCount,
                hintCount = (before?.hintCount ?: 0) + if (evidence.hintUsed) 1 else 0,
                level = update.levelAfter,
                lastResult = update.result.name,
                lastReviewedAt = command.completedAt,
                nextReviewAt = update.nextReviewAt,
                manualMastered = before?.manualMastered ?: false,
                manualMasteredAt = before?.manualMasteredAt,
            )
            sessionItems += SessionItemEntity(
                command.sessionId, kana.id.value, evidence.wrongCount, evidence.firstTryCorrect,
                evidence.hintUsed, evidence.responseMs, before == null, oldLevel, update.levelAfter,
                update.nextReviewAt, kana.word, kana.meaningZhCn,
            )
        }

        sessions.insert(
            StudySessionEntity(
                command.sessionId, command.completedAt, command.localDate, command.timezoneId,
                command.durationSeconds, command.mode.name, command.source.name, command.bestCombo,
                command.wrongAttempts, command.items.count { it.second.hintUsed },
            ),
        )
        database.sessionItemDao().insertAll(sessionItems)
        database.progressDao().upsertAll(progressUpdates)
        val overflow = sessions.count() - 500
        if (overflow > 0) sessions.deleteOldest(overflow)
        CompleteRoundResult(
            command.sessionId,
            true,
            sessionItems.map { item ->
                CompletedItem(
                    KanaId(item.kanaId),
                    progressUpdates.single { it.kanaId == item.kanaId }.seenCount,
                    item.levelAfter,
                    item.nextReviewAt,
                )
            },
        )
    }

    override suspend fun markMastered(ids: Set<KanaId>, at: Long) {
        database.progressDao().markMastered(ids.map { it.value }.toSet(), at)
    }

    override suspend fun getReport(start: Long, end: Long, previousStart: Long): LearningReport {
        val sessions = database.sessionDao().between(start, end)
        val previousRounds = database.sessionDao().between(previousStart, start).size
        if (sessions.isEmpty()) return LearningReport(0, 0, 0, 0, previousRounds, emptyList())
        val sessionItems = database.sessionItemDao().forSessions(sessions.map { it.id })
        val progress = database.progressDao().getByIds(sessionItems.map { it.kanaId }.toSet()).associateBy { it.kanaId }
        val correctPairs = sessionItems.size
        val wrongAttempts = sessions.sumOf { it.wrongAttempts }
        val reportItems = sessionItems.groupBy { it.kanaId }.map { (id, items) ->
            val latest = items.last()
            ReportItem(
                KanaId(id), items.size, latest.wordSnapshot, latest.meaningSnapshot,
                progress[id]?.manualMastered == true,
            )
        }.sortedBy { it.kanaId.value }
        return LearningReport(
            rounds = sessions.size,
            distinctKana = reportItems.size,
            accuracyPercent = if (correctPairs + wrongAttempts == 0) 0 else
                ((correctPairs * 100.0) / (correctPairs + wrongAttempts)).toInt(),
            durationSeconds = sessions.sumOf { it.durationSeconds },
            previousRounds = previousRounds,
            items = reportItems,
        )
    }

    override suspend fun exportData(): KanaExport = database.withTransaction {
        KanaExport(
            database.progressDao().getAll(),
            database.sessionDao().getAll(),
            database.sessionItemDao().getAll(),
        )
    }

    override suspend fun clearHistory() = database.withTransaction {
        database.sessionDao().clear()
        database.progressDao().clear()
    }
}

private fun KanaProgressEntity.toDomain() = KanaProgress(
    KanaId(kanaId), seenCount, firstTryCorrectCount, wrongCount, hintCount, level,
    lastResult?.let(ReviewResult::valueOf), lastReviewedAt, nextReviewAt, manualMastered, manualMasteredAt,
)

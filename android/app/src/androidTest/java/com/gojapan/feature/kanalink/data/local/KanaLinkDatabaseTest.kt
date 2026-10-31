package com.gojapan.feature.kanalink.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gojapan.feature.kanalink.data.repository.CompleteRoundCommand
import com.gojapan.feature.kanalink.data.repository.RoomLearningRepository
import com.gojapan.feature.kanalink.domain.model.ItemEvidence
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.PairMode
import com.gojapan.feature.kanalink.domain.model.RoundSource
import com.gojapan.feature.kanalink.domain.scheduler.MemoryScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KanaLinkDatabaseTest {
    private lateinit var database: KanaLinkDatabase
    private lateinit var repository: RoomLearningRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), KanaLinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomLearningRepository(database, MemoryScheduler { 0.0 })
    }

    @After fun close() = database.close()

    @Test
    fun completeRoundWritesAllTablesAndIsIdempotent() = runTest {
        val command = command("session-1")
        assertTrue(repository.completeRound(command).inserted)
        assertFalse(repository.completeRound(command).inserted)
        assertEquals(1, database.sessionDao().count())
        assertEquals(1, database.sessionItemDao().forSession("session-1").size)
        assertEquals(0, database.progressDao().getByIds(setOf("a")).single().level)
    }

    @Test
    fun deletingSessionCascadesItemsButKeepsProgress() = runTest {
        repository.completeRound(command("session-1"))
        database.sessionDao().deleteOldest(1)
        assertEquals(0, database.sessionItemDao().forSession("session-1").size)
        assertEquals(1, database.progressDao().getByIds(setOf("a")).size)
    }

    @Test
    fun manualMasteryDoesNotChangeMemoryLevelOrReviewDate() = runTest {
        repository.completeRound(command("session-1"))
        val before = database.progressDao().getByIds(setOf("a")).single()

        repository.markMastered(setOf(KanaId("a")), 1_800_000_100_000L)

        val after = database.progressDao().getByIds(setOf("a")).single()
        assertTrue(after.manualMastered)
        assertEquals(1_800_000_100_000L, after.manualMasteredAt)
        assertEquals(before.level, after.level)
        assertEquals(before.nextReviewAt, after.nextReviewAt)
        assertEquals(before.seenCount, after.seenCount)
    }

    @Test
    fun repositoryKeepsLatestFiveHundredSessionsWithoutLosingProgress() = runTest {
        repeat(501) { index ->
            repository.completeRound(command("session-$index").copy(completedAt = 1_800_000_000_000L + index))
        }
        assertEquals(500, database.sessionDao().count())
        assertEquals(null, database.sessionDao().get("session-0"))
        assertEquals(501, database.progressDao().getByIds(setOf("a")).single().seenCount)
    }

    @Test
    fun reportAggregatesOnlyRequestedCompleteSessions() = runTest {
        repository.completeRound(command("previous").copy(completedAt = 100L, durationSeconds = 30))
        repository.completeRound(command("current").copy(completedAt = 200L, durationSeconds = 45, wrongAttempts = 1))

        val report = repository.getReport(start = 150L, end = 300L, previousStart = 0L)

        assertEquals(1, report.rounds)
        assertEquals(1, report.previousRounds)
        assertEquals(1, report.distinctKana)
        assertEquals(50, report.accuracyPercent)
        assertEquals(45, report.durationSeconds)
        assertEquals(1, report.items.single().appearances)
    }

    @Test
    fun exportContainsCompleteSnapshotAndClearRemovesHistory() = runTest {
        repository.completeRound(command("session-1"))
        val exported = repository.exportData()
        assertEquals(1, exported.progress.size)
        assertEquals(1, exported.sessions.size)
        assertEquals(1, exported.sessionItems.size)

        repository.clearHistory()

        assertEquals(0, database.sessionDao().count())
        assertTrue(database.sessionItemDao().getAll().isEmpty())
        assertTrue(database.progressDao().getAll().isEmpty())
    }

    private fun command(id: String): CompleteRoundCommand {
        val kana = KanaItem(KanaId("a"), 0, KanaGroup.SEION, "あ行", "あ", "ア", "a", "朝", "早晨", "kana_a")
        return CompleteRoundCommand(
            id, 1_800_000_000_000L, "2027-01-15", "Asia/Shanghai", 60,
            PairMode.HIRAGANA_ROMAJI, RoundSource.DAILY, 5, 0,
            listOf(kana to ItemEvidence(kana.id, 0, true, false, 100)),
        )
    }
}

package com.gojapan.feature.travelplan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class TravelDatabaseTest {
    @Test fun retriesSelectionOwnershipAndRollback() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TravelDatabase::class.java).build()
        try {
            val repo = TravelRepository(db)
            val first = TravelPlan("", 0, "first", 0, "日本", "日本", "Asia/Tokyo", "ja", null, null)
            assertTrue(repo.observe().first().isEmpty())
            coroutineScope {
                listOf("first", "first", "second").map { key -> async { repo.save(first.copy(request = key), null) } }.awaitAll()
            }
            val rows = repo.observe().first()
            assertEquals(setOf(0L, 1L), rows.map { it.plan.id }.toSet())
            repo.select(0)
            repo.save(first.copy(request = rows.first { it.plan.id == 1L }.plan.request), null)
            assertEquals(0L, repo.observe().first().single { it.selected }.plan.id)
            db.plans().insert(first.copy(owner = "other", id = 9))
            assertTrue(runCatching { repo.select(9) }.isFailure)
            assertTrue(runCatching { repo.save(first, 9) }.isFailure)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_change BEFORE UPDATE ON CurrentPlan BEGIN SELECT RAISE(ABORT,'test'); END")
            assertTrue(runCatching { repo.save(first.copy(request = "third"), null) }.isFailure)
            assertEquals(2, repo.observe().first().size)
        } finally { db.close() }
    }
}

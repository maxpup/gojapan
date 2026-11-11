package com.gojapan.feature.library.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.gojapan.feature.lesson.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.time.Clock

class LearningProgressPersistenceTest {
    @Test fun finishingSessionIsAtomicIdempotentAndKeepsNewerCheckpoint() = runBlocking {
        val file = Files.createTempDirectory("session-commit").resolve("library.preferences_pb").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val library = TravelLibraryPreferences(store, Clock.systemUTC())
        val lesson = SceneLessonCatalog.allLessons.first()
        val older = LessonCheckpoint(lesson.id, SceneLessonEngine(lesson).start(), 100)
        val newer = older.copy(sessionId = "new-session", startedAt = 200)
        try {
            library.saveLessonCheckpoint(newer)
            repeat(2) { library.finishLessonSession(lesson.id, older.sessionId, mapOf("station" to listOf(false)), 90, true, false) }
            val values = store.data.first()
            val history = AnswerHistoryCodec.decode(values[androidx.datastore.preferences.core.stringPreferencesKey("travel_answer_history_v1")].orEmpty())
            assertEquals(listOf(false), history["station"])
            val state = library.state.first()
            assertEquals(newer, state.lessonCheckpoint)
            assertTrue("station" in state.reviewIds)
            assertTrue("station" in state.learnedExpressionIds)
            assertTrue(lesson.id in state.completedLessonIds)
            library.finishLessonSession(lesson.id, newer.sessionId, mapOf("station" to listOf(true)), 100, true, true)
            assertEquals(90, library.state.first().lessonBestScores[lesson.id])
            assertNull(library.state.first().lessonCheckpoint)
            library.saveLessonCheckpoint(newer)
            assertNull(library.state.first().lessonCheckpoint)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.parentFile?.deleteRecursively()
        }
    }
    @Test fun progressSurvivesNewStoreWithoutCountingFavoritesOrDuplicateLessons() = runBlocking {
        val file = Files.createTempDirectory("learning-progress").resolve("library.preferences_pb").toFile()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun store() = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        var library = TravelLibraryPreferences(store(), Clock.systemUTC())
        val lesson = SceneLessonCatalog.allLessons.first()
        try {
            library.toggleFavorite("toilet")
            assertTrue(library.state.first().learnedExpressionIds.isEmpty())
            val checkpoint = LessonCheckpoint(lesson.id, SceneLessonEngine(lesson).start(), 100)
            library.saveLessonCheckpoint(checkpoint)
            assertTrue(library.state.first().completedLessonIds.isEmpty())
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            library = TravelLibraryPreferences(store(), Clock.systemUTC())
            assertEquals(checkpoint, library.state.first().lessonCheckpoint)
            library.recordLessonResults(mapOf("station" to listOf(true)))
            library.recordLessonCompletion(lesson.id, 90, true)
            library.recordLessonCompletion(lesson.id, 80, true)
            val state = library.state.first()
            assertEquals(setOf(lesson.id), state.completedLessonIds)
            assertEquals(90, state.lessonBestScores[lesson.id])
            assertEquals(setOf("station"), state.learnedExpressionIds)
            assertNull(state.lessonCheckpoint)
            library.recordLessonCompletion("failed-lesson", 20, false)
            assertEquals(setOf(lesson.id), library.state.first().completedLessonIds)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.parentFile?.deleteRecursively()
        }
    }
}

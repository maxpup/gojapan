package com.gojapan.feature.kanalink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KanaProgressDao {
    @Query("SELECT * FROM kana_progress")
    fun observeAll(): Flow<List<KanaProgressEntity>>

    @Query("SELECT * FROM kana_progress ORDER BY kana_id")
    suspend fun getAll(): List<KanaProgressEntity>

    @Query("SELECT COUNT(*) FROM kana_progress WHERE next_review_at IS NOT NULL AND next_review_at <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT * FROM kana_progress WHERE kana_id IN (:ids)")
    suspend fun getByIds(ids: Set<String>): List<KanaProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<KanaProgressEntity>)

    @Query("UPDATE kana_progress SET manual_mastered = 1, manual_mastered_at = :at WHERE kana_id IN (:ids)")
    suspend fun markMastered(ids: Set<String>, at: Long)

    @Query("DELETE FROM kana_progress")
    suspend fun clear()
}

@Dao
interface StudySessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: StudySessionEntity)

    @Query("SELECT COUNT(*) FROM study_session")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM study_session")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT local_date) FROM study_session")
    suspend fun learningDays(): Int

    @Query("SELECT COUNT(DISTINCT local_date) FROM study_session")
    fun observeLearningDays(): Flow<Int>

    @Query("SELECT * FROM study_session WHERE id = :id")
    suspend fun get(id: String): StudySessionEntity?

    @Query("SELECT * FROM study_session WHERE completed_at >= :start AND completed_at < :end ORDER BY completed_at DESC")
    suspend fun between(start: Long, end: Long): List<StudySessionEntity>

    @Query("SELECT * FROM study_session ORDER BY completed_at, id")
    suspend fun getAll(): List<StudySessionEntity>

    @Query("DELETE FROM study_session WHERE id IN (SELECT id FROM study_session ORDER BY completed_at ASC, id ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    @Query("DELETE FROM study_session")
    suspend fun clear()
}

@Dao
interface SessionItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<SessionItemEntity>)

    @Query("SELECT * FROM session_item WHERE session_id = :sessionId ORDER BY kana_id")
    suspend fun forSession(sessionId: String): List<SessionItemEntity>

    @Query("SELECT * FROM session_item WHERE session_id IN (:sessionIds) ORDER BY kana_id")
    suspend fun forSessions(sessionIds: List<String>): List<SessionItemEntity>

    @Query("SELECT * FROM session_item ORDER BY session_id, kana_id")
    suspend fun getAll(): List<SessionItemEntity>
}

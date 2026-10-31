package com.gojapan.feature.kanalink.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "kana_progress")
data class KanaProgressEntity(
    @androidx.room.PrimaryKey @ColumnInfo(name = "kana_id") val kanaId: String,
    @ColumnInfo(name = "seen_count") val seenCount: Int,
    @ColumnInfo(name = "first_try_correct_count") val firstTryCorrectCount: Int,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int,
    @ColumnInfo(name = "hint_count") val hintCount: Int,
    val level: Int,
    @ColumnInfo(name = "last_result") val lastResult: String?,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long?,
    @ColumnInfo(name = "manual_mastered") val manualMastered: Boolean,
    @ColumnInfo(name = "manual_mastered_at") val manualMasteredAt: Long?,
)

@Entity(
    tableName = "study_session",
    indices = [Index("completed_at"), Index("local_date")],
)
data class StudySessionEntity(
    @androidx.room.PrimaryKey val id: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "timezone_id") val timezoneId: String,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    val mode: String,
    val source: String,
    @ColumnInfo(name = "best_combo") val bestCombo: Int,
    @ColumnInfo(name = "wrong_attempts") val wrongAttempts: Int,
    @ColumnInfo(name = "hint_uses") val hintUses: Int,
)

@Entity(
    tableName = "session_item",
    primaryKeys = ["session_id", "kana_id"],
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id"), Index("kana_id")],
)
data class SessionItemEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "kana_id") val kanaId: String,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int,
    @ColumnInfo(name = "first_try_correct") val firstTryCorrect: Boolean,
    @ColumnInfo(name = "hint_used") val hintUsed: Boolean,
    @ColumnInfo(name = "response_ms") val responseMs: Long?,
    @ColumnInfo(name = "was_new") val wasNew: Boolean,
    @ColumnInfo(name = "level_before") val levelBefore: Int,
    @ColumnInfo(name = "level_after") val levelAfter: Int,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long,
    @ColumnInfo(name = "word_snapshot") val wordSnapshot: String,
    @ColumnInfo(name = "meaning_snapshot") val meaningSnapshot: String,
)

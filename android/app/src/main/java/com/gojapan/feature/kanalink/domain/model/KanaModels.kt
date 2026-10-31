package com.gojapan.feature.kanalink.domain.model

@JvmInline
value class KanaId(val value: String) {
    init { require(value.isNotBlank()) }
}

enum class KanaGroup { SEION, DAKUON, HANDAKUON, YOON }

enum class PairMode { HIRAGANA_ROMAJI, KATAKANA_ROMAJI, HIRAGANA_KATAKANA }

enum class RoundSource { DAILY, CUSTOM }

enum class ReviewResult { INDEPENDENT, DIFFICULT, RELEARN }

data class KanaItem(
    val id: KanaId,
    val order: Int,
    val group: KanaGroup,
    val row: String,
    val hiragana: String,
    val katakana: String,
    val romaji: String,
    val word: String,
    val meaningZhCn: String,
    val audioKey: String,
)

data class KanaProgress(
    val kanaId: KanaId,
    val seenCount: Int = 0,
    val firstTryCorrectCount: Int = 0,
    val wrongCount: Int = 0,
    val hintCount: Int = 0,
    val level: Int = -1,
    val lastResult: ReviewResult? = null,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long? = null,
    val manualMastered: Boolean = false,
    val manualMasteredAt: Long? = null,
) {
    init { require(level in -1..5) }
}

data class ItemEvidence(
    val kanaId: KanaId,
    val wrongCount: Int,
    val firstTryCorrect: Boolean,
    val hintUsed: Boolean,
    val responseMs: Long?,
)

data class MemoryUpdate(
    val result: ReviewResult,
    val levelAfter: Int,
    val nextReviewAt: Long,
)

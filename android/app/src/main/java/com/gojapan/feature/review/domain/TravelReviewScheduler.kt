package com.gojapan.feature.review.domain

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 86_400_000L
private val SUCCESS_INTERVAL_DAYS = intArrayOf(1, 3, 7, 14, 30)

enum class TravelReviewResult { RELEARN, DIFFICULT, INDEPENDENT }

data class TravelMemoryState(
    val expressionId: String,
    val level: Int = -1,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long,
)

class TravelReviewScheduler {
    fun complete(old: TravelMemoryState, result: TravelReviewResult, now: Long): TravelMemoryState {
        val newLevel = when (result) {
            TravelReviewResult.RELEARN -> 0
            TravelReviewResult.DIFFICULT -> old.level.coerceAtLeast(0)
            TravelReviewResult.INDEPENDENT -> (old.level + 1).coerceIn(0, SUCCESS_INTERVAL_DAYS.lastIndex)
        }
        val interval = when (result) {
            TravelReviewResult.RELEARN -> 10 * MINUTE_MS
            TravelReviewResult.DIFFICULT -> DAY_MS
            TravelReviewResult.INDEPENDENT -> SUCCESS_INTERVAL_DAYS[newLevel] * DAY_MS
        }
        return old.copy(
            level = newLevel,
            correctCount = old.correctCount + if (result == TravelReviewResult.RELEARN) 0 else 1,
            wrongCount = old.wrongCount + if (result == TravelReviewResult.RELEARN) 1 else 0,
            lastReviewedAt = now,
            nextReviewAt = now + interval,
        )
    }
}


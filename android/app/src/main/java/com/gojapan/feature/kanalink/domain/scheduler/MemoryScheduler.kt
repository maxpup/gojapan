package com.gojapan.feature.kanalink.domain.scheduler

import com.gojapan.feature.kanalink.domain.model.ItemEvidence
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.KanaProgress
import com.gojapan.feature.kanalink.domain.model.MemoryUpdate
import com.gojapan.feature.kanalink.domain.model.ReviewResult
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

private const val DAY_MS = 86_400_000L
private val INTERVAL_DAYS = intArrayOf(1, 3, 7, 15, 30, 60)

fun interface RandomSource { fun nextDouble(): Double }

class KotlinRandomSource(private val random: Random = Random.Default) : RandomSource {
    override fun nextDouble(): Double = random.nextDouble()
}

class MemoryScheduler(private val random: RandomSource = KotlinRandomSource()) {
    fun select(
        course: List<KanaItem>,
        progress: Map<KanaId, KanaProgress>,
        now: Long,
        limit: Int = 5,
    ): List<KanaItem> = course
        .map { item -> item to priority(item, course.size, progress[item.id], now) }
        .sortedWith(compareByDescending<Pair<KanaItem, Double>> { it.second }.thenBy { it.first.order })
        .take(limit.coerceAtMost(course.size))
        .map { it.first }

    fun priority(item: KanaItem, candidateCount: Int, progress: KanaProgress?, now: Long): Double {
        var score = random.nextDouble() * 10.0
        if (progress == null || progress.level == -1) {
            return score + 80 + (candidateCount - item.order)
        }
        val due = progress.nextReviewAt
        if (due != null && due <= now) {
            score += 120
            score += min(80.0, ((now - due).toDouble() / DAY_MS) * 10.0)
        }
        score += progress.wrongCount * 8
        score -= progress.level.coerceAtLeast(0) * 5
        return score
    }

    fun complete(oldLevel: Int, evidence: ItemEvidence, completedAt: Long): MemoryUpdate {
        val result = when {
            evidence.wrongCount >= 2 -> ReviewResult.RELEARN
            evidence.wrongCount == 1 || evidence.hintUsed -> ReviewResult.DIFFICULT
            else -> ReviewResult.INDEPENDENT
        }
        val levelAfter = when (result) {
            ReviewResult.INDEPENDENT -> (oldLevel + 1).coerceIn(0, 5)
            ReviewResult.DIFFICULT -> if (oldLevel == -1) 0 else oldLevel
            ReviewResult.RELEARN -> 0
        }
        return MemoryUpdate(result, levelAfter, completedAt + INTERVAL_DAYS[levelAfter] * DAY_MS)
    }

    fun stageProgress(level: Int): Double = if (level < 0) 0.0 else (level + 1) / 6.0

    fun predictedRetention(progress: KanaProgress, now: Long): Double {
        if (progress.level < 0 || progress.lastReviewedAt == null) return 0.0
        val elapsedDays = (now - progress.lastReviewedAt).coerceAtLeast(0).toDouble() / DAY_MS
        return 0.9.pow(elapsedDays / INTERVAL_DAYS[progress.level])
    }
}

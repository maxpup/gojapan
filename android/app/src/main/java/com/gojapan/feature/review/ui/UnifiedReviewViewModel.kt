package com.gojapan.feature.review.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.kanalink.data.course.KanaCourseRepository
import com.gojapan.feature.kanalink.data.preferences.KanaPreferences
import com.gojapan.feature.kanalink.data.preferences.KanaSkillAnswer
import com.gojapan.feature.kanalink.data.preferences.KanaSkillDimension
import com.gojapan.feature.kanalink.data.repository.CompleteRoundCommand
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.kanalink.domain.model.ItemEvidence
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaProgress
import com.gojapan.feature.kanalink.data.preferences.KanaSkillEvidence
import com.gojapan.feature.kanalink.domain.model.PairMode
import com.gojapan.feature.kanalink.domain.model.RoundSource
import com.gojapan.feature.library.data.TravelLibraryPreferences
import com.gojapan.feature.library.data.TravelLibraryState
import com.gojapan.feature.review.domain.TravelReviewResult
import com.gojapan.feature.search.domain.TravelExpression
import com.gojapan.feature.search.domain.TravelExpressionSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.gojapan.core.analytics.AnalyticsTracker

sealed interface UnifiedReviewItem {
    val key: String
    data class Kana(
        val item: KanaItem,
        val dimension: KanaSkillDimension,
        val dueAt: Long,
        val fromLegacySchedule: Boolean,
    ) : UnifiedReviewItem { override val key = "kana:${item.id.value}:${dimension.name}" }
    data class Expression(val expression: TravelExpression, val dueAt: Long) : UnifiedReviewItem {
        override val key = "expression:${expression.id}"
    }
}

data class UnifiedReviewState(
    val loading: Boolean = true,
    val queue: List<UnifiedReviewItem> = emptyList(),
) {
    val kanaCount get() = queue.count { it is UnifiedReviewItem.Kana }
    val expressionCount get() = queue.count { it is UnifiedReviewItem.Expression }
}

@HiltViewModel
class UnifiedReviewViewModel @Inject constructor(
    courseRepository: KanaCourseRepository,
    private val kanaPreferences: KanaPreferences,
    private val learningRepository: LearningRepository,
    private val travelPreferences: TravelLibraryPreferences,
    private val clock: Clock,
    private val analytics: AnalyticsTracker,
) : ViewModel() {
    private val course = MutableStateFlow<List<KanaItem>?>(null)
    private val answered = MutableStateFlow(emptySet<String>())
    private val nowSignal = MutableStateFlow(clock.millis())
    private val reviewContext = combine(answered, nowSignal) { answeredKeys, now -> answeredKeys to now }
    val state: StateFlow<UnifiedReviewState> = combine(
        course,
        kanaPreferences.skillEvidence,
        learningRepository.observeProgress(),
        travelPreferences.state,
        reviewContext,
    ) { loadedCourse, skills, overall, travel, context ->
        if (loadedCourse == null) return@combine UnifiedReviewState()
        val (answeredKeys, now) = context
        val queue = UnifiedReviewQueueBuilder.build(
            loadedCourse, skills, overall, travel, now, answeredKeys,
        )
        UnifiedReviewState(false, queue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnifiedReviewState())

    init {
        viewModelScope.launch { course.value = courseRepository.getCourse() }
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                nowSignal.value = clock.millis()
            }
        }
    }

    fun refresh() { nowSignal.value = clock.millis() }

    fun answer(item: UnifiedReviewItem, correct: Boolean) {
        answered.value = answered.value + item.key
        viewModelScope.launch {
            try {
                when (item) {
                    is UnifiedReviewItem.Expression -> travelPreferences.recordReview(
                        item.expression.id,
                        if (correct) TravelReviewResult.INDEPENDENT else TravelReviewResult.RELEARN,
                    )
                    is UnifiedReviewItem.Kana -> recordKana(item, correct)
                }
                analytics.track("review_answer", mapOf(
                    "content_type" to if (item is UnifiedReviewItem.Kana) "kana" else "expression",
                    "content_id" to when (item) {
                        is UnifiedReviewItem.Kana -> item.item.id.value
                        is UnifiedReviewItem.Expression -> item.expression.id
                    },
                    "result" to correct,
                    "review_interval" to if (correct) "success" else "10_minutes",
                ))
            } finally {
                answered.value = answered.value - item.key
                nowSignal.value = clock.millis()
            }
        }
    }

    private suspend fun recordKana(review: UnifiedReviewItem.Kana, correct: Boolean) {
        val now = clock.millis()
        kanaPreferences.recordSkillAnswers(listOf(KanaSkillAnswer(review.item.id, review.dimension, correct)), now)
        val zone = ZoneId.systemDefault()
        learningRepository.completeRound(
            CompleteRoundCommand(
                sessionId = "unified-${UUID.randomUUID()}",
                completedAt = now,
                localDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString(),
                timezoneId = zone.id,
                durationSeconds = 0,
                mode = PairMode.HIRAGANA_ROMAJI,
                source = RoundSource.CUSTOM,
                bestCombo = if (correct) 1 else 0,
                wrongAttempts = if (correct) 0 else 1,
                items = listOf(
                    review.item to ItemEvidence(
                        review.item.id,
                        wrongCount = if (correct) 0 else 2,
                        firstTryCorrect = correct,
                        hintUsed = false,
                        responseMs = null,
                    ),
                ),
            ),
        )
    }
}

object UnifiedReviewQueueBuilder {
    fun build(
        course: List<KanaItem>,
        skills: Map<Pair<KanaId, KanaSkillDimension>, KanaSkillEvidence>,
        overall: Map<KanaId, KanaProgress>,
        travel: TravelLibraryState,
        now: Long,
        answeredKeys: Set<String> = emptySet(),
    ): List<UnifiedReviewItem> {
        val byId = course.associateBy { it.id }
        val skillDue = skills.values.filter { evidence -> evidence.nextReviewAt?.let { it <= now } == true }
            .mapNotNull { evidence -> byId[evidence.kanaId]?.let { item ->
                UnifiedReviewItem.Kana(item, evidence.dimension, evidence.nextReviewAt ?: now, false)
            } }
        val skillKanaIds = skillDue.mapTo(mutableSetOf()) { it.item.id }
        val legacyDue = overall.values.filter { progress ->
            progress.nextReviewAt?.let { it <= now } == true && progress.kanaId !in skillKanaIds
        }.mapNotNull { progress -> byId[progress.kanaId]?.let { item ->
            UnifiedReviewItem.Kana(item, KanaSkillDimension.READING, progress.nextReviewAt ?: now, true)
        } }
        val expressionDue = travel.dueReviewIds.mapNotNull { id ->
            TravelExpressionSearch.reviewedExpressions.firstOrNull { it.id == id }?.let { expression ->
                UnifiedReviewItem.Expression(expression, travel.memory[id]?.nextReviewAt ?: now)
            }
        }
        return (skillDue + legacyDue + expressionDue)
            .filter { it.key !in answeredKeys }
            .sortedWith(compareBy<UnifiedReviewItem> {
                when (it) { is UnifiedReviewItem.Kana -> it.dueAt; is UnifiedReviewItem.Expression -> it.dueAt }
            }.thenBy { it.key })
    }
}

package com.gojapan.feature.kanalink.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.kanalink.data.repository.CompleteRoundCommand
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.kanalink.domain.model.ItemEvidence
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.PairMode
import com.gojapan.feature.kanalink.domain.model.RoundSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

enum class CardSide { HIRAGANA, KATAKANA, ROMAJI }
enum class GameStatus { PLAYING, COMPLETING, COMPLETE, FAILED }

data class GameCard(val id: String, val kanaId: KanaId, val text: String, val side: CardSide)

data class GameUiState(
    val items: List<KanaItem>,
    val cards: List<GameCard>,
    val mode: PairMode = PairMode.HIRAGANA_ROMAJI,
    val source: RoundSource = RoundSource.DAILY,
    val selectedCardId: String? = null,
    val matchedKanaIds: Set<KanaId> = emptySet(),
    val wrongCounts: Map<KanaId, Int> = emptyMap(),
    val hintUsed: Set<KanaId> = emptySet(),
    val hintedCardIds: Set<String> = emptySet(),
    val hintsLeft: Int = 3,
    val shufflesLeft: Int = 2,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val elapsedSeconds: Int = 0,
    val responseMs: Map<KanaId, Long> = emptyMap(),
    val status: GameStatus = GameStatus.PLAYING,
) {
    val remainingPairs = items.size - matchedKanaIds.size
}

data class RoundSummary(
    val sessionId: String,
    val items: List<KanaItem>,
    val wrongCounts: Map<KanaId, Int>,
    val hintUsed: Set<KanaId>,
    val seenCounts: Map<KanaId, Int>,
    val levelsAfter: Map<KanaId, Int>,
    val nextReviewAt: Long,
    val durationSeconds: Int,
    val bestCombo: Int,
)

sealed interface GameEvent { data class Completed(val summary: RoundSummary) : GameEvent }

@HiltViewModel
class GameViewModel @Inject constructor(
    private val repository: LearningRepository,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow<GameUiState?>(null)
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<GameEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var sessionId = ""
    private var startedAt = 0L
    private var selectedAt = 0L
    private var timerJob: Job? = null

    fun start(items: List<KanaItem>, mode: PairMode = PairMode.HIRAGANA_ROMAJI, source: RoundSource = RoundSource.DAILY) {
        if (mutableState.value?.status == GameStatus.PLAYING &&
            mutableState.value?.items?.map { it.id } == items.map { it.id } &&
            mutableState.value?.mode == mode && mutableState.value?.source == source
        ) return
        sessionId = UUID.randomUUID().toString()
        startedAt = clock.millis()
        val cards = items.flatMap { item -> cardsFor(item, mode) }.shuffled()
        mutableState.value = GameUiState(items, cards, mode, source)
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                mutableState.value = mutableState.value?.let { current ->
                    if (current.status == GameStatus.PLAYING) {
                        current.copy(elapsedSeconds = ((clock.millis() - startedAt) / 1_000).coerceAtLeast(0).toInt())
                    } else current
                }
            }
        }
    }

    fun select(cardId: String) {
        val state = mutableState.value ?: return
        if (state.status != GameStatus.PLAYING) return
        val card = state.cards.singleOrNull { it.id == cardId } ?: return
        if (card.kanaId in state.matchedKanaIds || state.selectedCardId == cardId) return
        val firstId = state.selectedCardId
        if (firstId == null) {
            selectedAt = clock.millis()
            mutableState.value = state.copy(selectedCardId = cardId, hintedCardIds = emptySet())
            return
        }
        val first = state.cards.single { it.id == firstId }
        val response = state.responseMs.toMutableMap()
        val responseDelta = (clock.millis() - selectedAt).coerceAtLeast(0)
        if (first.kanaId == card.kanaId && first.side != card.side) {
            response[card.kanaId] = (response[card.kanaId] ?: 0) + responseDelta
            val combo = state.combo + 1
            val matched = state.matchedKanaIds + card.kanaId
            mutableState.value = state.copy(
                selectedCardId = null,
                matchedKanaIds = matched,
                hintedCardIds = emptySet(),
                combo = combo,
                bestCombo = maxOf(state.bestCombo, combo),
                responseMs = response,
            )
            if (matched.size == state.items.size) completeRound()
        } else {
            val wrong = state.wrongCounts.toMutableMap()
            wrong[first.kanaId] = (wrong[first.kanaId] ?: 0) + 1
            wrong[card.kanaId] = (wrong[card.kanaId] ?: 0) + 1
            response[first.kanaId] = (response[first.kanaId] ?: 0) + responseDelta
            response[card.kanaId] = (response[card.kanaId] ?: 0) + responseDelta
            mutableState.value = state.copy(
                selectedCardId = null,
                wrongCounts = wrong,
                hintedCardIds = emptySet(),
                combo = 0,
                responseMs = response,
            )
        }
    }

    fun hint() {
        val state = mutableState.value ?: return
        if (state.status != GameStatus.PLAYING || state.hintsLeft == 0) return
        val target = state.items.firstOrNull { it.id !in state.matchedKanaIds } ?: return
        mutableState.value = state.copy(
            hintsLeft = state.hintsLeft - 1,
            hintUsed = state.hintUsed + target.id,
            hintedCardIds = state.cards.filter { it.kanaId == target.id }.map { it.id }.toSet(),
        )
    }

    fun shuffle() {
        val state = mutableState.value ?: return
        if (state.status != GameStatus.PLAYING || state.shufflesLeft == 0) return
        val unmatched = state.cards.filter { it.kanaId !in state.matchedKanaIds }.shuffled().iterator()
        val reordered = state.cards.map { if (it.kanaId in state.matchedKanaIds) it else unmatched.next() }
        mutableState.value = state.copy(
            cards = reordered,
            selectedCardId = null,
            hintedCardIds = emptySet(),
            shufflesLeft = state.shufflesLeft - 1,
        )
    }

    fun retryComplete() {
        if (mutableState.value?.status == GameStatus.FAILED) completeRound()
    }

    private fun completeRound() {
        val state = mutableState.value ?: return
        if (state.status == GameStatus.COMPLETING || state.status == GameStatus.COMPLETE) return
        mutableState.value = state.copy(status = GameStatus.COMPLETING)
        timerJob?.cancel()
        viewModelScope.launch {
            val completedAt = clock.millis()
            val command = CompleteRoundCommand(
                sessionId = sessionId,
                completedAt = completedAt,
                localDate = Instant.ofEpochMilli(completedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                timezoneId = ZoneId.systemDefault().id,
                durationSeconds = ((completedAt - startedAt) / 1_000).coerceAtLeast(0).toInt(),
                mode = state.mode,
                source = state.source,
                bestCombo = state.bestCombo,
                wrongAttempts = state.wrongCounts.values.sum() / 2,
                items = state.items.map { item ->
                    val wrong = state.wrongCounts[item.id] ?: 0
                    item to ItemEvidence(
                        item.id, wrong, wrong == 0, item.id in state.hintUsed, state.responseMs[item.id],
                    )
                },
            )
            runCatching { repository.completeRound(command) }
                .onSuccess { receipt ->
                    mutableState.value = mutableState.value?.copy(status = GameStatus.COMPLETE)
                    eventChannel.send(
                        GameEvent.Completed(
                            RoundSummary(
                                sessionId = receipt.sessionId,
                                items = state.items,
                                wrongCounts = state.wrongCounts,
                                hintUsed = state.hintUsed,
                                seenCounts = receipt.items.associate { it.kanaId to it.seenCount },
                                levelsAfter = receipt.items.associate { it.kanaId to it.levelAfter },
                                nextReviewAt = receipt.items.minOf { it.nextReviewAt },
                                durationSeconds = command.durationSeconds,
                                bestCombo = state.bestCombo,
                            ),
                        ),
                    )
                }
                .onFailure { mutableState.value = mutableState.value?.copy(status = GameStatus.FAILED) }
        }
    }

    private fun cardsFor(item: KanaItem, mode: PairMode): List<GameCard> = when (mode) {
        PairMode.HIRAGANA_ROMAJI -> listOf(
            GameCard("${item.id.value}-h", item.id, item.hiragana, CardSide.HIRAGANA),
            GameCard("${item.id.value}-r", item.id, item.romaji, CardSide.ROMAJI),
        )
        PairMode.KATAKANA_ROMAJI -> listOf(
            GameCard("${item.id.value}-k", item.id, item.katakana, CardSide.KATAKANA),
            GameCard("${item.id.value}-r", item.id, item.romaji, CardSide.ROMAJI),
        )
        PairMode.HIRAGANA_KATAKANA -> listOf(
            GameCard("${item.id.value}-h", item.id, item.hiragana, CardSide.HIRAGANA),
            GameCard("${item.id.value}-k", item.id, item.katakana, CardSide.KATAKANA),
        )
    }
}

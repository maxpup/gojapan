package com.gojapan.feature.kanalink.ui.game

import com.gojapan.feature.kanalink.data.repository.CompleteRoundCommand
import com.gojapan.feature.kanalink.data.repository.CompleteRoundResult
import com.gojapan.feature.kanalink.data.repository.CompletedItem
import com.gojapan.feature.kanalink.data.repository.HomeStats
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.kanalink.data.repository.LearningReport
import com.gojapan.feature.kanalink.data.repository.KanaExport
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.KanaProgress
import com.gojapan.feature.kanalink.domain.model.PairMode
import com.gojapan.feature.kanalink.domain.model.RoundSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private lateinit var viewModel: GameViewModel
    private lateinit var repository: FakeRepository
    private val a = item("a", "あ", "a", 0)
    private val i = item("i", "い", "i", 1)

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeRepository()
        viewModel = GameViewModel(repository, Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC))
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun mismatchIsAttributedToBothKanaAndMatchBuildsCombo() {
        viewModel.start(listOf(a, i))
        viewModel.select("a-h")
        viewModel.select("i-r")
        assertEquals(1, viewModel.state.value?.wrongCounts?.get(a.id))
        assertEquals(1, viewModel.state.value?.wrongCounts?.get(i.id))

        viewModel.select("a-h")
        viewModel.select("a-r")
        assertTrue(a.id in viewModel.state.value!!.matchedKanaIds)
        assertEquals(1, viewModel.state.value?.combo)
    }

    @Test
    fun hintAndShuffleConsumeTheirBudgets() {
        viewModel.start(listOf(a, i))
        viewModel.hint()
        assertEquals(2, viewModel.state.value?.hintsLeft)
        assertTrue(viewModel.state.value!!.hintUsed.isNotEmpty())
        viewModel.shuffle()
        assertEquals(1, viewModel.state.value?.shufflesLeft)
    }

    @Test
    fun finalPairWritesExactlyOneCompleteRound() {
        viewModel.start(listOf(a, i))
        match(a)
        match(i)

        assertEquals(GameStatus.COMPLETE, viewModel.state.value?.status)
        assertEquals(1, repository.commands.size)
        assertEquals(setOf(a.id, i.id), repository.commands.single().items.map { it.first.id }.toSet())

        viewModel.select("i-r")
        assertEquals(1, repository.commands.size)
    }

    @Test
    fun failedCommitCanRetryWithTheSameSessionId() {
        repository.failuresLeft = 1
        viewModel.start(listOf(a, i))
        match(a)
        match(i)
        assertEquals(GameStatus.FAILED, viewModel.state.value?.status)

        viewModel.retryComplete()

        assertEquals(GameStatus.COMPLETE, viewModel.state.value?.status)
        assertEquals(2, repository.commands.size)
        assertEquals(repository.commands[0].sessionId, repository.commands[1].sessionId)
    }

    @Test
    fun customKatakanaModeBuildsCorrectCardsAndPersistsModeAndSource() {
        viewModel.start(listOf(a), PairMode.KATAKANA_ROMAJI, RoundSource.CUSTOM)
        assertEquals(setOf(CardSide.KATAKANA, CardSide.ROMAJI), viewModel.state.value!!.cards.map { it.side }.toSet())

        viewModel.select("a-k")
        viewModel.select("a-r")

        assertEquals(PairMode.KATAKANA_ROMAJI, repository.commands.single().mode)
        assertEquals(RoundSource.CUSTOM, repository.commands.single().source)
    }

    @Test
    fun hiraganaKatakanaModeHasNoRomajiCard() {
        viewModel.start(listOf(a), PairMode.HIRAGANA_KATAKANA, RoundSource.CUSTOM)
        assertEquals(setOf(CardSide.HIRAGANA, CardSide.KATAKANA), viewModel.state.value!!.cards.map { it.side }.toSet())
    }

    private fun match(item: KanaItem) {
        viewModel.select("${item.id.value}-h")
        viewModel.select("${item.id.value}-r")
    }

    private fun item(id: String, hira: String, romaji: String, order: Int) = KanaItem(
        KanaId(id), order, KanaGroup.SEION, "row", hira, hira, romaji, hira, hira, "kana_$id",
    )
}

private class FakeRepository : LearningRepository {
    val commands = mutableListOf<CompleteRoundCommand>()
    var failuresLeft = 0
    override fun observeProgress(): Flow<Map<KanaId, KanaProgress>> = flowOf(emptyMap())
    override fun observeHomeStats(now: Long): Flow<HomeStats> = flowOf(HomeStats(emptyMap(), 0, 0, 0))
    override suspend fun completeRound(command: CompleteRoundCommand): CompleteRoundResult {
        commands += command
        if (failuresLeft-- > 0) error("simulated write failure")
        return CompleteRoundResult(
            command.sessionId,
            true,
            command.items.map { (item, _) -> CompletedItem(item.id, 1, 0, command.completedAt + 86_400_000) },
        )
    }
    override suspend fun markMastered(ids: Set<KanaId>, at: Long) = Unit
    override suspend fun getReport(start: Long, end: Long, previousStart: Long) = LearningReport(0, 0, 0, 0, 0, emptyList())
    override suspend fun exportData() = KanaExport(emptyList(), emptyList(), emptyList())
    override suspend fun clearHistory() = Unit
}

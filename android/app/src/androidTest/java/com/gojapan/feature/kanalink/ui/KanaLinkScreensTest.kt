package com.gojapan.feature.kanalink.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.ui.game.CardSide
import com.gojapan.feature.kanalink.ui.game.GameCard
import com.gojapan.feature.kanalink.ui.game.GameScreen
import com.gojapan.feature.kanalink.ui.game.GameUiState
import com.gojapan.feature.kanalink.ui.game.RoundSummary
import com.gojapan.feature.kanalink.ui.result.ResultScreen
import com.gojapan.feature.kanalink.ui.custom.CustomPracticeScreen
import com.gojapan.feature.kanalink.ui.custom.CustomPracticeUiState
import com.gojapan.feature.kanalink.domain.model.PairMode
import com.gojapan.feature.kanalink.ui.report.ReportPeriod
import com.gojapan.feature.kanalink.ui.report.ReportScreen
import com.gojapan.feature.kanalink.ui.report.ReportUiState
import com.gojapan.feature.kanalink.data.repository.LearningReport
import com.gojapan.feature.kanalink.ui.settings.SettingsScreen
import com.gojapan.feature.kanalink.data.preferences.KanaSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KanaLinkScreensTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val kana = KanaItem(
        KanaId("a"), 0, KanaGroup.SEION, "A", "あ", "ア", "a", "朝（あさ）", "早晨", "kana_a",
    )

    @Test
    fun exitRequiresConfirmationAndExplainsNoWrite() {
        var exited = false
        val cards = listOf(
            GameCard("a-h", kana.id, kana.hiragana, CardSide.HIRAGANA),
            GameCard("a-r", kana.id, kana.romaji, CardSide.ROMAJI),
        )
        composeRule.setContent {
            GameScreen(GameUiState(listOf(kana), cards), {}, {}, {}, {}, { exited = true })
        }

        composeRule.onNodeWithText("退出").performClick()
        composeRule.onNodeWithText("未完成的棋盘不会写入学习记录。").assertExists()
        composeRule.onNodeWithText("确认退出").performClick()
        composeRule.runOnIdle { assertTrue(exited) }
    }

    @Test
    fun cardsExposeStateWithoutRelyingOnColor() {
        val cards = listOf(
            GameCard("a-h", kana.id, kana.hiragana, CardSide.HIRAGANA),
            GameCard("a-r", kana.id, kana.romaji, CardSide.ROMAJI),
        )
        composeRule.setContent {
            GameScreen(
                GameUiState(
                    items = listOf(kana),
                    cards = cards,
                    selectedCardId = "a-h",
                    hintedCardIds = setOf("a-r"),
                ),
                {}, {}, {}, {}, {},
            )
        }

        composeRule.onNode(hasContentDescription("平假名 あ"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已选择"))
        composeRule.onNode(hasContentDescription("罗马音 a"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "提示目标"))
        composeRule.onNodeWithText("● あ").assertExists()
        composeRule.onNodeWithText("提示 a").assertExists()
    }

    @Test
    fun resultSelectionEnablesManualMastery() {
        val summary = RoundSummary(
            "session", listOf(kana), emptyMap(), emptySet(), mapOf(kana.id to 1), mapOf(kana.id to 0),
            1_800_086_400_000L, 12, 1,
        )
        val selected = mutableStateOf(emptySet<KanaId>())
        composeRule.setContent {
            ResultScreen(
                summary = summary,
                selected = selected.value,
                onToggle = { selected.value = setOf(it) },
                onMarkMastered = {},
                onAgain = {},
                onHome = {},
            )
        }

        composeRule.onNodeWithText("全部完成，太棒了！").assertExists()
        composeRule.onNodeWithText("标记选中项为自评记熟").assertIsNotEnabled()
        composeRule.onNodeWithText("あ a").performClick()
        composeRule.onNodeWithText("标记选中项为自评记熟").assertIsEnabled()
    }

    @Test
    fun customPracticeRequiresAtLeastOneGroup() {
        composeRule.setContent {
            CustomPracticeScreen(
                state = CustomPracticeUiState(PairMode.HIRAGANA_KATAKANA, emptySet(), 0),
                onMode = {}, onGroup = {}, onAll = {}, onClear = {}, onStart = {}, onBack = {},
            )
        }
        composeRule.onNodeWithText("平假名 ↔ 片假名").assertExists()
        composeRule.onNodeWithText("已选择 0 个音，本轮最多 5 个").assertExists()
        composeRule.onNodeWithText("开始自定义练习").assertIsNotEnabled()
    }

    @Test
    fun emptyReportDoesNotInventHistoricalData() {
        composeRule.setContent {
            ReportScreen(ReportUiState(ReportPeriod.WEEK, LearningReport(0, 0, 0, 0, 0, emptyList())), {}, {}, {}, {})
        }
        composeRule.onNodeWithText("本期还没有完整练习记录").assertExists()
        composeRule.onNodeWithText("每周").assertExists()
    }

    @Test
    fun clearHistoryRequiresSecondConfirmation() {
        var cleared = false
        composeRule.setContent {
            SettingsScreen(KanaSettings(), {}, {}, {}, onClearHistory = { cleared = true }, onBack = {})
        }
        composeRule.onNodeWithText("清除学习记录").performClick()
        composeRule.onNodeWithText("进度和完整轮次会永久删除；练习模式与开关设置会保留。").assertExists()
        composeRule.runOnIdle { assertTrue(!cleared) }
        composeRule.onNodeWithText("确认清除").performClick()
        composeRule.runOnIdle { assertTrue(cleared) }
    }
}

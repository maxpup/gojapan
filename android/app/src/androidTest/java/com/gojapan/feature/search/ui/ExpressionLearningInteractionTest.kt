package com.gojapan.feature.search.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.gojapan.app.theme.GoJapanTheme
import com.gojapan.feature.search.domain.TravelExpressionSearch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExpressionLearningInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val expression = TravelExpressionSearch.reviewedExpressions.first { it.id == "toilet" }

    @Test fun selectingWordAndOpeningGrammarNeverStartsPlayback() {
        val spoken = mutableListOf<String>()
        rule.setContent { GoJapanTheme { Column { ExpressionLearningContent(expression, true) { spoken += it } } } }
        rule.onNodeWithText("は").performClick()
        rule.runOnIdle { assertEquals(emptyList<String>(), spoken) }
        rule.onNodeWithText("提示话题").assertIsDisplayed()
        rule.onNodeWithText("查看语法结构").performClick()
        rule.onNodeWithText("「ですか」", substring = true).assertIsDisplayed()
        rule.runOnIdle { assertEquals(emptyList<String>(), spoken) }
        rule.onNodeWithText("▶ 播放「は」").performClick()
        rule.runOnIdle { assertEquals(listOf("わ"), spoken) }
    }

    @Test fun darkThemeKeepsUnavailableAudioDisabled() {
        rule.setContent { GoJapanTheme(darkTheme = true) { Column { ExpressionLearningContent(expression, false) {} } } }
        rule.onNodeWithText("トイレ").performClick()
        rule.onNodeWithText("▶ 播放「トイレ」").assertIsNotEnabled()
    }
}

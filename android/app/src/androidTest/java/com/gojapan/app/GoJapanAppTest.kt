package com.gojapan.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material3.Text
import org.junit.Rule
import org.junit.Test

class GoJapanAppTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fiveTabsAreVisibleAndHomeIsSelectedForKanaPath() {
        composeRule.setContent { GoJapanApp(kanaLinkContent = { Text("KANA LINK") }) }
        listOf("首页", "学习", "复习", "搜索", "收藏").forEach {
            composeRule.onNode(hasText(it) and hasClickAction()).assertExists()
        }
        composeRule.onNode(hasText("首页") and hasClickAction()).assertIsSelected()
    }

    @Test
    fun kanaPathDisplaysInjectedContentInsideHomeDestination() {
        composeRule.setContent { GoJapanApp(kanaLinkContent = { Text("KANA LINK") }) }
        composeRule.onNodeWithText("KANA LINK").assertExists()
    }
}

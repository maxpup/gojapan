package com.gojapan.app

import androidx.activity.compose.setContent
import com.gojapan.MainActivity
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
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun twoTabsAreVisibleAndTripsIsSelected() {
        composeRule.activity.runOnUiThread { composeRule.activity.setContent { GoJapanApp(kanaLinkContent = { Text("KANA LINK") }) } }
        listOf("行程", "语言", "记账").forEach {
            composeRule.onNode(hasText(it) and hasClickAction()).assertExists()
        }
        composeRule.onNode(hasText("行程") and hasClickAction()).assertIsSelected()
    }

    @Test
    fun kanaPathDisplaysInjectedContentInsideHomeDestination() {
        composeRule.activity.runOnUiThread { composeRule.activity.setContent { GoJapanApp(kanaLinkContent = { Text("KANA LINK") }) } }
        composeRule.onNode(hasText("语言") and hasClickAction()).performClick()
        composeRule.onNodeWithText("五十音").performClick()
        composeRule.onNodeWithText("KANA LINK").assertExists()
    }
}

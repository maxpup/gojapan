package com.gojapan.feature.trip.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class SwipeDeleteConfirmationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun swipeKeepsItemUntilDeletionIsConfirmed() {
        rule.setContent {
            var present by remember { mutableStateOf(true) }
            if (present) SwipeDeleteRow(true, "订车票", { present = false }) {
                Text("订车票", Modifier.fillMaxWidth().padding(16.dp))
            }
        }
        fun swipe() = rule.onNodeWithText("订车票").performTouchInput { swipeLeft() }
        swipe()
        rule.onNodeWithText("确认删除？").assertIsDisplayed()
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithText("订车票").assertIsDisplayed()
        swipe()
        rule.onNode(hasText("删除") and hasClickAction()).performClick()
        rule.onNodeWithText("订车票").assertDoesNotExist()
    }
}

package com.gojapan.feature.trip.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class TripInlineInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun addInModuleThenSwipeDeleteAndUndoWithoutLeavingDay() {
        rule.setContent { InteractiveTripPreview(initialDay = 0) }
        rule.onNodeWithText("＋ 新增美食推荐").performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and hasText("美食推荐")).performTextInput("测试早餐饭团")
        rule.onNodeWithText("添加").performScrollTo().performClick()
        rule.onNodeWithText("11月21日").assertIsSelected()
        rule.onNodeWithText("测试早餐饭团").performScrollTo().assertIsDisplayed()
        rule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and
                hasAnyDescendant(hasText("测试早餐饭团"))
        ).performTouchInput { swipeLeft() }
        rule.onNodeWithText("测试早餐饭团").assertDoesNotExist()
        rule.onNodeWithText("撤销").performClick()
        rule.onNodeWithText("测试早餐饭团").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("11月21日").assertIsSelected()
    }
}

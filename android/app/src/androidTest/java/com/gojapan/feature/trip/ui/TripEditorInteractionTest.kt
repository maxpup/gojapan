package com.gojapan.feature.trip.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.gojapan.feature.trip.model.TripPlan
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TripEditorInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun addedFoodFieldIsVisibleFocusedAndSaved() {
        var saved: TripPlan? = null
        rule.setContent {
            TripEditor(
                TripPlan(tripTitle = "东京之旅", destination = "东京", days = emptyList()),
                initialDay = -1, onDismiss = {}, onSave = { saved = it; true },
            )
        }
        rule.onNodeWithText("添加美食推荐").performScrollTo().performClick()
        rule.onNodeWithText("美食推荐 1").assertIsDisplayed().assertIsFocused().performTextInput("午餐：拉面")
        rule.onNodeWithText("保存").performClick()
        rule.runOnIdle { assertEquals(listOf("午餐：拉面"), saved?.foodRecommendations) }
    }

    @Test fun addedDayAndStopRevealTheirInputFields() {
        rule.setContent {
            TripEditor(
                TripPlan(tripTitle = "东京之旅", destination = "东京", days = emptyList()),
                initialDay = -1, onDismiss = {}, onSave = { true },
            )
        }
        rule.onNodeWithText("添加一天").performScrollTo().performClick()
        rule.onNodeWithText("当天主题").assertIsDisplayed().assertIsFocused().performTextInput("浅草散步")
        rule.onNodeWithText("添加地点").performScrollTo().performClick()
        rule.onNodeWithText("地点名称").assertIsDisplayed().assertIsFocused().performTextInput("浅草寺")
    }
}

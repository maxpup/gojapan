package com.gojapan.feature.trip.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

class TripVisualTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun styledTabsSwitchAndEditorStillOpens() {
        rule.setContent { InteractiveTripPreview(initialDay = -1) }
        rule.onNodeWithText("总览").assertIsSelected()
        screenshot("trip-overview.png")
        rule.onAllNodesWithText("11月21日")[0].performClick()
        rule.onAllNodesWithText("11月21日")[0].assertIsSelected()
        rule.onNodeWithText("浅草寺").assertIsDisplayed()
        screenshot("trip-day.png")
        rule.onNodeWithText("更多编辑").performClick()
        rule.onNodeWithText("保存").assertIsDisplayed()
        rule.onNodeWithText("行程主题").performTextReplacement("东京慢旅行")
        rule.onAllNodesWithText("添加美食推荐")[0].performScrollTo().performClick()
        rule.onAllNodesWithText("美食推荐 1")[0].assertIsDisplayed().performTextInput("早餐：饭团")
        rule.onNodeWithText("保存").performClick()
        rule.onNodeWithText("东京慢旅行").assertIsDisplayed()
        rule.onNodeWithText("更多编辑").performClick()
        rule.onAllNodesWithText("美食推荐 1")[0].performScrollTo().assertTextContains("早餐：饭团")
        rule.onNodeWithText("取消").performClick()
    }

    private fun screenshot(name: String) {
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { File(it).apply { mkdirs() } } ?: context.cacheDir
        File(outputDir, name).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

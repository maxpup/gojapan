package com.gojapan.feature.trip.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.gojapan.feature.trip.model.TripStop
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StopLocationFieldTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectionStoresCoordinatesAndChangingAddressClearsOldPoint() {
        var saved = TripStop("浅草寺", googleAddress = "旧地址", latitude = 1.0, longitude = 2.0)
        rule.setContent {
            var draft by remember { mutableStateOf(saved) }
            StopLocationField(draft, true, { draft = it; saved = it },
                search = { query -> listOf(StopSuggestion("temple", "浅草寺 · 东京 · $query")) },
                locate = { StopLocation("东京都台东区浅草二丁目", 35.7148, 139.7967) })
        }
        rule.onNodeWithText("Google 地图地址").performTextReplacement("浅草寺")
        rule.runOnIdle { assertNull(saved.latitude); assertNull(saved.longitude) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("浅草寺 · 东京 · 浅草寺").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("浅草寺 · 东京 · 浅草寺").performClick()
        rule.onNodeWithText("已定位，可在地图上查看").assertExists()
        rule.runOnIdle {
            assertEquals(35.7148, saved.latitude!!, 0.00001)
            assertEquals(139.7967, saved.longitude!!, 0.00001)
            val encoded = Json.encodeToString(TripStop.serializer(), saved)
            assertEquals(saved, Json.decodeFromString(TripStop.serializer(), encoded))
        }
        rule.onNodeWithText("Google 地图地址").performTextReplacement("东京站")
        rule.runOnIdle { assertNull(saved.latitude); assertNull(saved.longitude) }
    }

    @Test fun olderAddressCanBeSearchedAndFailedRequestCanBeRetried() {
        var attempts = 0
        rule.setContent {
            var draft by remember { mutableStateOf(TripStop("浅草寺", googleAddress = "浅草寺")) }
            StopLocationField(draft, true, { draft = it },
                search = {
                    attempts++
                    if (attempts == 1) error("测试网络失败")
                    listOf(StopSuggestion("temple", "浅草寺 · 东京"))
                },
                locate = { StopLocation("浅草寺", 35.7148, 139.7967) })
        }
        rule.onNodeWithText("搜索并定位此地址").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("测试网络失败").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("重试").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("浅草寺 · 东京").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("浅草寺 · 东京").performClick()
        rule.onNodeWithText("已定位，可在地图上查看").assertExists()
    }

    @Test fun lateResponseCannotReplaceAChangedSearch() {
        var saved = TripStop("地点")
        rule.setContent {
            var draft by remember { mutableStateOf(saved) }
            StopLocationField(draft, true, { draft = it; saved = it },
                search = { listOf(StopSuggestion(it, "候选$it")) },
                locate = { delay(2000); StopLocation(it.label, 1.0, 2.0) })
        }
        rule.onNodeWithText("Google 地图地址").performTextInput("浅草寺")
        rule.waitUntil(5000) { rule.onAllNodesWithText("候选浅草寺").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("候选浅草寺").performClick()
        rule.onNodeWithText("Google 地图地址").performTextReplacement("东京站")
        rule.waitUntil(5000) { rule.onAllNodesWithText("候选东京站").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle {
            assertEquals("东京站", saved.googleAddress)
            assertNull(saved.latitude)
        }
    }
}

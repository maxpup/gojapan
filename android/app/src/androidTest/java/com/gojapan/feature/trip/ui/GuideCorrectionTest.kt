package com.gojapan.feature.trip.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.gojapan.core.ai.*
import com.gojapan.feature.trip.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuideCorrectionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun questionsAndCorrectionsContinueUntilUserConfirms() {
        val current = TripPlan(tripTitle = "现有计划", destination = "东京", days = emptyList())
        var saved: TripPlan? = null
        var calls = 0
        rule.setContent {
            GuideTextImportScreen(current, { saved = it; true }, {}, { AiChatAvailability.Available }) { turns, _, _, _ ->
                calls++
                check(turns.first().content.contains("去公园"))
                when (calls) {
                    1 -> """{"question":"哪座公园？"}"""
                    2 -> """{"question":"安排在哪天？"}"""
                    else -> {
                        check(turns.any { it.content == "上野公园" })
                        val date = turns.last().content
                        """{"tripTitle":"","destination":"","days":[{"date":"$date","title":"散步","summary":"","stops":[{"name":"上野公园"}]}]}"""
                    }
                }
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("去公园")
        rule.onNodeWithText("生成攻略").performClick()
        fun answer(text: String) {
            rule.onNodeWithText("补充或修改行程要求").performScrollTo().performTextInput(text)
            rule.onNodeWithText("发送给 AI").performScrollTo().performClick()
        }
        rule.onNodeWithText("哪座公园？").assertExists()
        rule.onNodeWithText("确认导入").assertDoesNotExist()
        answer("上野公园")
        rule.onNodeWithText("安排在哪天？").assertExists()
        answer("第 1 天")
        rule.onNodeWithText("确认导入").assertIsEnabled()
        answer("第 2 天")
        assertNull(saved)
        rule.onNodeWithText("确认导入").performScrollTo().performClick()
        rule.waitUntil { saved != null }
        assertEquals("第 2 天", saved!!.days.single().date)
        assertEquals(4, calls)
    }

    @Test fun emptyContentKeepsServiceResponseVisible() {
        val body = """{"choices":[{"finish_reason":"length","message":{"content":""}}],"usage":{"completion_tokens":4096}}"""
        rule.setContent {
            GuideTextImportScreen(TripPlan(tripTitle = "测试", destination = "东京", days = emptyList()),
                { false }, {}, { AiChatAvailability.Available }) { _, _, _, report ->
                report(200, body)
                throw AiChatException("AI 输出达到长度上限，未返回回复正文")
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("浅草寺")
        rule.onNodeWithText("生成攻略").performClick()
        rule.onNodeWithText("第 1 次服务原始响应（HTTP 200）\n$body").assertExists()
        rule.onNodeWithText("AI 输出达到长度上限，未返回回复正文").assertExists()
    }

    @Test fun datedRouteReplacesFirstPlaceholderDayOnlyAfterConfirmation() {
        val first = TripDay("第 1 天", "旧路线", "", listOf(TripStop("旧站")), todos = listOf("订票"))
        val second = first.copy(date = "第 2 天", title = "次日")
        val current = TripPlan(tripTitle = "当前计划", destination = "东京", days = listOf(first, second))
        val incoming = first.copy(date = "11月21日", title = "新路线", stops = listOf(TripStop("新站")), todos = emptyList())
        var saved: TripPlan? = null
        rule.setContent {
            GuideTextImportScreen(current, { saved = it; true }, {}, { AiChatAvailability.Available }) { _, _, _, _ ->
                kotlinx.serialization.json.Json.encodeToString(TripPlan.serializer(), current.copy(days = listOf(incoming)))
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("11月21日去新站")
        rule.onNodeWithText("生成攻略").performClick()
        rule.onNodeWithText("原安排：旧站").assertExists()
        rule.onNodeWithText("新安排：新站").assertExists()
        assertNull(saved)
        rule.onNodeWithText("确认导入").performScrollTo().performClick()
        rule.waitUntil { saved != null }
        assertEquals(listOf(incoming.copy(todos = first.todos), second), saved!!.days)
        assertEquals(0, current.importTarget(incoming.copy(date = "第一天"), 0))
    }

    @Test fun invalidPointsAreReportedAndCorrectedPreviewCanBeSaved() {
        val source = "从浅草寺前往上野公园，搭乘银座线。"
        val original = TripPlan(tripTitle = "东京之旅", destination = "东京",
            days = listOf(TripDay("第 1 天", "原安排", "", listOf(TripStop("浅草寺", time = "上午")))))
        var saved: TripPlan? = null
        val invalid = """{"tripTitle":"东京","destination":"东京","days":[{"date":"第 1 天","title":"游览","summary":"","stops":[{"name":""}]}]}"""
        var calls = 0
        rule.setContent {
            GuideTextImportScreen(original, { saved = it; true }, {}, { AiChatAvailability.Available }) { turns, _, _, _ ->
                calls++
                if (calls == 1) invalid else {
                    assertEquals(2, calls)
                    assertTrue(turns.first().content.contains(source))
                    assertEquals(AiChatTurn(AiChatRole.ASSISTANT, invalid), turns[1])
                    assertTrue(turns.last().content.contains("days[0].stops[0].name"))
                    """{"tripTitle":"东京","destination":"东京","days":[{"date":"第 1 天","title":"游览","summary":"","stops":[{"name":"浅草寺"},{"name":"上野公园","time":"下午"}],"packingItems":["伞"],"todos":["预订门票"],"precautions":["周一闭馆"]}]}"""
                }
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput(source)
        rule.onNodeWithText("生成攻略").performClick()
        rule.onNodeWithText("确认导入").assertIsEnabled()
        rule.onNodeWithText("第 1 天 · 替换日程：原安排 → 游览").assertExists()
        rule.onNode(isToggleable()).performScrollTo().performClick()
        assertNull(saved)
        rule.onNodeWithText("第 1 天 · 新增携带物品：伞").assertExists()
        rule.onNodeWithText("第 1 次 AI 回复\n$invalid").assertExists()
        rule.onNodeWithText("用户消息\n校验失败：days[0].stops[0].name", substring = true).assertExists()
        rule.onNodeWithText("第 2 次 AI 回复", substring = true).assertExists()
        rule.onNodeWithText("攻略校验通过").assertExists()
        rule.onNodeWithText("确认导入").performScrollTo().performClick()
        rule.waitUntil { saved != null }
        val day = requireNotNull(saved).days.single()
        assertEquals(original.days.single().stops.first(), day.stops.first())
        assertEquals(listOf("浅草寺", "上野公园"), day.stops.map { it.name })
        assertEquals(listOf("预订门票"), day.todos)
        assertEquals(listOf("周一闭馆"), day.precautions)
        assertEquals(listOf("伞"), day.packingItems)
        assertEquals(2, calls)
    }
}

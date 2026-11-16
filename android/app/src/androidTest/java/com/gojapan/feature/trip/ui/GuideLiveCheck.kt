package com.gojapan.feature.trip.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.gojapan.MainActivity
import org.junit.Rule
import org.junit.Test

class GuideLiveCheck {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun compactHeaderAndSectionTabs() {
        rule.onNode(hasText("行程") and hasClickAction()).performClick()
        val edit = rule.onNodeWithContentDescription("修改日程").fetchSemanticsNode().boundsInRoot
        val entry = rule.onNodeWithContentDescription("攻略导入")
        check(entry.fetchSemanticsNode().boundsInRoot.left >= edit.right)
        for (label in listOf("注意事项", "待办", "美食", "日程")) {
            val tab = rule.onNode(hasText(label) and isSelectable())
            tab.performClick()
            tab.assertIsSelected()
        }
        entry.performClick()
        rule.onNodeWithText("生成攻略").assertExists()
    }
    @Test fun generateLongPreview() {
        val places = listOf("浅草寺", "吉祥寺", "上野公园", "东京塔", "箱根", "大阪城", "奈良公园", "清水寺", "成田机场")
        val source = places.mapIndexed { index, place ->
            val notes = (1..24).joinToString("\n") { stop ->
                "观察点 $stop 位于示例区域。步行前先核对入口标识，留意同行者的集合时间，雨天可以缩短步行距离并提前返回集合地点。"
            }
            "第 ${index + 1} 天：$place。\n$notes"
        }.joinToString("\n") + "\n最后一天16:40返程。"
        rule.onNode(hasText("行程") and hasClickAction()).performClick()
        rule.onNodeWithContentDescription("攻略导入").performClick()
        rule.onNode(hasSetTextAction()).performTextInput(source)
        rule.onNodeWithText("生成攻略").performScrollTo().performClick()
        rule.waitUntil(180000) {
            rule.onAllNodesWithText("确认导入").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("重试", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("确认导入").assertExists()
        for (term in places + "16:40") {
            check(rule.onAllNodes(hasText(term, substring = true) and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()) { term }
        }
    }
    @Test fun generatePreview() {
        rule.onNode(hasText("行程") and hasClickAction()).performClick()
        rule.onNodeWithContentDescription("攻略导入").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("第一天先去浅草寺，再去上野公园。交通建议：搭乘银座线。")
        rule.onNodeWithText("生成攻略").performScrollTo().performClick()
        rule.waitUntil(120000) {
            rule.onAllNodesWithText("确认导入").fetchSemanticsNodes().isNotEmpty()
        }
        for (term in listOf("浅草寺","上野公园","银座线")) {
            check(rule.onAllNodes(hasText(term, substring = true) and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
        }
    }
}

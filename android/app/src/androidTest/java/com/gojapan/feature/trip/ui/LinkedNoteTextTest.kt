package com.gojapan.feature.trip.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LinkedNoteTextTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun copiedShareTextOpensIndividualLinksWithFullRouteParameters() {
        val xhs = "http://xhslink.com/o/Abc123"
        val route = "https://www.google.com/maps/dir/?api=1&origin=Tokyo&destination=Asakusa&travelmode=transit"
        val text = "小红书攻略 $xhs，复制本条信息打开小红书\n路线 $route"
        val opened = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) { LinkedNoteText(text) }
        }
        fun clickLink(url: String) {
            val layouts = mutableListOf<TextLayoutResult>()
            val node = rule.onNodeWithText(text)
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val box = layouts.single().getBoundingBox(text.indexOf(url) + 8)
            node.performTouchInput { click(box.center) }
        }
        clickLink(xhs)
        clickLink(route)
        rule.runOnIdle { assertEquals(listOf(xhs, route), opened) }
        rule.onNodeWithText(text).assertIsDisplayed()
    }
}

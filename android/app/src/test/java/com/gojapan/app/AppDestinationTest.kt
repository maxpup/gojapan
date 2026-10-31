package com.gojapan.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    @Test
    fun destinationsKeepProductOrder() {
        assertEquals(
            listOf("首页", "学习", "复习", "行程", "搜索", "收藏"),
            AppDestination.entries.map(AppDestination::label),
        )
    }
}

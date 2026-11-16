package com.gojapan.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    @Test
    fun destinationsKeepProductOrder() {
        assertEquals(
            listOf("行程", "语言", "记账"),
            AppDestination.tabs.map(AppDestination::label),
        )
    }
}

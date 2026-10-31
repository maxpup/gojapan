package com.gojapan.feature.search.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelExpressionSearchTest {
    @Test fun `exact Chinese match ranks first`() {
        val results = TravelExpressionSearch.search("洗手间")
        assertEquals("toilet", results.first().id)
    }

    @Test fun `common spoken aliases are recalled`() {
        assertEquals("bill", TravelExpressionSearch.search("买单").first().id)
        assertEquals("luggage", TravelExpressionSearch.search("存包").first().id)
        assertEquals("directions", TravelExpressionSearch.search("问路").first().id)
        assertEquals("bill", TravelExpressionSearch.search("我想买单").first().id)
    }

    @Test fun `punctuation and whitespace do not affect matching`() {
        assertEquals("price", TravelExpressionSearch.search(" 多少钱？ ").first().id)
    }

    @Test fun `Japanese and kana can be searched`() {
        assertEquals("exit", TravelExpressionSearch.search("出口").first().id)
        assertEquals("hospital", TravelExpressionSearch.search("びょういん").first().id)
    }

    @Test fun `blank and unknown queries return no result`() {
        assertTrue(TravelExpressionSearch.search(" ").isEmpty())
        assertTrue(TravelExpressionSearch.search("潜水艇维修").isEmpty())
    }

    @Test fun `reviewed corpus covers every MVP scene`() {
        val scenes = TravelExpressionSearch.reviewedExpressions.map { it.scene }.toSet()
        assertTrue(setOf("交通", "住宿", "餐饮", "购物", "观光", "应急").all { it in scenes })
        assertTrue(TravelExpressionSearch.reviewedExpressions.size >= 50)
    }
}

package com.gojapan.feature.review.ui

import com.gojapan.feature.search.domain.TravelExpressionSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewOptionsTest {
    @Test fun `question has four unique options including answer`() {
        val expression = TravelExpressionSearch.reviewedExpressions.first()
        val options = reviewOptions(expression)
        assertEquals(4, options.size)
        assertEquals(4, options.distinct().size)
        assertTrue(expression.chinese in options)
    }

    @Test fun `option order is stable for same expression`() {
        val expression = TravelExpressionSearch.reviewedExpressions.last()
        assertEquals(reviewOptions(expression), reviewOptions(expression))
    }
}

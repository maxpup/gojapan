package com.gojapan.feature.trip.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideWebImportTest {
    @Test fun parsesWebViewResultAndFiltersNonHttpsImages() {
        val result = "\"{\\\"title\\\":\\\"东京攻略\\\",\\\"text\\\":\\\"  浅草寺  \\\",\\\"images\\\":[\\\"https://example.com/a.jpg\\\",\\\"data:image/png,abc\\\"]}\""
        val page = parseExtractedPage(result, "https://www.xiaohongshu.com/example")
        assertEquals("东京攻略", page.title)
        assertEquals("浅草寺", page.text)
        assertEquals(listOf("https://example.com/a.jpg"), page.images)
    }

    @Test fun validatesGuidePreview() {
        val preview = validateGuidePreview("""{"schemaVersion":1,"title":"东京","sections":[]}""")
        assertTrue(preview.contains("\"sections\""))
    }
}

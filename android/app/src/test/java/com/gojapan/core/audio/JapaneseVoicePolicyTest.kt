package com.gojapan.core.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseVoicePolicyTest {
    @Test fun acceptsInstalledOfflineJapaneseVoice() {
        assertTrue(isInstalledJapaneseVoice("ja", false, null))
        assertTrue(isInstalledJapaneseVoice("ja", false, setOf("embeddedTts")))
    }

    @Test fun neverUsesChineseOrDefaultEnglishVoice() {
        assertFalse(isInstalledJapaneseVoice("zh", false, emptySet()))
        assertFalse(isInstalledJapaneseVoice("en", false, emptySet()))
    }

    @Test fun rejectsUnavailableOrNetworkOnlyJapaneseVoice() {
        assertFalse(isInstalledJapaneseVoice("ja", true, emptySet()))
        assertFalse(isInstalledJapaneseVoice("ja", false, setOf("notInstalled")))
    }
}

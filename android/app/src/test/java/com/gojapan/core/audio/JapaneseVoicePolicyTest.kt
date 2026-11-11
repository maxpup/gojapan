package com.gojapan.core.audio

import org.junit.Assert.*
import org.junit.Test

class JapaneseVoicePolicyTest {
    private val offline = JapaneseVoiceInfo("ja-offline", "ja", false)
    private val online = JapaneseVoiceInfo("ja-online", "ja", true)
    private val chinese = JapaneseVoiceInfo("zh-default", "zh", false)

    private class Engine(
        val status: Int = 0,
        var active: JapaneseVoiceInfo? = null,
        val available: List<JapaneseVoiceInfo> = emptyList(),
        var language: String? = "ja",
        val refusesSelection: Boolean = false,
        val ignoresSelection: Boolean = false,
    ) : JapaneseVoiceBackend {
        val selections = mutableListOf<String>()
        override fun setJapaneseLanguage() = status
        override fun voices() = available
        override fun currentVoice() = active
        override fun currentLanguage() = language
        override fun selectVoice(name: String): Boolean {
            selections += name
            if (refusesSelection) return false
            if (!ignoresSelection) active = available.firstOrNull { it.name == name }
            return true
        }
    }

    @Test fun installedNetworkJapaneseIsUsableInsteadOfReportedMissing() {
        val result = configureJapaneseVoice(Engine(active = online, available = listOf(online)))
        assertTrue(result.ready)
        assertTrue(result.requiresNetwork)
    }

    @Test fun prefersInstalledOfflineVoiceWhenAvailable() {
        val engine = Engine(active = online, available = listOf(online, offline))
        val result = configureJapaneseVoice(engine)
        assertTrue(result.ready)
        assertFalse(result.requiresNetwork)
        assertEquals(offline, engine.active)
    }

    @Test fun currentJapaneseVoiceWorksWhenEnumerationIsEmpty() {
        val engine = Engine(active = offline)
        assertTrue(configureJapaneseVoice(engine).ready)
        assertTrue(engine.selections.isEmpty())
    }

    @Test fun legacyLanguageOnlyEngineAcceptsIsoThreeLetterJapanese() {
        val result = configureJapaneseVoice(Engine(status = 2, language = "jpn"))
        assertTrue(result.ready)
        assertTrue(result.languageOnly)
    }

    @Test fun legacyLanguageOnlyEngineAcceptsNormalJapanese() {
        assertTrue(configureJapaneseVoice(Engine(language = "ja")).ready)
    }

    @Test fun successfulSetLanguageNeverAuthorizesChineseOrEnglishFallback() {
        listOf("zh", "zho", "en", "eng").forEach {
            assertFalse(configureJapaneseVoice(Engine(language = it)).ready)
        }
        assertFalse(configureJapaneseVoice(Engine(active = chinese, language = "ja")).ready)
    }

    @Test fun selectsConfirmedJapaneseVoiceEvenIfDefaultLanguageSelectionFails() {
        val engine = Engine(status = -2, active = chinese, available = listOf(offline))
        assertTrue(configureJapaneseVoice(engine).ready)
        assertEquals(offline, engine.active)
    }

    @Test fun engineIgnoringJapaneseVoiceSelectionCannotUseChinese() {
        val engine = Engine(active = chinese, available = listOf(offline), ignoresSelection = true)
        assertFalse(configureJapaneseVoice(engine).ready)
    }

    @Test fun unavailableVoiceDoesNotEnablePlayback() {
        val missing = offline.copy(notInstalled = true)
        val result = configureJapaneseVoice(Engine(status = -1, active = missing, available = listOf(missing)))
        assertFalse(result.ready)
        assertEquals(JapaneseVoiceFailure.MISSING_DATA, result.failure)
    }

    @Test fun unsupportedEngineReportsDifferentFailureFromMissingData() {
        val result = configureJapaneseVoice(Engine(status = -2, language = "zh"))
        assertFalse(result.ready)
        assertEquals(JapaneseVoiceFailure.UNSUPPORTED, result.failure)
    }

    @Test fun failedOfflineAttemptCannotReuseStaleJapaneseVoiceMetadata() {
        var active = online
        val engine = object : JapaneseVoiceBackend {
            override fun setJapaneseLanguage() = 0
            override fun voices() = listOf(offline, online)
            override fun currentVoice() = active
            override fun currentLanguage() = "ja"
            override fun selectVoice(name: String): Boolean {
                if (name == offline.name) { active = chinese; return true }
                return false
            }
        }
        assertFalse(configureJapaneseVoice(engine).ready)
    }

    @Test fun failedVoiceSelectionNeverEnablesAnUnconfirmedEngine() {
        val engine = Engine(active = chinese, available = listOf(offline), refusesSelection = true)
        assertFalse(configureJapaneseVoice(engine).ready)
    }
}

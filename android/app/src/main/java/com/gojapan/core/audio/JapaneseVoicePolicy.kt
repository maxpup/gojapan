package com.gojapan.core.audio

/** Some OEM engines report ISO-639-2 language codes from getLanguage(). */
internal fun isJapaneseLanguage(language: String?): Boolean = language?.lowercase() in setOf("ja", "jpn")

internal data class JapaneseVoiceInfo(
    val name: String,
    val language: String,
    val requiresNetwork: Boolean,
    val notInstalled: Boolean = false,
)

/** Platform-free boundary so vendor voice reporting can be exercised in regression tests. */
internal interface JapaneseVoiceBackend {
    fun setJapaneseLanguage(): Int
    fun voices(): List<JapaneseVoiceInfo>
    fun currentVoice(): JapaneseVoiceInfo?
    fun currentLanguage(): String?
    fun selectVoice(name: String): Boolean
}

internal enum class JapaneseVoiceFailure { MISSING_DATA, UNSUPPORTED, UNCONFIRMED }
internal data class JapaneseVoiceResolution(
    val ready: Boolean,
    val requiresNetwork: Boolean = false,
    val languageOnly: Boolean = false,
    val failure: JapaneseVoiceFailure? = null,
)

/** Prefer offline Japanese voices, but do not confuse network-only/legacy engines with missing data. */
internal fun configureJapaneseVoice(backend: JapaneseVoiceBackend): JapaneseVoiceResolution {
    val languageResult = backend.setJapaneseLanguage()
    val current = backend.currentVoice()
    val candidates = (backend.voices() + listOfNotNull(current))
        .distinctBy { it.name }
        .filter { isJapaneseLanguage(it.language) && !it.notInstalled }
        .sortedWith(compareBy<JapaneseVoiceInfo> { it.requiresNetwork }.thenBy { it.name != current?.name }.thenBy { it.name })
    for (candidate in candidates) {
        if (candidate == backend.currentVoice() && languageResult >= 0) {
            return JapaneseVoiceResolution(true, candidate.requiresNetwork)
        }
        if (backend.selectVoice(candidate.name)) {
            val actual = backend.currentVoice()
            if (actual != null && isJapaneseLanguage(actual.language) && !actual.notInstalled) {
                return JapaneseVoiceResolution(true, actual.requiresNetwork)
            }
            if (actual == null && isJapaneseLanguage(backend.currentLanguage())) {
                return JapaneseVoiceResolution(true, candidate.requiresNetwork)
            }
        }
    }
    // Older engines may implement language selection without getVoices/getVoice.
    // A contradictory Chinese/English current voice must never be accepted here.
    if (languageResult >= 0 && current == null && backend.currentVoice() == null && isJapaneseLanguage(backend.currentLanguage())) {
        return JapaneseVoiceResolution(true, languageOnly = true)
    }
    return JapaneseVoiceResolution(false, failure = when {
        languageResult == -1 || current?.notInstalled == true -> JapaneseVoiceFailure.MISSING_DATA
        languageResult == -2 -> JapaneseVoiceFailure.UNSUPPORTED
        else -> JapaneseVoiceFailure.UNCONFIRMED
    })
}

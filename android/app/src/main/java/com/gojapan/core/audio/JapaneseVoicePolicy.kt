package com.gojapan.core.audio

/** Do not substitute the device's default language or an uninstalled/network-only voice. */
internal fun isInstalledJapaneseVoice(language: String, requiresNetwork: Boolean, features: Set<String>?): Boolean =
    language == "ja" && !requiresNetwork && "notInstalled" !in features.orEmpty()

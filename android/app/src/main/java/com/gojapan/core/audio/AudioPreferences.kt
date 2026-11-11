package com.gojapan.core.audio

import android.content.Context
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** App-wide mute, persisted independently of individual exercise sound settings. */
object AudioPreferences {
    var muted by mutableStateOf(false)
        private set
    var ttsEnginePackage by mutableStateOf<String?>(null)
        private set
    fun selectTtsEngine(packageName: String?) {
        stopPlayback()
        ttsEnginePackage = packageName
        preferences?.edit()?.putString("tts_engine_package", packageName)?.apply()
    }
    var stopRequests by mutableIntStateOf(0)
        private set
    var playbackLabel by mutableStateOf("")
    fun stopPlayback() { ++stopRequests; playbackLabel = "播放已停止" }
    private var preferences: android.content.SharedPreferences? = null
    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences("audio_preferences", Context.MODE_PRIVATE)
        muted = preferences?.getBoolean("muted", false) ?: false
        ttsEnginePackage = preferences?.getString("tts_engine_package", null)
    }
    fun toggle() {
        muted = !muted
        if (muted) stopPlayback()
        preferences?.edit()?.putBoolean("muted", muted)?.apply()
    }
}

@Composable
fun GlobalAudioControl() {
    AudioPreferences.initialize(LocalContext.current)
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
    FilterChip(
        selected = AudioPreferences.muted,
        onClick = AudioPreferences::toggle,
        label = { Text(if (AudioPreferences.muted) "🔇 全局静音：开" else "♪ 全局静音：关") },
    )
    androidx.compose.material3.TextButton(onClick = AudioPreferences::stopPlayback) { Text("停止播放") }
    AppBuildInfo()
    }
    if (AudioPreferences.playbackLabel.isNotBlank()) Text(AudioPreferences.playbackLabel)
}

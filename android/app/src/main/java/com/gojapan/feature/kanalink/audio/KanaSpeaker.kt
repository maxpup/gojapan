package com.gojapan.feature.kanalink.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.gojapan.BuildConfig
import com.gojapan.feature.kanalink.domain.model.KanaItem

private const val TAG = "KanaSpeaker"

/** Plays the bundled human recording, with development TTS only as a failure fallback. */
@Composable
fun rememberKanaSpeaker(): (KanaItem) -> Unit {
    val context = LocalContext.current.applicationContext
    com.gojapan.core.audio.AudioPreferences.initialize(context)
    var playbackGeneration by remember { mutableIntStateOf(0) }
    val player = remember {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }
    }

    val tts = if (BuildConfig.ENABLE_TTS_FALLBACK) com.gojapan.core.audio.rememberJapaneseSpeaker() else null
    var showAudioError by remember { mutableStateOf(false) }
    if (showAudioError) {
        AlertDialog(
            onDismissRequest = { showAudioError = false },
            title = { Text("假名音频无法播放") },
            text = {
                Column {
                    if (tts != null) com.gojapan.core.audio.JapanesePlaybackStatus(tts)
                    else Text("假名音频损坏或缺失，请重新安装应用")
                }
            },
            confirmButton = { TextButton(onClick = { showAudioError = false }) { Text("关闭") } },
        )
    }
    androidx.compose.runtime.LaunchedEffect(com.gojapan.core.audio.AudioPreferences.stopRequests) {
        ++playbackGeneration; player.reset(); tts?.stop()
    }
    androidx.compose.runtime.LaunchedEffect(com.gojapan.core.audio.AudioPreferences.muted) {
        if (com.gojapan.core.audio.AudioPreferences.muted) { ++playbackGeneration; player.reset(); tts?.stop() }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { ++playbackGeneration; player.reset(); tts?.stop() }
        }
        lifecycle.addObserver(observer)
        onDispose { ++playbackGeneration; lifecycle.removeObserver(observer); player.release() }
    }

    return remember(player, tts) {
        speak@{ item ->
            if (com.gojapan.core.audio.AudioPreferences.muted) return@speak
            tts?.stop()
            val generation = ++playbackGeneration
            fun fallback(reason: String) {
                Log.w(TAG, "Bundled audio failed for ${item.audioKey}: $reason")
                if (tts != null && tts.ready) {
                    tts.speak(item.hiragana) { success -> if (!success) showAudioError = true }
                } else {
                    showAudioError = true
                }
            }

            val resourceId = context.resources.getIdentifier(item.audioKey, "raw", context.packageName)
            if (resourceId == 0) {
                player.reset()
                fallback("resource missing")
            } else {
                runCatching {
                    player.reset()
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    context.resources.openRawResourceFd(resourceId).use { descriptor ->
                        player.setDataSource(
                            descriptor.fileDescriptor,
                            descriptor.startOffset,
                            descriptor.length,
                        )
                    }
                    player.setOnPreparedListener {
                        if (generation == playbackGeneration) {
                            com.gojapan.core.audio.AudioPreferences.playbackLabel = "正在播放「${item.hiragana} / ${item.katakana}」"
                            it.start()
                        }
                    }
                    player.setOnCompletionListener {
                        if (generation == playbackGeneration) com.gojapan.core.audio.AudioPreferences.playbackLabel = "假名播放完成"
                    }
                    player.setOnErrorListener { _, what, extra ->
                        if (generation == playbackGeneration) fallback("MediaPlayer error $what/$extra")
                        true
                    }
                    player.prepareAsync()
                }.onFailure { fallback(it.message ?: it.javaClass.simpleName) }
            }
        }
    }
}

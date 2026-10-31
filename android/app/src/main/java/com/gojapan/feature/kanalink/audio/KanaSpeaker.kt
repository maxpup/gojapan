package com.gojapan.feature.kanalink.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.gojapan.BuildConfig
import com.gojapan.feature.kanalink.domain.model.KanaItem
import java.util.Locale

private const val TAG = "KanaSpeaker"

/** Plays the bundled human recording, with development TTS only as a failure fallback. */
@Composable
fun rememberKanaSpeaker(): (KanaItem) -> Unit {
    val context = LocalContext.current.applicationContext
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

    var ttsReady by remember { mutableStateOf(false) }
    val tts = if (BuildConfig.ENABLE_TTS_FALLBACK) {
        remember {
            TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
            }
        }
    } else {
        null
    }

    DisposableEffect(player, tts) {
        tts?.language = Locale.JAPANESE
        onDispose {
            player.release()
            tts?.stop()
            tts?.shutdown()
        }
    }

    return remember(player, tts, ttsReady) {
        { item ->
            val generation = ++playbackGeneration
            fun fallback(reason: String) {
                Log.w(TAG, "Bundled audio failed for ${item.audioKey}: $reason")
                if (ttsReady) {
                    tts?.speak(item.hiragana, TextToSpeech.QUEUE_FLUSH, null, "kana-current")
                }
            }

            val resourceId = context.resources.getIdentifier(item.audioKey, "raw", context.packageName)
            if (resourceId == 0) {
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
                        if (generation == playbackGeneration) it.start()
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

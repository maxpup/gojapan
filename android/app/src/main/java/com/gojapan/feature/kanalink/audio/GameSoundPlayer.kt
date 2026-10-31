package com.gojapan.feature.kanalink.audio

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

enum class GameFeedback { CORRECT, WRONG, COMPLETE }

@Composable
fun rememberGameSoundPlayer(enabled: Boolean): (GameFeedback) -> Unit {
    val tones = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }
    DisposableEffect(tones) { onDispose { tones.release() } }
    return remember(tones, enabled) {
        { feedback ->
            if (enabled) {
                val tone = when (feedback) {
                    GameFeedback.CORRECT -> ToneGenerator.TONE_PROP_ACK
                    GameFeedback.WRONG -> ToneGenerator.TONE_PROP_NACK
                    GameFeedback.COMPLETE -> ToneGenerator.TONE_PROP_BEEP2
                }
                tones.startTone(tone, if (feedback == GameFeedback.COMPLETE) 300 else 120)
            }
        }
    }
}

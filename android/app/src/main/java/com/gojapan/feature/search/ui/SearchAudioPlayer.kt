package com.gojapan.feature.search.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.gojapan.feature.search.domain.TravelExpression
import java.util.Locale

/** Plays Google Cloud TTS clips bundled with the app; system TTS covers missing clips. */
internal class SearchAudioPlayer(private val context: Context) {
    private var current: MediaPlayer? = null
    private var ttsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var released = false
    private val fallback: TextToSpeech

    init {
        fallback = TextToSpeech(context) { status ->
            mainHandler.post { configureSpeech(status) }
        }
    }

    private fun configureSpeech(status: Int) {
        if (!released && status == TextToSpeech.SUCCESS) {
            ttsReady = fallback.setLanguage(Locale.JAPAN) >= TextToSpeech.LANG_AVAILABLE
        }
    }

    fun playExpression(expression: TravelExpression, speed: Float = 1f): Boolean =
        play("${expression.id}.mp3", expression.japanese, speed)

    fun playKana(kana: String): Boolean =
        play("kana_${Character.codePointAt(kana, 0).toString(16)}.mp3", kana)

    private fun play(filename: String, spokenText: String, speed: Float = 1f): Boolean {
        stop()
        val descriptor = runCatching { context.assets.openFd("audio/expressions/$filename") }.getOrNull()
            ?: return speakFallback(spokenText, speed)
        return runCatching {
            val player = MediaPlayer()
            current = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            descriptor.use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            player.setOnPreparedListener { prepared ->
                if (current === prepared) {
                    if (speed != 1f) prepared.playbackParams = prepared.playbackParams.setSpeed(speed)
                    prepared.start()
                }
            }
            player.setOnCompletionListener {
                if (current === it) current = null
                it.release()
            }
            player.setOnErrorListener { failed, _, _ ->
                if (current === failed) {
                    current = null
                    speakFallback(spokenText, speed)
                }
                failed.release()
                true
            }
            player.prepareAsync()
            true
        }.getOrElse {
            current?.release()
            current = null
            speakFallback(spokenText, speed)
        }
    }

    fun playWord(word: String): Boolean {
        stop()
        return speakFallback(word, 1f)
    }

    private fun speakFallback(text: String, speed: Float): Boolean =
        ttsReady && fallback.setSpeechRate(speed) == TextToSpeech.SUCCESS &&
            fallback.speak(text, TextToSpeech.QUEUE_FLUSH, null, text) == TextToSpeech.SUCCESS

    fun stop() {
        current?.release()
        current = null
        fallback.stop()
    }

    fun release() {
        released = true
        stop()
        fallback.shutdown()
    }
}

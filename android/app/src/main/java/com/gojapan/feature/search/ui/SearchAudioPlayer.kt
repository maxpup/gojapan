package com.gojapan.feature.search.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.gojapan.feature.search.domain.TravelExpression
import java.util.Locale

/** Plays Google Cloud TTS clips bundled with the app; system TTS covers missing clips. */
internal class SearchAudioPlayer(private val context: Context) {
    private var current: MediaPlayer? = null
    private var ttsReady = false
    private lateinit var fallback: TextToSpeech

    init {
        fallback = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS && this::fallback.isInitialized) {
                ttsReady = fallback.setLanguage(Locale.JAPAN) >= TextToSpeech.LANG_AVAILABLE
            }
        }
    }

    fun playExpression(expression: TravelExpression): Boolean =
        play("${expression.id}.mp3", expression.japanese)

    fun playKana(kana: String): Boolean =
        play("kana_${Character.codePointAt(kana, 0).toString(16)}.mp3", kana)

    private fun play(filename: String, spokenText: String): Boolean {
        current?.release()
        current = null
        fallback.stop()
        val descriptor = runCatching { context.assets.openFd("audio/expressions/$filename") }.getOrNull()
            ?: return speakFallback(spokenText)
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
            player.setOnPreparedListener { if (current === it) it.start() }
            player.setOnCompletionListener {
                if (current === it) current = null
                it.release()
            }
            player.setOnErrorListener { failed, _, _ ->
                if (current === failed) {
                    current = null
                    speakFallback(spokenText)
                }
                failed.release()
                true
            }
            player.prepareAsync()
            true
        }.getOrElse {
            current?.release()
            current = null
            speakFallback(spokenText)
        }
    }

    fun playWord(word: String): Boolean {
        current?.release()
        current = null
        return speakFallback(word)
    }

    private fun speakFallback(text: String): Boolean =
        ttsReady && fallback.speak(text, TextToSpeech.QUEUE_FLUSH, null, text) == TextToSpeech.SUCCESS

    fun release() {
        current?.release()
        current = null
        fallback.stop()
        fallback.shutdown()
    }
}

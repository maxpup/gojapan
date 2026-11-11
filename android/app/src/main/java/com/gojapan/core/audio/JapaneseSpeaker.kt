package com.gojapan.core.audio

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import java.util.Locale

/** Owns one screen's speech engine. Only a confirmed, installed Japanese voice is used. */
class JapaneseSpeaker(private val context: Context) {
    var status by mutableStateOf("正在初始化日语语音…")
        private set
    var ready by mutableStateOf(false)
        private set
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var disposed = false
    private var initializing = false
    private var initialization = 0
    private var generation = 0
    private var playbackText = ""
    private var completion: ((Boolean) -> Unit)? = null

    init { AudioPreferences.initialize(context); retry() }

    fun retry() {
        if (disposed) return
        stop()
        engine?.shutdown()
        ready = false
        status = "正在初始化日语语音…"
        initializing = true
        val attempt = ++initialization
        engine = TextToSpeech(context) { result -> main.post {
            if (!disposed && attempt == initialization) {
                initializing = false
                runCatching { initialize(result) }.onFailure {
                    ready = false
                    status = "日语语音初始化失败，请检查语音引擎后重试"
                }
            }
        } }
        main.postDelayed({
            if (!disposed && attempt == initialization && initializing) {
                ++initialization
                initializing = false
                status = "日语语音初始化失败或超时，请安装日语语音包后重试"
            }
        }, 15_000)
    }

    private fun initialize(result: Int) {
        val tts = engine ?: return
        if (result != TextToSpeech.SUCCESS) {
            status = "语音引擎初始化失败，请安装语音引擎后重试"
            return
        }
        val language = tts.setLanguage(Locale.JAPAN)
        val voice = tts.voices?.firstOrNull {
            isInstalledJapaneseVoice(it.locale.language, it.isNetworkConnectionRequired, it.features)
        }
        if (language < TextToSpeech.LANG_AVAILABLE || voice == null ||
            tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            status = "缺少可离线使用的日语语音，请安装日语语音包后重试"
            return
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post {
                if (!disposed && id == generation.toString()) status = "正在播放「$playbackText」…"
            } }
            override fun onDone(id: String?) = finish(id, true)
            @Deprecated("Android callback")
            override fun onError(id: String?) = finish(id, false)
            override fun onError(id: String?, code: Int) = finish(id, false)
        })
        ready = true
        status = "日语语音已就绪"
    }

    private fun finish(id: String?, success: Boolean) { main.post {
        if (!disposed && id == generation.toString() && completion != null) {
            status = if (success) "播放完成" else "日语播放失败，请检查语音包后重试"
            val callback = completion
            completion = null
            callback?.invoke(success)
        }
    } }

    fun speak(text: String, onComplete: (Boolean) -> Unit = {}) {
        if (!ready || disposed || AudioPreferences.muted) return
        stop()
        playbackText = text
        completion = onComplete
        status = "正在准备播放「$text」…"
        val id = generation.toString()
        val result = runCatching {
            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }.getOrNull()
        if (result != TextToSpeech.SUCCESS) finish(id, false)
        main.postDelayed({
            if (!disposed && id == generation.toString() && completion != null) {
                engine?.stop()
                finish(id, false)
            }
        }, 60_000)
    }

    fun stop() {
        ++generation
        engine?.stop()
        completion = null
        if (ready) status = "日语语音已就绪"
    }

    fun installVoice() {
        runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
            status = "无法打开语音安装器，请在系统设置中安装语音引擎和日语语音包"
        }
    }

    fun close() {
        stop()
        disposed = true
        ++initialization
        engine?.shutdown()
        engine = null
    }
}

@Composable
fun rememberJapaneseSpeaker(): JapaneseSpeaker {
    val context = LocalContext.current.applicationContext
    val speaker = remember(context) { JapaneseSpeaker(context) }
    LaunchedEffect(AudioPreferences.stopRequests) { speaker.stop() }
    LaunchedEffect(AudioPreferences.muted) { if (AudioPreferences.muted) speaker.stop() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(speaker, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) speaker.stop()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); speaker.close() }
    }
    return speaker
}

@Composable
fun JapanesePlaybackStatus(speaker: JapaneseSpeaker) {
    Text(if (AudioPreferences.muted) "全局静音中，关闭静音后可播放" else speaker.status)
    if (!speaker.ready) {
        Button(onClick = speaker::installVoice) { Text("安装日语语音") }
        Button(onClick = speaker::retry) { Text("重试语音初始化") }
    } else {
        Button(onClick = speaker::stop) { Text("停止播放") }
    }
}

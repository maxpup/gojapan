package com.gojapan.core.audio

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

internal data class SpeechEngineChoice(val packageName: String, val label: String)

/** Owns one screen's engine; confirmed Japanese is required, offline voice enumeration is not. */
class JapaneseSpeaker(private val context: Context) {
    var status by mutableStateOf("正在初始化日语语音…")
        private set
    var ready by mutableStateOf(false)
        private set
    var engineLabel by mutableStateOf("系统默认")
        private set
    internal var engines by mutableStateOf<List<SpeechEngineChoice>>(emptyList())
        private set
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var disposed = false
    private var initializing = false
    private var initialization = 0
    private var generation = 0
    private var playbackText = ""
    private var completion: ((Boolean) -> Unit)? = null
    private var configuredPreference: String? = null
    private var enginePackage: String? = null
    private var recheckOnResume = false
    private var readinessMessage = "日语语音已就绪"

    init { AudioPreferences.initialize(context); retry() }

    fun retry() {
        if (disposed) return
        stop()
        engine?.shutdown()
        ready = false
        status = "正在初始化日语语音…"
        initializing = true
        configuredPreference = AudioPreferences.ttsEnginePackage
        enginePackage = configuredPreference
        val attempt = ++initialization
        runCatching {
            engine = TextToSpeech(context, { result -> main.post {
                if (!disposed && attempt == initialization) {
                    initializing = false
                    runCatching { initialize(result) }.onFailure {
                        ready = false
                        status = "语音引擎连接失败，请重试或切换引擎"
                    }
                }
            } }, configuredPreference)
        }.onFailure {
            initializing = false
            status = "语音引擎无法启动，请打开系统语音设置检查"
        }
        main.postDelayed({
            if (!disposed && attempt == initialization && initializing) {
                ++initialization
                initializing = false
                status = "语音引擎初始化超时，请重试或切换引擎"
            }
        }, 15_000)
    }

    private fun initialize(result: Int) {
        val tts = engine ?: return
        engines = runCatching { tts.engines.map { SpeechEngineChoice(it.name, it.label) } }.getOrDefault(emptyList())
        enginePackage = configuredPreference ?: tts.defaultEngine
        engineLabel = engines.firstOrNull { it.packageName == enginePackage }?.label ?: enginePackage ?: "未发现语音引擎"
        if (configuredPreference != null && engines.isNotEmpty() && engines.none { it.packageName == configuredPreference }) {
            status = "所选语音引擎已卸载，请切换引擎"
            return
        }
        if (result != TextToSpeech.SUCCESS) {
            status = "语音引擎初始化失败，请重试、切换引擎或打开系统语音设置"
            return
        }
        val platformVoices = runCatching { tts.voices.orEmpty().toList() }.getOrDefault(emptyList())
        fun Voice.info() = JapaneseVoiceInfo(name, locale.language, isNetworkConnectionRequired, "notInstalled" in features.orEmpty())
        val resolution = configureJapaneseVoice(object : JapaneseVoiceBackend {
            override fun setJapaneseLanguage(): Int {
                val first = tts.setLanguage(Locale.JAPAN)
                return if (first >= 0) first else tts.setLanguage(Locale.JAPANESE)
            }
            override fun voices() = platformVoices.map { it.info() }
            override fun currentVoice() = runCatching { tts.voice?.info() }.getOrNull()
            @Suppress("DEPRECATION")
            override fun currentLanguage() = runCatching { tts.language?.language }.getOrNull()
            override fun selectVoice(name: String): Boolean = platformVoices.firstOrNull { it.name == name }
                ?.let { runCatching { tts.setVoice(it) == TextToSpeech.SUCCESS }.getOrDefault(false) } ?: false
        })
        if (!resolution.ready) {
            status = when (resolution.failure) {
                JapaneseVoiceFailure.MISSING_DATA -> "当前引擎缺少日语数据，请为此引擎安装日语包，或切换到已安装日语的引擎"
                JapaneseVoiceFailure.UNSUPPORTED -> "当前引擎不支持日语；其他引擎中安装的日语包不能在此引擎使用，请切换引擎"
                else -> "当前引擎未能确认使用日语，请切换引擎或在系统语音设置中检查"
            }
            return
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post {
                if (!disposed && id == generation.toString()) status = "正在播放「$playbackText」…"
            } }
            override fun onDone(id: String?) = finish(id, true)
            @Deprecated("Android callback")
            override fun onError(id: String?) = finish(id, false)
            override fun onError(id: String?, code: Int) = finish(id, false, when (code) {
                TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT -> "日语语音连接失败，请检查网络，或在当前引擎下载离线日语包后重试"
                TextToSpeech.ERROR_NOT_INSTALLED_YET -> "当前日语语音尚未下载完成，请打开语音安装页面检查后重试"
                TextToSpeech.ERROR_OUTPUT -> "音频输出失败，请检查媒体音量及输出设备后重试"
                else -> "日语合成失败（$code），请重试或切换语音引擎"
            })
        })
        readinessMessage = when {
            resolution.requiresNetwork -> "日语语音已就绪（联网语音，需要网络）"
            resolution.languageOnly -> "日语语音已就绪（引擎已确认日语，联网需求由引擎决定）"
            else -> "日语语音已就绪"
        }
        ready = true
        status = readinessMessage
    }

    private fun finish(id: String?, success: Boolean, reason: String = "日语播放失败，请重试或切换语音引擎") { main.post {
        if (!disposed && id == generation.toString() && completion != null) {
            status = if (success) "播放完成" else reason
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
        val result = runCatching { engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) }.getOrNull()
        if (result != TextToSpeech.SUCCESS) finish(id, false)
        main.postDelayed({
            if (!disposed && id == generation.toString() && completion != null) {
                engine?.stop()
                finish(id, false, "播放超时，请检查网络或重试语音初始化")
            }
        }, 60_000)
    }

    fun stop() {
        ++generation
        runCatching { engine?.stop() }
        completion = null
        if (ready) status = readinessMessage
    }

    fun installVoice() {
        val target = enginePackage
        if (target == null) { openSystemSettings(); return }
        recheckOnResume = true
        runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .setPackage(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { openSystemSettings() }
    }

    fun openSystemSettings() {
        recheckOnResume = true
        runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { status = "无法打开系统设置，请手动进入系统的“文字转语音输出”选择引擎" }
    }

    internal fun synchronizeEngine() { if (configuredPreference != AudioPreferences.ttsEnginePackage) retry() }
    internal fun onResume() {
        if (recheckOnResume) { recheckOnResume = false; retry() }
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
    LaunchedEffect(AudioPreferences.ttsEnginePackage) { speaker.synchronizeEngine() }
    LaunchedEffect(AudioPreferences.stopRequests) { speaker.stop() }
    LaunchedEffect(AudioPreferences.muted) { if (AudioPreferences.muted) speaker.stop() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(speaker, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> speaker.stop()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> speaker.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); speaker.close() }
    }
    return speaker
}

@Composable
fun JapanesePlaybackStatus(speaker: JapaneseSpeaker) {
    var settingsOpen by remember { mutableStateOf(false) }
    Column {
        Text(if (AudioPreferences.muted) "全局静音中，关闭静音后可播放" else speaker.status)
        TextButton(onClick = { settingsOpen = true }) { Text("语音引擎：${speaker.engineLabel} · 设置") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!speaker.ready) Button(onClick = speaker::installVoice) { Text("安装日语包") }
            OutlinedButton(onClick = speaker::retry) { Text("重试") }
            if (speaker.ready) TextButton(onClick = speaker::stop) { Text("停止播放") }
        }
    }
    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("日语语音设置") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("应用当前使用：${speaker.engineLabel}")
                    Text("日语包只对所属语音引擎生效。若已安装日语，请在此选择安装该语音包的引擎。")
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }) { Text("切换语音引擎") }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("跟随系统默认") }, onClick = {
                                AudioPreferences.selectTtsEngine(null); speaker.retry(); expanded = false
                            })
                            speaker.engines.forEach { choice ->
                                DropdownMenuItem(text = { Text(choice.label) }, onClick = {
                                    AudioPreferences.selectTtsEngine(choice.packageName); speaker.retry(); expanded = false
                                })
                            }
                        }
                    }
                    Text(speaker.status)
                    Button(onClick = speaker::installVoice) { Text("为当前引擎安装日语") }
                    OutlinedButton(onClick = speaker::retry) { Text("重新检测日语语音") }
                    TextButton(onClick = speaker::openSystemSettings) { Text("系统语音设置") }
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("关闭") } },
        )
    }
}

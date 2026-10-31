package com.gojapan.feature.kanalink.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.gojapan.feature.kanalink.data.preferences.KanaPreferences
import com.gojapan.feature.kanalink.data.preferences.KanaSettings
import com.gojapan.feature.kanalink.data.repository.KanaExport
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.kanalink.notification.ReviewNotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: KanaPreferences,
    private val repository: LearningRepository,
    private val clock: Clock,
    private val notificationScheduler: ReviewNotificationScheduler,
) : ViewModel() {
    val state = preferences.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KanaSettings())
    private val exportsChannel = Channel<String>(Channel.BUFFERED)
    val exports = exportsChannel.receiveAsFlow()

    fun voice(enabled: Boolean) = update(state.value.copy(voiceEnabled = enabled))
    fun sound(enabled: Boolean) = update(state.value.copy(soundEffectEnabled = enabled))
    fun notifications(enabled: Boolean) {
        notificationScheduler.setEnabled(enabled)
        update(state.value.copy(reviewNotificationEnabled = enabled))
    }
    fun reduceMotion(enabled: Boolean) = update(state.value.copy(reduceMotionEnabled = enabled))
    fun export() { viewModelScope.launch { exportsChannel.send(repository.exportData().toJson(clock.millis())) } }
    fun clearHistory() { viewModelScope.launch { repository.clearHistory() } }
    private fun update(settings: KanaSettings) { viewModelScope.launch { preferences.update(settings) } }
}

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExport
        if (uri != null && json != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
        }
        pendingExport = null
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.notifications(granted)
    }
    val changeNotifications: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.notifications(enabled)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.exports.collect { json ->
            pendingExport = json
            createDocument.launch("gojapan-kana-link.json")
        }
    }
    SettingsScreen(
        state, viewModel::voice, viewModel::sound, changeNotifications, viewModel::reduceMotion,
        viewModel::export, viewModel::clearHistory, onBack,
    )
}

@Composable
fun SettingsScreen(
    state: KanaSettings,
    onVoice: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onReduceMotion: (Boolean) -> Unit = {},
    onExport: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("设置")
        SettingRow("假名语音", state.voiceEnabled, onVoice)
        SettingRow("游戏音效", state.soundEffectEnabled, onSound)
        SettingRow("复习通知", state.reviewNotificationEnabled, onNotifications)
        SettingRow("减少动态效果", state.reduceMotionEnabled, onReduceMotion)
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("导出学习记录") }
        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("清除学习记录") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("返回") }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除全部学习记录？") },
            text = { Text("进度和完整轮次会永久删除；练习模式与开关设置会保留。") },
            confirmButton = {
                Button(onClick = { onClearHistory(); confirmClear = false }) { Text("确认清除") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable private fun SettingRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) { Text(label); Switch(checked = checked, onCheckedChange = onChecked) }
}

private fun KanaExport.toJson(generatedAt: Long): String = buildJsonObject {
    put("format", "gojapan-kana-link-export")
    put("version", 1)
    put("generatedAt", generatedAt)
    put("progress", buildJsonArray {
        progress.forEach { row -> add(buildJsonObject {
            put("kanaId", row.kanaId); put("seenCount", row.seenCount); put("firstTryCorrectCount", row.firstTryCorrectCount)
            put("wrongCount", row.wrongCount); put("hintCount", row.hintCount); put("level", row.level)
            row.lastResult?.let { put("lastResult", it) }; row.lastReviewedAt?.let { put("lastReviewedAt", it) }
            row.nextReviewAt?.let { put("nextReviewAt", it) }; put("manualMastered", row.manualMastered)
            row.manualMasteredAt?.let { put("manualMasteredAt", it) }
        }) }
    })
    put("sessions", buildJsonArray {
        sessions.forEach { row -> add(buildJsonObject {
            put("id", row.id); put("completedAt", row.completedAt); put("localDate", row.localDate); put("timezoneId", row.timezoneId)
            put("durationSeconds", row.durationSeconds); put("mode", row.mode); put("source", row.source); put("bestCombo", row.bestCombo)
            put("wrongAttempts", row.wrongAttempts); put("hintUses", row.hintUses)
        }) }
    })
    put("sessionItems", buildJsonArray {
        sessionItems.forEach { row -> add(buildJsonObject {
            put("sessionId", row.sessionId); put("kanaId", row.kanaId); put("wrongCount", row.wrongCount)
            put("firstTryCorrect", row.firstTryCorrect); put("hintUsed", row.hintUsed); row.responseMs?.let { put("responseMs", it) }
            put("wasNew", row.wasNew); put("levelBefore", row.levelBefore); put("levelAfter", row.levelAfter)
            put("nextReviewAt", row.nextReviewAt); put("word", row.wordSnapshot); put("meaning", row.meaningSnapshot)
        }) }
    })
}.toString()

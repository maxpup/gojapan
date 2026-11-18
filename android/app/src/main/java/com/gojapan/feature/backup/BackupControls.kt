package com.gojapan.feature.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import java.time.LocalDate
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class BackupModel @Inject constructor(private val backup: LocalBackup) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var preview by mutableStateOf<LocalBackup.Preview?>(null)
        private set

    private fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = ""
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "操作失败：" + (e.localizedMessage ?: "文件无法读取或写入") }
            finally { busy = false }
        }
    }
    fun export(uri: android.net.Uri) = run {
        withContext(Dispatchers.IO) { backup.export(uri) }
        message = "已导出全部数据"
    }
    fun inspect(uri: android.net.Uri) = run {
        discard()
        preview = withContext(Dispatchers.IO) { backup.inspect(uri) }
    }
    fun discard() { preview?.directory?.deleteRecursively(); preview = null }
    fun confirm(close: () -> Unit) = run {
        backup.confirm(requireNotNull(preview))
        preview = null
        close()
    }
}

@Composable
fun BackupControls(model: BackupModel = hiltViewModel()) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(model::export) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::inspect) }
    TextButton(onClick = { open = true }) { Text("数据备份") }
    if (open) AlertDialog(onDismissRequest = { if (!model.busy) { model.discard(); open = false } },
        title = { Text("数据备份") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("旅行计划、攻略、语言学习和记账将一起备份。")
                Button(enabled = !model.busy, onClick = { export.launch("GoJapan-${LocalDate.now()}.zip") }) { Text("导出到文件") }
                OutlinedButton(enabled = !model.busy, onClick = { import.launch(arrayOf("*/*")) }) { Text("从文件恢复") }
                if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (model.message.isNotBlank()) Text(model.message)
            }
        }, confirmButton = { TextButton(enabled = !model.busy, onClick = { open = false }) { Text("关闭") } })
    model.preview?.let { data ->
        AlertDialog(onDismissRequest = model::discard, title = { Text("整份恢复备份？") },
            text = { Text("备份时间：${DateFormat.getDateTimeInstance().format(Date(data.created))}\n${data.plans} 个旅行计划 · ${data.expenses} 笔支出\n当前四个模块的数据将被覆盖。确认后应用会关闭，再次打开即可完成恢复。") },
            confirmButton = { Button(enabled = !model.busy, onClick = {
                model.confirm {
                    (context as? Activity)?.finishAndRemoveTask()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }) { Text("覆盖并关闭应用") } },
            dismissButton = { TextButton(onClick = model::discard) { Text("取消") } })
    }
}

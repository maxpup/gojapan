package com.gojapan.core.audio

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gojapan.BuildConfig
import java.security.MessageDigest

@Suppress("DEPRECATION")
private fun installedCertificate(context: Context): String = runCatching {
    val signatures = if (Build.VERSION.SDK_INT >= 28) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners.orEmpty()
    } else {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
    }
    signatures.joinToString("\n") { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    }.ifBlank { "无法读取证书" }
}.getOrDefault("无法读取证书")

@Composable
fun AppBuildInfo() {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    TextButton(onClick = { open = true }) { Text("v${BuildConfig.VERSION_NAME}") }
    if (open) {
        val fingerprint = remember(context) { installedCertificate(context) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("版本与安装签名") },
            text = {
                SelectionContainer {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GoJapan ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
                        Text("包名：${context.packageName}")
                        Text("构建：${if (BuildConfig.DEBUG) "Debug" else "Release"}")
                        Text("已安装应用的证书 SHA-256：")
                        Text(fingerprint)
                        Text("反馈安装冲突时，可复制以上版本及签名信息。")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } },
        )
    }
}

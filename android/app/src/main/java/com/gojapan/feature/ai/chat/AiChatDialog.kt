package com.gojapan.feature.ai.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.core.ai.AiChatAvailability

@Composable
fun AiChatDialogRoute(
    onDismiss: () -> Unit,
    viewModel: AiChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AiChatDialog(
        state = state,
        onInputChange = viewModel::updateInput,
        onSend = viewModel::send,
        onDismissError = viewModel::dismissError,
        onOpenApiKeyEditor = viewModel::openApiKeyEditor,
        onCloseApiKeyEditor = viewModel::closeApiKeyEditor,
        onApiKeyChange = viewModel::updateApiKey,
        onSaveApiKey = viewModel::saveApiKey,
        onClearApiKey = viewModel::clearApiKey,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatDialog(
    state: AiChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismissError: () -> Unit,
    onOpenApiKeyEditor: () -> Unit,
    onCloseApiKeyEditor: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onClearApiKey: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    LaunchedEffect(state.messages.size, state.isSending) {
        val extraItem = if (state.isSending) 1 else 0
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex + extraItem)
    }

    Dialog(
        onDismissRequest = { if (!state.isSending) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(min = 420.dp, max = screenHeight * 0.86f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("GoJapan AI 助手", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Debug Demo · AI 可能出错，请核实重要信息", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = onDismiss, enabled = !state.isSending) {
                        Text("×", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                HorizontalDivider()
                val unavailable = state.availability as? AiChatAvailability.Unavailable
                unavailable?.let {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = it.message,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onOpenApiKeyEditor) { Text("配置") }
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Spacer(Modifier.size(2.dp)) }
                    items(state.messages, key = ChatMessage::id) { message ->
                        ChatBubble(message)
                    }
                    if (state.isSending) {
                        item {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("AI 正在思考…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                state.errorMessage?.let { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onDismissError) { Text("知道了") }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(if (unavailable == null) "输入旅行问题" else "配置 API Key 后可用")
                        },
                        supportingText = { Text("${state.input.length}/${AiChatViewModel.MAX_INPUT_LENGTH}") },
                        maxLines = 4,
                        enabled = !state.isSending && unavailable == null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                    )
                    Button(
                        onClick = onSend,
                        enabled = state.input.isNotBlank() && !state.isSending && unavailable == null,
                    ) { Text("发送") }
                }
            }
        }
    }
    if (state.showApiKeyEditor) {
        AlertDialog(
            onDismissRequest = onCloseApiKeyEditor,
            title = { Text("配置 AI API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Key 会经 Android Keystore 加密后保存在本机。此直连方式仅用于 Debug Demo。")
                    OutlinedTextField(
                        value = state.apiKeyInput,
                        onValueChange = onApiKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("DeepSeek API Key") },
                        placeholder = { Text("sk-…") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = state.apiKeyError != null,
                        supportingText = { state.apiKeyError?.let { Text(it) } },
                    )
                }
            },
            confirmButton = {
                Button(onClick = onSaveApiKey, enabled = state.apiKeyInput.isNotBlank()) { Text("保存") }
            },
            dismissButton = {
                Row {
                    if (state.hasStoredApiKey) {
                        TextButton(onClick = onClearApiKey) { Text("清除 Key") }
                    }
                    TextButton(onClick = onCloseApiKeyEditor) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.author == ChatAuthor.USER
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = message.text,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

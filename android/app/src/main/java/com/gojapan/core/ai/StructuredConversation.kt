package com.gojapan.core.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private val displayJson = Json { prettyPrint = true }

fun formatAiText(text: String): String = runCatching {
    displayJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text.trim().removeSurrounding("```json", "```").trim()))
}.getOrDefault(text)

@Composable
fun StructuredConversation(
    initial: String, send: suspend (List<AiChatTurn>) -> String,
    invalidate: () -> Unit,
    accept: (String) -> Unit,
) {
    var turns by remember { mutableStateOf(emptyList<AiChatTurn>()) }
    var message by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column {
        turns.forEach { Text(formatAiText(it.content), style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(message, { message = it; invalidate() }, enabled = !busy, shape = RoundedCornerShape(18.dp),
            label = { Text(if (turns.isEmpty()) "你的要求" else "补充或修改要求") })
        Button(enabled = !busy && message.isNotBlank(), onClick = {
            val input = buildJsonObject { put("request", message.trim()) }
            val request = turns + AiChatTurn(AiChatRole.USER, input.toString())
            invalidate()
            busy = true
            error = ""
            scope.launch {
                try {
                    val reply = send(request)
                    turns = request + AiChatTurn(AiChatRole.ASSISTANT, reply)
                    message = ""
                    accept(reply)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "请求失败，请重试" }
                finally { busy = false }
            }
        }) { Text(if (busy) "处理中…" else "发送给 AI") }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

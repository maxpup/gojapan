package com.gojapan.feature.ai.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.core.ai.AiChatClient
import com.gojapan.core.ai.AiChatAvailability
import com.gojapan.core.ai.AiApiKeyStore
import com.gojapan.core.ai.AiChatRole
import com.gojapan.core.ai.AiChatTurn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChatAuthor { USER, ASSISTANT }

data class ChatMessage(
    val id: String,
    val author: ChatAuthor,
    val text: String,
)

data class AiChatUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            id = "welcome",
            author = ChatAuthor.ASSISTANT,
            text = "你好，我是 GoJapan AI 助手。可以问我日本行程、地点或旅行准备问题。",
        ),
    ),
    val input: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val availability: AiChatAvailability = AiChatAvailability.Available,
    val showApiKeyEditor: Boolean = false,
    val apiKeyInput: String = "",
    val apiKeyError: String? = null,
    val hasStoredApiKey: Boolean = false,
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val client: AiChatClient,
    private val apiKeyStore: AiApiKeyStore,
) : ViewModel() {
    private val _state = MutableStateFlow(
        AiChatUiState(
            availability = client.availability(),
            hasStoredApiKey = apiKeyStore.read() != null,
        ),
    )
    val state: StateFlow<AiChatUiState> = _state.asStateFlow()

    fun updateInput(value: String) {
        if (value.length <= MAX_INPUT_LENGTH) {
            _state.update { it.copy(input = value, errorMessage = null) }
        }
    }

    fun send() {
        if (_state.value.availability !is AiChatAvailability.Available) return
        val content = _state.value.input.trim()
        if (content.isEmpty() || _state.value.isSending) return

        val userMessage = ChatMessage(UUID.randomUUID().toString(), ChatAuthor.USER, content)
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                input = "",
                isSending = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            val turns = _state.value.messages
                .filterNot { it.id == "welcome" }
                .map { message ->
                    AiChatTurn(
                        role = if (message.author == ChatAuthor.USER) AiChatRole.USER else AiChatRole.ASSISTANT,
                        content = message.text,
                    )
                }
            runCatching { client.reply(turns) }
                .onSuccess { answer ->
                    _state.update {
                        it.copy(
                            messages = it.messages + ChatMessage(
                                id = UUID.randomUUID().toString(),
                                author = ChatAuthor.ASSISTANT,
                                text = answer,
                            ),
                            isSending = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSending = false,
                            errorMessage = error.message ?: "AI 请求失败，请重试",
                            input = content,
                        )
                    }
                }
        }
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    fun openApiKeyEditor() = _state.update {
        it.copy(showApiKeyEditor = true, apiKeyInput = "", apiKeyError = null)
    }

    fun closeApiKeyEditor() = _state.update {
        it.copy(showApiKeyEditor = false, apiKeyInput = "", apiKeyError = null)
    }

    fun updateApiKey(value: String) {
        if (value.length <= MAX_API_KEY_LENGTH) {
            _state.update { it.copy(apiKeyInput = value, apiKeyError = null) }
        }
    }

    fun saveApiKey() {
        val value = _state.value.apiKeyInput.trim()
        runCatching { apiKeyStore.save(value) }
            .onSuccess {
                _state.update {
                    it.copy(
                        availability = client.availability(),
                        showApiKeyEditor = false,
                        apiKeyInput = "",
                        apiKeyError = null,
                        hasStoredApiKey = true,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(apiKeyError = error.message ?: "API Key 保存失败") }
            }
    }

    fun clearApiKey() {
        apiKeyStore.clear()
        _state.update {
            it.copy(
                availability = client.availability(),
                showApiKeyEditor = false,
                apiKeyInput = "",
                apiKeyError = null,
                hasStoredApiKey = false,
            )
        }
    }

    companion object {
        const val MAX_INPUT_LENGTH = 2_000
        const val MAX_API_KEY_LENGTH = 512
    }
}

package com.gojapan.core.ai

data class AiChatTurn(
    val role: AiChatRole,
    val content: String,
)

enum class AiChatRole {
    USER,
    ASSISTANT,
}

sealed interface AiChatAvailability {
    data object Available : AiChatAvailability
    data class Unavailable(val message: String) : AiChatAvailability
}

interface AiChatClient {
    fun availability(): AiChatAvailability = AiChatAvailability.Available
    suspend fun reply(messages: List<AiChatTurn>): String
}

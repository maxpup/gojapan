package com.gojapan.core.ai

import com.gojapan.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class OpenAiCompatibleChatClient @Inject constructor(
    private val apiKeyStore: AiApiKeyStore,
) : AiChatClient {
    private val json = Json { ignoreUnknownKeys = true }

    override fun availability(): AiChatAvailability = when {
        !BuildConfig.DEBUG -> AiChatAvailability.Unavailable("AI 直连仅允许在 Debug 构建中使用")
        !apiKeyIsConfigured() -> AiChatAvailability.Unavailable(
            "尚未配置 AI API Key。点击“配置”后输入 Key，即可在本机启用 Demo 对话。",
        )
        else -> AiChatAvailability.Available
    }

    override suspend fun reply(messages: List<AiChatTurn>): String = withContext(Dispatchers.IO) {
        val availability = availability()
        check(availability is AiChatAvailability.Available) {
            (availability as AiChatAvailability.Unavailable).message
        }
        require(messages.any { it.role == AiChatRole.USER }) { "请输入问题" }

        val request = buildJsonObject {
            put("model", BuildConfig.DEEPSEEK_MODEL.ifBlank { DEFAULT_MODEL })
            put("stream", false)
            put("max_tokens", 2048)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                messages.takeLast(MAX_CONTEXT_TURNS).forEach { turn ->
                    add(buildJsonObject {
                        put("role", turn.role.apiValue)
                        put("content", turn.content)
                    })
                }
            })
        }

        val connection = (URL("${baseUrl()}/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${configuredApiKey()}")
        }

        try {
            connection.outputStream.bufferedWriter().use { it.write(request.toString()) }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw AiChatException(httpErrorMessage(status))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.parseToJsonElement(body).jsonObject["choices"]
                ?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull
                ?.trim()?.takeIf(String::isNotEmpty)
                ?: throw AiChatException("AI 未返回有效内容")
        } catch (error: AiChatException) {
            throw error
        } catch (error: Exception) {
            throw AiChatException("网络连接失败，请检查网络后重试", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun apiKeyIsConfigured(): Boolean = configuredApiKey() != null

    private fun configuredApiKey(): String? = apiKeyStore.read()
        ?: BuildConfig.DEEPSEEK_API_KEY.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }

    private fun baseUrl(): String = BuildConfig.DEEPSEEK_BASE_URL
        .trimEnd('/')
        .ifBlank { DEFAULT_BASE_URL }

    private fun httpErrorMessage(status: Int): String = when (status) {
        401, 403 -> "AI 服务认证失败，请检查 API Key"
        402 -> "AI 账户余额不足，请充值后重试"
        408 -> "AI 请求超时，请重试"
        429 -> "AI 请求过于频繁，请稍后重试"
        in 500..599 -> "AI 服务暂时不可用，请稍后重试"
        else -> "AI 请求失败（$status）"
    }

    private val AiChatRole.apiValue: String
        get() = when (this) {
            AiChatRole.USER -> "user"
            AiChatRole.ASSISTANT -> "assistant"
        }

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-flash"
        const val MAX_CONTEXT_TURNS = 12
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 90_000
        const val SYSTEM_PROMPT = """
            你是 GoJapan 应用内的日本旅行助手。使用简洁、实用的中文回答。
            不确定的信息要明确说明需要核实，不要编造实时营业时间、票价或交通状态。
        """
    }
}

class AiChatException(message: String, cause: Throwable? = null) : Exception(message, cause)

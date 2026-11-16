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
        !apiKeyIsConfigured() -> AiChatAvailability.Unavailable(
            "尚未配置 AI API Key。点击“配置”后输入 Key，即可使用。请求会发送至 AI 服务商，可能产生费用。",
        )
        else -> AiChatAvailability.Available
    }

    override suspend fun reply(messages: List<AiChatTurn>): String = complete(messages, SYSTEM_PROMPT, 2048)

    val modelName: String get() = BuildConfig.DEEPSEEK_MODEL.ifBlank { DEFAULT_MODEL }
    val providerName: String get() = URL(baseUrl()).host

    suspend fun complete(messages: List<AiChatTurn>, systemPrompt: String, maxTokens: Int): String =
        send(messages, systemPrompt, maxTokens, null, false)

    suspend fun completeGuide(messages: List<AiChatTurn>, systemPrompt: String, maxTokens: Int,
                              onResponse: (Int, String) -> Unit): String =
        send(messages, systemPrompt, maxTokens, onResponse, true)

    private suspend fun send(messages: List<AiChatTurn>, systemPrompt: String, maxTokens: Int,
                         onResponse: ((Int, String) -> Unit)?, guide: Boolean): String = withContext(Dispatchers.IO) {
        val apiKey = configuredApiKey() ?: throw AiChatException("未能读取 AI API Key，请解锁设备重试或打开 AI 设置检查配置")
        require(messages.any { it.role == AiChatRole.USER }) { "请输入问题" }

        val request = buildJsonObject {
            put("model", BuildConfig.DEEPSEEK_MODEL.ifBlank { DEFAULT_MODEL })
            if (guide && providerName == "api.deepseek.com") {
                put("thinking", buildJsonObject { put("type", "disabled") })
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
            put("stream", false)
            put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                (if (guide) messages else messages.takeLast(MAX_CONTEXT_TURNS)).forEach { turn ->
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
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            connection.outputStream.bufferedWriter().use { it.write(request.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (onResponse != null) withContext(Dispatchers.Main.immediate) {
                onResponse(status, body.replace(apiKey, "[已隐藏密钥]"))
            }
            if (status !in 200..299) throw AiChatException(httpErrorMessage(status))
            if (body.isBlank()) throw AiChatException("AI 服务返回了空响应体")
            val choice = try {
                json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            } catch (error: IllegalArgumentException) {
                throw AiChatException("AI 服务响应格式无法解析", error)
            }
            if (guide && choice?.get("finish_reason")?.toString() == "\"length\"") {
                throw AiChatException("服务因长度限制截断了攻略；本次输出预算为 ${maxTokens} token，请查看原始响应中的 usage")
            }
            val message = choice?.get("message") as? kotlinx.serialization.json.JsonObject
            val content = message?.get("content") as? kotlinx.serialization.json.JsonPrimitive
            content?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) ?: throw AiChatException(
                if (choice?.get("finish_reason")?.toString() == "\"length\"")
                    "AI 输出达到长度上限，未返回回复正文"
                else "AI 响应中没有可读取的回复正文（choices[0].message.content）"
            )
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: AiChatException) {
            throw error
        } catch (error: java.net.SocketTimeoutException) {
            throw AiChatException("AI 请求超时，请重试", error)
        } catch (error: Exception) {
            throw AiChatException("网络连接失败，请检查网络后重试", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun apiKeyIsConfigured(): Boolean = configuredApiKey() != null

    private fun configuredApiKey(): String? = apiKeyStore.read()
        ?: if (BuildConfig.DEBUG) BuildConfig.DEEPSEEK_API_KEY.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") } else null

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

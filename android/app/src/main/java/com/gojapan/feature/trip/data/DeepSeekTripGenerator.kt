package com.gojapan.feature.trip.data

import com.gojapan.BuildConfig
import com.gojapan.feature.trip.model.TripPlan
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
class DeepSeekTripGenerator @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(sourceText: String, sourceName: String, sourceUrl: String): TripPlan =
        withContext(Dispatchers.IO) {
            check(BuildConfig.DEEPSEEK_API_KEY.isNotBlank() && !BuildConfig.DEEPSEEK_API_KEY.startsWith("YOUR_")) {
                "请先在 android/local.properties 配置 DEEPSEEK_API_KEY"
            }
            require(sourceText.isNotBlank()) { "攻略内容不能为空" }

            val request = buildJsonObject {
                put("model", BuildConfig.DEEPSEEK_MODEL.ifBlank { "deepseek-flash" })
                put("stream", false)
                put("max_tokens", 4096)
                put("response_format", buildJsonObject { put("type", "json_object") })
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", "请把下面攻略整理成 json 行程。不要虚构坐标；不能确定时 latitude/longitude 写 null。\n\n$sourceText")
                    })
                })
            }
            val baseUrl = BuildConfig.DEEPSEEK_BASE_URL.trimEnd('/').ifBlank { "https://api.deepseek.com" }
            val connection = (URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
            }
            try {
                connection.outputStream.bufferedWriter().use { it.write(request.toString()) }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                check(status in 200..299) {
                    if (status == 402) "DeepSeek 账户余额不足，请充值后重试"
                    else "DeepSeek 请求失败（$status）：${body.take(180)}"
                }
                val content = json.parseToJsonElement(body).jsonObject["choices"]
                    ?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull
                    ?: error("DeepSeek 未返回行程内容")
                json.decodeFromString<TripPlan>(content).copy(
                    sourceName = sourceName,
                    sourceUrl = sourceUrl,
                    sourceText = sourceText,
                )
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        val SYSTEM_PROMPT = """
            你是日本旅行规划助手。只输出合法 json，不要 Markdown。输出必须完全符合：
            {"tripTitle":"名称","destination":"城市","days":[{"date":"第 1 天","title":"主题","summary":"摘要","stops":[{"name":"地点","time":"09:00","category":"景点","note":"来自攻略的提示","latitude":35.0,"longitude":139.0}]}]}
            根对象和每天的对象都需添加 precautions（注意事项字符串数组）和 packingItems（必带物品字符串数组），同时添加 foodRecommendations（美食推荐字符串数组，每项包含攻略中明确提及的餐食或餐厅及用餐提示）。仅提取攻略明确提及的内容，没有则返回空数组，不编造店名或营业信息。
            保留攻略事实，按合理地理顺序整理；攻略没有的信息不要编造。days 和 stops 必须是非空数组。
        """.trimIndent()
    }
}

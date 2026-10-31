package com.gojapan.feature.trip.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.core.ai.AiChatClient
import com.gojapan.core.ai.AiChatAvailability
import com.gojapan.core.ai.AiChatTurn
import com.gojapan.core.ai.AiChatRole
import com.gojapan.feature.ai.chat.AiChatDialogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class GuideWebImportViewModel @Inject constructor(val aiClient: AiChatClient) : ViewModel()

@Composable
internal fun GuideWebImportRoute(initialUrl: String, onBack: () -> Unit) {
    val viewModel = hiltViewModel<GuideWebImportViewModel>()
    GuideWebImportScreen(initialUrl, viewModel.aiClient, viewModel.viewModelScope, onBack)
}

internal data class ExtractedGuidePage(
    val title: String,
    val text: String,
    val images: List<String>,
    val url: String,
)

internal fun isAllowedGuideUrl(value: String): Boolean = runCatching {
    val uri = android.net.Uri.parse(value.trim())
    uri.scheme == "https" && uri.host?.lowercase() in setOf("xiaohongshu.com", "www.xiaohongshu.com")
}.getOrDefault(false)

internal fun parseExtractedPage(raw: String, url: String): ExtractedGuidePage {
    val payload = Json.parseToJsonElement(raw).jsonPrimitive.content
    val objectValue = Json.parseToJsonElement(payload).jsonObject
    return ExtractedGuidePage(
        title = objectValue["title"]?.jsonPrimitive?.content.orEmpty(),
        text = objectValue["text"]?.jsonPrimitive?.content.orEmpty().trim(),
        images = objectValue["images"]?.jsonArray?.mapNotNull {
            runCatching { it.jsonPrimitive.content }.getOrNull()
        }?.filter { it.startsWith("https://") }?.distinct().orEmpty(),
        url = url,
    )
}

internal fun validateGuidePreview(raw: String): String {
    val result = Json.parseToJsonElement(raw.trim()).jsonObject
    require(result["schemaVersion"]?.jsonPrimitive?.content == "1") { "AI 返回的结构版本不正确" }
    require(!result["title"]?.jsonPrimitive?.content.isNullOrBlank()) { "AI 没有返回攻略标题" }
    require(result["sections"]?.jsonArray != null) { "AI 没有返回章节列表" }
    return Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), result)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GuideWebImportScreen(
    initialUrl: String,
    aiClient: AiChatClient,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(initialUrl) }
    var pageUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("仅支持小红书 HTTPS 网页。打开后请等正文显示，再点“提取页面”。") }
    var sourceText by remember { mutableStateOf("") }
    var page by remember { mutableStateOf<ExtractedGuidePage?>(null) }
    var aiResult by remember { mutableStateOf("") }
    var aiStatus by remember { mutableStateOf("") }
    var showAiSettings by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    loading = true
                    pageUrl = url
                    message = "页面加载中；动态内容可能需要再等几秒。"
                }
                override fun onPageFinished(view: WebView, url: String) {
                    loading = false
                    pageUrl = url
                    message = "页面已加载。确认正文显示完整后点击提取。"
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !isAllowedGuideUrl(request.url.toString())
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        loading = false
                        message = "网页加载失败：${error.description}。可在下方粘贴正文。"
                    }
                }
            }
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("网页攻略提取 Demo", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("小红书攻略链接") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (isAllowedGuideUrl(url)) {
                    page = null
                    aiResult = ""
                    webView.loadUrl(url.trim())
                } else message = "请输入小红书 HTTPS 网页链接"
            }) { Text("打开页面") }
            OutlinedButton(onClick = {
                if (!isAllowedGuideUrl(pageUrl)) {
                    message = "当前页面不是允许提取的小红书网页"
                    return@OutlinedButton
                }
                webView.evaluateJavascript(EXTRACT_SCRIPT) { raw ->
                    runCatching { parseExtractedPage(raw, pageUrl) }
                        .onSuccess { extracted ->
                            page = extracted
                            sourceText = extracted.text
                            message = if (extracted.text.isBlank()) {
                                "没有提取到正文。请等待页面完成渲染，或在下方粘贴正文。"
                            } else "已提取 ${extracted.text.length} 字、${extracted.images.size} 张图片引用。请核对内容。"
                        }
                        .onFailure { message = "页面提取失败。可以在下方粘贴正文。" }
                }
            }, enabled = !loading && pageUrl.isNotBlank()) { Text("提取页面") }
        }
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().height(420.dp))
        page?.let { extracted ->
            Text("网页标题：${extracted.title.ifBlank { "未取得" }}")
            Text("图片引用：${extracted.images.size} 张", style = MaterialTheme.typography.bodySmall)
            extracted.images.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
        }
        OutlinedTextField(
            value = sourceText,
            onValueChange = { sourceText = it },
            label = { Text("提取正文预览／手动粘贴") },
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        Button(onClick = {
            val availability = aiClient.availability()
            if (availability is AiChatAvailability.Unavailable) {
                aiStatus = availability.message
                return@Button
            }
            generating = true
            aiResult = ""
            aiStatus = "正在生成结构化预览，请稍候…"
            scope.launch {
                runCatching {
                    aiClient.reply(listOf(AiChatTurn(AiChatRole.USER, buildGuidePrompt(sourceText, page))))
                }.onSuccess { raw ->
                    runCatching { validateGuidePreview(raw) }
                        .onSuccess { aiResult = it; aiStatus = "已生成结构化预览，尚未保存。" }
                        .onFailure { aiStatus = "AI 返回内容不是有效攻略 JSON：${it.message}" }
                }
                    .onFailure { aiStatus = it.message ?: "AI 整理失败" }
                generating = false
            }
        }, enabled = sourceText.isNotBlank() && !generating) {
            if (generating) CircularProgressIndicator() else Text("AI 生成结构化预览")
        }
        if (sourceText.isBlank()) {
            Text("请先提取网页正文，或在上方粘贴攻略内容。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (aiStatus.isNotBlank()) {
            Text(
                aiStatus,
                color = if (aiResult.isNotBlank() || generating || aiClient.availability() is AiChatAvailability.Available)
                    MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
            )
            if (aiClient.availability() is AiChatAvailability.Unavailable) {
                TextButton(onClick = { showAiSettings = true }) { Text("配置 AI Key") }
            }
        }
        if (aiResult.isNotBlank()) {
            Text("AI 结果（未保存，请核对）", style = MaterialTheme.typography.titleMedium)
            Text(aiResult)
        }
    }
    if (showAiSettings) {
        AiChatDialogRoute(onDismiss = {
            showAiSettings = false
            aiStatus = if (aiClient.availability() is AiChatAvailability.Available) {
                "AI Key 已配置，可重新点击生成预览。"
            } else {
                (aiClient.availability() as AiChatAvailability.Unavailable).message
            }
        })
    }
}

private fun buildGuidePrompt(text: String, page: ExtractedGuidePage?): String = """
    将下列实际取得的小红书攻略整理成 JSON。只输出 JSON，不要 Markdown。
    格式：{"schemaVersion":1,"title":"标题","summary":"摘要","destination":null,"tags":[],"sections":[{"title":"章节","type":"GENERAL","blocks":[{"type":"PARAGRAPH|PLACE|SUGGESTION|TODO|IDEA|FOOD_RECOMMENDATION","text":"仅段落使用","place":null,"suggestion":null,"todo":null,"idea":null,"foodRecommendation":null,"sourceExcerpt":"原文片段","needsReview":false}]}]}
    PLACE 使用 name、nameJa、type、area、addressText、description、visitTip、googleMatch、amapMatch；FOOD_RECOMMENDATION 使用 shopName、area、relatedPlaceId:null、recommendedDishes:[{"name":"菜名","reason":null,"priceText":null}]、images:[]、note:null。其他对象请遵照字段语义。只能根据原文提取，不得编造店名、菜品、地点、坐标、日期或图片 URL。图片引用仅供人工核对，不要自动关联到店铺。没有依据的块请省略。
    网页标题：${page?.title.orEmpty()}
    网页正文：${text.take(30000)}
    实际取得的图片引用（最多 30 个）：${page?.images?.take(30)?.joinToString("\n").orEmpty()}
""".trimIndent()

private const val EXTRACT_SCRIPT = """(function() {
  const text = (document.body && document.body.innerText || '').slice(0, 30000);
  const images = Array.from(document.images).map(img => img.currentSrc || img.src)
    .filter(src => src && src.startsWith('https://')).slice(0, 30);
  return JSON.stringify({title: document.title || '', text: text, images: images});
})()"""

package com.gojapan.feature.travelplan

import android.app.DatePickerDialog
import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.model.AutocompletePrediction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.google.android.libraries.places.api.Places
import com.google.android.gms.common.api.ApiException
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun CalendarField(label: String, value: String, zone: String, change: (String) -> Unit) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("$label · ${value.ifEmpty { "选填" }}") }
    if (open) DisposableEffect(context) {
        val date = value.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault()))
        val dialog = DatePickerDialog(context, { _, year, month, day ->
            change(LocalDate.of(year, month + 1, day).toString())
        }, date.year, date.monthValue - 1, date.dayOfMonth)
        dialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "清空") { _, _ -> change("") }
        dialog.setOnDismissListener { open = false }
        dialog.show()
        onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
    }
}

@Composable
internal fun GoogleDestinationSearch(destination: String, invalidate: () -> Unit, select: (String) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(destination) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<AutocompletePrediction>()) }
    var retry by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val token = remember(editing) { AutocompleteSessionToken.newInstance() }
    OutlinedTextField(query, {
        invalidate()
        query = it
        editing = true
        results = emptyList()
        message = ""
        failed = false
    }, label = { Text("目的地") }, placeholder = { Text("搜索城市或地点") }, colors = planInputColors(), singleLine = true)
    LaunchedEffect(query, editing, retry) {
        if (!editing || query.isBlank()) return@LaunchedEffect
        delay(350)
        message = "正在搜索…"
        failed = false
        try {
            val key = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
            require(key.isNotBlank() && key != "YOUR_MAPS_API_KEY")
            if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context.applicationContext, key)
            val request = FindAutocompletePredictionsRequest.builder().setQuery(query.trim()).setSessionToken(token).build()
            results = kotlinx.coroutines.withTimeout(10000L) { suspendCancellableCoroutine { continuation ->
                Places.createClient(context).findAutocompletePredictions(request)
                    .addOnSuccessListener { continuation.resume(it.autocompletePredictions) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            } }
            message = if (results.isEmpty()) "没有匹配地点" else ""
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            message = "Google 地点服务响应超时，请检查手机网络后重试"
            failed = true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            val status = (e as? ApiException)?.statusCode
            message = when (status) {
                7, 15 -> "Google 地点服务连接失败，请检查网络"
                9011, 9012 -> "Google 地点服务拒绝请求，请检查 Places API 授权及配额"
                else -> "Google 地点搜索失败"
            } + (status?.let { "（$it）" } ?: "")
            failed = true
        }
    }
    if (editing && query.isNotBlank()) {
        if (message.isNotEmpty()) Text(message)
        if (failed) TextButton(onClick = { retry++ }) { Text("重试搜索") }
        results.forEach { prediction ->
            TextButton(onClick = {
                val selected = prediction.getPrimaryText(null).toString()
                query = selected
                editing = false
                select(selected)
            }) { Text(prediction.getFullText(null).toString()) }
        }
        if (results.isNotEmpty()) androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.google.android.libraries.places.R.drawable.places_powered_by_google_light),
            contentDescription = "Powered by Google"
        )

    }
}

@Composable
internal fun ZoneChoice(zone: String, select: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("目的地时区 · ${zone.ifEmpty { "请选择" }}") }
    if (open) {
        var query by rememberSaveable { mutableStateOf("") }
        val zones = remember(query) { ZoneId.getAvailableZoneIds().filter { it.contains(query, ignoreCase = true) }.sorted() }
        AlertDialog(onDismissRequest = { open = false }, title = { Text("选择目的地时区") },
            text = { Column {
                OutlinedTextField(query, { query = it }, colors = planInputColors(), singleLine = true, label = { Text("搜索，例如 Tokyo") })
                LazyColumn { items(zones) { item -> TextButton(onClick = { select(item); open = false }) { Text(item) } } }
            } }, confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } })
    }
}

@Composable
internal fun planInputColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = androidx.compose.ui.graphics.Color(0xFF182230),
    unfocusedTextColor = androidx.compose.ui.graphics.Color(0xFF182230),
    focusedContainerColor = androidx.compose.ui.graphics.Color.White,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.White,
    focusedPlaceholderColor = androidx.compose.ui.graphics.Color(0xFF536174),
    unfocusedPlaceholderColor = androidx.compose.ui.graphics.Color(0xFF536174)
)

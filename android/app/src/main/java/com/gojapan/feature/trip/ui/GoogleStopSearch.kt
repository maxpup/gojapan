package com.gojapan.feature.trip.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.gojapan.BuildConfig
import com.gojapan.feature.trip.model.TripStop
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class StopSuggestion(val id: String, val label: String)
internal data class StopLocation(val address: String, val latitude: Double, val longitude: Double)

private suspend fun <T> Task<T>.awaitPlace(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

@Composable
internal fun GoogleStopSearch(stop: TripStop, enabled: Boolean, onChange: (TripStop) -> Unit) {
    val context = LocalContext.current.applicationContext
    var token by remember { mutableStateOf(AutocompleteSessionToken.newInstance()) }
    fun client(): com.google.android.libraries.places.api.net.PlacesClient {
        require(BuildConfig.MAPS_API_KEY.isNotBlank() && !BuildConfig.MAPS_API_KEY.startsWith("YOUR_")) { "请配置 Google 地图密钥后搜索地点" }
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.MAPS_API_KEY)
        return Places.createClient(context)
    }
    StopLocationField(stop, enabled, onChange,
        search = { query ->
            val request = FindAutocompletePredictionsRequest.builder().setQuery(query).setSessionToken(token).build()
            client().findAutocompletePredictions(request).awaitPlace().autocompletePredictions.map {
                StopSuggestion(it.placeId, it.getFullText(null).toString())
            }
        },
        locate = { suggestion ->
            val request = FetchPlaceRequest.builder(suggestion.id, listOf(Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS))
                .setSessionToken(token).build()
            val place = client().fetchPlace(request).awaitPlace().place
            val location = requireNotNull(place.location) { "该地点没有可用坐标，请选择其他结果" }
            token = AutocompleteSessionToken.newInstance()
            StopLocation(place.formattedAddress ?: suggestion.label, location.latitude, location.longitude)
        })
}

@Composable
internal fun StopLocationField(
    stop: TripStop,
    enabled: Boolean,
    onChange: (TripStop) -> Unit,
    search: suspend (String) -> List<StopSuggestion>,
    locate: suspend (StopSuggestion) -> StopLocation,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<StopSuggestion>()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<StopSuggestion?>(null) }
    val currentSearch by rememberUpdatedState(search)
    val currentLocate by rememberUpdatedState(locate)
    val currentChange by rememberUpdatedState(onChange)
    val currentStop by rememberUpdatedState(stop)
    LaunchedEffect(stop.googleAddress, editing, selected, retry, enabled) {
        results = emptyList()
        error = ""
        busy = false
        if (!enabled || !editing || stop.googleAddress.isBlank()) return@LaunchedEffect
        busy = true
        try {
            if (selected == null) {
                delay(350)
                results = withTimeout(10000) { currentSearch(stop.googleAddress.trim()) }
                if (results.isEmpty()) error = "没有匹配地点，请补充城市或地址"
            } else {
                val value = withTimeout(10000) { currentLocate(requireNotNull(selected)) }
                currentChange(currentStop.copy(googleAddress = value.address, latitude = value.latitude, longitude = value.longitude))
                editing = false
                selected = null
            }
        } catch (e: TimeoutCancellationException) {
            error = "Google 地点服务响应超时，请检查网络后重试"
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            error = if (e is ApiException) "Google 地点服务请求失败（${e.statusCode}），请检查网络、Places API 授权和配额"
                else e.localizedMessage ?: "地点搜索失败，请重试"
        } finally { busy = false }
    }
    Column {
        OutlinedTextField(stop.googleAddress, {
            selected = null
            editing = true
            onChange(stop.copy(googleAddress = it, latitude = null, longitude = null))
        }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            label = { Text("Google 地图地址") }, placeholder = { Text("输入地点名或地址，选择搜索结果") })
        if (busy) Text(if (selected == null) "正在搜索…" else "正在获取地点位置…")
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { retry++ }, enabled = enabled) { Text("重试") }
        }
        results.forEach { suggestion ->
            TextButton(onClick = { selected = suggestion }, enabled = enabled) { Text(suggestion.label) }
        }
        if (results.isNotEmpty()) Image(
            painterResource(com.google.android.libraries.places.R.drawable.places_powered_by_google_light),
            contentDescription = "Powered by Google")
        if (stop.googleAddress.isNotBlank() && !editing && (stop.latitude == null || stop.longitude == null)) {
            TextButton(onClick = { editing = true }, enabled = enabled) { Text("搜索并定位此地址") }
        }
        if (stop.latitude != null && stop.longitude != null) Text("已定位，可在地图上查看", style = MaterialTheme.typography.labelSmall)
    }
}

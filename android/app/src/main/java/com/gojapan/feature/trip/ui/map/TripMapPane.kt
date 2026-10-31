package com.gojapan.feature.trip.ui.map

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gojapan.BuildConfig
import com.gojapan.core.maps.TripMapPoint
import com.gojapan.core.maps.TripMapProvider
import com.gojapan.feature.trip.model.TripStop
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.amap.api.maps.CameraUpdateFactory as AmapCameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng as AmapLatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions as AmapPolylineOptions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun TripMapPane(
    stops: List<TripStop>,
    selectedIndex: Int,
    provider: TripMapProvider,
    onProviderChange: (TripMapProvider) -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val points = stops.mapIndexedNotNull { index, stop ->
        val latitude = stop.latitude ?: return@mapIndexedNotNull null
        val longitude = stop.longitude ?: return@mapIndexedNotNull null
        TripMapPoint(index, stop.name, stop.time, stop.note, latitude, longitude)
    }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))) {
        ProviderSelector(provider, onProviderChange)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (provider) {
                TripMapProvider.GOOGLE -> GoogleTripMap(points, selectedIndex, onSelect)
                TripMapProvider.AMAP -> AmapTripMap(points, selectedIndex, onSelect)
            }
        }
    }
}

@Composable
private fun ProviderSelector(selected: TripMapProvider, onSelect: (TripMapProvider) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("地图", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        TripMapProvider.entries.forEach { provider ->
            if (provider == selected) {
                TextButton(onClick = { onSelect(provider) }) { Text(provider.displayName, fontWeight = FontWeight.Bold) }
            } else {
                OutlinedButton(onClick = { onSelect(provider) }) { Text(provider.displayName) }
            }
        }
    }
}

@Composable
private fun GoogleTripMap(points: List<TripMapPoint>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val selected = points.firstOrNull { it.stopIndex == selectedIndex } ?: points.firstOrNull()
    val hasApiKey = BuildConfig.MAPS_API_KEY.isNotBlank() && !BuildConfig.MAPS_API_KEY.startsWith("YOUR_")
    if (!hasApiKey || selected == null) {
        MapUnavailable(
            message = if (!hasApiKey) "配置 MAPS_API_KEY 后显示 Google 地图" else "攻略暂无可用坐标",
            selected = selected,
            provider = TripMapProvider.GOOGLE,
        )
        return
    }
    val cameraState = rememberCameraPositionState {
        position = if (points.size > 1) {
            CameraPosition.fromLatLngZoom(
                LatLng(points.map { it.latitude }.average(), points.map { it.longitude }.average()),
                12f,
            )
        } else {
            CameraPosition.fromLatLngZoom(LatLng(selected.latitude, selected.longitude), 14f)
        }
    }
    var previousSelection by remember(points) { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex, selected.latitude, selected.longitude) {
        if (previousSelection != selectedIndex) {
            cameraState.position = CameraPosition.fromLatLngZoom(
                LatLng(selected.latitude, selected.longitude),
                14f,
            )
            previousSelection = selectedIndex
        }
    }
    Box(Modifier.fillMaxSize()) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraState) {
            if (points.size > 1) {
                Polyline(
                    points = points.map { LatLng(it.latitude, it.longitude) },
                    color = androidx.compose.ui.graphics.Color(0xFFB3261E),
                    width = 8f,
                )
            }
            points.forEach { point ->
                Marker(
                    state = MarkerState(LatLng(point.latitude, point.longitude)),
                    title = "${point.stopIndex + 1}. ${point.name}",
                    snippet = listOf(point.time, point.note).filter(String::isNotBlank).joinToString(" · "),
                    onClick = { onSelect(point.stopIndex); false },
                )
            }
        }
        SelectedPlaceCard(selected, TripMapProvider.GOOGLE, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun AmapTripMap(
    points: List<TripMapPoint>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val selected = points.firstOrNull { it.stopIndex == selectedIndex } ?: points.firstOrNull()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val preferences = remember(context) {
        context.getSharedPreferences("map_privacy", android.content.Context.MODE_PRIVATE)
    }
    var privacyAccepted by remember { mutableStateOf(preferences.getBoolean("amap_accepted", false)) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    val hasApiKey = BuildConfig.AMAP_API_KEY.isNotBlank() && !BuildConfig.AMAP_API_KEY.startsWith("YOUR_")
    val isArmDevice = Build.SUPPORTED_ABIS.firstOrNull()?.startsWith("arm") == true

    if (!hasApiKey || selected == null) {
        MapUnavailable(
            message = if (!hasApiKey) "配置 AMAP_API_KEY 后显示高德地图" else "攻略暂无可用坐标",
            selected = selected,
            provider = TripMapProvider.AMAP,
        )
        return
    }

    if (!isArmDevice) {
        MapUnavailable(
            message = "高德世界地图 10.x 不支持当前 x86_64 模拟器，请使用 ARM 安卓真机验证",
            selected = selected,
            provider = TripMapProvider.AMAP,
        )
        return
    }

    if (!privacyAccepted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("启用高德地图前需要确认隐私说明")
                TextButton(onClick = { showPrivacyDialog = true }) { Text("查看并启用高德地图") }
            }
        }
        if (showPrivacyDialog) {
            AlertDialog(
                onDismissRequest = { showPrivacyDialog = false },
                title = { Text("高德地图隐私说明") },
                text = {
                    Column {
                        Text("行程地图将使用北京高德图强科技有限公司提供的高德地图 SDK 加载地图和地点标记。SDK 可能处理网络状态、设备信息及地图交互数据。")
                        TextButton(onClick = { uriHandler.openUri("https://lbs.amap.com/pages/privacy/") }) {
                            Text("查看《高德地图开放平台隐私权政策》")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        MapsInitializer.updatePrivacyShow(context, true, true)
                        MapsInitializer.updatePrivacyAgree(context, true)
                        preferences.edit().putBoolean("amap_accepted", true).apply()
                        privacyAccepted = true
                        showPrivacyDialog = false
                    }) { Text("同意并启用") }
                },
                dismissButton = {
                    TextButton(onClick = { showPrivacyDialog = false }) { Text("暂不启用") }
                },
            )
        }
        return
    }

    AmapSdkMap(points, selectedIndex, onSelect)
}

@Composable
private fun AmapSdkMap(
    points: List<TripMapPoint>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    MapsInitializer.updatePrivacyShow(context, true, true)
    MapsInitializer.updatePrivacyAgree(context, true)
    MapsInitializer.loadWorldVectorMap(true)
    MapsInitializer.initRegionLanguageType(8)
    val mapView = remember { MapView(context).apply { onCreate(null) } }

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView, points, selectedIndex) {
        val map = mapView.map
        map.clear()
        map.uiSettings.isZoomControlsEnabled = false
        map.setOnMarkerClickListener { marker ->
            (marker.`object` as? Int)?.let(onSelect)
            false
        }
        if (points.size > 1) {
            map.addPolyline(
                AmapPolylineOptions()
                    .addAll(points.map { AmapLatLng(it.latitude, it.longitude) })
                    .color(android.graphics.Color.rgb(179, 38, 30))
                    .width(8f),
            )
        }
        points.forEach { point ->
            map.addMarker(
                MarkerOptions()
                    .position(AmapLatLng(point.latitude, point.longitude))
                    .title("${point.stopIndex + 1}. ${point.name}")
                    .snippet(listOf(point.time, point.note).filter(String::isNotBlank).joinToString(" · ")),
            ).`object` = point.stopIndex
        }
        val selected = points.firstOrNull { it.stopIndex == selectedIndex } ?: points.first()
        map.animateCamera(
            AmapCameraUpdateFactory.newLatLngZoom(AmapLatLng(selected.latitude, selected.longitude), 14f),
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        points.firstOrNull { it.stopIndex == selectedIndex }?.let {
            SelectedPlaceCard(it, TripMapProvider.AMAP, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun MapUnavailable(message: String, selected: TripMapPoint?, provider: TripMapProvider) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message)
            selected?.let { MapAppButton(it, provider) }
        }
    }
}

@Composable
private fun SelectedPlaceCard(point: TripMapPoint, provider: TripMapProvider, modifier: Modifier) {
    Card(modifier.padding(12.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(point.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            MapAppButton(point, provider)
        }
    }
}

@Composable
private fun MapAppButton(point: TripMapPoint, provider: TripMapProvider) {
    val context = LocalContext.current
    TextButton(onClick = {
        val intent = when (provider) {
            TripMapProvider.GOOGLE -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(point.name + " 日本")}"),
            )
            TripMapProvider.AMAP -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "androidamap://viewMap?sourceApplication=GoJapan&poiname=${Uri.encode(point.name)}" +
                        "&lat=${point.latitude}&lon=${point.longitude}&dev=1",
                ),
            )
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://uri.amap.com/marker?position=${point.longitude},${point.latitude}")),
            )
        }
    }) { Text("在${provider.displayName}地图打开 ↗") }
}

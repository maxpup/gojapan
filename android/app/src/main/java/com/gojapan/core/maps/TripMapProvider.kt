package com.gojapan.core.maps

enum class TripMapProvider(val displayName: String) {
    GOOGLE("Google"),
    AMAP("高德"),
}

data class TripMapPoint(
    val stopIndex: Int,
    val name: String,
    val time: String,
    val note: String,
    val latitude: Double,
    val longitude: Double,
)

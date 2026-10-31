package com.gojapan.feature.trip.model

import kotlinx.serialization.Serializable

@Serializable
data class TripPlan(
    val sourceName: String = "本地攻略",
    val sourceUrl: String = "",
    val sourceText: String = "",
    val tripTitle: String,
    val destination: String,
    val days: List<TripDay>,
)

@Serializable
data class TripDay(
    val date: String,
    val title: String,
    val summary: String,
    val stops: List<TripStop>,
)

@Serializable
data class TripStop(
    val name: String,
    val time: String = "",
    val category: String = "景点",
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

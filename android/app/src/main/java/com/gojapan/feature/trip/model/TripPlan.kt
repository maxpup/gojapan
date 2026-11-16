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
    val precautions: List<String> = emptyList(),
    val packingItems: List<String> = emptyList(),
    val foodRecommendations: List<String> = emptyList(),
    val todos: List<String> = emptyList(),
    val overview: String = "",
    val dateRange: String = "",
)

@Serializable
data class TripDay(
    val date: String,
    val title: String,
    val summary: String,
    val stops: List<TripStop>,
    val precautions: List<String> = emptyList(),
    val packingItems: List<String> = emptyList(),
    val foodRecommendations: List<String> = emptyList(),
    val todos: List<String> = emptyList(),
)

@Serializable
data class TripStop(
    val name: String,
    val time: String = "",
    val category: String = "景点",
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val googleAddress: String = "",
)

internal fun resolveGuideDate(label: String, departure: String?): java.time.LocalDate? {
    val start = departure?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    val full = Regex("""(?<!\d)(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})(?!\d)""").find(label)
    if (full != null) {
        val (year, month, day) = full.destructured
        return runCatching { java.time.LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
    }
    if (start == null) return null
    val expected = dayNumber(label)?.let { start.plusDays(it.toLong() - 1) }
    val partial = Regex("""(?<!\d)(\d{1,2})[/.月-](\d{1,2})(?!\d)""").find(label) ?: return expected
    val (month, day) = partial.destructured
    val year = expected?.year ?: (start.year + if (month.toInt() < start.monthValue) 1 else 0)
    return runCatching { java.time.LocalDate.of(year, month.toInt(), day.toInt()) }.getOrNull()
}

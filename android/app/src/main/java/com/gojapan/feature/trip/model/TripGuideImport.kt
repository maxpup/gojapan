package com.gojapan.feature.trip.model

internal data class TripImportPreview(val plan: TripPlan, val changes: List<String>)

internal fun dayNumber(label: String): Int? {
    val compact = label.filterNot(Char::isWhitespace)
    val token = Regex("""(?:第([0-9一二三四五六七八九十两]+)天|Day([0-9]+))(?:[｜|·：:(（].*)?""", RegexOption.IGNORE_CASE)
        .matchEntire(compact)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() } ?: return null
    token.toIntOrNull()?.let { return it.takeIf { n -> n > 0 } }
    fun digit(c: Char?) = if (c == '两') 2 else "零一二三四五六七八九".indexOf(c ?: ' ').takeIf { it > 0 }
    if ('十' !in token) return token.singleOrNull()?.let(::digit)
    val tens = token.substringBefore('十')
    val ones = token.substringAfter('十')
    return (if (tens.isEmpty()) 1 else digit(tens.singleOrNull()) ?: return null) * 10 +
        (if (ones.isEmpty()) 0 else digit(ones.singleOrNull()) ?: return null)
}

internal fun TripPlan.importTarget(day: TripDay, position: Int): Int? {
    val exact = days.indexOfFirst { it.date.filterNot(Char::isWhitespace) == day.date.filterNot(Char::isWhitespace) }
    if (exact >= 0) return exact
    val number = dayNumber(day.date)
    if (number != null) return days.indexOfFirst { dayNumber(it.date) == number }.takeIf { it >= 0 }
        ?: (number - 1).takeIf { it in days.indices }
    return position.takeIf { dayNumber(days.getOrNull(it)?.date.orEmpty()) == it + 1 }
}

internal fun TripPlan.previewImport(draft: TripPlan, mergeOnly: Set<Int> = emptySet()): TripImportPreview {
    val changes = mutableListOf<String>()
    fun mergeItems(scope: String, label: String, old: List<String>, items: List<String>): List<String> {
        val existing = if (label == "待办") old.map { it.removePrefix("☑ ") } else old
        val added = items.map(String::trim).distinct().filterNot(existing::contains)
        added.forEach { changes += "$scope · 新增$label：$it" }
        return old + added
    }
    fun overviewField(label: String, old: String, incoming: String): String {
        val value = incoming.trim()
        if (value.isEmpty() || value == old) return old
        changes += "行程概览 · $label：" + if (old.isBlank()) value else "$old → $value"
        return value
    }
    val merged = days.toMutableList()
    for ((position, day) in draft.days.withIndex()) {
        val date = day.date.trim()
        val target = importTarget(day, position) ?: -1
        val old = merged.getOrNull(target) ?: day.copy(date = date, stops = emptyList(), precautions = emptyList(),
            todos = emptyList(), packingItems = emptyList(), foodRecommendations = emptyList())
        if (target < 0) changes += "新增一天：$date · ${day.title} ${day.summary}"
        val replace = target >= 0 && day.stops.isNotEmpty() && position !in mergeOnly
        val base = if (replace) old.copy(
            date = if (dayNumber(date) == null) date else old.date,
            title = day.title, summary = day.summary, stops = day.stops
        ) else if (target >= 0 && day.stops.isEmpty() && day.summary.isNotBlank()) {
            old.copy(title = overviewField("${old.date}主题", old.title, day.title),
                summary = overviewField("${old.date}摘要", old.summary, day.summary))
        } else old
        if (replace && base != old) {
            changes += "${old.date} · 替换日程：${old.title} → ${base.title}"
            changes += "原安排：" + old.stops.joinToString(" → ") { it.name }
            changes += "新安排：" + base.stops.joinToString(" → ") { listOf(it.time, it.name, it.note).filter(String::isNotBlank).joinToString(" ") }
            if (base.summary.isNotBlank()) changes += base.summary
        }
        val stops = base.stops.toMutableList()
        for (stop in day.stops) {
            if (stops.any { it.name.trim() == stop.name.trim() }) continue
            stops += stop
            changes += "${old.date} · 新增站点：${stop.name} ${stop.time} · ${stop.category} ${stop.note}"
        }
        val updated = base.copy(stops = stops,
            todos = mergeItems(old.date, "待办", old.todos, day.todos),
            precautions = mergeItems(old.date, "提醒", old.precautions, day.precautions),
            packingItems = mergeItems(old.date, "携带物品", old.packingItems, day.packingItems),
            foodRecommendations = mergeItems(old.date, "美食", old.foodRecommendations, day.foodRecommendations))
        if (target < 0) merged += updated else merged[target] = updated
    }
    return TripImportPreview(copy(days = merged,
        tripTitle = overviewField("行程名称", tripTitle, draft.tripTitle),
        destination = overviewField("目的地", destination, draft.destination),
        overview = overviewField("全程摘要", overview, draft.overview),
        dateRange = overviewField("旅行日期", dateRange, draft.dateRange),
        todos = mergeItems("全程", "待办", todos, draft.todos),
        precautions = mergeItems("全程", "提醒", precautions, draft.precautions),
        packingItems = mergeItems("全程", "携带物品", packingItems, draft.packingItems),
        foodRecommendations = mergeItems("全程", "美食", foodRecommendations, draft.foodRecommendations)), changes)
}

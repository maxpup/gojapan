package com.gojapan.feature.trip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gojapan.feature.trip.model.TripDay
import com.gojapan.feature.trip.model.TripPlan
import kotlinx.coroutines.launch

internal enum class TripNoteSection(val title: String) {
    Food("美食推荐"), Precautions("注意事项"), Packing("必带物品");
}
internal data class TripNoteEntry(val dayIndex: Int, val itemIndex: Int, val text: String, val date: String?)

internal fun TripPlan.noteEntries(section: TripNoteSection, dayIndex: Int): List<TripNoteEntry> {
    fun values(day: TripDay?): List<String> = when (section) {
        TripNoteSection.Food -> day?.foodRecommendations ?: foodRecommendations
        TripNoteSection.Precautions -> day?.precautions ?: precautions
        TripNoteSection.Packing -> day?.packingItems ?: packingItems
    }
    if (dayIndex >= 0) return values(days[dayIndex]).mapIndexed { i, text -> TripNoteEntry(dayIndex, i, text, null) }
    return values(null).mapIndexed { i, text -> TripNoteEntry(-1, i, text, null) } +
        days.flatMapIndexed { d, day -> values(day).mapIndexed { i, text -> TripNoteEntry(d, i, text, day.date) } }
}

internal fun TripPlan.changeNotes(section: TripNoteSection, dayIndex: Int, change: (List<String>) -> List<String>): TripPlan {
    if (dayIndex < 0) return when (section) {
        TripNoteSection.Food -> copy(foodRecommendations = change(foodRecommendations))
        TripNoteSection.Precautions -> copy(precautions = change(precautions))
        TripNoteSection.Packing -> copy(packingItems = change(packingItems))
    }
    return copy(days = days.mapIndexed { i, day ->
        if (i != dayIndex) day else when (section) {
            TripNoteSection.Food -> day.copy(foodRecommendations = change(day.foodRecommendations))
            TripNoteSection.Precautions -> day.copy(precautions = change(day.precautions))
            TripNoteSection.Packing -> day.copy(packingItems = change(day.packingItems))
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeDeleteRow(enabled: Boolean, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val latestDelete by rememberUpdatedState(onDelete)
    val latestEnabled by rememberUpdatedState(enabled)
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart && latestEnabled) latestDelete()
            // Keep the item visible until its save succeeds, and reset after a failure.
            false
        },
    )
    SwipeToDismissBox(
        state = swipeState, enableDismissFromStartToEnd = false, enableDismissFromEndToStart = enabled,
        modifier = Modifier.semantics {
            if (enabled) customActions = listOf(CustomAccessibilityAction("删除") { latestDelete(); true })
        },
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(20.dp),
                contentAlignment = Alignment.CenterEnd) {
                Text("删除", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

@Composable
internal fun InlineTripAdd(
    label: String,
    fields: List<String>,
    enabled: Boolean,
    onAdd: suspend (List<String>) -> Boolean,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var third by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (!expanded) {
            TextButton(enabled = enabled, onClick = { expanded = true; focus = true }) { Text("＋ $label") }
        } else {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    EditorField(fields[0], first, enabled && !busy, requestFocus = focus,
                        onFocusHandled = { focus = false }) { first = it }
                    if (fields.size > 1) EditorField(fields[1], second, enabled && !busy) { second = it }
                    if (fields.size > 2) EditorField(fields[2], third, enabled && !busy) { third = it }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = enabled && !busy && first.isNotBlank(), onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    if (onAdd(listOf(first.trim(), second.trim(), third.trim()))) {
                                        expanded = false; first = ""; second = ""; third = ""; error = null
                                    } else error = "未保存成功，请重试；输入内容已保留。"
                                } finally { busy = false }
                            }
                        }) { Text(if (busy) "保存中…" else "添加") }
                        TextButton(enabled = !busy, onClick = { expanded = false; error = null }) { Text("取消") }
                    }
                }
            }
        }
    }
}

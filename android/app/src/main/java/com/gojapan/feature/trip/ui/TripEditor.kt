package com.gojapan.feature.trip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gojapan.feature.trip.model.TripDay
import com.gojapan.feature.trip.model.TripPlan
import com.gojapan.feature.trip.model.TripStop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal fun TripPlan.editValidationError(): String? = when {
    tripTitle.isBlank() -> "请填写行程主题"
    destination.isBlank() -> "请填写目的地"
    days.any { it.date.isBlank() || it.title.isBlank() } -> "请填写每天的日期和主题"
    days.any { day -> day.stops.any { it.name.isBlank() } } -> "请填写地点名称，或删除空地点"
    (foodRecommendations + precautions + packingItems).any { it.isBlank() } ||
        days.any { day -> (day.foodRecommendations + day.precautions + day.packingItems).any { it.isBlank() } } ->
        "请填写清单内容，或删除空白条目"
    else -> null
}

// A renamed place must not retain coordinates belonging to the previous place.
internal fun TripStop.renameForEdit(name: String): TripStop =
    if (name == this.name) this else copy(name = name, latitude = null, longitude = null)

private val TripDraftSaver = Saver<TripPlan, String>(
    save = { Json.encodeToString(TripPlan.serializer(), it) },
    restore = { Json.decodeFromString<TripPlan>(it) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripEditor(
    plan: TripPlan,
    initialDay: Int,
    onDismiss: () -> Unit,
    onSave: suspend (TripPlan) -> Boolean,
    inline: Boolean = false,
) {
    var draft by rememberSaveable(stateSaver = TripDraftSaver) { mutableStateOf(plan) }
    var expandedDay by rememberSaveable { mutableStateOf(initialDay) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var newDay by remember { mutableStateOf<Int?>(null) }
    var newStop by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(newDay) {
        newDay?.let { listState.scrollToItem(it + 2) }
    }
    fun updateDay(index: Int, day: TripDay) {
        draft = draft.copy(days = draft.days.toMutableList().also { it[index] = day })
    }
    EditorContainer(inline, onDismiss = { if (!saving) onDismiss() }) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("编辑行程") },
                    navigationIcon = {
                        TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                    },
                    actions = {
                        TextButton(
                            enabled = !saving,
                            onClick = {
                                error = draft.editValidationError()
                                if (error == null) {
                                    saving = true
                                    scope.launch {
                                        try {
                                            if (onSave(draft)) onDismiss()
                                            else error = "保存失败，请重试。修改内容仍保留在这里。"
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            error = "保存失败，请重试。修改内容仍保留在这里。"
                                        } finally {
                                            saving = false
                                        }
                                    }
                                }
                            },
                        ) { Text(if (saving) "保存中…" else "保存") }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).imePadding(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        EditorField("行程主题", draft.tripTitle, !saving) { draft = draft.copy(tripTitle = it) }
                        EditorField("目的地", draft.destination, !saving) { draft = draft.copy(destination = it) }
                    }
                }
                item {
                    Column {
                        Text("全程信息", style = MaterialTheme.typography.titleMedium)
                        EditableList("美食推荐", draft.foodRecommendations, !saving) { draft = draft.copy(foodRecommendations = it) }
                        EditableList("注意事项", draft.precautions, !saving) { draft = draft.copy(precautions = it) }
                        EditableList("必带物品", draft.packingItems, !saving) { draft = draft.copy(packingItems = it) }
                    }
                }
                draft.days.forEachIndexed { dayIndex, day ->
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    enabled = !saving,
                                    onClick = { expandedDay = if (expandedDay == dayIndex) -1 else dayIndex },
                                ) { Text("${day.date} · ${day.title}  ${if (expandedDay == dayIndex) "收起" else "展开编辑"}") }
                                if (expandedDay == dayIndex) {
                                    EditorField("日期（如 2026-11-21）", day.date, !saving) { updateDay(dayIndex, day.copy(date = it)) }
                                    EditorField("当天主题", day.title, !saving, requestFocus = newDay == dayIndex,
                                        onFocusHandled = { newDay = null }) { updateDay(dayIndex, day.copy(title = it)) }
                                    EditorField("当天简介", day.summary, !saving) { updateDay(dayIndex, day.copy(summary = it)) }
                                    day.stops.forEachIndexed { stopIndex, stop ->
                                        HorizontalDivider()
                                        Text("地点 ${stopIndex + 1}", style = MaterialTheme.typography.labelLarge)
                                        fun updateStop(updated: TripStop) {
                                            updateDay(dayIndex, day.copy(stops = day.stops.toMutableList().also { it[stopIndex] = updated }))
                                        }
                                        EditorField("地点名称", stop.name, !saving, requestFocus = newStop == (dayIndex to stopIndex),
                                            onFocusHandled = { newStop = null }) { updateStop(stop.renameForEdit(it)) }
                                        EditorField("时间", stop.time, !saving) { updateStop(stop.copy(time = it)) }
                                        EditorField("类别", stop.category, !saving) { updateStop(stop.copy(category = it)) }
                                        EditorField("安排与备注", stop.note, !saving) { updateStop(stop.copy(note = it)) }
                                        TextButton(enabled = !saving, onClick = {
                                            updateDay(dayIndex, day.copy(stops = day.stops.filterIndexed { index, _ -> index != stopIndex }))
                                        }) { Text("删除地点") }
                                    }
                                    OutlinedButton(enabled = !saving, onClick = {
                                        newStop = dayIndex to day.stops.size
                                        updateDay(dayIndex, day.copy(stops = day.stops + TripStop(name = "")))
                                    }) { Text("添加地点") }
                                    EditableList("美食推荐", day.foodRecommendations, !saving) { updateDay(dayIndex, day.copy(foodRecommendations = it)) }
                                    EditableList("注意事项", day.precautions, !saving) { updateDay(dayIndex, day.copy(precautions = it)) }
                                    EditableList("必带物品", day.packingItems, !saving) { updateDay(dayIndex, day.copy(packingItems = it)) }
                                    TextButton(enabled = !saving, onClick = {
                                        draft = draft.copy(days = draft.days.filterIndexed { index, _ -> index != dayIndex })
                                        expandedDay = -1
                                    }) { Text("删除这一天") }
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(enabled = !saving, onClick = {
                        expandedDay = draft.days.size
                        newDay = draft.days.size
                        draft = draft.copy(days = draft.days + TripDay("第 ${draft.days.size + 1} 天", "", "", emptyList()))
                    }) { Text("添加一天") }
                }
            }
        }
    }
}

@Composable
internal fun EditorField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    onFocusHandled: () -> Unit = {},
    onChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(requestFocus, placed) {
        if (requestFocus && placed) {
            focusRequester.requestFocus()
            // Wait for the new field to be laid out before scrolling it into view.
            withFrameNanos { }
            bringIntoViewRequester.bringIntoView()
            onFocusHandled()
        }
    }
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onGloballyPositioned { placed = true },
    )
}

@Composable
private fun EditableList(title: String, entries: List<String>, enabled: Boolean, onChange: (List<String>) -> Unit) {
    var newEntry by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        entries.forEachIndexed { index, value ->
            Row(Modifier.fillMaxWidth()) {
                EditorField(
                    label = "$title ${index + 1}", value = value, enabled = enabled,
                    modifier = Modifier.weight(1f),
                    requestFocus = newEntry == index,
                    onFocusHandled = { newEntry = null },
                    onChange = { updated -> onChange(entries.toMutableList().also { it[index] = updated }) },
                )
                TextButton(enabled = enabled, onClick = {
                    newEntry = null
                    onChange(entries.filterIndexed { i, _ -> i != index })
                }) { Text("删除") }
            }
        }
        TextButton(enabled = enabled, onClick = {
            newEntry = entries.size
            onChange(entries + "")
        }) { Text("添加$title") }
    }
}


@Composable
private fun EditorContainer(inline: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (inline) {
        content()
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            content = content,
        )
    }
}

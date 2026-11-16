package com.gojapan.feature.trip.ui

import com.gojapan.app.preview.GoJapanPreview
import com.gojapan.app.preview.GoJapanPreviews

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.gojapan.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gojapan.core.maps.TripMapProvider
import com.gojapan.feature.ai.chat.AiChatDialogRoute
import com.gojapan.feature.trip.model.TripDay
import com.gojapan.feature.trip.model.TripPlan
import com.gojapan.feature.trip.model.TripStop
import com.gojapan.feature.trip.ui.map.TripMapPane

@Composable
fun TripPlannerRoute(viewModel: TripPlannerViewModel = hiltViewModel(), planHeader: (@Composable () -> Unit)? = null) {
    val state by viewModel.state.collectAsState()
    TripPlannerScreen(
        state = state,
        onSelectDay = viewModel::selectDay,
        onSelectStop = viewModel::selectStop,
        onSelectMapProvider = viewModel::selectMapProvider,
        onGenerate = viewModel::saveSourceAndGenerate,
        onMessageShown = viewModel::clearMessage,
        onSavePlan = viewModel::saveEditedPlan,
        planHeader = planHeader,
    )
}

@Composable
fun TripPlannerScreen(
    state: TripPlannerUiState,
    onSelectDay: (Int) -> Unit,
    onSelectStop: (Int) -> Unit,
    onSelectMapProvider: (TripMapProvider) -> Unit,
    onGenerate: (String) -> Unit,
    onMessageShown: () -> Unit,
    onSavePlan: suspend (TripPlan) -> Boolean,
    planHeader: (@Composable () -> Unit)? = null,
) {
    TripVisualTheme {
        TripPlannerBody(state, onSelectDay, onSelectStop, onSelectMapProvider, onGenerate, onMessageShown, onSavePlan, planHeader)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripPlannerBody(
    state: TripPlannerUiState,
    onSelectDay: (Int) -> Unit,
    onSelectStop: (Int) -> Unit,
    onSelectMapProvider: (TripMapProvider) -> Unit,
    onGenerate: (String) -> Unit,
    onMessageShown: () -> Unit,
    onSavePlan: suspend (TripPlan) -> Boolean,
    planHeader: (@Composable () -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    if (showGuide && state.plan != null) {
        GuideTextImportRoute(state.plan, onSavePlan, onBack = { showGuide = false })
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = planHeader ?: { Text("旅の手帖", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val plan = state.plan
        if (plan == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            TripContent(plan, state, onSelectDay, onSelectStop, onSelectMapProvider, Modifier.padding(padding), onSavePlan = onSavePlan, onImportGuide = { showGuide = true })
        }
    }

}

@Composable
private fun TripContent(
    plan: TripPlan,
    state: TripPlannerUiState,
    onSelectDay: (Int) -> Unit,
    onSelectStop: (Int) -> Unit,
    onSelectMapProvider: (TripMapProvider) -> Unit,
    modifier: Modifier = Modifier,
    showMaps: Boolean = true,
    initialEditing: Boolean = false,
    onImportGuide: (() -> Unit)? = null,
    onSavePlan: suspend (TripPlan) -> Boolean,
) {
    var editing by rememberSaveable { mutableStateOf(initialEditing) }
    if (editing) {
        TripEditor(plan, state.selectedDay, onDismiss = { editing = false }, onSave = onSavePlan, inline = !showMaps)
        if (!showMaps) return
    }
    val scope = rememberCoroutineScope()
    val localSnackbar = remember { SnackbarHostState() }
    val currentPlan by rememberUpdatedState(plan)
    val currentState by rememberUpdatedState(state)
    var saving by remember { mutableStateOf(false) }
    val enabled = !saving && !state.isSaving && !state.isGenerating
    suspend fun save(updated: TripPlan, deleting: Boolean = false): Boolean {
        if (saving || currentState.isSaving || currentState.isGenerating) return false
        val before = currentPlan
        saving = true
        localSnackbar.currentSnackbarData?.dismiss()
        val success = try {
            onSavePlan(updated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally { saving = false }
        if (!success) {
            scope.launch { localSnackbar.showSnackbar("保存失败，请重试；原内容保持不变") }
        } else if (deleting) {
            scope.launch {
                val result = localSnackbar.showSnackbar("已删除", actionLabel = "撤销")
                if (result == SnackbarResult.ActionPerformed && currentPlan == updated) save(before)
            }
        }
        return success
    }
    fun delete(updated: TripPlan) { scope.launch { save(updated, deleting = true) } }
    val dayIndex = state.selectedDay.takeIf { it in plan.days.indices } ?: -1
    val day = plan.days.getOrNull(dayIndex)
    var sectionTab by rememberSaveable(dayIndex) { mutableStateOf<TripNoteSection?>(null) }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            TripHeader(plan, dayIndex, onSelectDay,
                canEdit = enabled, onImportGuide = onImportGuide, onEdit = { editing = true })
            BoxWithConstraints(Modifier.weight(1f)) {
                val wide = maxWidth >= 840.dp && showMaps && day != null
                Row(Modifier.fillMaxSize()) {
                    androidx.compose.runtime.key(dayIndex, sectionTab) {
                        LazyColumn(
                            Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            if (sectionTab == null) item(key = "saved-guides") { SavedTripGuides() }
                            if (sectionTab == null && day == null) {
                                item { SectionTitle("行程概览") }
                                itemsIndexed(plan.days) { index, entry ->
                                    androidx.compose.runtime.key(entry, index) {
                                        SwipeDeleteRow(enabled, onDelete = {
                                            delete(plan.copy(days = plan.days.filterIndexed { i, _ -> i != index }))
                                        }) {
                                            Card(onClick = { onSelectDay(index) },
                                                shape = RoundedCornerShape(24.dp),
                                                colors = CardDefaults.cardColors(containerColor = if (index % 2 == 0)
                                                    MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(dateWithWeekday(entry.date), style = MaterialTheme.typography.labelLarge)
                                                    Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                    Text(entry.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(entry.stops.joinToString(" → ") { it.name }, style = MaterialTheme.typography.bodySmall)
                                                    Text("展开这一天  ↗", style = MaterialTheme.typography.labelLarge)
                                                }
                                            }
                                        }
                                    }
                                }
                                item {
                                    InlineTripAdd("新增一天", listOf("当天主题", "日期（选填）", "当天简介（选填）"), enabled) {
                                        save(plan.copy(days = plan.days + TripDay(
                                            date = it[1].ifBlank { "第 ${plan.days.size + 1} 天" },
                                            title = it[0], summary = it[2], stops = emptyList(),
                                        )))
                                    }
                                }
                            } else if (sectionTab == null && day != null) {
                                item { DayHeader(day) }
                                item { SectionTitle("行程安排") }
                                itemsIndexed(day.stops) { index, stop ->
                                    androidx.compose.runtime.key(stop, index) {
                                        SwipeDeleteRow(enabled, onDelete = {
                                            delete(plan.copy(days = plan.days.mapIndexed { i, d ->
                                                if (i == dayIndex) d.copy(stops = d.stops.filterIndexed { j, _ -> j != index }) else d
                                            }))
                                        }) {
                                            var editAddress by remember { mutableStateOf(false) }
                                            StopCard(stop, index, index == state.selectedStop) {
                                                onSelectStop(index)
                                                editAddress = enabled
                                            }
                                            if (editAddress) {
                                                var address by remember(stop) { mutableStateOf(stop.googleAddress) }
                                                AlertDialog(onDismissRequest = { editAddress = false },
                                                    title = { Text(stop.name) },
                                                    text = { OutlinedTextField(address, { address = it }, label = { Text("Google 地图地址") }) },
                                                    confirmButton = { Button(enabled = enabled, onClick = {
                                                        scope.launch {
                                                            val stops = day.stops.toMutableList()
                                                            stops[index] = stop.copy(googleAddress = address.trim())
                                                            val days = plan.days.toMutableList()
                                                            days[dayIndex] = day.copy(stops = stops)
                                                            if (save(plan.copy(days = days))) editAddress = false
                                                        }
                                                    }) { Text("保存") } },
                                                    dismissButton = { TextButton({ editAddress = false }) { Text("取消") } })
                                            }
                                        }
                                    }
                                }
                                item {
                                    InlineTripAdd("新增安排", listOf("地点名称", "时间（选填）", "安排与备注（选填）"), enabled) { values ->
                                        save(plan.copy(days = plan.days.mapIndexed { i, d ->
                                            if (i == dayIndex) d.copy(stops = d.stops + TripStop(
                                                name = values[0], time = values[1], note = values[2],
                                            )) else d
                                        }))
                                    }
                                }
                            }
                            listOf(sectionTab ?: TripNoteSection.Packing).forEach { section ->
                                item {
                                    InlineNoteCard(section, plan.noteEntries(section, dayIndex), enabled,
                                        onToggle = { entry -> scope.launch {
                                            save(plan.changeNotes(section, entry.dayIndex) { notes ->
                                                notes.mapIndexed { i, text ->
                                                    if (i != entry.itemIndex) text else if (text.startsWith("☑ ")) text.removePrefix("☑ ") else "☑ $text"
                                                }
                                            })
                                        } },
                                        onDelete = { entry ->
                                            delete(plan.changeNotes(section, entry.dayIndex) {
                                                it.filterIndexed { i, _ -> i != entry.itemIndex }
                                            })
                                        },
                                        onAdd = { text -> save(plan.changeNotes(section, dayIndex) { it + text }) },
                                    )
                                }
                            }
                            if (sectionTab == null && day != null && showMaps && !wide) {
                                item { SectionTitle("当天路线") }
                                item {
                                    TripMapPane(day.stops, state.selectedStop, state.mapProvider,
                                        onSelectMapProvider, onSelectStop,
                                        Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 20.dp))
                                }
                            }
                        }
                    }
                    if (sectionTab == null && wide && day != null) {
                        TripMapPane(day.stops, state.selectedStop, state.mapProvider, onSelectMapProvider,
                            onSelectStop, Modifier.weight(1f).fillMaxHeight())
                    }
                    Column(Modifier.width(64.dp).verticalScroll(rememberScrollState()).selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(null to "日程", TripNoteSection.Precautions to "注意事项",
                            TripNoteSection.Todos to "待办", TripNoteSection.Food to "美食").forEach { (tab, label) ->
                            val selected = sectionTab == tab
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                .selectable(selected, role = Role.Tab) { sectionTab = tab }
                                .padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(localSnackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun InlineNoteCard(
    section: TripNoteSection,
    entries: List<TripNoteEntry>,
    enabled: Boolean,
    onToggle: (TripNoteEntry) -> Unit,
    onDelete: (TripNoteEntry) -> Unit,
    onAdd: suspend (String) -> Boolean,
) {
    val (background, foreground) = when (section) {
        TripNoteSection.Food -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TripNoteSection.Packing -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        TripNoteSection.Todos, TripNoteSection.Precautions -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = background, contentColor = foreground)) {
        Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
            Text(section.title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (entries.isEmpty()) Text("暂无内容，点击下方新增", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium)
            entries.forEach { entry ->
                androidx.compose.runtime.key(entry.dayIndex, entry.itemIndex, entry.text) {
                    SwipeDeleteRow(enabled, onDelete = { onDelete(entry) }) {
                        Column(Modifier.fillMaxWidth().background(background).padding(horizontal = 20.dp, vertical = 12.dp)) {
                            entry.date?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            if (section == TripNoteSection.Todos) Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(entry.text.startsWith("☑ "), { onToggle(entry) }, enabled = enabled)
                                Text(entry.text.removePrefix("☑ "))
                            } else Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            InlineTripAdd("新增${section.title}", listOf(section.title), enabled) { onAdd(it[0]) }
        }
    }
}

@Composable
private fun TripHeader(plan: TripPlan, selectedDay: Int, onSelectDay: (Int) -> Unit, canEdit: Boolean, onImportGuide: (() -> Unit)?, onEdit: () -> Unit) {
    Column {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp)) {
                Text(plan.tripTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${plan.days.size} 天 · ${plan.days.sumOf { it.stops.size }} 个地点",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = onEdit, enabled = canEdit) {
                        Icon(painterResource(R.drawable.ic_trip_edit), "修改日程", Modifier.size(20.dp))
                    }
                    if (onImportGuide != null) IconButton(onClick = onImportGuide) {
                        Icon(painterResource(R.drawable.ic_guide_import), "攻略导入", Modifier.size(20.dp))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TripTab("总览", selectedDay == -1) { onSelectDay(-1) }
            plan.days.forEachIndexed { index, day ->
                TripTab(dateWithWeekday(day.date), selectedDay == index) { onSelectDay(index) }
            }
        }
    }
}

internal val LocalDepartureDate = androidx.compose.runtime.staticCompositionLocalOf<String?> { null }

@Composable
private fun dateWithWeekday(date: String): String {
    val start = LocalDepartureDate.current
    val parsed = com.gojapan.feature.trip.model.resolveGuideDate(date, start) ?: return date
    return date + "\n" + parsed.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.SIMPLIFIED_CHINESE)
}

@Composable
private fun TripTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .height(48.dp).widthIn(min = 72.dp).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun DayHeader(day: TripDay) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(day.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(day.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StopCard(stop: TripStop, index: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stop.name, modifier = Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stop.time, color = MaterialTheme.colorScheme.primary)
                }
                Text(stop.category, style = MaterialTheme.typography.labelMedium)
                if (stop.googleAddress.isNotBlank()) Text(stop.googleAddress)
                if (stop.note.isNotBlank()) Text(stop.note, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ImportDialog(
    initialText: String,
    sourceUrl: String,
    loading: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入小红书攻略") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sourceUrl, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("攻略会发送给 DeepSeek 做结构化整理，结果仅保存在本机。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    label = { Text("攻略原文") },
                    enabled = !loading,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onGenerate(text) }, enabled = text.isNotBlank() && !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("用 DeepSeek 生成")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )
}

// Full itinerary preview with local interaction state, without map SDK or network.
@GoJapanPreviews
@Composable
private fun TripItineraryPreview() {
    InteractiveTripPreview(initialDay = -1)
}

@GoJapanPreviews
@Composable
private fun TripDayPreview() {
    InteractiveTripPreview(initialDay = 0)
}

@GoJapanPreviews
@Composable
private fun TripEditingPreview() {
    InteractiveTripPreview(initialDay = 0, initialEditing = true)
}

@Composable
internal fun InteractiveTripPreview(initialDay: Int, initialEditing: Boolean = false) {
    val plan = remember {
        TripPlan(
            tripTitle = "日本东京之旅", destination = "东京",
            precautions = listOf("提前确认开放时间，为换乘预留时间"),
            packingItems = listOf("手机与充电宝", "饮用水"),
            days = listOf(
                TripDay(
                    date = "11月21日", title = "浅草与上野漫步", summary = "寺院参拜、街区散步与午后逛街",
                    stops = listOf(
                        TripStop(name = "浅草寺", time = "09:00", note = "参拜后逛仲见世商店街"),
                        TripStop(name = "上野公园", time = "12:00", note = "午餐后散步，预留休息时间"),
                    ),
                    precautions = listOf("步行较多，途中安排休息"),
                    packingItems = listOf("饮用水", "雨具"),
                    foodRecommendations = listOf("午餐 · 天妇罗定食（示例）", "下午茶 · 日式团子（示例）"),
                ),
                TripDay(
                    date = "11月22日", title = "东京站周边探索", summary = "丸之内散步与购物",
                    stops = listOf(
                        TripStop(name = "东京站", time = "10:00", note = "欣赏车站建筑"),
                        TripStop(name = "丸之内", time = "14:00", note = "街区散步与购物"),
                    ),
                    precautions = listOf("东京站出口较多，提前确认集合地点"),
                    packingItems = listOf("手机与充电宝"),
                    foodRecommendations = listOf("午餐 · 拉面（示例）", "晚餐 · 寿司（示例）"),
                ),
            ),
        )
    }
    var state by remember { mutableStateOf(TripPlannerUiState(plan = plan, selectedDay = initialDay)) }
    GoJapanPreview {
        TripVisualTheme {
            TripContent(
                plan = state.plan ?: plan,
                state = state,
                onSelectDay = { state = state.copy(selectedDay = it, selectedStop = 0) },
                onSelectStop = { state = state.copy(selectedStop = it) },
                onSelectMapProvider = {},
                showMaps = false,
                initialEditing = initialEditing,
                onSavePlan = {
                    state = state.copy(plan = it, selectedDay = state.selectedDay.takeIf { index -> index in it.days.indices } ?: -1, selectedStop = 0)
                    true
                },
            )
        }
    }
}

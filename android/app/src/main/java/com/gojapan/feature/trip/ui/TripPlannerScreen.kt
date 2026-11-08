package com.gojapan.feature.trip.ui

import com.gojapan.app.preview.GoJapanPreview
import com.gojapan.app.preview.GoJapanPreviews

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
fun TripPlannerRoute(viewModel: TripPlannerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    TripPlannerScreen(
        state = state,
        onSelectDay = viewModel::selectDay,
        onSelectStop = viewModel::selectStop,
        onSelectMapProvider = viewModel::selectMapProvider,
        onGenerate = viewModel::saveSourceAndGenerate,
        onMessageShown = viewModel::clearMessage,
        onSavePlan = viewModel::saveEditedPlan,
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
) {
    TripVisualTheme {
        TripPlannerBody(state, onSelectDay, onSelectStop, onSelectMapProvider, onGenerate, onMessageShown, onSavePlan)
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
) {
    val snackbar = remember { SnackbarHostState() }
    var showImporter by remember { mutableStateOf(false) }
    var showWebImport by remember { mutableStateOf(false) }
    var showAiChat by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    if (showWebImport) {
        GuideWebImportRoute(initialUrl = state.plan?.sourceUrl.orEmpty(), onBack = { showWebImport = false })
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("旅の手帖", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    TextButton(onClick = { showAiChat = true }) { Text("AI 助手") }
                    TextButton(onClick = { showWebImport = true }) { Text("网页提取") }
                    TextButton(enabled = !state.isSaving && !state.isGenerating, onClick = { showImporter = true }) { Text("生成行程") }
                },
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
            TripContent(plan, state, onSelectDay, onSelectStop, onSelectMapProvider, Modifier.padding(padding), onSavePlan = onSavePlan)
        }
    }
    if (showImporter && state.plan != null) {
        ImportDialog(
            initialText = state.plan.sourceText,
            sourceUrl = state.plan.sourceUrl,
            loading = state.isGenerating,
            onDismiss = { if (!state.isGenerating) showImporter = false },
            onGenerate = onGenerate,
        )
    }
    if (showAiChat) {
        AiChatDialogRoute(onDismiss = { showAiChat = false })
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
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            TripHeader(plan, dayIndex, onSelectDay,
                canEdit = enabled, onEdit = { editing = true })
            BoxWithConstraints(Modifier.weight(1f)) {
                val wide = maxWidth >= 840.dp && showMaps && day != null
                Row(Modifier.fillMaxSize()) {
                    androidx.compose.runtime.key(dayIndex) {
                        LazyColumn(
                            Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            if (day == null) {
                                item { SectionTitle("行程安排") }
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
                                                    Text(entry.date, style = MaterialTheme.typography.labelLarge)
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
                            } else {
                                item { DayHeader(day) }
                                item { SectionTitle("行程安排") }
                                itemsIndexed(day.stops) { index, stop ->
                                    androidx.compose.runtime.key(stop, index) {
                                        SwipeDeleteRow(enabled, onDelete = {
                                            delete(plan.copy(days = plan.days.mapIndexed { i, d ->
                                                if (i == dayIndex) d.copy(stops = d.stops.filterIndexed { j, _ -> j != index }) else d
                                            }))
                                        }) {
                                            StopCard(stop, index, index == state.selectedStop) { onSelectStop(index) }
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
                            TripNoteSection.entries.forEach { section ->
                                item {
                                    InlineNoteCard(section, plan.noteEntries(section, dayIndex), enabled,
                                        onDelete = { entry ->
                                            delete(plan.changeNotes(section, entry.dayIndex) {
                                                it.filterIndexed { i, _ -> i != entry.itemIndex }
                                            })
                                        },
                                        onAdd = { text -> save(plan.changeNotes(section, dayIndex) { it + text }) },
                                    )
                                }
                            }
                            if (day != null && showMaps && !wide) {
                                item { SectionTitle("当天路线") }
                                item {
                                    TripMapPane(day.stops, state.selectedStop, state.mapProvider,
                                        onSelectMapProvider, onSelectStop,
                                        Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 20.dp))
                                }
                            }
                        }
                    }
                    if (wide && day != null) {
                        TripMapPane(day.stops, state.selectedStop, state.mapProvider, onSelectMapProvider,
                            onSelectStop, Modifier.weight(1f).fillMaxHeight())
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
    onDelete: (TripNoteEntry) -> Unit,
    onAdd: suspend (String) -> Boolean,
) {
    val (background, foreground) = when (section) {
        TripNoteSection.Food -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TripNoteSection.Packing -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        TripNoteSection.Precautions -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
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
                            Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            InlineTripAdd("新增${section.title}", listOf(section.title), enabled) { onAdd(it[0]) }
        }
    }
}

@Composable
private fun TripHeader(plan: TripPlan, selectedDay: Int, onSelectDay: (Int) -> Unit, canEdit: Boolean, onEdit: () -> Unit) {
    Column {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(28.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("旅の記録  /  TRAVEL NOTES", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(plan.tripTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    }
                    TripMascot()
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${plan.days.size} 天慢慢逛 · ${plan.days.sumOf { it.stops.size }} 个地点",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onEdit, enabled = canEdit) {
                        Text("更多编辑", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TripTab("总览", selectedDay == -1) { onSelectDay(-1) }
            plan.days.forEachIndexed { index, day ->
                TripTab(day.date, selectedDay == index) { onSelectDay(index) }
            }
        }
    }
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
private fun TripMascot() {
    val yellow = MaterialTheme.colorScheme.primaryContainer
    val paper = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(68.dp)) {
        drawOval(yellow, topLeft = Offset(size.width * .18f, size.height * .35f), size = Size(size.width * .82f, size.height * .62f))
        drawOval(paper, topLeft = Offset(0f, 0f), size = Size(size.width * .85f, size.height * .76f))
        drawOval(ink, topLeft = Offset(size.width * .32f, size.height * .28f), size = Size(size.width * .065f, size.height * .10f))
        drawOval(ink, topLeft = Offset(size.width * .47f, size.height * .28f), size = Size(size.width * .065f, size.height * .10f))
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

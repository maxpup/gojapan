package com.gojapan.feature.trip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPlannerScreen(
    state: TripPlannerUiState,
    onSelectDay: (Int) -> Unit,
    onSelectStop: (Int) -> Unit,
    onSelectMapProvider: (TripMapProvider) -> Unit,
    onGenerate: (String) -> Unit,
    onMessageShown: () -> Unit,
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
                title = { Text("我的日本行程") },
                actions = {
                    TextButton(onClick = { showAiChat = true }) { Text("AI 助手") }
                    TextButton(onClick = { showWebImport = true }) { Text("网页提取") }
                    TextButton(onClick = { showImporter = true }) { Text("生成行程") }
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
            TripContent(plan, state, onSelectDay, onSelectStop, onSelectMapProvider, Modifier.padding(padding))
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
) {
    val day = plan.days.getOrNull(state.selectedDay) ?: return
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                ItineraryPane(plan, day, state, onSelectDay, onSelectStop, Modifier.weight(0.95f))
                TripMapPane(day.stops, state.selectedStop, state.mapProvider, onSelectMapProvider, onSelectStop, Modifier.weight(1.05f).fillMaxHeight())
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item { TripHeader(plan, state.selectedDay, onSelectDay) }
                item {
                    TripMapPane(
                        day.stops,
                        state.selectedStop,
                        state.mapProvider,
                        onSelectMapProvider,
                        onSelectStop,
                        Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 16.dp),
                    )
                }
                item { DayHeader(day) }
                itemsIndexed(day.stops) { index, stop -> StopCard(stop, index, index == state.selectedStop) { onSelectStop(index) } }
            }
        }
    }
}

@Composable
private fun ItineraryPane(
    plan: TripPlan,
    day: TripDay,
    state: TripPlannerUiState,
    onSelectDay: (Int) -> Unit,
    onSelectStop: (Int) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TripHeader(plan, state.selectedDay, onSelectDay) }
        item { DayHeader(day) }
        itemsIndexed(day.stops) { index, stop -> StopCard(stop, index, index == state.selectedStop) { onSelectStop(index) } }
    }
}

@Composable
private fun TripHeader(plan: TripPlan, selectedDay: Int, onSelectDay: (Int) -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(plan.destination.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(plan.tripTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("本地攻略 · 由 DeepSeek 整理", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.days.forEachIndexed { index, day ->
                if (selectedDay == index) Button(onClick = { onSelectDay(index) }) { Text(day.date) }
                else OutlinedButton(onClick = { onSelectDay(index) }) { Text(day.date) }
            }
        }
    }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stop.time, color = MaterialTheme.colorScheme.primary)
                }
                Text(stop.category, style = MaterialTheme.typography.labelMedium)
                if (stop.note.isNotBlank()) Text(stop.note, maxLines = 3, overflow = TextOverflow.Ellipsis)
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

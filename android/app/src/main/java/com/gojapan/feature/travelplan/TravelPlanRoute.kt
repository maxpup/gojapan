package com.gojapan.feature.travelplan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.*
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.lifecycle.compose.LifecycleResumeEffect

@HiltViewModel
class TravelViewModel @Inject constructor(private val repository: TravelRepository, private val state: SavedStateHandle): ViewModel() {
    var rows by mutableStateOf<List<PlanRow>?>(null)
        private set
    var readError by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    val panel = state.getStateFlow("panel", "home")
    var editId: Long?
        get() = state["editId"]
        private set(value) { state["editId"] = value }
    var request: String
        get() = state["request"] ?: ""
        private set(value) { state["request"] = value }
    private var observation: Job? = null
    init { refresh() }
    fun refresh() {
        observation?.cancel()
        observation = viewModelScope.launch {
            try {
                repository.observe().collect { plans ->
                    rows = plans
                    readError = false
                    if (panel.value == "form" && editId == null && plans.any { it.plan.request == request }) show("home")
                    if (plans.isEmpty() && panel.value != "form") form(null)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { readError = true }
        }
    }
    fun clearError() { error = "" }
    fun show(page: String) { error = ""; state["panel"] = page }
    fun form(plan: TravelPlan?) {
        request = UUID.randomUUID().toString()
        editId = plan?.id
        show("form")
    }
    private fun write(target: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { action(); show(target) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalArgumentException) e.message.orEmpty() else "保存失败，请重试" }
            finally { busy = false }
        }
    }
    fun save(plan: TravelPlan) = write(if (editId == null) "home" else "list") { repository.save(plan, editId) }
    fun select(id: Long) = write("home") { repository.select(id) }
}

@Composable
fun TravelPlanRoute(content: @Composable (TravelPlan?, @Composable () -> Unit) -> Unit) {
    val vm = hiltViewModel<TravelViewModel>()
    val panel by vm.panel.collectAsStateWithLifecycle()
    val rows = vm.rows
    val mainState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val current = rows?.firstOrNull { it.selected }?.plan
    var clock by remember { mutableStateOf(Clock.fixed(Instant.now(), ZoneOffset.UTC)) }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { resumed = true; onPauseOrDispose { resumed = false } }
    LaunchedEffect(rows, resumed) {
        while (resumed) {
            val now = Instant.now()
            clock = Clock.fixed(now, ZoneOffset.UTC)
            val next = rows.orEmpty().minOfOrNull {
                val zone = ZoneId.of(it.plan.zone)
                now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            } ?: break
            delay(Duration.between(now, next).toMillis().coerceAtLeast(1))
        }
    }
    fun back() { if (!vm.busy) vm.show(if (panel == "form") "list" else "home") }
    BackHandler(panel != "home" && !rows.isNullOrEmpty()) { back() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (vm.readError) TextButton(onClick = vm::refresh) { Text("计划列表加载失败，重试") }
        when {
            rows == null -> if (!vm.readError) Text("正在读取旅行计划…")
            panel == "form" -> key(vm.request) {
                com.gojapan.app.theme.GoJapanTheme(darkTheme = false) {
                    Surface(Modifier.fillMaxSize()) {
                        PlanForm(rows.find { it.plan.id == vm.editId }?.plan, vm, if (rows.isEmpty()) null else ::back)
                    }
                }
            }
            panel == "list" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                com.gojapan.feature.backup.BackupControls()
                Text("我的旅行计划", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { vm.form(null) }, enabled = !vm.busy) { Text("新建计划") }
                rows.forEach { row ->
                    Column {
                        TextButton(onClick = { vm.select(row.plan.id) }, enabled = !vm.busy) {
                            Text((if (row.selected) "当前 · " else "") + row.plan.summary(clock) + " · " + row.plan.destination)
                        }
                        TextButton(onClick = { vm.form(row.plan) }, enabled = !vm.busy) { Text("编辑「${row.plan.name}」") }
                    }
                }
                Text(vm.error)
                TextButton(onClick = ::back, enabled = !vm.busy) { Text("返回") }
            }
            rows.isNotEmpty() -> {
                mainState.SaveableStateProvider("main-${current?.owner}-${current?.id}") {
                    content(current) {
                        val summary = current?.summary(clock) ?: "旅行计划"
                        TextButton(onClick = { vm.show("list") }, modifier = Modifier.heightIn(min = 48.dp)
                            .semantics { contentDescription = "$summary，选择旅行计划" }) {
                            Column {
                                Text(current?.name ?: "旅行计划", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                current?.let { Text(summary.removePrefix(it.name + " · "), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, value: String, items: List<String>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label · ${value.ifEmpty { "请选择" }}") }
        DropdownMenu(expanded, { expanded = false }) {
            items.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { select(it); expanded = false }) }
        }
    }
}

@Composable
private fun PlanForm(old: TravelPlan?, vm: TravelViewModel, back: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(old?.name.orEmpty()) }
    var wish by rememberSaveable { mutableStateOf(old?.wish.orEmpty()) }
    var custom by rememberSaveable { mutableStateOf(old != null) }
    var destination by rememberSaveable { mutableStateOf(old?.destination.orEmpty()) }
    var zone by rememberSaveable { mutableStateOf(old?.zone.orEmpty()) }
    var language by rememberSaveable { mutableStateOf(old?.language ?: "ja") }
    var start by rememberSaveable { mutableStateOf(old?.start.orEmpty()) }
    var end by rememberSaveable { mutableStateOf(old?.end.orEmpty()) }
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (old == null) "创建旅行计划" else "编辑旅行计划", style = MaterialTheme.typography.headlineSmall)
        if (back == null) com.gojapan.feature.backup.BackupControls()
        fun chooseDestination(selected: String) {
            vm.clearError()
            destination = selected
            zone = destinations[selected].orEmpty()
            if (!custom) name = selected + "之旅"
        }
        GoogleDestinationSearch(destination, invalidate = { destination = ""; zone = ""; vm.clearError() }, select = ::chooseDestination)
        if (destination.isNotEmpty() && destination !in destinations) {
            ZoneChoice(zone) { zone = it; vm.clearError() }
        }
        OutlinedTextField(name, { name = it; custom = true; vm.clearError() }, label = { Text("计划名称") }, colors = planInputColors(), singleLine = true)
        OutlinedTextField(wish, { wish = it }, label = { Text("旅行心愿（选填）") }, minLines = 2, colors = planInputColors())
        Choice("翻译目标语言", languages.getValue(language), languages.values.toList()) { selected ->
            language = languages.entries.first { it.value == selected }.key
        }
        CalendarField("出发日", start, zone) { start = it; vm.clearError() }
        CalendarField("结束日", end, zone) { end = it; vm.clearError() }
        if (vm.error.isNotEmpty()) Text(vm.error, color = MaterialTheme.colorScheme.error)
        Button(onClick = { vm.save(TravelPlan("", 0, vm.request, 0, name, destination,
            zone, language, start.ifBlank { null }, end.ifBlank { null }, wish)) }, enabled = !vm.busy && destination.isNotBlank()) { Text(if (old == null) "开启奇妙之旅" else "保存修改") }
        if (back != null) TextButton(onClick = back, enabled = !vm.busy) { Text("取消") }
    }
}

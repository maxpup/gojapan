package com.gojapan.feature.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.travelplan.TravelPlan
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val currencyNames = mapOf("CNY" to "人民币", "JPY" to "日元", "USD" to "美元", "GBP" to "英镑")
private fun currencyName(code: String) = currencyNames[code] ?: code

@Composable
fun ExpenseScreen(plan: TravelPlan, model: ExpenseModel = hiltViewModel()) {
    val flow = remember(plan.owner, plan.id) { model.dao.observe(plan.owner, plan.id) }
    val rows by flow.collectAsStateWithLifecycle(emptyList())
    var input by remember { mutableStateOf("") }
    var drafts by remember { mutableStateOf(emptyList<Draft>()) }
    var conversation by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var selected by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showAiSettings by remember { mutableStateOf(false) }
    if (showAiSettings) com.gojapan.feature.ai.chat.AiChatDialogRoute(onDismiss = { showAiSettings = false })
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try { action() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage ?: "操作失败，请重试" }
            finally { busy = false }
        }
    }
    val groups = rows.groupBy { it.currency }
    val currency = selected.takeIf { it in groups } ?: groups.keys.firstOrNull()
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(Modifier.fillMaxSize().background(colors.background).imePadding().padding(horizontal = 20.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (currency != null) item {
                val entries = groups.getValue(currency)
                val byCategory = entries.groupBy { it.category }
                val sums = byCategory.mapValues { (_, items) -> items.sumOf { major(it.minor, currency) } }
                val total = sums.values.fold(BigDecimal.ZERO, BigDecimal::add)
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (groups.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            groups.keys.sorted().forEach { code ->
                                FilterChip(selected = currency == code, onClick = { selected = code },
                                    label = { Text(currencyName(code)) }, shape = RoundedCornerShape(50))
                            }
                        }
                        Text("${currencyName(currency)} ${total.toPlainString()}", style = type.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("总支出 · ${entries.size} 笔", style = type.bodySmall, color = colors.onSurfaceVariant)
                        HorizontalDivider()
                        sums.entries.sortedByDescending { it.value }.forEach { (category, sum) ->
                            val share = sum.movePointRight(2).divide(total, 1, RoundingMode.HALF_UP)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(category, style = type.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${byCategory.getValue(category).size} 笔", style = type.bodySmall)
                                }
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                    Text("${currencyName(currency)} ${sum.toPlainString()}", style = type.titleMedium)
                                    Text("占 ${currencyName(currency)} 总支出 ${share.stripTrailingZeros().toPlainString()}%",
                                        style = type.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                            LinearProgressIndicator(progress = { share.toFloat() / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item { Text("支出明细", style = type.titleMedium, fontWeight = FontWeight.Bold) }
            if (rows.isEmpty()) item { Text("还没有支出", color = colors.onSurfaceVariant) }
            rows.groupBy { it.day }.forEach { (day, entries) ->
                item { Text(day, style = type.labelLarge, color = colors.onSurfaceVariant) }
                entries.forEach { row -> item(key = row.id) {
                    Card(onClick = {
                        editing = row
                        drafts = listOf(row.draft())
                        error = ""
                    }, enabled = !busy && drafts.isEmpty(), shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(row.purpose, style = type.titleMedium, fontWeight = FontWeight.Bold)
                                Text(row.category, style = type.labelMedium, color = colors.onSurfaceVariant)
                            }
                            Text("${currencyName(row.currency)} ${major(row.minor, row.currency)}", style = type.titleMedium)
                        }
                    }
                } }
            }
        }
        if (conversation) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { conversation = false; drafts = emptyList() }) {
                Surface(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        com.gojapan.core.ai.StructuredConversation(input, model::request, { drafts = emptyList() }) {
                            drafts = emptyList()
                            drafts = model.parseAnswer(it)
                        }
                        if (drafts.isNotEmpty()) Button({ conversation = false }) { Text("采用此结果并核对") }
                        TextButton({ conversation = false; drafts = emptyList() }) { Text("取消") }
                    }
                }
            }
        } else if (drafts.isEmpty()) {
            OutlinedTextField(input, { input = it }, shape = RoundedCornerShape(18.dp), enabled = !busy, label = { Text("描述支出") },
                placeholder = { Text("今天午餐拉面 980 日元，地铁 210 日元") }, modifier = Modifier.fillMaxWidth())
            Row {
                Button(onClick = { conversation = true }, enabled = !busy && input.isNotBlank()) { Text(if (busy) "处理中…" else "解析") }
                TextButton(onClick = { showAiSettings = true }, enabled = !busy) { Text("AI 设置") }
                TextButton(onClick = { drafts = listOf(Draft(currency = "CNY")); error = "" }, enabled = !busy) { Text("手动记账") }
            }
            if (error.isNotBlank()) Text(error)
        } else {
            androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) { drafts = emptyList(); editing = null; error = "" } }) {
                Surface(shape = MaterialTheme.shapes.large) {
                    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { Text(if (editing == null) "核对支出" else "编辑支出", style = MaterialTheme.typography.titleLarge) }
                        drafts.forEach { draft -> item(key = draft.id) {
                            fun update(value: Draft) { drafts = drafts.map { if (it.id == value.id) value else it } }
                            Column {
                                OutlinedTextField(draft.purpose, { update(draft.copy(purpose = it)) }, shape = RoundedCornerShape(18.dp), enabled = !busy, label = { Text("用途") })
                                OutlinedTextField(draft.amount, { update(draft.copy(amount = it)) }, shape = RoundedCornerShape(18.dp), enabled = !busy, label = { Text("金额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                var chooseCurrency by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton({ chooseCurrency = true }, enabled = !busy) { Text("币种 · " + currencyName(draft.currency).ifBlank { "请选择" }) }
                                    DropdownMenu(chooseCurrency, { chooseCurrency = false }) {
                                        currencyNames.forEach { (code, name) ->
                                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                                update(draft.copy(currency = code))
                                                chooseCurrency = false
                                            })
                                        }
                                    }
                                }
                                com.gojapan.feature.travelplan.CalendarField("日期", draft.day, plan.zone) { update(draft.copy(day = it)) }
                                var menu by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { menu = true }, enabled = !busy) { Text("类别 · ${draft.category}") }
                                    DropdownMenu(menu, { menu = false }) { categories.forEach { category ->
                                        DropdownMenuItem(text = { Text(category) }, onClick = { update(draft.copy(category = category)); menu = false })
                                    } }
                                }
                                if (editing == null && drafts.size > 1) TextButton({ drafts = drafts - draft }, enabled = !busy) { Text("移除本笔") }
                            }
                        } }
                        item {
                            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                            Button({ perform {
                                model.dao.save(drafts.map { it.record(plan) })
                                if (editing == null) input = ""
                                drafts = emptyList(); editing = null
                            } }, enabled = !busy) { Text("保存 ${drafts.size} 笔") }
                            TextButton({ drafts = emptyList(); editing = null; error = "" }, enabled = !busy) { Text("取消") }
                            editing?.let { row -> TextButton({ perform { model.dao.delete(row); drafts = emptyList(); editing = null } }, enabled = !busy) { Text("删除支出") } }
                        }
                    }
                }
            }
        }
    }
}

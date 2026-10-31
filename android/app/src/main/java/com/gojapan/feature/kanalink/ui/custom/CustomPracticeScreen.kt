package com.gojapan.feature.kanalink.ui.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.kanalink.data.course.KanaCourseRepository
import com.gojapan.feature.kanalink.data.preferences.KanaPreferences
import com.gojapan.feature.kanalink.data.preferences.KanaSettings
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaItem
import com.gojapan.feature.kanalink.domain.model.PairMode
import com.gojapan.feature.kanalink.domain.scheduler.MemoryScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class CustomPracticeUiState(
    val mode: PairMode = PairMode.HIRAGANA_ROMAJI,
    val groups: Set<KanaGroup> = KanaGroup.entries.toSet(),
    val candidateCount: Int = 104,
)

data class CustomRound(val items: List<KanaItem>, val mode: PairMode)

@HiltViewModel
class CustomPracticeViewModel @Inject constructor(
    private val courseRepository: KanaCourseRepository,
    private val learningRepository: LearningRepository,
    private val preferences: KanaPreferences,
    private val scheduler: MemoryScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(CustomPracticeUiState())
    val uiState = state
    private val eventsChannel = Channel<CustomRound>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(preferences.settings, kotlinx.coroutines.flow.flow { emit(courseRepository.getCourse()) }) { settings, course ->
                CustomPracticeUiState(settings.preferredMode, settings.selectedGroups, course.count { it.group in settings.selectedGroups })
            }.collect { state.value = it }
        }
    }

    fun selectMode(mode: PairMode) = save(state.value.copy(mode = mode))
    fun toggleGroup(group: KanaGroup) {
        val groups = if (group in state.value.groups) state.value.groups - group else state.value.groups + group
        save(state.value.copy(groups = groups))
    }
    fun selectAll() = save(state.value.copy(groups = KanaGroup.entries.toSet()))
    fun clear() = save(state.value.copy(groups = emptySet()))

    fun start() {
        if (state.value.groups.isEmpty()) return
        viewModelScope.launch {
            val course = courseRepository.getCourse().filter { it.group in state.value.groups }
            val progress = learningRepository.observeProgress().first()
            eventsChannel.send(CustomRound(scheduler.select(course, progress, clock.millis()), state.value.mode))
        }
    }

    private fun save(updated: CustomPracticeUiState) {
        state.value = updated.copy(candidateCount = state.value.candidateCount)
        viewModelScope.launch {
            val current = preferences.settings.first()
            preferences.update(current.copy(preferredMode = updated.mode, selectedGroups = updated.groups))
        }
    }
}

@Composable
fun CustomPracticeRoute(
    onStart: (CustomRound) -> Unit,
    onBack: () -> Unit,
    viewModel: CustomPracticeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.events.collect(onStart) }
    CustomPracticeScreen(state, viewModel::selectMode, viewModel::toggleGroup, viewModel::selectAll, viewModel::clear, viewModel::start, onBack)
}

@Composable
fun CustomPracticeScreen(
    state: CustomPracticeUiState,
    onMode: (PairMode) -> Unit,
    onGroup: (KanaGroup) -> Unit,
    onAll: () -> Unit,
    onClear: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("自定义练习")
        Text("配对模式")
        PairMode.entries.forEach { mode ->
            FilterChip(selected = state.mode == mode, onClick = { onMode(mode) }, label = { Text(mode.label) })
        }
        Text("假名范围")
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            KanaGroup.entries.forEach { group ->
                FilterChip(selected = group in state.groups, onClick = { onGroup(group) }, label = { Text(group.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAll) { Text("全选") }
            OutlinedButton(onClick = onClear) { Text("清空") }
        }
        Text("已选择 ${state.candidateCount} 个音，本轮最多 5 个")
        Button(onClick = onStart, enabled = state.groups.isNotEmpty() && state.candidateCount > 0, modifier = Modifier.fillMaxWidth()) { Text("开始自定义练习") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
    }
}

private val PairMode.label get() = when (this) {
    PairMode.HIRAGANA_ROMAJI -> "平假名 ↔ 罗马音"
    PairMode.KATAKANA_ROMAJI -> "片假名 ↔ 罗马音"
    PairMode.HIRAGANA_KATAKANA -> "平假名 ↔ 片假名"
}
private val KanaGroup.label get() = when (this) {
    KanaGroup.SEION -> "清音"
    KanaGroup.DAKUON -> "浊音"
    KanaGroup.HANDAKUON -> "半浊音"
    KanaGroup.YOON -> "拗音"
}

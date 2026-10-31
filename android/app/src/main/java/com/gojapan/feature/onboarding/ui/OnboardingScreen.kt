package com.gojapan.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.lesson.domain.TravelScene
import com.gojapan.feature.onboarding.domain.JapaneseFoundation
import com.gojapan.feature.onboarding.domain.TravelStatus

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel, modifier: Modifier = Modifier) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    OnboardingScreen(
        draft,
        viewModel::selectFoundation,
        viewModel::selectStatus,
        viewModel::updateDate,
        viewModel::toggleScene,
        viewModel::answerAssessment,
        viewModel::next,
        viewModel::back,
        viewModel::complete,
        modifier,
    )
}

@Composable
fun OnboardingScreen(
    draft: OnboardingDraft,
    onFoundation: (JapaneseFoundation) -> Unit,
    onStatus: (TravelStatus) -> Unit,
    onDate: (String) -> Unit,
    onScene: (TravelScene) -> Unit,
    onAssessment: (Boolean?) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("开始你的日本旅行日语", style = MaterialTheme.typography.headlineSmall)
        Text("第 ${draft.step + 1} / 4 步")
        LinearProgressIndicator(progress = { (draft.step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
        when (draft.step) {
            0 -> FoundationStep(draft.foundation, onFoundation)
            1 -> TravelStep(draft.travelStatus, draft.tripDate, onStatus, onDate)
            2 -> SceneStep(draft.scenes, onScene)
            else -> AssessmentStep(draft.assessmentCorrect, onAssessment)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (draft.step > 0) OutlinedButton(onClick = onBack) { Text("上一步") }
            if (draft.step < 3) {
                Button(onClick = onNext, enabled = draft.step != 2 || draft.scenes.isNotEmpty()) { Text("下一步") }
            } else {
                Button(onClick = onComplete, enabled = draft.scenes.isNotEmpty()) { Text("生成学习路径") }
            }
        }
    }
}

@Composable
private fun FoundationStep(selected: JapaneseFoundation, onSelect: (JapaneseFoundation) -> Unit) {
    Text("你的日语基础？", style = MaterialTheme.typography.titleLarge)
    JapaneseFoundation.entries.forEach { option ->
        ChoiceCard(option.label, option == selected) { onSelect(option) }
    }
}

@Composable
private fun TravelStep(
    selected: TravelStatus,
    date: String,
    onStatus: (TravelStatus) -> Unit,
    onDate: (String) -> Unit,
) {
    Text("目前的出行状态？", style = MaterialTheme.typography.titleLarge)
    TravelStatus.entries.forEach { option ->
        FilterChip(selected = option == selected, onClick = { onStatus(option) }, label = { Text(option.label) })
    }
    if (selected == TravelStatus.PLANNING) {
        OutlinedTextField(
            value = date,
            onValueChange = onDate,
            label = { Text("预计出行日期（可选，YYYY-MM-DD）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun SceneStep(selected: Set<TravelScene>, onToggle: (TravelScene) -> Unit) {
    Text("你最感兴趣的场景？", style = MaterialTheme.typography.titleLarge)
    Text("至少选择一个，可多选。")
    TravelScene.entries.forEach { scene ->
        FilterChip(
            selected = scene in selected,
            onClick = { onToggle(scene) },
            label = { Text("${scene.icon} ${scene.label}") },
        )
    }
}

@Composable
private fun AssessmentStep(answer: Boolean?, onAnswer: (Boolean?) -> Unit) {
    Text("轻量水平判断", style = MaterialTheme.typography.titleLarge)
    Text("假名「あ」的罗马音是什么？")
    ChoiceCard("a", answer == true) { onAnswer(true) }
    ChoiceCard("i", answer == false) { onAnswer(false) }
    OutlinedButton(onClick = { onAnswer(null) }) { Text("跳过判断") }
    Text("判断只用于推荐起点，不会阻止你访问任何课程。", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChoiceCard(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("${if (selected) "●" else "○"} $label", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
}

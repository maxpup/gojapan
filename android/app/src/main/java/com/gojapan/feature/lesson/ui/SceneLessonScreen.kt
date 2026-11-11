package com.gojapan.feature.lesson.ui

import com.gojapan.core.audio.rememberJapaneseSpeaker
import com.gojapan.core.audio.JapanesePlaybackStatus
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.lesson.domain.LessonEngineState
import com.gojapan.feature.lesson.domain.LessonQuestionType
import com.gojapan.feature.lesson.domain.SceneLesson
import com.gojapan.feature.lesson.domain.SceneLessonCatalog
import com.gojapan.feature.lesson.domain.TravelScene

@Composable
fun SceneLessonRoute(modifier: Modifier = Modifier, viewModel: SceneLessonViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.phase) {
        LessonPhase.CATALOG -> SceneCatalog(state.orderedScenes, state.completedLessonIds, state.checkpoint, viewModel::resumeLesson, viewModel::openLesson, modifier)
        LessonPhase.PREVIEW -> LessonPreview(checkNotNull(state.lesson), viewModel::startLesson, viewModel::backToCatalog, modifier)
        LessonPhase.PLAYING -> LessonGame(
            checkNotNull(state.lesson), checkNotNull(state.engine), viewModel::replayAudio, viewModel::recordAudioResult,
            viewModel::answer, viewModel::advance, viewModel::backToCatalog, modifier,
        )
        LessonPhase.RESULT -> LessonResult(
            checkNotNull(state.lesson), checkNotNull(state.engine), viewModel::retryWeakItems,
            viewModel::backToCatalog, modifier,
        )
    }
}

@Composable
private fun SceneCatalog(
    orderedScenes: List<TravelScene>,
    completedIds: Set<String>,
    checkpoint: com.gojapan.feature.lesson.domain.LessonCheckpoint?,
    onResume: () -> Unit,
    onOpenLesson: (SceneLesson) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("旅游场景学习", style = MaterialTheme.typography.headlineSmall)
            Text("已通过 ${SceneLessonCatalog.allLessons.count { it.id in completedIds }}/${SceneLessonCatalog.allLessons.size} 关")
            checkpoint?.let { saved ->
                val title = SceneLessonCatalog.allLessons.firstOrNull { it.id == saved.lessonId }?.title.orEmpty()
                Button(onClick = onResume) { Text("继续上次：$title · 已答 ${saved.state.answeredCount}/${saved.state.progressQuestionCount}") }
            }
        }
        items(orderedScenes) { scene ->
            val lessons = SceneLessonCatalog.scenes[scene].orEmpty()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${scene.icon} ${scene.label}", style = MaterialTheme.typography.titleLarge)
                    Text("已通过 ${lessons.count { it.id in completedIds }}/${lessons.size} 关 · 每关 12 道基础及挑战题", style = MaterialTheme.typography.bodyMedium)
                    lessons.forEachIndexed { index, lesson ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${index + 1}. ${lesson.title}", modifier = Modifier.weight(1f))
                            Button(onClick = { onOpenLesson(lesson) }) { Text(if (lesson.id in completedIds) "✓ 再练" else "开始") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonPreview(lesson: SceneLesson, onStart: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val speaker = com.gojapan.core.audio.rememberJapaneseSpeaker()
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            com.gojapan.core.audio.JapanesePlaybackStatus(speaker)
            Text(lesson.title, style = MaterialTheme.typography.headlineSmall)
            Text("旅行任务：${lesson.mission}")
            Text("先预览 ${lesson.expressions.size} 个核心内容，再完成 12 道连续练习。")
        }
        items(lesson.expressions, key = { it.id }) { expression ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(expression.japanese, style = MaterialTheme.typography.titleLarge)
                    Text(expression.kana)
                    Text(expression.chinese, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { speaker.speak(expression.japanese) }, enabled = speaker.ready) { Text("▶ 播放整句") }
                    com.gojapan.feature.search.ui.ExpressionLearningContent(expression, speaker.ready) { speaker.speak(it) }

                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("返回") }
                Button(onClick = onStart) { Text("开始答题") }
            }
        }
    }
}

@Composable
private fun LessonGame(
    lesson: SceneLesson,
    state: LessonEngineState,
    onReplay: () -> Unit,
    onAudioResult: (Boolean) -> Unit,
    onAnswer: (String) -> Unit,
    onAdvance: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val question = checkNotNull(state.currentQuestion)
    val expression = lesson.expressions.first { it.id == question.expressionId }
    val speaker = rememberJapaneseSpeaker()
    LaunchedEffect(question.id) { speaker.stop() }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (question.isRetry) "错题重现" else if (question.isChallenge) "最终挑战" else question.type.label)
            Text("第 ${state.currentIndex + 1}/${state.progressQuestionCount} 题 · 已答 ${state.answeredCount} 题")
        }
        LinearProgressIndicator(
            progress = { state.completionFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("进度包含追加错题；首次正确率和最终挑战分别统计。", style = MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(question.prompt, style = MaterialTheme.typography.headlineSmall)
                if (question.type == LessonQuestionType.LISTENING) {
                    Button(onClick = {
                        onReplay()
                        speaker.speak(expression.japanese, onAudioResult)
                    }, enabled = speaker.ready) { Text("▶ 播放 / 重播") }
                    JapanesePlaybackStatus(speaker)
                }
                question.options.forEach { option ->
                    OutlinedButton(
                        onClick = { onAnswer(option) },
                        enabled = state.selectedAnswer == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(option) }
                }
                state.selectedAnswer?.let {
                    Text(
                        if (state.lastAnswerCorrect == true) "✓ 回答正确" else "✕ ${question.explanation}",
                        color = if (state.lastAnswerCorrect == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) { Text("继续") }
                }
            }
        }
        OutlinedButton(onClick = onExit) { Text("退出关卡") }
    }
}

@Composable
private fun LessonResult(
    lesson: SceneLesson,
    state: LessonEngineState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weak = state.evidence.values.count { it.firstTryCorrect == false || !it.finalCorrect }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (state.passed) "关卡完成！" else "再强化一下就会更稳", style = MaterialTheme.typography.headlineSmall)
        Text(lesson.title, style = MaterialTheme.typography.titleLarge)
        Text("首次练习正确率：${state.initialAccuracy}%")
        Text("最终挑战正确率：${state.challengeAccuracy}%")
        Text("薄弱内容：$weak · 已全部加入复习计划")
        if (!state.passed || weak > 0) Button(onClick = onRetry) { Text("只练错题与薄弱内容") }
        OutlinedButton(onClick = onBack) { Text("返回场景目录") }
    }
}

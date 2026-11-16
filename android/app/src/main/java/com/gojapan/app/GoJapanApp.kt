package com.gojapan.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gojapan.feature.kanalink.ui.home.KanaLinkHomeRoute
import com.gojapan.feature.kanalink.ui.game.GameRoute
import com.gojapan.feature.kanalink.ui.result.ResultRoute
import com.gojapan.feature.kanalink.ui.custom.CustomPracticeRoute
import com.gojapan.feature.kanalink.ui.settings.SettingsRoute
import com.gojapan.feature.kanalink.ui.report.ReportRoute
import com.gojapan.feature.kanalink.ui.KanaFlowState
import com.gojapan.feature.kanalink.ui.KanaFlowViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gojapan.feature.search.ui.SearchScreen
import com.gojapan.feature.library.ui.FavoritesRoute
import com.gojapan.feature.review.ui.ReviewRoute
import com.gojapan.feature.lesson.ui.SceneLessonRoute
import com.gojapan.feature.onboarding.ui.OnboardingRoute
import com.gojapan.feature.onboarding.ui.OnboardingViewModel
import com.gojapan.feature.onboarding.domain.LearningPathGenerator
import com.gojapan.feature.onboarding.domain.LearningStart
import com.gojapan.feature.kanalink.ui.tap.KanaTapPracticeRoute
import com.gojapan.feature.kanalink.ui.exercise.KanaCoreExerciseRoute
import com.gojapan.feature.home.HomeDashboardRoute
import com.gojapan.feature.trip.ui.TripPlannerRoute

@Composable
fun GoJapanApp(
    modifier: Modifier = Modifier,
    kanaLinkContent: (@Composable () -> Unit)? = null,
) {
    if (kanaLinkContent == null) {
        com.gojapan.feature.travelplan.TravelPlanRoute { plan, header -> MainPages(modifier, null, header, plan) }
    } else MainPages(modifier, kanaLinkContent)
}

@Composable
private fun MainPages(modifier: Modifier, kanaLinkContent: (@Composable () -> Unit)?, planHeader: (@Composable () -> Unit)? = null, plan: com.gojapan.feature.travelplan.TravelPlan? = null) {
    var route by rememberSaveable { mutableStateOf(AppDestination.TRIPS) }
    var page by rememberSaveable { mutableStateOf("目录") }
    var sceneSource by rememberSaveable { mutableStateOf("目录") }
    val selected = when (route) {
        AppDestination.TRIPS, AppDestination.HOME -> AppDestination.TRIPS
        AppDestination.EXPENSE -> AppDestination.EXPENSE
        else -> AppDestination.LEARN_JAPANESE
    }
    val section = when (route) {
        AppDestination.SEARCH -> "搜索"
        AppDestination.FAVORITES -> "我的收藏"
        AppDestination.REVIEW -> "目录"
        else -> page
    }
    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val open: (String) -> Unit = { page = it; route = AppDestination.LEARN_JAPANESE }
    val back = { open(if (section == "场景学习") sceneSource else "目录") }
    androidx.activity.compose.BackHandler(selected == AppDestination.LEARN_JAPANESE && section != "目录", back)
    val select: (AppDestination) -> Unit = { page = section; route = it }
    val content: @Composable (Modifier) -> Unit = { area ->
        Box(area.fillMaxSize()) {
            holder.SaveableStateProvider(if (selected == AppDestination.LEARN_JAPANESE) section else selected.name) {
                if (selected == AppDestination.TRIPS) {
                    if (kanaLinkContent == null) androidx.compose.runtime.CompositionLocalProvider(
                        com.gojapan.feature.trip.ui.LocalDepartureDate provides plan?.start
                    ) { TripPlannerRoute(planHeader = planHeader) } else Text("行程规划")
                } else if (selected == AppDestination.EXPENSE) androidx.compose.foundation.layout.Column {
                    planHeader?.invoke()
                    if (plan != null) androidx.compose.runtime.key(plan.owner, plan.id) { com.gojapan.feature.expense.ExpenseScreen(plan) }
                } else androidx.compose.foundation.layout.Column {
                    planHeader?.invoke()
                    if (section != "目录") androidx.compose.material3.TextButton(onClick = back) { Text("返回" + if (section == "场景学习" && sceneSource == "搜索") "搜索" else "语言") }
                    when (section) {
                        "搜索" -> SearchScreen(onOpenScenes = { sceneSource = "搜索"; open("场景学习") }, onOpenKana = { open("五十音") })
                        "我的收藏" -> FavoritesRoute()
                        "场景学习" -> SceneLessonRoute()
                        "五十音" -> if (kanaLinkContent != null) kanaLinkContent() else KanaLinkFlow(hiltViewModel())
                        else -> SearchScreen(
                            onOpenScenes = { sceneSource = "目录"; open("场景学习") },
                            onOpenKana = { open("五十音") },
                        )
                    }
                }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            AppNavigationRail(selected, select)
            content(Modifier.weight(1f))
        } else Scaffold(bottomBar = { AppNavigationBar(selected, select) }) { content(Modifier.padding(it)) }
    }
}

@Composable
private fun KanaLinkFlow(viewModel: KanaFlowViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        KanaFlowState.Home -> KanaLinkHomeRoute(
            onStartRound = viewModel::start,
            onCustomPractice = viewModel::showCustomPractice,
            onReport = viewModel::showReport,
            onSettings = viewModel::showSettings,
            onTapPractice = viewModel::showTapPractice,
            onCoreExercise = viewModel::showCoreExercise,
        )
        KanaFlowState.CustomPractice -> CustomPracticeRoute(
            onStart = { viewModel.startCustom(it.items, it.mode) },
            onBack = viewModel::goHome,
        )
        KanaFlowState.Settings -> SettingsRoute(viewModel::goHome)
        KanaFlowState.Report -> ReportRoute(viewModel::goHome)
        KanaFlowState.TapPractice -> KanaTapPracticeRoute(viewModel::goHome)
        KanaFlowState.CoreExercise -> KanaCoreExerciseRoute(viewModel::goHome)
        is KanaFlowState.Game -> GameRoute(current.items, current.mode, current.source, viewModel::complete, viewModel::goHome)
        is KanaFlowState.Result -> ResultRoute(current.summary, viewModel::again, viewModel::goHome)
    }
}

@Composable
private fun AppNavigationBar(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationBar {
        AppDestination.tabs.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = { Text(destination.iconLabel) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationRail {
        AppDestination.tabs.forEach { destination ->
            NavigationRailItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = { Text(destination.iconLabel) },
                label = { Text(destination.label) },
            )
        }
    }
}

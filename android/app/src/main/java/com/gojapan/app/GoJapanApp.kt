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
    val onboardingViewModel = if (kanaLinkContent == null) hiltViewModel<OnboardingViewModel>() else null
    val profile = onboardingViewModel?.profile?.collectAsStateWithLifecycle()?.value
    if (onboardingViewModel != null) {
        if (profile == null) {
            Box(modifier.fillMaxSize()) { Text("正在准备学习路径…", Modifier.padding(24.dp)) }
            return
        }
        if (!profile.completed) {
            OnboardingRoute(onboardingViewModel, modifier)
            return
        }
    }
    val learningStart = profile?.let(LearningPathGenerator::start) ?: LearningStart.KANA
    val initialDestination = if (learningStart == LearningStart.SCENE) {
        AppDestination.LEARN_JAPANESE
    } else {
        AppDestination.HOME
    }
    var selected by rememberSaveable { mutableStateOf(initialDestination) }
    var showKanaLink by rememberSaveable { mutableStateOf(learningStart == LearningStart.KANA) }
    val flowViewModel = if (kanaLinkContent == null) hiltViewModel<KanaFlowViewModel>() else null
    val selectDestination: (AppDestination) -> Unit = { destination ->
        if (destination == AppDestination.HOME) {
            showKanaLink = false
            flowViewModel?.goHome()
        }
        selected = destination
    }
    val defaultKanaContent: @Composable () -> Unit = { KanaLinkFlow(checkNotNull(flowViewModel)) }
    val kanaContent = kanaLinkContent ?: defaultKanaContent
    val homeContent: @Composable () -> Unit = {
        if (showKanaLink) {
            kanaContent()
        } else {
            HomeDashboardRoute(
                onKana = { showKanaLink = true; flowViewModel?.goHome() },
                onLearn = { selected = AppDestination.LEARN_JAPANESE },
                onReview = { selected = AppDestination.REVIEW },
                onFavorites = { selected = AppDestination.FAVORITES },
            )
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                AppNavigationRail(selected, onSelect = selectDestination)
                AppContent(
                    selected, Modifier.weight(1f), homeContent,
                    onOpenReview = { selected = AppDestination.REVIEW },
                    onOpenLearn = { selected = AppDestination.LEARN_JAPANESE },
                )
            }
        } else {
            Scaffold(
                bottomBar = { AppNavigationBar(selected, onSelect = selectDestination) },
            ) { padding -> AppContent(
                selected, Modifier.padding(padding), homeContent,
                onOpenReview = { selected = AppDestination.REVIEW },
                onOpenLearn = { selected = AppDestination.LEARN_JAPANESE },
            ) }
        }
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
private fun AppContent(
    destination: AppDestination,
    modifier: Modifier = Modifier,
    homeContent: @Composable () -> Unit,
    onOpenReview: () -> Unit,
    onOpenLearn: () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        when (destination) {
            AppDestination.HOME -> homeContent()
            AppDestination.LEARN_JAPANESE -> SceneLessonRoute()
            AppDestination.REVIEW -> ReviewRoute()
            AppDestination.TRIPS -> TripPlannerRoute()
            AppDestination.SEARCH -> SearchScreen(onOpenScenes = onOpenLearn)
            AppDestination.FAVORITES -> FavoritesRoute(onReview = onOpenReview)
        }
    }
}

@Composable
private fun AppNavigationBar(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationBar {
        AppDestination.entries.forEach { destination ->
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
        AppDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = { Text(destination.iconLabel) },
                label = { Text(destination.label) },
            )
        }
    }
}

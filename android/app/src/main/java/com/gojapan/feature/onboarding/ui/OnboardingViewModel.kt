package com.gojapan.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.lesson.domain.TravelScene
import com.gojapan.feature.onboarding.data.UserProfilePreferences
import com.gojapan.feature.onboarding.domain.JapaneseFoundation
import com.gojapan.feature.onboarding.domain.TravelStatus
import com.gojapan.feature.onboarding.domain.UserLearningProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingDraft(
    val step: Int = 0,
    val foundation: JapaneseFoundation = JapaneseFoundation.ZERO,
    val travelStatus: TravelStatus = TravelStatus.PLANNING,
    val tripDate: String = "",
    val scenes: Set<TravelScene> = setOf(TravelScene.TRANSPORT),
    val assessmentCorrect: Boolean? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserProfilePreferences,
) : ViewModel() {
    val profile: StateFlow<UserLearningProfile?> = preferences.profile.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )
    val draft = MutableStateFlow(OnboardingDraft())

    fun selectFoundation(value: JapaneseFoundation) = draft.update { it.copy(foundation = value) }
    fun selectStatus(value: TravelStatus) = draft.update { it.copy(travelStatus = value) }
    fun updateDate(value: String) = draft.update { it.copy(tripDate = value.filter { c -> c.isDigit() || c == '-' }.take(10)) }
    fun toggleScene(scene: TravelScene) = draft.update {
        val next = if (scene in it.scenes) it.scenes - scene else it.scenes + scene
        it.copy(scenes = next)
    }
    fun answerAssessment(correct: Boolean?) = draft.update { it.copy(assessmentCorrect = correct) }
    fun next() = draft.update { it.copy(step = (it.step + 1).coerceAtMost(3)) }
    fun back() = draft.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    fun complete() {
        val value = draft.value
        if (value.scenes.isEmpty()) return
        viewModelScope.launch {
            preferences.save(
                UserLearningProfile(
                    completed = true,
                    foundation = value.foundation,
                    travelStatus = value.travelStatus,
                    tripDate = value.tripDate.takeIf(String::isNotBlank),
                    interestedScenes = value.scenes,
                    assessmentCorrect = value.assessmentCorrect,
                ),
            )
        }
    }
}

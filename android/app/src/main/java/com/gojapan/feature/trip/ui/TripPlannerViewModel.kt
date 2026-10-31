package com.gojapan.feature.trip.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.trip.data.DeepSeekTripGenerator
import com.gojapan.feature.trip.data.TripPlanRepository
import com.gojapan.feature.trip.model.TripPlan
import com.gojapan.core.maps.TripMapProvider
import com.gojapan.core.maps.TripMapProviderPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripPlannerUiState(
    val plan: TripPlan? = null,
    val selectedDay: Int = 0,
    val selectedStop: Int = 0,
    val isGenerating: Boolean = false,
    val message: String? = null,
    val mapProvider: TripMapProvider = TripMapProvider.GOOGLE,
)

@HiltViewModel
class TripPlannerViewModel @Inject constructor(
    private val repository: TripPlanRepository,
    private val generator: DeepSeekTripGenerator,
    private val mapProviderPreferences: TripMapProviderPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(TripPlannerUiState(mapProvider = mapProviderPreferences.get()))
    val state: StateFlow<TripPlannerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.load() }
                .onSuccess { plan -> _state.update { it.copy(plan = plan) } }
                .onFailure { error -> _state.update { it.copy(message = error.message ?: "本地攻略读取失败") } }
        }
    }

    fun selectDay(index: Int) = _state.update { it.copy(selectedDay = index, selectedStop = 0) }
    fun selectStop(index: Int) = _state.update { it.copy(selectedStop = index) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun selectMapProvider(provider: TripMapProvider) {
        mapProviderPreferences.set(provider)
        _state.update { it.copy(mapProvider = provider) }
    }

    fun saveSourceAndGenerate(sourceText: String) {
        val current = _state.value.plan ?: return
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, message = null) }
            runCatching {
                repository.saveSourceText(sourceText)
                generator.generate(sourceText, current.sourceName, current.sourceUrl)
            }.onSuccess { plan ->
                repository.save(plan)
                _state.update {
                    it.copy(plan = plan, selectedDay = 0, selectedStop = 0, isGenerating = false, message = "新行程已生成并保存到本机")
                }
            }.onFailure { error ->
                _state.update { it.copy(isGenerating = false, message = error.message ?: "生成失败，请重试") }
            }
        }
    }
}

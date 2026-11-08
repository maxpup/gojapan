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
    val selectedDay: Int = -1,
    val selectedStop: Int = 0,
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
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

    suspend fun saveEditedPlan(plan: TripPlan): Boolean {
        if (_state.value.isSaving || _state.value.isGenerating || plan.editValidationError() != null) return false
        _state.update { it.copy(isSaving = true) }
        return try {
            repository.save(plan)
            _state.update {
                it.copy(plan = plan, selectedDay = it.selectedDay.takeIf { index -> index in plan.days.indices } ?: -1, selectedStop = 0)
            }
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun saveSourceAndGenerate(sourceText: String) {
        if (_state.value.isGenerating || _state.value.isSaving) return
        val current = _state.value.plan ?: return
        _state.update { it.copy(isGenerating = true, message = null) }
        viewModelScope.launch {
            runCatching {
                repository.saveSourceText(sourceText)
                generator.generate(sourceText, current.sourceName, current.sourceUrl).also { repository.save(it) }
            }.onSuccess { plan ->
                _state.update {
                    it.copy(plan = plan, selectedDay = -1, selectedStop = 0, isGenerating = false, message = "新行程已生成并保存到本机")
                }
            }.onFailure { error ->
                _state.update { it.copy(isGenerating = false, message = error.message ?: "生成失败，请重试") }
            }
        }
    }
}

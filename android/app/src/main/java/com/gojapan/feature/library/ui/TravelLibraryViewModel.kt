package com.gojapan.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gojapan.feature.library.data.TravelLibraryPreferences
import com.gojapan.feature.library.data.TravelLibraryState
import com.gojapan.feature.search.domain.TravelExpression
import com.gojapan.feature.search.domain.TravelExpressionSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.gojapan.feature.review.domain.TravelReviewResult
import com.gojapan.core.analytics.AnalyticsTracker

data class LibraryUiState(
    val favorites: List<TravelExpression> = emptyList(),
    val frequentSearches: List<TravelExpression> = emptyList(),
    val reviewIds: Set<String> = emptySet(),
    val frequentWrong: List<TravelExpression> = emptyList(),
)

@HiltViewModel
class TravelLibraryViewModel @Inject constructor(
    private val preferences: TravelLibraryPreferences,
    private val analytics: AnalyticsTracker,
) : ViewModel() {
    val rawState: StateFlow<TravelLibraryState> = preferences.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TravelLibraryState(),
    )

    val state: StateFlow<LibraryUiState> = preferences.state.map { library ->
        LibraryUiState(
            favorites = expressions(library.favoriteIds),
            frequentSearches = expressions(library.frequentSearchIds),
            reviewIds = library.reviewIds,
            frequentWrong = expressions(library.frequentWrongIds),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun toggleFavorite(id: String) = viewModelScope.launch {
        val adding = id !in rawState.value.favoriteIds
        preferences.toggleFavorite(id)
        analytics.track(if (adding) "favorite_add" else "favorite_remove", mapOf(
            "content_id" to id, "source" to "search_or_library", "label" to "我的收藏",
            "joined_review" to (id in rawState.value.reviewIds),
        ))
    }
    fun toggleReview(id: String) = viewModelScope.launch { preferences.toggleReview(id) }
    fun hideFrequentSearch(id: String) = viewModelScope.launch { preferences.hideFrequentSearch(id) }
    fun hideFrequentWrong(id: String) = viewModelScope.launch { preferences.hideFrequentWrong(id) }
    fun recordSearchResult(id: String) = viewModelScope.launch { preferences.recordSearchResult(id) }
    fun recordSearchSubmit(query: String, resultCount: Int) = viewModelScope.launch {
        analytics.track("search_submit", mapOf("query" to query, "result_count" to resultCount))
    }
    fun recordSearchClick(id: String, rank: Int, type: String = "expression") = viewModelScope.launch {
        analytics.track("search_result_click", mapOf("content_id" to id, "rank" to rank, "result_type" to type))
    }
    fun recordSearchFeedback(query: String) = viewModelScope.launch {
        analytics.track("search_no_result_feedback", mapOf("query" to query))
    }
    fun recordAudioPlay(entry: String, id: String, success: Boolean) = viewModelScope.launch {
        analytics.track("audio_play", mapOf("entry" to entry, "content_id" to id, "success" to success))
    }
    fun recordReview(id: String, result: TravelReviewResult) = viewModelScope.launch {
        preferences.recordReview(id, result)
    }

    private fun expressions(ids: Set<String>) = TravelExpressionSearch.reviewedExpressions.filter { it.id in ids }
}

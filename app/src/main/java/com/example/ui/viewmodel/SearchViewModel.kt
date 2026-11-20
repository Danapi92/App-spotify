package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SearchHistoryDoc
import com.example.data.model.UiState
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.MusicCatalogRepository
import com.example.data.repository.SearchResultsBundle
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchFilter {
    ALL, SONGS, ARTISTS, ALBUMS, PLAYLISTS
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val currentUserId: String,
    private val catalogRepo: MusicCatalogRepository,
    private val userRepo: FirestoreUserRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedFilter = MutableStateFlow(SearchFilter.ALL)
    val selectedFilter: StateFlow<SearchFilter> = _selectedFilter.asStateFlow()

    private val _resultsState = MutableStateFlow<UiState<SearchResultsBundle>>(
        UiState.Success(SearchResultsBundle(emptyList(), emptyList(), emptyList(), emptyList()))
    )
    val resultsState: StateFlow<UiState<SearchResultsBundle>> = _resultsState.asStateFlow()

    val searchHistory: StateFlow<UiState<List<SearchHistoryDoc>>> = userRepo.observeSearchHistory(currentUserId)
        .map<List<SearchHistoryDoc>, UiState<List<SearchHistoryDoc>>> { UiState.Success(it) }
        .catch { err ->
            val firestoreError = generateSequence(err) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && err is CancellationException) throw err
            Log.w("SearchVM", "Error observing search history", firestoreError ?: err)
            emit(UiState.Error((firestoreError ?: err).message ?: "Failed to load history"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        viewModelScope.launch {
            _query
                .debounce(350L)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { term ->
                    if (term.isEmpty()) {
                        _resultsState.value = UiState.Success(
                            SearchResultsBundle(emptyList(), emptyList(), emptyList(), emptyList())
                        )
                    } else {
                        performSearch(term, saveHistory = false)
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onSearchSubmit() {
        val term = _query.value.trim()
        if (term.isNotEmpty()) {
            viewModelScope.launch {
                performSearch(term, saveHistory = true)
            }
        }
    }

    fun selectHistoryQuery(historyQuery: String) {
        _query.value = historyQuery
        viewModelScope.launch {
            performSearch(historyQuery, saveHistory = true)
        }
    }

    fun setFilter(filter: SearchFilter) {
        _selectedFilter.value = filter
    }

    fun deleteHistoryItem(entryId: String) {
        viewModelScope.launch {
            userRepo.deleteSearchQuery(currentUserId, entryId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            userRepo.clearSearchHistory(currentUserId)
        }
    }

    private suspend fun performSearch(term: String, saveHistory: Boolean) {
        _resultsState.value = UiState.Loading
        if (saveHistory) {
            userRepo.saveSearchQuery(currentUserId, term)
        }
        catalogRepo.searchAll(term)
            .onSuccess { bundle -> _resultsState.value = UiState.Success(bundle) }
            .onFailure { err ->
                _resultsState.value = UiState.Error(err.localizedMessage ?: "Search failed")
            }
    }
}

package com.bone.android.a4v.oficial.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.SourceType
import com.bone.android.a4v.oficial.data.repository.EventsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoading: Boolean = false,
    val currentSource: SourceType = SourceType.CAIDO,
    val allEvents: List<EventItem> = emptyList(),
    val filteredEvents: List<EventItem> = emptyList(),
    val searchQuery: String = "",
    val sportFilter: String = "",
    val lastUpdated: String = "",
    val errorMessage: String? = null
)

class MainViewModel(
    private val repository: EventsRepository = EventsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadEvents(SourceType.CAIDO)
    }

    fun selectSource(source: SourceType) {
        if (_uiState.value.currentSource == source && _uiState.value.allEvents.isNotEmpty()) return
        _uiState.update { it.copy(currentSource = source, sportFilter = "") }
        loadEvents(source)
    }

    fun refresh() {
        loadEvents(_uiState.value.currentSource, forceRefresh = true)
    }

    fun setSportFilter(sport: String) {
        _uiState.update { it.copy(sportFilter = sport) }
        applyFilters(_uiState.value.searchQuery, sport)
    }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150)
            _uiState.update { it.copy(searchQuery = query) }
            applyFilters(query, _uiState.value.sportFilter)
        }
    }

    private fun loadEvents(source: SourceType, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = repository.getEvents(source, forceRefresh)
            val currentDateTime = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val footerText = "Actualizado $currentDateTime\nZona: Madrid,Paris,Bruselas"

            result.fold(
                onSuccess = { events ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            allEvents = events,
                            lastUpdated = footerText,
                            filteredEvents = filterList(events, state.searchQuery, state.sportFilter)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Error al cargar la agenda"
                        )
                    }
                }
            )
        }
    }

    private fun applyFilters(query: String, sport: String) {
        _uiState.update { state ->
            state.copy(filteredEvents = filterList(state.allEvents, query, sport))
        }
    }

    private fun filterList(list: List<EventItem>, query: String, sport: String): List<EventItem> {
        var filtered = list

        if (sport.isNotBlank() && !sport.equals("TODOS", ignoreCase = true) && !sport.equals("UNFILTERED", ignoreCase = true)) {
            filtered = filtered.filter { it.sport.contains(sport, ignoreCase = true) }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            filtered = filtered.filter {
                it.title.lowercase().contains(q) ||
                it.sport.lowercase().contains(q) ||
                it.competition.lowercase().contains(q) ||
                it.channels.any { ch -> ch.name.lowercase().contains(q) }
            }
        }

        return filtered
    }
}

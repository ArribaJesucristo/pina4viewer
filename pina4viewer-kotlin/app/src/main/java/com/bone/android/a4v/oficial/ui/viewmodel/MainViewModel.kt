package com.bone.android.a4v.oficial.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.SourceType
import com.bone.android.a4v.oficial.data.parser.ArenaVisionParser
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
    val currentSource: SourceType = SourceType.SERVER_IN,
    val allEvents: List<EventItem> = emptyList(),
    val filteredEvents: List<EventItem> = emptyList(),
    val searchQuery: String = "",
    val sportFilter: String = "",
    val lastUpdated: String = "",
    val isOffMode: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = EventsRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        ArenaVisionParser.initDefaultAgenda(application.applicationContext)
        loadEvents(SourceType.SERVER_IN)
    }

    fun selectSource(source: SourceType) {
        if (_uiState.value.currentSource == source && _uiState.value.allEvents.isNotEmpty()) return

        val cached = repository.peekCachedEvents(source)
        if (!cached.isNullOrEmpty()) {
            val isOff = repository.isSourceOffMode(source)
            _uiState.update { state ->
                state.copy(
                    currentSource = source,
                    sportFilter = "",
                    isLoading = false,
                    isOffMode = isOff,
                    allEvents = cached,
                    filteredEvents = filterList(cached, state.searchQuery, "")
                )
            }
        } else {
            _uiState.update { it.copy(currentSource = source, sportFilter = "") }
        }
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
            val hasCached = repository.peekCachedEvents(source) != null
            if (!hasCached || forceRefresh) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            val result = repository.getEvents(source, forceRefresh)
            val currentDateTime = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val footerText = "Actualizado $currentDateTime\nZona: Madrid,Paris,Bruselas"
            val isOff = repository.isCurrentSourceOffMode

            result.fold(
                onSuccess = { events ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            allEvents = events,
                            lastUpdated = footerText,
                            isOffMode = isOff,
                            filteredEvents = filterList(events, state.searchQuery, state.sportFilter)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isOffMode = true,
                            errorMessage = error.localizedMessage ?: "Error al cargar la agenda"
                        )
                    }
                }
            )
        }
    }

    private fun applyFilters(query: String, sport: String) {
        val filtered = filterList(_uiState.value.allEvents, query, sport)
        _uiState.update { it.copy(filteredEvents = filtered) }
    }

    private fun filterList(
        events: List<EventItem>,
        query: String,
        sport: String
    ): List<EventItem> {
        val trimmedQuery = query.trim()
        val hasQuery = trimmedQuery.isNotEmpty()
        val hasSport = sport.isNotBlank()

        if (!hasQuery && !hasSport) return events

        return events.filter { item ->
            val matchSport = !hasSport || item.sport.contains(sport, ignoreCase = true)
            val matchQuery = !hasQuery ||
                item.title.contains(trimmedQuery, ignoreCase = true) ||
                item.competition.contains(trimmedQuery, ignoreCase = true) ||
                item.sport.contains(trimmedQuery, ignoreCase = true) ||
                item.channels.any { it.name.contains(trimmedQuery, ignoreCase = true) }

            matchSport && matchQuery
        }
    }
}

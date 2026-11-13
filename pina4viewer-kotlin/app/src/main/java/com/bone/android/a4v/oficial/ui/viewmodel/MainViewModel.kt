package com.bone.android.a4v.oficial.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.SourceType
import com.bone.android.a4v.oficial.data.parser.ArenaVisionParser
import com.bone.android.a4v.oficial.data.parser.PinaVisionParser
import com.bone.android.a4v.oficial.data.repository.EventsRepository
import com.bone.android.a4v.oficial.util.SportFilterMatcher
import com.bone.android.a4v.oficial.util.TimeZoneHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MainUiState(
    val isLoading: Boolean = false,
    val currentSource: SourceType = SourceType.OFF_MODE,
    val allEvents: List<EventItem> = emptyList(),
    val filteredEvents: List<EventItem> = emptyList(),
    val searchQuery: String = "",
    val sportFilter: String = "",
    val selectedTimezoneId: String = TimeZoneHelper.DEFAULT_TIMEZONE_ID,
    val lastUpdated: String = "",
    val isOffMode: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(TimeZoneHelper.PREFS_NAME, Context.MODE_PRIVATE)
    private val repository = EventsRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedTimezoneId = prefs.getString(
                TimeZoneHelper.KEY_TIMEZONE_ID,
                TimeZoneHelper.DEFAULT_TIMEZONE_ID
            ) ?: TimeZoneHelper.DEFAULT_TIMEZONE_ID
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var filterJob: Job? = null

    init {
        val savedTime = prefs.getString("workflow_updated_at", null)
        PinaVisionParser.init(savedTime)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ArenaVisionParser.initDefaultAgenda(application.applicationContext)
            }
            loadEvents(SourceType.OFF_MODE)
        }
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
                    allEvents = cached
                )
            }
            applyFiltersAsync()
        } else {
            _uiState.update {
                it.copy(
                    currentSource = source,
                    sportFilter = "",
                    isLoading = true,
                    allEvents = emptyList(),
                    filteredEvents = emptyList()
                )
            }
        }
        loadEvents(source)
    }

    fun refresh() {
        loadEvents(_uiState.value.currentSource, forceRefresh = true)
    }

    fun setSportFilter(sport: String) {
        _uiState.update { it.copy(sportFilter = sport) }
        applyFiltersAsync()
    }

    fun setTimezone(zoneId: String) {
        prefs.edit().putString(TimeZoneHelper.KEY_TIMEZONE_ID, zoneId).apply()
        _uiState.update { it.copy(selectedTimezoneId = zoneId) }
        applyFiltersAsync()
    }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150)
            _uiState.update { it.copy(searchQuery = query) }
            applyFiltersAsync()
        }
    }

    fun consumeErrorMessage() {
        if (_uiState.value.errorMessage != null) {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun loadEvents(source: SourceType, forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hasCached = repository.peekCachedEvents(source) != null
            if (!hasCached || forceRefresh) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            val result = repository.getEvents(source, forceRefresh)
            val workflowTime = PinaVisionParser.lastParsedUpdatedAt
            if (!workflowTime.isNullOrBlank()) {
                prefs.edit().putString("workflow_updated_at", workflowTime).apply()
            }
            val displayTime = workflowTime ?: SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val footerText = "Actualizado:\n$displayTime"
            val isOff = repository.isCurrentSourceOffMode

            result.fold(
                onSuccess = { events ->
                    val currentSnapshot = _uiState.value
                    val filtered = withContext(Dispatchers.Default) {
                        filterAndTransformEvents(
                            events = events,
                            query = currentSnapshot.searchQuery,
                            sport = currentSnapshot.sportFilter,
                            timezoneId = currentSnapshot.selectedTimezoneId,
                            isFileBasedSource = isFileBasedSource(source),
                            referenceUpdatedAt = workflowTime
                        )
                    }
                    _uiState.update { state ->
                        if (state.currentSource != source) return@update state
                        state.copy(
                            isLoading = false,
                            allEvents = events,
                            lastUpdated = footerText,
                            isOffMode = isOff,
                            filteredEvents = filtered
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        if (state.currentSource != source) return@update state
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

    fun reapplyFilters() {
        applyFiltersAsync()
    }

    private fun applyFiltersAsync() {
        filterJob?.cancel()
        val snapshot = _uiState.value
        val workflowTime = PinaVisionParser.lastParsedUpdatedAt
        filterJob = viewModelScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                filterAndTransformEvents(
                    events = snapshot.allEvents,
                    query = snapshot.searchQuery,
                    sport = snapshot.sportFilter,
                    timezoneId = snapshot.selectedTimezoneId,
                    isFileBasedSource = isFileBasedSource(snapshot.currentSource),
                    referenceUpdatedAt = workflowTime
                )
            }
            _uiState.update { current ->
                if (current.currentSource == snapshot.currentSource &&
                    current.searchQuery == snapshot.searchQuery &&
                    current.sportFilter == snapshot.sportFilter &&
                    current.selectedTimezoneId == snapshot.selectedTimezoneId &&
                    current.allEvents === snapshot.allEvents
                ) {
                    current.copy(filteredEvents = filtered)
                } else {
                    current
                }
            }
        }
    }

    companion object {
        private fun isFileBasedSource(source: SourceType): Boolean =
            source == SourceType.OFF_MODE || source == SourceType.SEARCH

        fun filterAndTransformEvents(
            events: List<EventItem>,
            query: String,
            sport: String,
            timezoneId: String = TimeZoneHelper.DEFAULT_TIMEZONE_ID,
            isFileBasedSource: Boolean = true,
            referenceUpdatedAt: String? = null,
            nowMillis: Long = System.currentTimeMillis()
        ): List<EventItem> {
            val trimmedQuery = query.trim()
            val hasQuery = trimmedQuery.isNotEmpty()
            val targetZone = TimeZoneHelper.resolveTargetTimeZone(timezoneId)
            val effectiveRefTime = if (isFileBasedSource) referenceUpdatedAt else null

            return events.mapNotNull { item ->
                val adjusted = TimeZoneHelper.adjustAndFilterEvent(
                    event = item,
                    targetZone = targetZone,
                    referenceUpdatedAt = effectiveRefTime,
                    nowMillis = nowMillis
                ) ?: return@mapNotNull null

                if (!SportFilterMatcher.matchesFilter(adjusted, sport)) {
                    return@mapNotNull null
                }

                if (hasQuery) {
                    val matchesQuery = adjusted.title.contains(trimmedQuery, ignoreCase = true) ||
                        adjusted.competition.contains(trimmedQuery, ignoreCase = true) ||
                        adjusted.sport.contains(trimmedQuery, ignoreCase = true) ||
                        adjusted.channels.any { it.name.contains(trimmedQuery, ignoreCase = true) }
                    if (!matchesQuery) return@mapNotNull null
                }

                adjusted
            }
        }
    }
}

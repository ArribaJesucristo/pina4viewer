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
    val currentSource: SourceType = SourceType.OFF_MODE,
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
        val prefs = application.getSharedPreferences("pina_prefs", android.content.Context.MODE_PRIVATE)
        val savedTime = prefs.getString("workflow_updated_at", null)
        com.bone.android.a4v.oficial.data.parser.PinaVisionParser.init(savedTime)

        ArenaVisionParser.initDefaultAgenda(application.applicationContext)
        loadEvents(SourceType.OFF_MODE)
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
            val workflowTime = com.bone.android.a4v.oficial.data.parser.PinaVisionParser.lastParsedUpdatedAt
            if (!workflowTime.isNullOrBlank()) {
                val prefs = getApplication<Application>().getSharedPreferences("pina_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("workflow_updated_at", workflowTime).apply()
            }
            val displayTime = workflowTime ?: java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val footerText = "Actualizado:\n$displayTime"
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

    fun reapplyFilters() {
        applyFilters(_uiState.value.searchQuery, _uiState.value.sportFilter)
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
        val hasSport = sport.isNotBlank() && !sport.equals("ALL", ignoreCase = true)

        // Dynamic 3-hour purge for today's events based on local time
        val tz = try {
            java.util.TimeZone.getTimeZone("Europe/Madrid")
        } catch (_: Exception) {
            java.util.TimeZone.getDefault()
        }
        val cal = java.util.Calendar.getInstance(tz)
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(java.util.Calendar.MINUTE)
        val nowMinutes = currentHour * 60 + currentMinute
        val cutoffMinutes = nowMinutes - 180 // 3 hours ago
        val todayStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).apply {
            timeZone = tz
        }.format(cal.time)

        return events.filter { item ->
            // Purge events from today that started more than 3 hours ago
            val isToday = item.date.equals("Hoy", ignoreCase = true) || item.date == todayStr
            if (isToday && cutoffMinutes > 0 && item.time.isNotBlank()) {
                val timeClean = item.time.replace("CET", "", ignoreCase = true).trim()
                val parts = timeClean.split(":")
                if (parts.size >= 2) {
                    val h = parts[0].trim().toIntOrNull()
                    val m = parts[1].trim().toIntOrNull()
                    if (h != null && m != null) {
                        val itemMinutes = h * 60 + m
                        if (itemMinutes < cutoffMinutes) {
                            return@filter false
                        }
                    }
                }
            }

            val s = item.sport.uppercase().trim()
            val matchSport = when {
                !hasSport -> true
                sport.equals("FUTBOL", ignoreCase = true) ->
                    s.contains("FUTBOL") || s.contains("SOCCER") || s.contains("FOOTBALL") || s.contains("FUTSAL")
                sport.equals("TENIS", ignoreCase = true) ->
                    s.contains("TENIS") || s.contains("TENNIS") || s.contains("PADEL") || s.contains("ATP") || s.contains("WTA")
                sport.equals("MOTOR", ignoreCase = true) ->
                    s.contains("MOTOR") || s.contains("F1") || s.contains("FORMULA") || s.contains("MOTO") || s.contains("MOTOGP") || s.contains("SUPERBIKE")
                sport.equals("BASKET", ignoreCase = true) || sport.equals("BALONCESTO", ignoreCase = true) ->
                    s.contains("BALONCESTO") || s.contains("BASKET") || s.contains("NBA")
                sport.equals("OTROS", ignoreCase = true) -> {
                    val isMainSport = s.contains("FUTBOL") || s.contains("SOCCER") || s.contains("FOOTBALL") || s.contains("FUTSAL") ||
                        s.contains("TENIS") || s.contains("TENNIS") || s.contains("PADEL") || s.contains("ATP") || s.contains("WTA") ||
                        s.contains("MOTOR") || s.contains("F1") || s.contains("FORMULA") || s.contains("MOTO") || s.contains("MOTOGP") || s.contains("SUPERBIKE") ||
                        s.contains("BALONCESTO") || s.contains("BASKET") || s.contains("NBA")
                    !isMainSport
                }
                else -> item.sport.contains(sport, ignoreCase = true)
            }

            val matchQuery = !hasQuery ||
                item.title.contains(trimmedQuery, ignoreCase = true) ||
                item.competition.contains(trimmedQuery, ignoreCase = true) ||
                item.sport.contains(trimmedQuery, ignoreCase = true) ||
                item.channels.any { it.name.contains(trimmedQuery, ignoreCase = true) }

            matchSport && matchQuery
        }
    }
}

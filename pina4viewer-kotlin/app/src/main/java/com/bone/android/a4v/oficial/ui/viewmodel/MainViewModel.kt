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
    val errorMessage: String? = null,
    val liveCount: Int = 0,
    val totalActiveCount: Int = 0,
    val chipCounts: Map<String, Int> = emptyMap()
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

            val result = repository.getEventsWithMeta(source, forceRefresh)

            result.fold(
                onSuccess = { fetch ->
                    val events = fetch.events
                    val effectiveUpdatedAt = fetch.updatedAt ?: PinaVisionParser.lastParsedUpdatedAt
                    if (!fetch.isOffMode && !fetch.updatedAt.isNullOrBlank()) {
                        prefs.edit().putString("workflow_updated_at", fetch.updatedAt).apply()
                    }
                    val displayTime = effectiveUpdatedAt
                        ?: SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    val footerText = "Actualizado:\n$displayTime"

                    val currentSnapshot = _uiState.value
                    val bundle = withContext(Dispatchers.Default) {
                        computeFilteredBundle(
                            events = events,
                            source = source,
                            query = currentSnapshot.searchQuery,
                            sport = currentSnapshot.sportFilter,
                            timezoneId = currentSnapshot.selectedTimezoneId,
                            referenceUpdatedAt = effectiveUpdatedAt
                        )
                    }
                    _uiState.update { state ->
                        if (state.currentSource != source) return@update state
                        state.copy(
                            isLoading = false,
                            allEvents = events,
                            lastUpdated = footerText,
                            isOffMode = fetch.isOffMode,
                            filteredEvents = bundle.filteredEvents,
                            liveCount = bundle.liveCount,
                            totalActiveCount = bundle.totalActiveCount,
                            chipCounts = bundle.chipCounts
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
            val bundle = withContext(Dispatchers.Default) {
                computeFilteredBundle(
                    events = snapshot.allEvents,
                    source = snapshot.currentSource,
                    query = snapshot.searchQuery,
                    sport = snapshot.sportFilter,
                    timezoneId = snapshot.selectedTimezoneId,
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
                    current.copy(
                        filteredEvents = bundle.filteredEvents,
                        liveCount = bundle.liveCount,
                        totalActiveCount = bundle.totalActiveCount,
                        chipCounts = bundle.chipCounts
                    )
                } else {
                    current
                }
            }
        }
    }

    private data class FilteredBundle(
        val filteredEvents: List<EventItem>,
        val liveCount: Int,
        val totalActiveCount: Int,
        val chipCounts: Map<String, Int>
    )

    companion object {
        private fun isFileBasedSource(source: SourceType): Boolean =
            source == SourceType.OFF_MODE || source == SourceType.SEARCH

        private fun computeFilteredBundle(
            events: List<EventItem>,
            source: SourceType,
            query: String,
            sport: String,
            timezoneId: String,
            referenceUpdatedAt: String?,
            nowMillis: Long = System.currentTimeMillis()
        ): FilteredBundle {
            val targetZone = TimeZoneHelper.resolveTargetTimeZone(timezoneId)
            val batchCtx = TimeZoneHelper.BatchContext(targetZone, nowMillis)
            val effectiveRefTime = if (isFileBasedSource(source)) referenceUpdatedAt else null
            val trimmedQuery = query.trim()
            val hasQuery = trimmedQuery.isNotEmpty()

            val timeAdjusted = events.mapNotNull { item ->
                TimeZoneHelper.adjustAndFilterEvent(item, batchCtx, effectiveRefTime)
            }

            val queryMatched = if (hasQuery) {
                timeAdjusted.filter { SportFilterMatcher.matchesSearch(it, trimmedQuery) }
            } else {
                timeAdjusted
            }

            val chipCounts = computeChipCounts(queryMatched, source)
            val liveCount = queryMatched.count {
                it.isLiveNow ||
                    it.time.equals("Directo", ignoreCase = true) ||
                    it.time.equals("En Directo", ignoreCase = true) ||
                    it.date.equals("24/7", ignoreCase = true)
            }

            val filtered = queryMatched
                .filter { SportFilterMatcher.matchesFilter(it, sport) }
                .sortedBy { it.sortOrderMillis }

            return FilteredBundle(
                filteredEvents = filtered,
                liveCount = liveCount,
                totalActiveCount = queryMatched.size,
                chipCounts = chipCounts
            )
        }

        fun computeChipCounts(
            timeAdjustedEvents: List<EventItem>,
            source: SourceType
        ): Map<String, Int> {
            val keys = if (source == SourceType.SEARCH) {
                listOf(
                    SportFilterMatcher.FILTER_ALL,
                    SportFilterMatcher.FILTER_TV_DEPORTES,
                    SportFilterMatcher.FILTER_TV_ARENAVISION,
                    SportFilterMatcher.FILTER_TV_TDT
                )
            } else {
                listOf(
                    SportFilterMatcher.FILTER_ALL,
                    SportFilterMatcher.FILTER_LIVE,
                    SportFilterMatcher.FILTER_FUTBOL,
                    SportFilterMatcher.FILTER_TENIS_PADEL,
                    SportFilterMatcher.FILTER_MOTOR,
                    SportFilterMatcher.FILTER_BASKET,
                    SportFilterMatcher.FILTER_OTROS
                )
            }
            val map = LinkedHashMap<String, Int>(keys.size)
            for (k in keys) {
                map[k] = if (k == SportFilterMatcher.FILTER_ALL) {
                    timeAdjustedEvents.size
                } else {
                    timeAdjustedEvents.count { SportFilterMatcher.matchesFilter(it, k) }
                }
            }
            return map
        }

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
            val batchCtx = TimeZoneHelper.BatchContext(targetZone, nowMillis)
            val effectiveRefTime = if (isFileBasedSource) referenceUpdatedAt else null

            return events.mapNotNull { item ->
                val adjusted = TimeZoneHelper.adjustAndFilterEvent(
                    event = item,
                    ctx = batchCtx,
                    referenceUpdatedAt = effectiveRefTime
                ) ?: return@mapNotNull null

                if (!SportFilterMatcher.matchesFilter(adjusted, sport)) {
                    return@mapNotNull null
                }

                if (hasQuery && !SportFilterMatcher.matchesSearch(adjusted, trimmedQuery)) {
                    return@mapNotNull null
                }

                adjusted
            }.sortedBy { it.sortOrderMillis }
        }
    }
}

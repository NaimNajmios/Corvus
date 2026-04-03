package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.data.util.RedundantQueryDetector
import com.najmi.corvus.domain.model.HarmLevel
import com.najmi.corvus.domain.model.HistorySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistorySort {
    NEWEST, OLDEST, HIGHEST_HARM
}

data class HistoryUiState(
    val history: List<HistorySummary> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedVerdictFilter: String? = null,
    val currentSort: HistorySort = HistorySort.NEWEST,
    val isAnalyticsVisible: Boolean = false,
    val verdictDistribution: Map<String, Float> = emptyMap(),
    val isDeleteMode: Boolean = false,
    val deleteSelection: Set<String> = emptySet(),
    val pendingDeleteIds: Set<String> = emptySet(),
    val error: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedVerdictFilter = MutableStateFlow<String?>(null)
    private val _currentSort = MutableStateFlow(HistorySort.NEWEST)
    private val _isAnalyticsVisible = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _pendingDeleteItems = MutableStateFlow<Map<String, HistorySummary>>(emptyMap())
    val pendingDeleteItems: StateFlow<Map<String, HistorySummary>> = _pendingDeleteItems.asStateFlow()

    private var undoJob: kotlinx.coroutines.Job? = null

    companion object {
        const val UNDO_TIMEOUT_MS = 5000L
    }

    init {
        observeHistory()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeHistory() {
        viewModelScope.launch {
            combine(
                _searchQuery,
                _selectedVerdictFilter,
                _currentSort,
                _isAnalyticsVisible
            ) { query, filter, sort, analyticsVisible ->
                HistoryParams(query, filter, sort, analyticsVisible)
            }.flatMapLatest { params ->
                _uiState.update { it.copy(
                    isLoading = true,
                    searchQuery = params.query,
                    selectedVerdictFilter = params.filter,
                    currentSort = params.sort,
                    isAnalyticsVisible = params.analyticsVisible
                ) }

                val flow = when {
                    params.query.isNotBlank() -> historyRepository.searchHistorySummaries(params.query)
                    params.filter != null -> historyRepository.filterByVerdictSummaries(params.filter)
                    else -> historyRepository.getAllHistorySummaries()
                }

                flow.map { items ->
                    val sortedItems = when (params.sort) {
                        HistorySort.NEWEST -> items.sortedByDescending { it.checkedAt }
                        HistorySort.OLDEST -> items.sortedBy { it.checkedAt }
                        HistorySort.HIGHEST_HARM -> items.sortedByDescending { it.harmScore() }
                    }
                    sortedItems to calculateStats(items)
                }
            }.collect { (items, stats) ->
                _uiState.update { it.copy(
                    history = items,
                    isLoading = false,
                    verdictDistribution = stats
                ) }
            }
        }
    }

    private data class HistoryParams(
        val query: String,
        val filter: String?,
        val sort: HistorySort,
        val analyticsVisible: Boolean
    )

    private fun HistorySummary.harmScore(): Int =
        HarmLevel.valueOf(harmLevel).ordinal

    private fun calculateStats(items: List<HistorySummary>): Map<String, Float> {
        if (items.isEmpty()) return emptyMap()
        val total = items.size.toFloat()
        return items.groupBy { it.verdict }.mapValues { it.value.size / total }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun filterByVerdict(verdict: String?) {
        _selectedVerdictFilter.value = verdict
    }

    fun toggleAnalytics() {
        _isAnalyticsVisible.value = !_isAnalyticsVisible.value
    }

    fun setSort(sort: HistorySort) {
        _currentSort.value = sort
    }

    fun prepareDelete(item: HistorySummary) {
        deleteItems(listOf(item))
    }

    fun deleteItems(ids: List<String>) {
        if (ids.isEmpty()) return

        val currentHistory = _uiState.value.history
        val itemsToDelete = currentHistory.filter { it.id in ids }.associateBy { it.id }

        if (itemsToDelete.isEmpty()) return

        _pendingDeleteItems.update { it + itemsToDelete }
        _uiState.update { state ->
            state.copy(
                pendingDeleteIds = state.pendingDeleteIds + ids.toSet(),
                isDeleteMode = false,
                deleteSelection = emptySet()
            )
        }

        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            kotlinx.coroutines.delay(UNDO_TIMEOUT_MS)
            confirmPendingDeletes()
        }

        viewModelScope.launch {
            historyRepository.deleteResults(ids)
        }
    }

    fun undoDelete() {
        undoJob?.cancel()
        _pendingDeleteItems.update { it - _uiState.value.pendingDeleteIds }
        _uiState.update { it.copy(pendingDeleteIds = emptySet()) }
    }

    fun confirmPendingDeletes() {
        _pendingDeleteItems.update { current ->
            current - _uiState.value.pendingDeleteIds
        }
        _uiState.update { it.copy(pendingDeleteIds = emptySet()) }
    }

    fun deleteItem(id: String) {
        deleteItems(listOf(id))
    }

    fun deleteSelected(ids: List<String>) {
        deleteItems(ids)
    }

    fun refresh() {
        _searchQuery.value = ""
        _selectedVerdictFilter.value = null
    }

    fun clearAll() {
        val allIds = _uiState.value.history.map { it.id }
        if (allIds.isEmpty()) return

        val allItems = _uiState.value.history.associateBy { it.id }
        _pendingDeleteItems.update { it + allItems }
        _uiState.update { it.copy(pendingDeleteIds = allIds.toSet()) }

        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            kotlinx.coroutines.delay(UNDO_TIMEOUT_MS)
            confirmPendingDeletes()
        }

        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }

    fun selectAllForDeletion() {
        _uiState.update { state ->
            state.copy(deleteSelection = state.history.map { it.id }.toSet())
        }
    }

    fun deselectAllForDeletion() {
        _uiState.update { it.copy(deleteSelection = emptySet()) }
    }

    fun enterDeleteMode() {
        _uiState.update { it.copy(isDeleteMode = true) }
    }

    fun exitDeleteMode() {
        _uiState.update { it.copy(isDeleteMode = false, deleteSelection = emptySet()) }
    }

    fun deleteSelectedItems() {
        val ids = _uiState.value.deleteSelection.toList()
        deleteItems(ids)
    }

    fun toggleDeleteSelection(itemId: String) {
        _uiState.update { state ->
            val newSelection = if (itemId in state.deleteSelection) {
                state.deleteSelection - itemId
            } else {
                state.deleteSelection + itemId
            }
            state.copy(deleteSelection = newSelection)
        }
    }

    fun getRedundantCount(): Int {
        val items = _uiState.value.history
        if (items.isEmpty()) return 0
        val groups = RedundantQueryDetector.findRedundantQueries(items)
        return RedundantQueryDetector.getTotalRedundantCount(groups)
    }

    fun deleteRedundantExceptLatest() {
        val items = _uiState.value.history
        if (items.isEmpty()) return

        val groups = RedundantQueryDetector.findRedundantQueries(items)
        val idsToDelete = RedundantQueryDetector.getAllRedundantItems(groups).map { it.id }

        if (idsToDelete.isEmpty()) return
        deleteItems(idsToDelete)
    }

    fun hasPendingDeletes(): Boolean = _uiState.value.pendingDeleteIds.isNotEmpty()

    fun getPendingDeleteCount(): Int = _uiState.value.pendingDeleteIds.size
}

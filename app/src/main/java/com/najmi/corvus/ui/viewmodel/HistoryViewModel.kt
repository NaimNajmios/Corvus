package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.data.util.RedundantQueryDetector
import com.najmi.corvus.domain.model.HistorySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    companion object {
        private const val UNDO_TIMEOUT_MS = 5000L
    }

    private val _searchQuery = MutableStateFlow("")
    private val _selectedVerdictFilter = MutableStateFlow<String?>(null)
    private val _currentSort = MutableStateFlow(HistorySort.NEWEST)
    private val _isAnalyticsVisible = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _pendingDeleteItems = MutableStateFlow<Map<String, HistorySummary>>(emptyMap())
    val pendingDeleteItems: StateFlow<Map<String, HistorySummary>> = _pendingDeleteItems.asStateFlow()

    private var undoJob: Job? = null

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
                    verdictDistribution = stats,
                    pendingDeleteIds = _pendingDeleteItems.value.keys
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
        com.najmi.corvus.domain.model.HarmLevel.valueOf(harmLevel).ordinal

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

    fun refresh() {
        _searchQuery.value = ""
        _selectedVerdictFilter.value = null
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAll()
            _pendingDeleteItems.value = emptyMap()
            cancelPendingUndo()
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

    fun prepareDelete(item: HistorySummary) {
        prepareDeleteItems(listOf(item))
    }

    fun prepareDeleteItems(items: List<HistorySummary>) {
        if (items.isEmpty()) return

        cancelPendingUndo()

        val newPending = items.associateBy { it.id }
        _pendingDeleteItems.value = newPending
        _uiState.update { it.copy(pendingDeleteIds = newPending.keys) }

        undoJob = viewModelScope.launch {
            delay(UNDO_TIMEOUT_MS)
            confirmDeleteInternal()
        }
    }

    fun undoDelete() {
        _pendingDeleteItems.value = emptyMap()
        _uiState.update { it.copy(pendingDeleteIds = emptySet()) }
        cancelPendingUndo()
    }

    fun confirmDelete() {
        confirmDeleteInternal()
    }

    private fun confirmDeleteInternal() {
        val itemsToDelete = _pendingDeleteItems.value
        if (itemsToDelete.isEmpty()) return

        cancelPendingUndo()
        _pendingDeleteItems.value = emptyMap()
        _uiState.update { it.copy(pendingDeleteIds = emptySet()) }

        viewModelScope.launch {
            historyRepository.deleteResults(itemsToDelete.keys.toList())
        }
    }

    fun deleteItem(id: String) {
        val item = _uiState.value.history.find { it.id == id } ?: return
        prepareDeleteItems(listOf(item))
    }

    fun deleteItems(ids: List<String>) {
        val items = _uiState.value.history.filter { it.id in ids }
        prepareDeleteItems(items)
    }

    fun deleteSelectedItems() {
        val ids = _uiState.value.deleteSelection.toList()
        deleteItems(ids)
        _uiState.update { it.copy(isDeleteMode = false, deleteSelection = emptySet()) }
    }

    fun deleteSelected(ids: List<String>) {
        deleteItems(ids)
    }

    fun getRedundantCount(): Int {
        val items = _uiState.value.history
        if (items.isEmpty()) return 0
        val groups = RedundantQueryDetector.findRedundantQueries(items)
        return RedundantQueryDetector.getTotalRedundantCount(groups)
    }

    fun deleteRedundantExceptLatest() {
        viewModelScope.launch {
            val items = _uiState.value.history
            if (items.isEmpty()) return@launch
            
            val groups = RedundantQueryDetector.findRedundantQueries(items)
            val idsToDelete = RedundantQueryDetector.getAllRedundantItems(groups).map { it.id }
            
            if (idsToDelete.isNotEmpty()) {
                deleteItems(idsToDelete)
            }
        }
    }

    fun getPendingDeleteCount(): Int = _pendingDeleteItems.value.size

    fun isPendingDelete(itemId: String): Boolean = itemId in _pendingDeleteItems.value

    private fun cancelPendingUndo() {
        undoJob?.cancel()
        undoJob = null
    }
}

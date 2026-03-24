package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.CorvusCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistorySort {
    NEWEST, OLDEST, HIGHEST_HARM
}

data class HistoryUiState(
    val history: List<CorvusCheckResult> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedVerdictFilter: String? = null,
    val currentSort: HistorySort = HistorySort.NEWEST,
    val isAnalyticsVisible: Boolean = false,
    val verdictDistribution: Map<String, Float> = emptyMap(),
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

    private var pendingDeleteItem: CorvusCheckResult? = null

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
                    params.query.isNotBlank() -> historyRepository.searchHistory(params.query)
                    params.filter != null -> historyRepository.filterByVerdict(params.filter)
                    else -> historyRepository.getAllHistory()
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

    private fun CorvusCheckResult.harmScore(): Int = when (this) {
        is CorvusCheckResult.GeneralResult -> harmAssessment.level.ordinal
        is CorvusCheckResult.QuoteResult -> harmAssessment.level.ordinal
        is CorvusCheckResult.CompositeResult -> 0
        is CorvusCheckResult.ViralHoaxResult -> 3
    }

    private fun calculateStats(items: List<CorvusCheckResult>): Map<String, Float> {
        if (items.isEmpty()) return emptyMap()
        val total = items.size.toFloat()
        return items.groupBy { 
            when (it) {
                is CorvusCheckResult.GeneralResult -> it.verdict.name
                is CorvusCheckResult.QuoteResult -> it.quoteVerdict.name
                is CorvusCheckResult.CompositeResult -> it.compositeVerdict.name
                is CorvusCheckResult.ViralHoaxResult -> "FALSE"
            }
        }.mapValues { it.value.size / total }
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

    fun prepareDelete(item: CorvusCheckResult) {
        pendingDeleteItem = item
    }

    fun undoDelete() {
        pendingDeleteItem = null
    }

    fun confirmDelete() {
        pendingDeleteItem?.let { item ->
            viewModelScope.launch {
                historyRepository.deleteResult(item.id)
            }
        }
        pendingDeleteItem = null
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            historyRepository.deleteResult(id)
        }
    }

    fun deleteSelected(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { id ->
                historyRepository.deleteResult(id)
            }
        }
    }

    fun refresh() {
        _searchQuery.value = ""
        _selectedVerdictFilter.value = null
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }
}

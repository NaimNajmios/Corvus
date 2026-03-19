package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.CorvusCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val history: List<CorvusCheckResult> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedVerdictFilter: String? = null,
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
    private val _isAnalyticsVisible = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var pendingDeleteItem: CorvusCheckResult? = null

    init {
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            combine(
                _searchQuery,
                _selectedVerdictFilter,
                _isAnalyticsVisible
            ) { query, filter, analyticsVisible ->
                Triple(query, filter, analyticsVisible)
            }.collect { (query, filter, analyticsVisible) ->
                _uiState.update { it.copy(isLoading = true, searchQuery = query, selectedVerdictFilter = filter) }
                
                val flow = when {
                    query.isNotBlank() -> historyRepository.searchHistory(query)
                    filter != null -> historyRepository.filterByVerdict(filter)
                    else -> historyRepository.getAllHistory()
                }

                flow.collect { items ->
                    val stats = calculateStats(items)
                    _uiState.update { it.copy(
                        history = items, 
                        isLoading = false,
                        verdictDistribution = stats
                    ) }
                }
            }
        }
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


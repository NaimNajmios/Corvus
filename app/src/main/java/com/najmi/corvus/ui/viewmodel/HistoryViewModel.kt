package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.CorvusResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val history: List<CorvusResult> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedVerdictFilter: String? = null,
    val error: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var pendingDeleteItem: CorvusResult? = null

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                historyRepository.getAllHistory().collect { items ->
                    _uiState.update { it.copy(history = items, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            if (query.isBlank()) {
                loadHistory()
            } else {
                historyRepository.searchHistory(query).collect { items ->
                    _uiState.update { it.copy(history = items) }
                }
            }
        }
    }

    fun filterByVerdict(verdict: String?) {
        _uiState.update { it.copy(selectedVerdictFilter = verdict) }
        viewModelScope.launch {
            if (verdict == null) {
                loadHistory()
            } else {
                historyRepository.filterByVerdict(verdict).collect { items ->
                    _uiState.update { it.copy(history = items) }
                }
            }
        }
    }

    fun prepareDelete(item: CorvusResult) {
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

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }
}

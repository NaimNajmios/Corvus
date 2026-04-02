package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.data.repository.CompareRepository
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.CorvusCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompareUiState(
    val selectedClaims: List<CorvusCheckResult> = emptyList(),
    val isCompareMode: Boolean = false,
    val canAddMore: Boolean = true
)

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val compareRepository: CompareRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            compareRepository.selectedClaims.collect { claims ->
                _uiState.update {
                    it.copy(
                        selectedClaims = claims,
                        isCompareMode = claims.isNotEmpty(),
                        canAddMore = claims.size < CompareRepository.MAX_COMPARE_ITEMS
                    )
                }
            }
        }
    }
    
    fun isSelected(claimId: String): Boolean {
        return compareRepository.isSelected(claimId)
    }
    
    fun toggleSelection(summary: com.najmi.corvus.domain.model.HistorySummary) {
        viewModelScope.launch {
            try {
                val fullResult = historyRepository.getResultById(summary.id)
                if (fullResult != null) {
                    compareRepository.toggleSelection(fullResult)
                }
            } catch (e: Exception) {
                // Handle selection error gracefully
            }
        }
    }
    
    fun removeClaim(claimId: String) {
        compareRepository.removeClaim(claimId)
    }
    
    fun clearSelection() {
        compareRepository.clearSelection()
    }
    
    fun getSelectionCount(): Int {
        return compareRepository.getSelectionCount()
    }
    
    fun canAddMore(): Boolean {
        return compareRepository.getSelectionCount() < CompareRepository.MAX_COMPARE_ITEMS
    }
}

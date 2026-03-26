package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.data.repository.TokenUsageRepository
import com.najmi.corvus.domain.model.CheckTokenReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TokenUsageUiState(
    val totalTokens: Int = 0,
    val totalEstimatedCost: Double = 0.0,
    val reports: List<CheckTokenReport> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TokenUsageViewModel @Inject constructor(
    private val repository: TokenUsageRepository
) : ViewModel() {

    val uiState: StateFlow<TokenUsageUiState> = repository.getAllReports()
        .map { reports ->
            TokenUsageUiState(
                totalTokens = reports.sumOf { it.totalCombined },
                totalEstimatedCost = calculateCost(reports),
                reports = reports,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TokenUsageUiState()
        )

    private fun calculateCost(reports: List<CheckTokenReport>): Double {
        // Very rough estimation: $0.15 per 1M tokens avg across providers
        return (reports.sumOf { it.totalCombined }.toDouble() / 1_000_000.0) * 0.15
    }
}

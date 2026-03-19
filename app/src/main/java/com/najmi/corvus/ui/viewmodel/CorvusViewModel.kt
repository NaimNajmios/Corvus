package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.CorvusUiState
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.domain.usecase.CorvusFactCheckUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CorvusViewModel @Inject constructor(
    private val factCheckUseCase: CorvusFactCheckUseCase,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CorvusUiState())
    val uiState: StateFlow<CorvusUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    fun updateInputText(text: String) {
        if (text.length <= 500) {
            _uiState.update { it.copy(inputText = text, error = null) }
        }
    }

    fun analyze() {
        val claim = _uiState.value.inputText.trim()
        if (claim.isEmpty()) {
            _uiState.update { it.copy(error = "Please enter a claim to analyze") }
            return
        }
        if (claim.length < 10) {
            _uiState.update { it.copy(error = "Claim is too short to analyze") }
            return
        }

        analysisJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, result = null) }

            try {
                val result = factCheckUseCase.check(claim) { step ->
                    _uiState.update { it.copy(currentStep = step) }
                }
                _uiState.update { it.copy(result = result, isLoading = false) }
                
                historyRepository.saveResult(result)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    _uiState.update { CorvusUiState() }
                } else {
                    _uiState.update {
                        it.copy(
                            error = "Analysis failed: ${e.message}",
                            isLoading = false,
                            currentStep = PipelineStep.IDLE
                        )
                    }
                }
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.update { CorvusUiState() }
    }

    fun reset() {
        analysisJob?.cancel()
        _uiState.update { CorvusUiState() }
    }
}

private class CancellationException(message: String = "Analysis cancelled") : Exception(message)

package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.CorvusUiState
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.worker.FactCheckWorker
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
    private val historyRepository: HistoryRepository,
    private val workManager: WorkManager
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

        val workRequest = OneTimeWorkRequestBuilder<FactCheckWorker>()
            .setInputData(workDataOf("inputText" to claim))
            .build()

        workManager.enqueue(workRequest)

        analysisJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            val stepName = workInfo.progress.getString("step")
                            val step = stepName?.let { PipelineStep.valueOf(it) } ?: PipelineStep.IDLE
                            _uiState.update { it.copy(
                                isLoading = true, 
                                currentStep = step, 
                                error = null,
                                isEntityContextLoading = step != PipelineStep.DONE
                            ) }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            _uiState.update { it.copy(
                                isLoading = false, 
                                currentStep = PipelineStep.DONE,
                                isEntityContextLoading = false
                            ) }
                            // We might want to fetch the result from repository here if needed for UI
                            // but usually the result screen fetch it from state.
                            // For now, we rely on the background save.
                            loadLastResult()
                        }
                        WorkInfo.State.FAILED -> {
                            val error = workInfo.outputData.getString("error") ?: "Analysis failed"
                            _uiState.update { it.copy(isLoading = false, error = error) }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun loadLastResult() {
        viewModelScope.launch {
            historyRepository.getAllHistory().collect { history ->
                if (history.isNotEmpty()) {
                    _uiState.update { it.copy(result = history.first()) }
                }
            }
        }
    }

    fun loadResultById(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = historyRepository.getResultById(id)
            _uiState.update { it.copy(result = result, isLoading = false) }
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

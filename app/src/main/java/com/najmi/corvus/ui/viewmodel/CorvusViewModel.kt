package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.najmi.corvus.data.repository.BookmarkRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.work.ExistingWorkPolicy
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CorvusViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val workManager: WorkManager
) : ViewModel() {

    companion object {
        const val MAX_CLAIM_LENGTH = 500
        const val MIN_CLAIM_LENGTH = 10
    }

    private val _uiState = MutableStateFlow(CorvusUiState())
    val uiState: StateFlow<CorvusUiState> = _uiState.asStateFlow()

    init {
        observeFactCheckWork()
    }

    private fun observeFactCheckWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow("FactCheckWork").collect { workInfos ->
                val workInfo = workInfos.firstOrNull() ?: return@collect
                
                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
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
                        if (_uiState.value.isLoading) {
                            _uiState.update { it.copy(
                                isLoading = false, 
                                currentStep = PipelineStep.DONE,
                                isEntityContextLoading = false
                            ) }
                            loadLastResult()
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        if (_uiState.value.isLoading) {
                            val error = workInfo.outputData.getString("error") ?: "Analysis failed"
                            _uiState.update { it.copy(isLoading = false, error = error) }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun updateInputText(text: String) {
        if (text.length <= MAX_CLAIM_LENGTH) {
            _uiState.update { it.copy(inputText = text, error = null) }
        }
    }

    fun analyze() {
        val claim = _uiState.value.inputText.trim()
        if (claim.isEmpty()) {
            _uiState.update { it.copy(error = "Please enter a claim to analyze") }
            return
        }
        if (claim.length < MIN_CLAIM_LENGTH) {
            _uiState.update { it.copy(error = "Claim is too short to analyze") }
            return
        }

        // Cancel previous analysis if any
        cancelAnalysis()

        val workRequest = OneTimeWorkRequestBuilder<FactCheckWorker>()
            .setInputData(workDataOf("inputText" to claim))
            .build()
        
        workManager.enqueueUniqueWork("FactCheckWork", ExistingWorkPolicy.REPLACE, workRequest)
    }

    private fun loadLastResult() {
        viewModelScope.launch {
            val result = historyRepository.getLatestResult()
            if (result != null) {
                _uiState.update { it.copy(result = result) }
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
        workManager.cancelUniqueWork("FactCheckWork")
        _uiState.update { it.copy(isLoading = false) }
    }

    fun analyzeInBackground(text: String, onStarted: () -> Unit = {}) {
        val claim = text.trim()
        if (claim.isEmpty() || claim.length < MIN_CLAIM_LENGTH) {
            return
        }

        val workRequest = OneTimeWorkRequestBuilder<FactCheckWorker>()
            .setInputData(workDataOf("inputText" to claim))
            .build()
        
        workManager.enqueueUniqueWork("FactCheckWork", ExistingWorkPolicy.REPLACE, workRequest)
        
        _uiState.update { it.copy(inputText = claim) }
        onStarted()
    }

    fun reset() {
        cancelAnalysis()
        _uiState.update { CorvusUiState() }
    }

    fun requestHumanReview() {
        _uiState.update { it.copy(
            humanReviewRequested = true,
            showHumanReviewScreen = true
        ) }
    }

    fun dismissHumanReviewScreen() {
        _uiState.update { it.copy(showHumanReviewScreen = false) }
    }

    fun notifyWhenComplete() {
        _uiState.update { it.copy(notificationRequested = true) }
    }

    fun retryVerification() {
        val claim = _uiState.value.inputText
        if (claim.isNotBlank() && claim.length >= MIN_CLAIM_LENGTH) {
            analyze()
        }
    }

    fun addBookmark(notes: String = "", tags: String = "") {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            bookmarkRepository.addBookmark(result, notes, tags)
        }
    }

    suspend fun isCurrentResultBookmarked(): Boolean {
        val result = _uiState.value.result ?: return false
        return bookmarkRepository.isBookmarked(result.id)
    }
}

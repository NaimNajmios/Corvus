package com.najmi.corvus.domain.model

data class CorvusUiState(
    val inputText: String = "",
    val isLoading: Boolean = false,
    val result: CorvusResult? = null,
    val error: String? = null,
    val currentStep: PipelineStep = PipelineStep.IDLE
)

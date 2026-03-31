package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.BuildConfig
import com.najmi.corvus.domain.usecase.CohereQuotaGuard
import com.najmi.corvus.domain.usecase.GeminiQuotaGuard
import com.najmi.corvus.domain.usecase.GroqQuotaGuard
import com.najmi.corvus.domain.usecase.MistralQuotaGuard
import com.najmi.corvus.domain.usecase.OpenRouterQuotaGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsageViewModel @Inject constructor(
    private val cohereQuotaGuard: CohereQuotaGuard,
    private val openRouterQuotaGuard: OpenRouterQuotaGuard,
    private val mistralQuotaGuard: MistralQuotaGuard,
    private val geminiQuotaGuard: GeminiQuotaGuard,
    private val groqQuotaGuard: GroqQuotaGuard
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsageUiState())
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    init {
        loadAllQuotas()
    }

    private fun loadAllQuotas() {
        viewModelScope.launch {
            val quotas = mutableListOf<ApiQuotaInfo>()

            val hasCohereKey = BuildConfig.COHERE_API_KEY.isNotBlank()
            if (hasCohereKey) {
                quotas.add(
                    ApiQuotaInfo(
                        providerName = "Cohere",
                        modelName = "Command-R",
                        dailyCallsUsed = cohereQuotaGuard.dailyCallsR(),
                        dailyLimit = CohereQuotaGuard.DAILY_LIMIT_COMMAND_R,
                        monthlyCallsUsed = cohereQuotaGuard.monthlyCallsUsed(),
                        monthlyLimit = 950,
                        hasApiKey = true
                    )
                )
                quotas.add(
                    ApiQuotaInfo(
                        providerName = "Cohere",
                        modelName = "Command-R+",
                        dailyCallsUsed = cohereQuotaGuard.dailyCallsRPlus(),
                        dailyLimit = CohereQuotaGuard.DAILY_LIMIT_COMMAND_R_PLUS,
                        monthlyCallsUsed = cohereQuotaGuard.monthlyCallsUsed(),
                        monthlyLimit = 950,
                        hasApiKey = true
                    )
                )
            }

            val hasOpenRouterKey = BuildConfig.OPENROUTER_API_KEY.isNotBlank()
            if (hasOpenRouterKey) {
                quotas.add(
                    ApiQuotaInfo(
                        providerName = "OpenRouter",
                        modelName = "qwen/qwen3.6-plus-preview",
                        dailyCallsUsed = openRouterQuotaGuard.callsToday(),
                        dailyLimit = OpenRouterQuotaGuard.DAILY_LIMIT,
                        monthlyCallsUsed = openRouterQuotaGuard.monthlyCallsUsed(),
                        monthlyLimit = OpenRouterQuotaGuard.MONTHLY_LIMIT,
                        hasApiKey = true
                    )
                )
            }

            val hasMistralKey = BuildConfig.MISTRAL_API_KEY.isNotBlank()
            if (hasMistralKey) {
                quotas.add(
                    ApiQuotaInfo(
                        providerName = "Mistral SABA",
                        modelName = "mistral-saba-latest",
                        dailyCallsUsed = mistralQuotaGuard.callsToday(),
                        dailyLimit = MistralQuotaGuard.DAILY_LIMIT,
                        monthlyCallsUsed = mistralQuotaGuard.monthlyCallsUsed(),
                        monthlyLimit = MistralQuotaGuard.MONTHLY_LIMIT,
                        hasApiKey = true
                    )
                )
                quotas.add(
                    ApiQuotaInfo(
                        providerName = "Mistral Small",
                        modelName = "mistral-small-latest",
                        dailyCallsUsed = mistralQuotaGuard.callsToday(),
                        dailyLimit = MistralQuotaGuard.DAILY_LIMIT,
                        monthlyCallsUsed = mistralQuotaGuard.monthlyCallsUsed(),
                        monthlyLimit = MistralQuotaGuard.MONTHLY_LIMIT,
                        hasApiKey = true
                    )
                )
            }

            val hasGeminiKey = BuildConfig.GEMINI_API_KEY.isNotBlank()
            if (hasGeminiKey) {
                quotas.add(
                    ApiQuotaInfo(
                        providerName = "Gemini",
                        modelName = "gemini-2.0-flash",
                        dailyCallsUsed = geminiQuotaGuard.callsToday(),
                        dailyLimit = GeminiQuotaGuard.DAILY_LIMIT,
                        monthlyCallsUsed = geminiQuotaGuard.monthlyCallsUsed(),
                        monthlyLimit = GeminiQuotaGuard.MONTHLY_LIMIT,
                        hasApiKey = true
                    )
                )
            }

            val hasGroqKey = BuildConfig.GROQ_API_KEY.isNotBlank()
            if (hasGroqKey) {
                quotas.add(
                    ApiQuotaInfo(
                        providerName = "Groq",
                        modelName = "llama-3.3-70b-versatile",
                        dailyCallsUsed = groqQuotaGuard.callsToday(),
                        dailyLimit = GroqQuotaGuard.DAILY_LIMIT,
                        monthlyCallsUsed = groqQuotaGuard.monthlyCallsUsed(),
                        monthlyLimit = GroqQuotaGuard.MONTHLY_LIMIT,
                        hasApiKey = true
                    )
                )
            }

            _uiState.update { it.copy(apiQuotas = quotas) }
        }
    }

    fun refreshQuotas() {
        loadAllQuotas()
    }
}

data class UsageUiState(
    val apiQuotas: List<ApiQuotaInfo> = emptyList()
)

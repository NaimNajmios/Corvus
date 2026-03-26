package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.BuildConfig
import com.najmi.corvus.data.local.UserPreferences
import com.najmi.corvus.data.local.UserPreferencesRepository
import com.najmi.corvus.ui.theme.ColorPalette
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.LlmProvider
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

data class CohereQuotaInfo(
    val dailyCallsR: Int = 0,
    val dailyCallsRPlus: Int = 0,
    val monthlyCalls: Int = 0,
    val dailyLimitR: Int = 28,
    val dailyLimitRPlus: Int = 8,
    val monthlyLimit: Int = 950
)

data class ApiQuotaInfo(
    val providerName: String,
    val modelName: String,
    val dailyCallsUsed: Int,
    val dailyLimit: Int,
    val monthlyCallsUsed: Int?,
    val monthlyLimit: Int?,
    val hasApiKey: Boolean
)

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val historyCount: Int = 0,
    val isLoading: Boolean = false,
    val cohereQuota: CohereQuotaInfo = CohereQuotaInfo(),
    val apiQuotas: List<ApiQuotaInfo> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val historyRepository: HistoryRepository,
    private val cohereQuotaGuard: CohereQuotaGuard,
    private val openRouterQuotaGuard: OpenRouterQuotaGuard,
    private val mistralQuotaGuard: MistralQuotaGuard,
    private val geminiQuotaGuard: GeminiQuotaGuard,
    private val groqQuotaGuard: GroqQuotaGuard
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        loadHistoryCount()
        loadCohereQuota()
        loadAllQuotas()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
    }

    private fun loadHistoryCount() {
        viewModelScope.launch {
            val count = historyRepository.getCount()
            _uiState.update { it.copy(historyCount = count) }
        }
    }

    private fun loadCohereQuota() {
        viewModelScope.launch {
            val dailyR = cohereQuotaGuard.dailyCallsR()
            val dailyRPlus = cohereQuotaGuard.dailyCallsRPlus()
            val monthly = cohereQuotaGuard.monthlyCallsUsed()
            _uiState.update {
                it.copy(
                    cohereQuota = CohereQuotaInfo(
                        dailyCallsR = dailyR,
                        dailyCallsRPlus = dailyRPlus,
                        monthlyCalls = monthly
                    )
                )
            }
        }
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
                        modelName = "gemini-2.0-flash-exp:free",
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
                        providerName = "Mistral",
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

    fun setPreferredProvider(provider: LlmProvider) {
        viewModelScope.launch {
            userPreferencesRepository.setPreferredProvider(provider)
        }
    }

    fun setResponseLanguage(language: String) {
        viewModelScope.launch {
            userPreferencesRepository.setResponseLanguage(language)
        }
    }

    fun setDarkMode(enabled: Boolean?) {
        viewModelScope.launch {
            userPreferencesRepository.setDarkMode(enabled)
        }
    }

    fun setShowAnimations(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowAnimations(show)
        }
    }

    fun setColorPalette(palette: ColorPalette) {
        viewModelScope.launch {
            userPreferencesRepository.setColorPalette(palette)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
            loadHistoryCount()
        }
    }

    fun refreshQuotas() {
        loadCohereQuota()
        loadAllQuotas()
    }
}

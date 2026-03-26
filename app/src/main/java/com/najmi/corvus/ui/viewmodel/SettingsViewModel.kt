package com.najmi.corvus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmi.corvus.data.local.UserPreferences
import com.najmi.corvus.data.local.UserPreferencesRepository
import com.najmi.corvus.ui.theme.ColorPalette
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.LlmProvider
import com.najmi.corvus.domain.usecase.CohereQuotaGuard
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

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val historyCount: Int = 0,
    val isLoading: Boolean = false,
    val cohereQuota: CohereQuotaInfo = CohereQuotaInfo()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val historyRepository: HistoryRepository,
    private val cohereQuotaGuard: CohereQuotaGuard
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        loadHistoryCount()
        loadCohereQuota()
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
}
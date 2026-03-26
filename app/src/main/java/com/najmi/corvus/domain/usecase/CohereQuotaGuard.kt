package com.najmi.corvus.domain.usecase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cohereDataStore: DataStore<Preferences> by preferencesDataStore(name = "cohere_quota")

@Singleton
class CohereQuotaGuard @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val DAILY_LIMIT_COMMAND_R = 28
        const val DAILY_LIMIT_COMMAND_R_PLUS = 8
        private const val MONTHLY_HARD_STOP = 950

        private val KEY_R_CALLS_TODAY = intPreferencesKey("cohere_r_calls_today")
        private val KEY_R_PLUS_CALLS_TODAY = intPreferencesKey("cohere_r_plus_calls_today")
        private val KEY_RESET_DATE = longPreferencesKey("cohere_quota_reset_date")
        private val KEY_MONTHLY_CALLS = intPreferencesKey("cohere_monthly_calls")
        private val KEY_MONTHLY_RESET = longPreferencesKey("cohere_monthly_reset")
    }

    suspend fun canCall(model: String): Boolean {
        resetIfNewDay()
        resetIfNewMonth()

        val prefs = context.cohereDataStore.data.first()
        val monthlyTotal = prefs[KEY_MONTHLY_CALLS] ?: 0

        if (monthlyTotal >= MONTHLY_HARD_STOP) return false

        return when {
            model.contains("plus", ignoreCase = true) -> 
                (prefs[KEY_R_PLUS_CALLS_TODAY] ?: 0) < DAILY_LIMIT_COMMAND_R_PLUS
            else -> 
                (prefs[KEY_R_CALLS_TODAY] ?: 0) < DAILY_LIMIT_COMMAND_R
        }
    }

    suspend fun recordCall(model: String) {
        context.cohereDataStore.edit { prefs ->
            if (model.contains("plus", ignoreCase = true)) {
                prefs[KEY_R_PLUS_CALLS_TODAY] = (prefs[KEY_R_PLUS_CALLS_TODAY] ?: 0) + 1
            } else {
                prefs[KEY_R_CALLS_TODAY] = (prefs[KEY_R_CALLS_TODAY] ?: 0) + 1
            }
            prefs[KEY_MONTHLY_CALLS] = (prefs[KEY_MONTHLY_CALLS] ?: 0) + 1
        }
    }

    suspend fun callsToday(): Int {
        val prefs = context.cohereDataStore.data.first()
        return (prefs[KEY_R_CALLS_TODAY] ?: 0) + (prefs[KEY_R_PLUS_CALLS_TODAY] ?: 0)
    }

    suspend fun monthlyCallsUsed(): Int =
        context.cohereDataStore.data.first()[KEY_MONTHLY_CALLS] ?: 0

    suspend fun monthlyCallsRemaining(): Int =
        (1000 - monthlyCallsUsed()).coerceAtLeast(0)

    suspend fun dailyCallsR(): Int {
        val prefs = context.cohereDataStore.data.first()
        return prefs[KEY_R_CALLS_TODAY] ?: 0
    }

    suspend fun dailyCallsRPlus(): Int {
        val prefs = context.cohereDataStore.data.first()
        return prefs[KEY_R_PLUS_CALLS_TODAY] ?: 0
    }

    private suspend fun resetIfNewDay() {
        context.cohereDataStore.edit { prefs ->
            val lastReset = prefs[KEY_RESET_DATE] ?: 0L
            val todayStart = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            if (lastReset < todayStart) {
                prefs[KEY_R_CALLS_TODAY] = 0
                prefs[KEY_R_PLUS_CALLS_TODAY] = 0
                prefs[KEY_RESET_DATE] = todayStart
            }
        }
    }

    private suspend fun resetIfNewMonth() {
        context.cohereDataStore.edit { prefs ->
            val lastMonthReset = prefs[KEY_MONTHLY_RESET] ?: 0L
            val monthStart = LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            if (lastMonthReset < monthStart) {
                prefs[KEY_MONTHLY_CALLS] = 0
                prefs[KEY_MONTHLY_RESET] = monthStart
            }
        }
    }
}

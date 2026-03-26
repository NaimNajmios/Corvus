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

private val Context.mistralDataStore: DataStore<Preferences> by preferencesDataStore(name = "mistral_quota")

@Singleton
class MistralQuotaGuard @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val DAILY_LIMIT = 150
        const val MONTHLY_LIMIT = 3000

        private val KEY_CALLS_TODAY = intPreferencesKey("mistral_calls_today")
        private val KEY_RESET_DATE = longPreferencesKey("mistral_quota_reset_date")
        private val KEY_MONTHLY_CALLS = intPreferencesKey("mistral_monthly_calls")
        private val KEY_MONTHLY_RESET = longPreferencesKey("mistral_monthly_reset")
    }

    suspend fun canCall(): Boolean {
        resetIfNewDay()
        resetIfNewMonth()

        val prefs = context.mistralDataStore.data.first()
        val monthlyTotal = prefs[KEY_MONTHLY_CALLS] ?: 0

        if (monthlyTotal >= MONTHLY_LIMIT) return false

        return (prefs[KEY_CALLS_TODAY] ?: 0) < DAILY_LIMIT
    }

    suspend fun recordCall() {
        context.mistralDataStore.edit { prefs ->
            prefs[KEY_CALLS_TODAY] = (prefs[KEY_CALLS_TODAY] ?: 0) + 1
            prefs[KEY_MONTHLY_CALLS] = (prefs[KEY_MONTHLY_CALLS] ?: 0) + 1
        }
    }

    suspend fun callsToday(): Int {
        val prefs = context.mistralDataStore.data.first()
        return prefs[KEY_CALLS_TODAY] ?: 0
    }

    suspend fun monthlyCallsUsed(): Int =
        context.mistralDataStore.data.first()[KEY_MONTHLY_CALLS] ?: 0

    private suspend fun resetIfNewDay() {
        context.mistralDataStore.edit { prefs ->
            val lastReset = prefs[KEY_RESET_DATE] ?: 0L
            val todayStart = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            if (lastReset < todayStart) {
                prefs[KEY_CALLS_TODAY] = 0
                prefs[KEY_RESET_DATE] = todayStart
            }
        }
    }

    private suspend fun resetIfNewMonth() {
        context.mistralDataStore.edit { prefs ->
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

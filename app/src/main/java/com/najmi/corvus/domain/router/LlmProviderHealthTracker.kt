package com.najmi.corvus.domain.router

import android.util.Log
import com.najmi.corvus.domain.model.LlmProvider
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class LlmProviderHealthTracker @Inject constructor() {
    private val providerErrors = ConcurrentHashMap<String, MutableList<Long>>()
    
    companion object {
        private const val ERROR_THRESHOLD = 3
        private const val HEALTH_WINDOW_MS = 300_000L
        private const val TAG = "LlmHealthTracker"
    }

    fun reportError(providerId: String) {
        val now = System.currentTimeMillis()
        val errors = providerErrors.getOrPut(providerId) { mutableListOf() }
        synchronized(errors) {
            errors.add(now)
            errors.removeIf { it < now - HEALTH_WINDOW_MS }
        }
        Log.w(TAG, "Reported error for $providerId. Total in window: ${errors.size}")
    }

    fun isHealthy(providerId: String): Boolean {
        val now = System.currentTimeMillis()
        val errors = providerErrors[providerId] ?: return true
        
        return synchronized(errors) {
            errors.removeIf { it < now - HEALTH_WINDOW_MS }
            errors.size < ERROR_THRESHOLD
        }
    }

    fun isAvailable(provider: LlmProvider): Boolean {
        if (!isHealthy(provider.name)) return false

        return when (provider) {
            LlmProvider.COHERE_R,
            LlmProvider.COHERE_R_PLUS -> true
            else -> true
        }
    }

    fun getErrorCount(providerId: String): Int {
        val now = System.currentTimeMillis()
        val errors = providerErrors[providerId] ?: return 0
        return synchronized(errors) {
            errors.removeIf { it < now - HEALTH_WINDOW_MS }
            errors.size
        }
    }

    fun reset(providerId: String) {
        providerErrors.remove(providerId)
    }
}

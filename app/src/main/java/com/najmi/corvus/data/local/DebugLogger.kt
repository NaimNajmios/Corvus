package com.najmi.corvus.data.local

import android.util.Log

object DebugLogger {
    private const val TAG = "CORVUS"
    private var isEnabled = false

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) {
            DebugLogBuffer.clear()
        }
    }

    fun isEnabled(): Boolean = isEnabled

    fun d(message: String) {
        if (isEnabled) {
            Log.d(TAG, message)
            DebugLogBuffer.addMessage("DEBUG", message)
        }
    }

    fun v(message: String) {
        if (isEnabled) {
            Log.v(TAG, message)
            DebugLogBuffer.addMessage("VERBOSE", message)
        }
    }

    fun w(message: String) {
        if (isEnabled) {
            Log.w(TAG, message)
            DebugLogBuffer.addMessage("WARN", message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            Log.e(TAG, message, throwable)
            DebugLogBuffer.addMessage("ERROR", message)
        }
    }

    fun network(method: String, url: String, status: Int, latencyMs: Long) {
        if (isEnabled) {
            Log.d(TAG, "[NET] $method $url -> $status (${latencyMs}ms)")
            DebugLogBuffer.addNetwork(method, url, status, latencyMs)
        }
    }

    fun llm(provider: String, model: String, tokens: Int, latencyMs: Long) {
        if (isEnabled) {
            Log.d(TAG, "[LLM] $provider/$model: ${tokens}tokens (${latencyMs}ms)")
            DebugLogBuffer.addLlm(provider, model, tokens, latencyMs)
        }
    }

    fun stage(stage: String, durationMs: Long) {
        if (isEnabled) {
            Log.d(TAG, "[STAGE] $stage completed in ${durationMs}ms")
            DebugLogBuffer.addStage(stage, durationMs)
        }
    }
}
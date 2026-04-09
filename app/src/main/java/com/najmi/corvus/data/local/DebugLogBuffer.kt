package com.najmi.corvus.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class DebugLogEntry {
    abstract val timestamp: Long
    abstract val category: String
    
    data class Stage(
        override val timestamp: Long,
        val stageName: String,
        val durationMs: Long
    ) : DebugLogEntry() {
        override val category: String = "STAGE"
    }
    
    data class Llm(
        override val timestamp: Long,
        val provider: String,
        val model: String,
        val tokens: Int,
        val latencyMs: Long
    ) : DebugLogEntry() {
        override val category: String = "LLM"
    }
    
    data class Network(
        override val timestamp: Long,
        val method: String,
        val url: String,
        val status: Int,
        val latencyMs: Long
    ) : DebugLogEntry() {
        override val category: String = "NET"
    }
    
    data class Message(
        override val timestamp: Long,
        val level: String,
        val message: String
    ) : DebugLogEntry() {
        override val category: String = "LOG"
    }
}

object DebugLogBuffer {
    private const val MAX_ENTRIES = 100
    
    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val entries: StateFlow<List<DebugLogEntry>> = _entries.asStateFlow()
    
    private val _stageTimings = MutableStateFlow<List<Pair<String, Long>>>(emptyList())
    val stageTimings: StateFlow<List<Pair<String, Long>>> = _stageTimings.asStateFlow()
    
    fun clear() {
        _entries.value = emptyList()
        _stageTimings.value = emptyList()
    }
    
    fun addStage(stageName: String, durationMs: Long) {
        val entry = DebugLogEntry.Stage(
            timestamp = System.currentTimeMillis(),
            stageName = stageName,
            durationMs = durationMs
        )
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        _stageTimings.value = _stageTimings.value + (stageName to durationMs)
    }
    
    fun addLlm(provider: String, model: String, tokens: Int, latencyMs: Long) {
        val entry = DebugLogEntry.Llm(
            timestamp = System.currentTimeMillis(),
            provider = provider,
            model = model,
            tokens = tokens,
            latencyMs = latencyMs
        )
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
    }
    
    fun addNetwork(method: String, url: String, status: Int, latencyMs: Long) {
        val entry = DebugLogEntry.Network(
            timestamp = System.currentTimeMillis(),
            method = method,
            url = url,
            status = status,
            latencyMs = latencyMs
        )
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
    }
    
    fun addMessage(level: String, message: String) {
        val entry = DebugLogEntry.Message(
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message
        )
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
    }
    
    fun formatForExport(): String {
        val sb = StringBuilder()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val logTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        
        sb.appendLine("=== CORVUS DEBUG LOG ===")
        sb.appendLine("Generated: ${timeFormat.format(Date())}")
        sb.appendLine()
        
        _entries.value.forEach { entry ->
            val time = logTimeFormat.format(Date(entry.timestamp))
            when (entry) {
                is DebugLogEntry.Stage -> {
                    sb.appendLine("[$time] [STAGE] ${entry.stageName} completed in ${entry.durationMs}ms")
                }
                is DebugLogEntry.Llm -> {
                    sb.appendLine("[$time] [LLM] ${entry.provider}/${entry.model}: ${entry.tokens}tokens (${entry.latencyMs}ms)")
                }
                is DebugLogEntry.Network -> {
                    sb.appendLine("[$time] [NET] ${entry.method} ${entry.url} -> ${entry.status} (${entry.latencyMs}ms)")
                }
                is DebugLogEntry.Message -> {
                    sb.appendLine("[$time] [${entry.level}] ${entry.message}")
                }
            }
        }
        
        if (_stageTimings.value.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("=== STAGE SUMMARY ===")
            val totalMs = _stageTimings.value.sumOf { it.second }
            _stageTimings.value.forEach { (name, ms) ->
                val pct = (ms.toFloat() / totalMs * 100).toInt()
                sb.appendLine("  $name: ${ms}ms ($pct%)")
            }
            sb.appendLine("  TOTAL: ${totalMs}ms")
        }
        
        return sb.toString()
    }
}
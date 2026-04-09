package com.najmi.corvus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.data.local.DebugLogBuffer
import com.najmi.corvus.data.local.DebugLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogExport(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries by DebugLogBuffer.entries.collectAsState()
    val stageTimings by DebugLogBuffer.stageTimings.collectAsState()

    val logTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Debug Logs",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No debug logs yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                        .padding(12.dp)
                ) {
                    items(entries) { entry ->
                        val time = logTimeFormat.format(Date(entry.timestamp))
                        val (color, prefix) = when (entry) {
                            is DebugLogEntry.Stage -> MaterialTheme.colorScheme.tertiary to "[STAGE]"
                            is DebugLogEntry.Llm -> MaterialTheme.colorScheme.primary to "[LLM]"
                            is DebugLogEntry.Network -> MaterialTheme.colorScheme.secondary to "[NET]"
                            is DebugLogEntry.Message -> when (entry.level) {
                                "ERROR" -> MaterialTheme.colorScheme.error
                                "WARN" -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.onSurface
                            } to "[${entry.level}]"
                        }
                        val message = when (entry) {
                            is DebugLogEntry.Stage -> "${entry.stageName} completed in ${entry.durationMs}ms"
                            is DebugLogEntry.Llm -> "${entry.provider}/${entry.model}: ${entry.tokens}tokens (${entry.latencyMs}ms)"
                            is DebugLogEntry.Network -> "${entry.method} ${entry.url} -> ${entry.status} (${entry.latencyMs}ms)"
                            is DebugLogEntry.Message -> entry.message
                        }
                        Text(
                            text = "[$time] $prefix $message",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = color,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                if (stageTimings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Stage Summary",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val totalMs = stageTimings.sumOf { it.second }
                    stageTimings.forEach { (name, ms) ->
                        val pct = if (totalMs > 0) (ms.toFloat() / totalMs * 100).toInt() else 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${ms}ms ($pct%)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "Total: ${totalMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val logText = DebugLogBuffer.formatForExport()
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, logText)
                        }
                        val chooser = android.content.Intent.createChooser(shareIntent, "Export Debug Logs")
                        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        android.app.Application().startActivity(chooser)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Logs")
                }
            }
        }
    }
}
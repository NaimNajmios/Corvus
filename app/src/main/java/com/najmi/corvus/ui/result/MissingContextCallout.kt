package com.najmi.corvus.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.ContextType
import com.najmi.corvus.domain.model.MissingContextInfo
import com.najmi.corvus.ui.theme.VerdictMisleading

@Composable
fun MissingContextCallout(missingContext: MissingContextInfo?) {
    if (missingContext == null) return

    val accentColor = VerdictMisleading

    Surface(
        color = accentColor.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(accentColor, accentColor.copy(alpha = 0.2f))
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor
                    )
                    Text(
                        text = "CRITICAL CONTEXT",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.sp
                        ),
                        color = accentColor
                    )
                }

                // Context type tag
                ContextTypeTag(missingContext.contextType)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = accentColor.copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))

            // Context content
            Text(
                text = missingContext.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun ContextTypeTag(type: ContextType) {
    val label = when (type) {
        ContextType.TEMPORAL     -> "TEMPORAL"
        ContextType.GEOGRAPHIC   -> "GEOGRAPHIC"
        ContextType.ATTRIBUTION  -> "ATTRIBUTION"
        ContextType.STATISTICAL  -> "STATISTICAL"
        ContextType.SELECTIVE    -> "SELECTIVE DATA"
        ContextType.GENERAL      -> "CONTEXT"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

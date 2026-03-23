package com.najmi.corvus.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.GroundedFact
import com.najmi.corvus.domain.model.KernelOfTruth
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.ui.theme.VerdictFalse
import com.najmi.corvus.ui.theme.VerdictMisleading
import com.najmi.corvus.ui.theme.VerdictTrue

@Composable
fun KernelOfTruthCard(
    kernel: KernelOfTruth?,
    sources: List<Source>,
    onSourceClick: (Int) -> Unit
) {
    if (kernel == null) return
    if (kernel.twistExplanation.isBlank() && kernel.trueParts.isEmpty() && kernel.falseParts.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, VerdictMisleading.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            // Card header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VerdictMisleading.copy(alpha = 0.1f))
                    .padding(16.dp)
            ) {
                Text(
                    text = "KERNEL OF TRUTH",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
                    color = VerdictMisleading
                )
                Text(
                    text = "How a real fact was twisted into misinformation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // True parts block
            if (kernel.trueParts.isNotEmpty()) {
                KernelSection(
                    label = "WHAT IS TRUE",
                    icon = Icons.Default.Check,
                    iconTint = VerdictTrue,
                    facts = kernel.trueParts,
                    sources = sources,
                    onSourceClick = onSourceClick,
                    leftBarColor = VerdictTrue
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // False parts block
            if (kernel.falseParts.isNotEmpty()) {
                KernelSection(
                    label = "WHAT IS FALSE OR MISLEADING",
                    icon = Icons.Default.Close,
                    iconTint = VerdictFalse,
                    facts = kernel.falseParts,
                    sources = sources,
                    onSourceClick = onSourceClick,
                    leftBarColor = VerdictFalse
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // Twist explanation
            if (kernel.twistExplanation.isNotBlank()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "HOW IT WAS TWISTED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = kernel.twistExplanation,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

@Composable
private fun KernelSection(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    facts: List<GroundedFact>,
    sources: List<Source>,
    onSourceClick: (Int) -> Unit,
    leftBarColor: Color
) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Section label with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = iconTint
            )
        }

        Spacer(Modifier.height(12.dp))

        // Facts
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (facts.isEmpty()) {
                Text(
                    text = "No specific items identified.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            } else {
                facts.forEach { fact ->
                    GroundedFactRow(
                        fact = fact,
                        source = fact.sourceIndex?.let { sources.getOrNull(it) },
                        onCitationClick = { fact.sourceIndex?.let { onSourceClick(it) } },
                        leftBarColor = leftBarColor
                    )
                }
            }
        }
    }
}

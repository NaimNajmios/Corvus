package com.najmi.corvus.ui.result

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.*
import com.najmi.corvus.ui.theme.*

@Composable
fun VerdictCard(
    result: CorvusCheckResult,
    modifier: Modifier = Modifier,
    onSourceClick: (Int) -> Unit = {}
) {
    AnimatedVerdictCard(modifier = modifier) {
        when (result) {
            is CorvusCheckResult.GeneralResult -> GeneralVerdictCard(result)
            is CorvusCheckResult.QuoteResult -> QuoteVerdictCard(result)
            is CorvusCheckResult.CompositeResult -> CompositeResultCard(
                result = result,
                onSourceClick = onSourceClick
            )
            is CorvusCheckResult.ViralHoaxResult -> ViralHoaxResultCard(result)
        }
    }
}

@Composable
fun AnimatedVerdictCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isRevealed by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isRevealed = true
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0.92f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "verdictScale"
    )
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(400),
        label = "verdictAlpha"
    )

    Box(
        modifier = modifier
            .scale(animatedScale)
            .alpha(animatedAlpha)
    ) {
        content()
    }
}

@Composable
fun TypeBadge(type: ClaimType) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Text(
            text = type.name.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun VerdictSignalChips(
    recencySignal: RecencySignal?,
    viralDetection: ViralDetectionResult?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Recency Signal
        when (recencySignal) {
            RecencySignal.BREAKING -> {
                InlineSignalChip(
                    label = "BREAKING",
                    icon = Icons.Default.Info,
                    color = Color(0xFFC8FF00) // Corvus Accent
                )
            }
            RecencySignal.RECENT -> {
                InlineSignalChip(
                    label = "RECENT",
                    icon = Icons.Default.Info,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
            else -> {}
        }

        // Viral Detection
        when (viralDetection) {
            is ViralDetectionResult.KnownHoax -> {
                InlineSignalChip(
                    label = "KNOWN HOAX",
                    icon = Icons.Default.Warning,
                    color = VerdictFalse
                )
            }
            is ViralDetectionResult.PossiblyRelated -> {
                InlineSignalChip(
                    label = "VIRAL CONTEXT",
                    icon = Icons.Default.Info,
                    color = VerdictMisleading
                )
            }
            else -> {}
        }
    }
}

@Composable
fun InlineSignalChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), CircleShape)
            .border(0.5.dp, color.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = color
        )
    }
}

@Composable
fun VerdictBadge(
    verdict: Verdict,
    plausibility: PlausibilityScore? = null,
    modifier: Modifier = Modifier
) {
    val color = getVerdictColor(verdict)
    
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), CorvusShapes.extraSmall)
            .border(0.5.dp, color.copy(alpha = 0.3f), CorvusShapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = verdict.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            
            if (verdict == Verdict.UNVERIFIABLE && plausibility != null) {
                Text(
                    text = plausibility.displayLabel(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = plausibility.labelColor()
                )
            }
        }
    }
}

@Composable
fun QuoteVerdictBadge(
    verdict: QuoteVerdict,
    modifier: Modifier = Modifier
) {
    val color = getQuoteVerdictColor(verdict)
    
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), CorvusShapes.extraSmall)
            .border(0.5.dp, color.copy(alpha = 0.3f), CorvusShapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = verdict.name.replace("_", " "),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun LargeVerdictBadge(
    verdict: Verdict,
    modifier: Modifier = Modifier
) {
    val color = getVerdictColor(verdict)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.05f), CorvusShapes.small)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = verdict.displayName().uppercase(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = DmSerifDisplay,
                fontSize = 46.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-1).sp,
                lineHeight = 48.sp
            ),
            color = color
        )
    }
}

@Composable
fun LargeQuoteVerdictBadge(
    verdict: QuoteVerdict,
    modifier: Modifier = Modifier
) {
    val color = getQuoteVerdictColor(verdict)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.05f), CorvusShapes.small)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = verdict.displayName().uppercase(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = DmSerifDisplay,
                fontSize = 46.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-1).sp,
                lineHeight = 48.sp
            ),
            color = color
        )
    }
}

@Composable
fun KernelSummaryRow(
    kernel: KernelOfTruth,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(VerdictFalse, CircleShape)
        )
        Text(
            text = "TWIST: ${kernel.twistExplanation}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HarmWarningBlock(harm: HarmAssessment) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(VerdictFalse.copy(alpha = 0.1f), CorvusShapes.small)
            .border(1.dp, VerdictFalse.copy(alpha = 0.3f), CorvusShapes.small)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = VerdictFalse.copy(alpha = pulseAlpha),
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "${harm.category.displayLabel()} RISK".uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = VerdictFalse
                )
                Text(
                    text = harm.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HarmInlineTag(harm: HarmAssessment) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(VerdictMisleading.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, VerdictMisleading.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Information",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "MODERATE ${harm.category.displayLabel()} CONCERN",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = VerdictMisleading,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PlausibilityDetailCard(plausibility: PlausibilityAssessment) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CorvusShapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CorvusShapes.small)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PLAUSIBILITY BREAKDOWN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlausibilitySpectrumBar(plausibility.score)

                Column {
                    Text(
                        text = "REASONING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = plausibility.reasoning,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                plausibility.closestEvidence?.let { evidence ->
                    Column {
                        Text(
                            text = "CLOSEST EVIDENCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = evidence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlausibilitySpectrumBar(score: PlausibilityScore) {
    val position = when (score) {
        PlausibilityScore.IMPLAUSIBLE -> 0.1f
        PlausibilityScore.UNLIKELY -> 0.3f
        PlausibilityScore.NEUTRAL -> 0.5f
        PlausibilityScore.PLAUSIBLE -> 0.7f
        PlausibilityScore.PROBABLE -> 0.9f
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("IMPLAUSIBLE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = VerdictFalse)
            Text(score.displayLabel(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = score.labelColor())
            Text("PROBABLE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = VerdictTrue)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(VerdictFalse, VerdictMisleading, VerdictUnverifiable, VerdictPartiallyTrue, VerdictTrue)
                    )
                )
        ) {
            val configuration = LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .offset(x = (screenWidth - 64.dp) * position) // rough estimate
                    .background(Color.White, CircleShape)
                    .border(2.dp, score.labelColor(), CircleShape)
            )
        }
    }
}

/**
 * Extension to get human-readable verdict names.
 */
fun Verdict.displayName(): String = when (this) {
    Verdict.TRUE -> "True"
    Verdict.FALSE -> "False"
    Verdict.MISLEADING -> "Misleading"
    Verdict.PARTIALLY_TRUE -> "Partial Truth"
    Verdict.UNVERIFIABLE -> "Unverifiable"
    Verdict.RECENCY_UNVERIFIABLE -> "Too Recent"
    Verdict.CHECKING -> "Checking..."
    Verdict.NOT_A_CLAIM -> "Opinion/Query"
}

/**
 * Extension to get human-readable quote verdict names.
 */
fun QuoteVerdict.displayName(): String = when (this) {
    QuoteVerdict.VERIFIED -> "Verified"
    QuoteVerdict.PARAPHRASED -> "Paraphrased"
    QuoteVerdict.OUT_OF_CONTEXT -> "Missing Context"
    QuoteVerdict.MISATTRIBUTED -> "Misattributed"
    QuoteVerdict.FABRICATED -> "Fabricated"
    QuoteVerdict.SATIRE_ORIGIN -> "Satire"
    QuoteVerdict.UNVERIFIABLE -> "Unverifiable"
}

fun PlausibilityScore.displayLabel() = when (this) {
    PlausibilityScore.IMPLAUSIBLE -> "High Implausibility"
    PlausibilityScore.UNLIKELY -> "Unlikely"
    PlausibilityScore.NEUTRAL -> "Genuinely Unknown"
    PlausibilityScore.PLAUSIBLE -> "Plausible"
    PlausibilityScore.PROBABLE -> "Probably True"
}

@Composable
fun PlausibilityScore.labelColor() = when (this) {
    PlausibilityScore.IMPLAUSIBLE -> VerdictFalse
    PlausibilityScore.UNLIKELY -> VerdictMisleading
    PlausibilityScore.NEUTRAL -> VerdictUnverifiable
    PlausibilityScore.PLAUSIBLE -> VerdictTrue.copy(alpha = 0.8f)
    PlausibilityScore.PROBABLE -> VerdictTrue
}

@Composable
fun getVerdictColor(verdict: Verdict): Color {
    return when (verdict) {
        Verdict.TRUE -> VerdictTrue
        Verdict.FALSE -> VerdictFalse
        Verdict.MISLEADING -> VerdictMisleading
        Verdict.PARTIALLY_TRUE -> VerdictPartiallyTrue
        Verdict.UNVERIFIABLE -> VerdictUnverifiable
        Verdict.RECENCY_UNVERIFIABLE -> VerdictMisleading
        Verdict.CHECKING -> MaterialTheme.colorScheme.primary
        Verdict.NOT_A_CLAIM -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
}

@Composable
fun getQuoteVerdictColor(verdict: QuoteVerdict): Color {
    return when (verdict) {
        QuoteVerdict.VERIFIED -> VerdictTrue
        QuoteVerdict.FABRICATED -> VerdictFalse
        QuoteVerdict.MISATTRIBUTED -> VerdictFalse
        QuoteVerdict.OUT_OF_CONTEXT -> VerdictMisleading
        QuoteVerdict.PARAPHRASED -> VerdictPartiallyTrue
        QuoteVerdict.SATIRE_ORIGIN -> VerdictMisleading
        QuoteVerdict.UNVERIFIABLE -> VerdictUnverifiable
    }
}

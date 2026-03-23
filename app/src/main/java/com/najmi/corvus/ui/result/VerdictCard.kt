package com.najmi.corvus.ui.result

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.PlausibilityScore
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.domain.model.displayLabel
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

    val scale by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0.92f,
        animationSpec = tween(380),
        label = "verdictScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(380),
        label = "verdictAlpha"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
    ) {
        content()
    }
}

@Composable
fun TypeBadge(
    type: ClaimType,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CorvusShapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = type.displayLabel(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
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
            .border(1.dp, color.copy(alpha = 0.5f), CorvusShapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = verdict.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
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
fun LargeVerdictBadge(
    verdict: Verdict,
    modifier: Modifier = Modifier
) {
    val color = getVerdictColor(verdict)
    
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), CorvusShapes.small)
            .border(2.dp, color, CorvusShapes.small)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = verdict.name.replace("_", " "),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
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
            .background(color.copy(alpha = 0.1f), CorvusShapes.small)
            .border(2.dp, color, CorvusShapes.small)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = verdict.name.replace("_", " "),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = color
        )
    }
}

@Composable
fun getVerdictColor(verdict: Verdict): Color {
    return when (verdict) {
        Verdict.TRUE -> VerdictTrue
        Verdict.FALSE -> VerdictFalse
        Verdict.MISLEADING -> VerdictMisleading
        Verdict.PARTIALLY_TRUE -> VerdictPartiallyTrue
        Verdict.UNVERIFIABLE -> VerdictUnverifiable
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
                contentDescription = null,
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
            contentDescription = null,
            tint = VerdictMisleading,
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

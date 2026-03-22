package com.najmi.corvus.ui.result

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.QuoteVerdict
import com.najmi.corvus.domain.model.SubClaim
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.ui.components.ConfidenceBar
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.VerdictFalse
import com.najmi.corvus.ui.theme.VerdictMisleading
import com.najmi.corvus.ui.theme.VerdictPartiallyTrue
import com.najmi.corvus.ui.theme.VerdictTrue
import com.najmi.corvus.ui.theme.VerdictUnverifiable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.najmi.corvus.domain.model.*

@Composable
fun VerdictCard(
    result: CorvusCheckResult,
    modifier: Modifier = Modifier
) {
    when (result) {
        is CorvusCheckResult.GeneralResult -> GeneralVerdictCard(result, modifier)
        is CorvusCheckResult.QuoteResult -> QuoteVerdictCard(result, modifier)
        is CorvusCheckResult.CompositeResult -> CompositeResultCard(result, modifier)
        is CorvusCheckResult.ViralHoaxResult -> ViralHoaxResultCard(result, modifier)
    }
}

@Composable
fun ViralHoaxResultCard(
    result: CorvusCheckResult.ViralHoaxResult,
    modifier: Modifier = Modifier
) {
    var isRevealed by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isRevealed = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0.95f,
        animationSpec = tween(380),
        label = "verdictScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(380),
        label = "verdictAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .background(VerdictFalse.copy(alpha = 0.1f), CorvusShapes.medium)
            .border(
                width = 3.dp,
                color = VerdictFalse,
                shape = CorvusShapes.medium
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KNOWN HOAX",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 24.sp,
                    letterSpacing = 1.sp
                ),
                color = VerdictFalse,
                fontWeight = FontWeight.Bold
            )
            
            TypeBadge(ClaimType.GENERAL) 
        }

        Text(
            text = "Match found in misinformation database",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(
            color = VerdictFalse.copy(alpha = 0.3f)
        )

        Text(
            text = "Original Match:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = result.matchedClaim,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = result.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (result.debunkUrls.isNotEmpty()) {
            Text(
                text = "Debunking Sources:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            result.debunkUrls.forEach { url ->
                Text(
                    text = "• $url",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun GeneralVerdictCard(
    result: CorvusCheckResult.GeneralResult,
    modifier: Modifier = Modifier
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

    val verdictColor = getVerdictColor(result.verdict)
    val harm = result.harmAssessment

    // Border intensifies with harm level
    val borderColor = when {
        harm.level == HarmLevel.HIGH && result.verdict == Verdict.FALSE -> VerdictFalse
        harm.level == HarmLevel.HIGH -> VerdictFalse.copy(alpha = 0.7f)
        harm.level == HarmLevel.MODERATE -> VerdictMisleading
        else -> verdictColor
    }

    val borderWidth = when (harm.level) {
        HarmLevel.HIGH -> 3.dp
        HarmLevel.MODERATE -> 2.dp
        else -> 1.5.dp
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .background(
                color = when (harm.level) {
                    HarmLevel.HIGH -> VerdictFalse.copy(alpha = 0.05f)
                    else -> MaterialTheme.colorScheme.surface
                },
                shape = CorvusShapes.medium
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CorvusShapes.medium
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = result.verdict.name.replace("_", " "),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 32.sp,
                        letterSpacing = 2.sp
                    ),
                    color = verdictColor,
                    fontWeight = FontWeight.Normal
                )
                
                // Plausibility sub-label for UNVERIFIABLE
                if (result.verdict == Verdict.UNVERIFIABLE && result.plausibility != null) {
                    Text(
                        text = "↳ ${result.plausibility.score.displayLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = result.plausibility.score.labelColor()
                    )
                }
            }
            
            if (result.claimType != ClaimType.GENERAL) {
                TypeBadge(result.claimType)
            }
        }

        // HIGH harm — prominent warning block
        if (harm.level == HarmLevel.HIGH) {
            HarmWarningBlock(harm)
        }

        // MODERATE harm — inline tag
        if (harm.level == HarmLevel.MODERATE) {
            HarmInlineTag(harm)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Confidence:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(result.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        }

        ConfidenceBar(confidence = result.confidence)

        if (result.verdict == Verdict.UNVERIFIABLE && result.plausibility != null) {
            PlausibilityDetailCard(result.plausibility)
        }
    }
}

@Composable
fun QuoteVerdictCard(
    result: CorvusCheckResult.QuoteResult,
    modifier: Modifier = Modifier
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

    val verdictColor = getQuoteVerdictColor(result.quoteVerdict)
    val harm = result.harmAssessment

    val borderColor = when {
        harm.level == HarmLevel.HIGH -> VerdictFalse.copy(alpha = 0.7f)
        harm.level == HarmLevel.MODERATE -> VerdictMisleading
        else -> verdictColor
    }

    val borderWidth = when (harm.level) {
        HarmLevel.HIGH -> 3.dp
        HarmLevel.MODERATE -> 2.dp
        else -> 1.5.dp
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .background(
                color = when (harm.level) {
                    HarmLevel.HIGH -> VerdictFalse.copy(alpha = 0.05f)
                    else -> MaterialTheme.colorScheme.surface
                },
                shape = CorvusShapes.medium
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CorvusShapes.medium
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = result.quoteVerdict.name.replace("_", " "),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 28.sp,
                        letterSpacing = 1.sp
                    ),
                    color = verdictColor,
                    fontWeight = FontWeight.Normal
                )
                
                // Plausibility sub-label
                if (result.quoteVerdict == QuoteVerdict.UNVERIFIABLE && result.plausibility != null) {
                    Text(
                        text = "↳ ${result.plausibility.score.displayLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = result.plausibility.score.labelColor()
                    )
                }
            }
            
            TypeBadge(ClaimType.QUOTE)
        }

        // HIGH harm — prominent warning block
        if (harm.level == HarmLevel.HIGH) {
            HarmWarningBlock(harm)
        }

        // MODERATE harm — inline tag
        if (harm.level == HarmLevel.MODERATE) {
            HarmInlineTag(harm)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Speaker: ${result.speaker}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            
            if (result.originalDate != null) {
                Text(
                    text = "Claimed Date: ${result.originalDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Confidence:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(result.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        }

        ConfidenceBar(confidence = result.confidence)

        if (result.quoteVerdict == QuoteVerdict.UNVERIFIABLE && result.plausibility != null) {
            PlausibilityDetailCard(result.plausibility)
        }
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
            text = type.name,
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
            .background(color.copy(alpha = 0.15f), CorvusShapes.extraSmall)
            .border(1.dp, color, CorvusShapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = verdict.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
            
            if (verdict == Verdict.UNVERIFIABLE && plausibility != null) {
                Text(
                    text = plausibility.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = plausibility.labelColor()
                )
            }
        }
    }
}

@Composable
fun CompositeResultCard(
    result: CorvusCheckResult.CompositeResult,
    modifier: Modifier = Modifier
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

    val verdictColor = getVerdictColor(result.compositeVerdict)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surface, CorvusShapes.medium)
            .border(
                width = 3.dp,
                color = verdictColor,
                shape = CorvusShapes.medium
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = result.compositeVerdict.name.replace("_", " "),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 28.sp,
                        letterSpacing = 2.sp
                    ),
                    color = verdictColor
                )
                Text(
                    text = "Compound claim • ${result.subClaims.size} sub-claims",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            TypeBadge(ClaimType.GENERAL) // Or add SUB_CLAIMS type
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Avg. Confidence:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(result.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        }

        ConfidenceBar(confidence = result.confidence)

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        result.subClaims.forEachIndexed { index, subClaim ->
            SubClaimRow(
                index = index + 1,
                subClaim = subClaim
            )
            if (index < result.subClaims.lastIndex) {
                 HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }

        Text(
            text = result.compositeSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun SubClaimRow(index: Int, subClaim: SubClaim) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = subClaim.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            subClaim.result?.let { res ->
                val color = when (res) {
                    is CorvusCheckResult.GeneralResult -> getVerdictColor(res.verdict)
                    is CorvusCheckResult.QuoteResult -> getQuoteVerdictColor(res.quoteVerdict)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
            }
        }

        if (expanded && subClaim.result != null) {
            SubClaimDetail(subClaim.result)
        }
    }
}

@Composable
fun SubClaimDetail(result: CorvusCheckResult) {
    Column(
        modifier = Modifier
            .padding(start = 28.dp, top = 8.dp, bottom = 8.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val verdictText = when (result) {
            is CorvusCheckResult.GeneralResult -> result.verdict.name.replace("_", " ")
            is CorvusCheckResult.QuoteResult -> result.quoteVerdict.name.replace("_", " ")
            else -> "UNKNOWN"
        }
        
        val verdictColor = when (result) {
            is CorvusCheckResult.GeneralResult -> getVerdictColor(result.verdict)
            is CorvusCheckResult.QuoteResult -> getQuoteVerdictColor(result.quoteVerdict)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = verdictText,
                style = MaterialTheme.typography.labelMedium,
                color = verdictColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• ${(result.confidence * 100).toInt()}% confidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val explanation = when (result) {
            is CorvusCheckResult.GeneralResult -> result.explanation
            is CorvusCheckResult.QuoteResult -> result.contextExplanation
            else -> ""
        }

        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
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
                    text = "${harm.category.name} RISK".uppercase(),
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
            text = "MODERATE ${harm.category.name} CONCERN",
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
            Text(score.name, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = score.labelColor())
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
            val density = LocalDensity.current
            
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

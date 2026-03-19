package com.najmi.corvus.ui.result

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.QuoteVerdict
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.ui.components.ConfidenceBar
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.VerdictFalse
import com.najmi.corvus.ui.theme.VerdictMisleading
import com.najmi.corvus.ui.theme.VerdictPartiallyTrue
import com.najmi.corvus.ui.theme.VerdictTrue
import com.najmi.corvus.ui.theme.VerdictUnverifiable

@Composable
fun VerdictCard(
    result: CorvusCheckResult,
    modifier: Modifier = Modifier
) {
    when (result) {
        is CorvusCheckResult.GeneralResult -> GeneralVerdictCard(result, modifier)
        is CorvusCheckResult.QuoteResult -> QuoteVerdictCard(result, modifier)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.verdict.name.replace("_", " "),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 32.sp,
                    letterSpacing = 2.sp
                ),
                color = verdictColor,
                fontWeight = FontWeight.Normal
            )
            
            if (result.claimType != com.najmi.corvus.domain.model.ClaimType.GENERAL) {
                TypeBadge(result.claimType)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.quoteVerdict.name.replace("_", " "),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 28.sp,
                    letterSpacing = 1.sp
                ),
                color = verdictColor,
                fontWeight = FontWeight.Normal
            )
            
            TypeBadge(com.najmi.corvus.domain.model.ClaimType.QUOTE)
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
    }
}

@Composable
fun TypeBadge(
    type: com.najmi.corvus.domain.model.ClaimType,
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
    modifier: Modifier = Modifier
) {
    val color = getVerdictColor(verdict)
    
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), CorvusShapes.extraSmall)
            .border(1.dp, color, CorvusShapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = verdict.name.replace("_", " "),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
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

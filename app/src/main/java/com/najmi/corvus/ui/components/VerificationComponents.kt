package com.najmi.corvus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.CitationConfidence
import com.najmi.corvus.domain.model.ExplanationConfidence
import com.najmi.corvus.domain.model.ExplanationVerification
import com.najmi.corvus.ui.theme.*

@Composable
fun CitationConfidenceBadge(confidence: CitationConfidence) {
    val (label, color) = when (confidence) {
        CitationConfidence.VERIFIED      -> "Verified in source"   to VerdictTrue
        CitationConfidence.PARTIAL       -> "Partially matched"    to VerdictMisleading
        CitationConfidence.LOW_CONFIDENCE -> "Low confidence"      to VerdictFalse
        CitationConfidence.UNATTRIBUTED  -> "General knowledge"    to CorvusTextTertiary
    }

    Surface(
        color  = color.copy(alpha = 0.12f),
        shape  = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text      = label.uppercase(),
            modifier  = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = color,
            fontFamily = IbmPlexMono,
            letterSpacing = 0.3.sp,
            fontSize = 9.sp
        )
    }
}

@Composable
fun MatchedFragmentCard(fragment: String) {
    Surface(
        color  = MaterialTheme.colorScheme.surface,
        shape  = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier              = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.Top
        ) {
            // Vertical quote mark accent
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .heightIn(min = 20.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            )
            Text(
                text      = fragment,
                style     = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun ExplanationConfidenceBanner(verification: ExplanationVerification) {
    if (verification.overallConfidence == ExplanationConfidence.WELL_GROUNDED ||
        verification.overallConfidence == ExplanationConfidence.MOSTLY_GROUNDED) return

    val isPoor = verification.overallConfidence == ExplanationConfidence.POORLY_GROUNDED
    val color  = if (isPoor) VerdictFalse else VerdictMisleading

    Surface(
        color  = color.copy(alpha = 0.08f),
        shape  = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
            Column {
                Text(
                    text      = if (isPoor) "LOW SOURCE GROUNDING" else "PARTIAL SOURCE GROUNDING",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = color,
                    fontFamily = IbmPlexMono,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "${verification.groundedSentences} of ${verification.totalSentences} " +
                            "sentences traced to retrieved sources. " +
                            "Treat this explanation with caution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

fun CitationConfidence.barColor(): Color = when (this) {
    CitationConfidence.VERIFIED       -> VerdictTrue
    CitationConfidence.PARTIAL        -> VerdictMisleading
    CitationConfidence.LOW_CONFIDENCE -> VerdictFalse
    CitationConfidence.UNATTRIBUTED   -> Color.Transparent
}

package com.najmi.corvus.ui.result

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.util.Log
import com.najmi.corvus.domain.model.*
import com.najmi.corvus.ui.theme.*

@Composable
fun SourceCard(
    source: Source,
    modifier: Modifier = Modifier,
    index: Int? = null
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    
    var isExpanded by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Card(
        onClick = {
            isExpanded = !isExpanded
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) 
            else CorvusTheme.colors.sectionEvidence.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Header: [Index] Publisher & Basic Metrics ────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PublisherFavicon(
                publisher = source.publisher,
                url = source.url
            )

            index?.let { i ->
                Text(
                    text = "[${i + 1}]",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            source.publisher?.let { publisher ->
                Text(
                    text = publisher.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            source.publishedDate?.let { date ->
                DateBadge(date)
            }

            source.outletRating?.let { rating ->
                if (!isExpanded) {
                    CredibilityIndicator(rating.credibility)
                }
            }
        }

        // ── Title & Snippet ──────────────────────────────────────────────
        Text(
            text = source.title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = if (isExpanded) 4 else 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!isExpanded) {
            source.snippet?.let { snippet ->
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ── Expanded Breakdown ───────────────────────────────────────────
        if (isExpanded) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            source.outletRating?.let { rating ->
                SourceRatingBreakdown(rating)
            }

            Text(
                text = source.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("SourceCard", "Failed to open link: ${e.message}")
                    }
                }
            )

            source.rawContent?.let {
                Text(
                    text = "Full article text retrieved for verification.",
                    style = MaterialTheme.typography.labelSmall,
                    color = CorvusTextTertiary,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
}

@Composable
private fun SourceRatingBreakdown(rating: com.najmi.corvus.domain.model.OutletRating) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CREDIBILITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = when {
                        rating.credibility >= 80 -> "High / Reliable"
                        rating.credibility >= 60 -> "Mostly Factual"
                        rating.credibility >= 40 -> "Mixed / Variable"
                        else -> "Low / Unreliable"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        rating.credibility >= 80 -> CredibilityHigh
                        rating.credibility >= 60 -> CredibilityMedium
                        else -> CredibilityLow
                    }
                )
            }
            
            // Large circular indicator
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = rating.credibility / 100f,
                    modifier = Modifier.size(36.dp),
                    color = when {
                        rating.credibility >= 80 -> CredibilityHigh
                        rating.credibility >= 60 -> CredibilityMedium
                        else -> CredibilityLow
                    },
                    strokeWidth = 3.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${rating.credibility}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Bias Badge
            BiasTag(rating.bias)
            
            // Category Badge
            rating.mbfcCategory?.let { cat ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = cat.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (rating.isGovAffiliated) {
                Surface(
                    color = VerdictMisleading.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, VerdictMisleading.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "GOVT AFFILIATED",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = VerdictMisleading
                    )
                }
            }
        }
        
        Text(
            text = "Rating source: ${rating.ratingSource.name.replace("_", " ")}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = CorvusTextTertiary
        )
    }
}

@Composable
private fun DateBadge(date: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CorvusShapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = date.take(10),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CredibilityIndicator(score: Int) {
    val color = when {
        score >= 80 -> CredibilityHigh
        score >= 60 -> CredibilityMedium
        else -> CredibilityLow
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
                .semantics { contentDescription = "Credibility: $score percent" }
        )
        Text(
            text = "$score%",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BiasTag(bias: Int) {
    val (label, color) = when (bias) {
        -2 -> "LEFT" to BiasLeft
        -1 -> "L-CENTER" to BiasLeftCenter
        0 -> "CENTER" to BiasCenter
        1 -> "R-CENTER" to BiasRightCenter
        2 -> "RIGHT" to BiasRight
        else -> "UNKNOWN" to BiasCenter
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.5f), CorvusShapes.small)
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .semantics { contentDescription = "Political bias: $label" }
    )
}

@Composable
private fun PublisherFavicon(
    publisher: String?,
    url: String,
    modifier: Modifier = Modifier
) {
    val faviconUrl = try {
        val uri = Uri.parse(url)
        val domain = uri.host ?: ""
        "https://www.google.com/s2/favicons?domain=$domain&sz=32"
    } catch (e: Exception) {
        null
    }

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (faviconUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(faviconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Publisher favicon",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = publisher?.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

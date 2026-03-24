package com.najmi.corvus.ui.result

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.ui.theme.*

@Composable
fun SourceCard(
    source: Source,
    modifier: Modifier = Modifier,
    index: Int? = null
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 400f),
        label = "cardScale"
    )
    

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .background(MaterialTheme.colorScheme.surface, CorvusShapes.medium)
            .border(
                width = 1.dp,
                color = CorvusTheme.colors.sectionEvidence.copy(alpha = 0.2f),
                shape = CorvusShapes.medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Unable to open link", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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
                BiasTag(rating.bias)
                CredibilityIndicator(rating.credibility)
            }
        }

        Text(
            text = source.title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = source.url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        source.snippet?.let { snippet ->
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
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
            text = date.take(10), // Take the first 10 chars (e.g., YYYY-MM-DD) for brevity if it's a long timestamp
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

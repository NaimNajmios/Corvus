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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.theme.CorvusShapes

@Composable
fun SourceCard(
    source: Source,
    modifier: Modifier = Modifier
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
    
    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0.6f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "borderAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .background(MaterialTheme.colorScheme.surface, CorvusShapes.medium)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha),
                shape = CorvusShapes.medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                context.startActivity(intent)
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

            source.publisher?.let { publisher ->
                Text(
                    text = publisher.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = CorvusAccent
                )
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
                color = CorvusAccent
            )
        }
    }
}

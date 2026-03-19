package com.najmi.corvus.ui.result

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.theme.CorvusBorder
import com.najmi.corvus.ui.theme.CorvusSurface
import com.najmi.corvus.ui.theme.CorvusTextPrimary
import com.najmi.corvus.ui.theme.CorvusTextSecondary
import com.najmi.corvus.ui.theme.CorvusTextTertiary
import com.najmi.corvus.ui.theme.CorvusVoid
import com.najmi.corvus.ui.theme.CorvusShapes

@Composable
fun SourceCard(
    source: Source,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CorvusSurface, CorvusShapes.medium)
            .border(1.dp, CorvusBorder, CorvusShapes.medium)
            .clickable {
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
            color = CorvusTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = source.url,
            style = MaterialTheme.typography.labelSmall,
            color = CorvusTextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        source.snippet?.let { snippet ->
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = CorvusTextSecondary,
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
            .background(CorvusVoid),
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

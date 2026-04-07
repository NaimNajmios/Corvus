package com.najmi.corvus.ui.result.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.najmi.corvus.domain.model.EntityContext
import com.najmi.corvus.domain.model.EntityMedia
import com.najmi.corvus.domain.model.MediaConfidence
import com.najmi.corvus.domain.model.MediaEntityType
import com.najmi.corvus.ui.theme.CorvusTheme
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.IbmPlexMono
import com.najmi.corvus.ui.theme.VerdictMisleading
import com.najmi.corvus.ui.theme.VerdictTrue

@Composable
fun EntityMediaPanel(
    context: EntityContext,
    modifier: Modifier = Modifier
) {
    when (val media = context.media) {
        is EntityMedia.Portrait    -> PersonMediaCard(context, media, modifier)
        is EntityMedia.CountryFlag -> CountryMediaCard(context, media, modifier)
        is EntityMedia.OrgLogo    -> OrgMediaCard(context, media, modifier)
        is EntityMedia.PlacePhoto -> PlaceMediaCard(context, media, modifier)
        is EntityMedia.NotFound,
        is EntityMedia.Skipped    -> EntityTextOnlyPanel(context, modifier)
    }
}

@Composable
fun PersonMediaCard(
    context: EntityContext,
    portrait: EntityMedia.Portrait,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.size(72.dp)) {
                AsyncImage(
                    model = portrait.imageUrl,
                    contentDescription = portrait.altText,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                if (portrait.confidence == MediaConfidence.LOW) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp),
                        color = VerdictMisleading.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            "?",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        context.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MediaEntityTypeBadge(MediaEntityType.PERSON)
                }
                context.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                context.detailedSnippet?.let {
                    Text(
                        text = it.take(180).trimEnd().let { s ->
                            if (it.length > 180) "$s…" else s
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                WikiAttributionLink(portrait.sourceUrl)
            }
        }
    }
}

@Composable
fun CountryMediaCard(
    context: EntityContext,
    flag: EntityMedia.CountryFlag,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = flag.pngUrl,
                contentDescription = "${flag.countryName} flag",
                modifier = Modifier
                    .width(80.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .then(
                        Modifier.clickable(enabled = false) {}
                    ),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        flag.countryName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            flag.isoCode,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            fontFamily = IbmPlexMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                flag.capital?.let {
                    Text(
                        "Capital: $it",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = IbmPlexMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                flag.population?.let {
                    Text(
                        "Population: ${it.formatPopulation()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = IbmPlexMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                context.detailedSnippet?.let {
                    Text(
                        text = it.take(140).let { s -> if (it.length > 140) "$s…" else s },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun OrgMediaCard(
    context: EntityContext,
    logo: EntityMedia.OrgLogo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                AsyncImage(
                    model = logo.imageUrl,
                    contentDescription = "${logo.orgName} logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        logo.orgName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MediaEntityTypeBadge(MediaEntityType.ORGANISATION)
                }
                context.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                context.detailedSnippet?.let {
                    Text(
                        text = it.take(160).let { s -> if (it.length > 160) "$s…" else s },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                WikiAttributionLink(logo.sourceUrl)
            }
        }
    }
}

@Composable
fun PlaceMediaCard(
    context: EntityContext,
    place: EntityMedia.PlacePhoto,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = place.imageUrl,
                contentDescription = place.placeName,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        place.placeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MediaEntityTypeBadge(MediaEntityType.PLACE)
                }
                place.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                context.detailedSnippet?.let {
                    Text(
                        text = it.take(160).let { s -> if (it.length > 160) "$s…" else s },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                WikiAttributionLink(place.sourceUrl)
            }
        }
    }
}

@Composable
fun EntityTextOnlyPanel(
    context: EntityContext,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ENTITY CONTEXT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        context.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    context.description?.let { desc ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            context.detailedSnippet?.let { snippet ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun WikiAttributionLink(url: String) {
    if (url.isBlank()) return
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier.clickable { uriHandler.openUri(url) },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Wikipedia",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = IbmPlexMono,
            fontSize = 8.sp
        )
    }
}

@Composable
fun MediaEntityTypeBadge(type: MediaEntityType) {
    val (label, color) = when (type) {
        MediaEntityType.PERSON       -> "PERSON"  to MaterialTheme.colorScheme.primary
        MediaEntityType.COUNTRY      -> "COUNTRY" to VerdictTrue
        MediaEntityType.ORGANISATION -> "ORG"     to VerdictMisleading
        MediaEntityType.PLACE        -> "PLACE"   to MaterialTheme.colorScheme.tertiary
        MediaEntityType.UNKNOWN      -> return
    }
    Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(1.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 7.5.sp,
            color = color,
            fontFamily = IbmPlexMono,
            letterSpacing = 0.5.sp
        )
    }
}

fun Long.formatPopulation(): String = when {
    this >= 1_000_000_000L -> "${this / 1_000_000_000}B"
    this >= 1_000_000L     -> "${this / 1_000_000}M"
    this >= 1_000L         -> "${this / 1_000}K"
    else                   -> this.toString()
}

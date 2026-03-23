package com.najmi.corvus.ui.result

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.VerdictFalse

@Composable
fun ViralHoaxResultCard(
    result: CorvusCheckResult.ViralHoaxResult,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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

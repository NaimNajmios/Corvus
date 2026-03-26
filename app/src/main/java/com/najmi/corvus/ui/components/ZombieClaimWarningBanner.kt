package com.najmi.corvus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.TemporalMismatchReport
import com.najmi.corvus.ui.theme.VerdictMisleading
import com.najmi.corvus.ui.theme.CorvusTextSecondary
import com.najmi.corvus.ui.theme.CorvusTextTertiary

@Composable
fun ZombieClaimWarningBanner(
    mismatchReport: TemporalMismatchReport,
    modifier: Modifier = Modifier
) {
    if (!mismatchReport.hasSignificantMismatch) return

    val oldestAge = mismatchReport.oldestSourceAge ?: return

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, VerdictMisleading),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = VerdictMisleading.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 2.dp),
                contentDescription = "Temporal warning",
                tint = VerdictMisleading
            )
            Column {
                Text(
                    text = "TEMPORAL MISMATCH DETECTED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = VerdictMisleading
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "This claim implies a current event, but retrieved sources are " +
                            "${oldestAge / 30} month(s) old. This may be a recycled claim " +
                            "presented as recent news.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CorvusTextSecondary
                )
                if (mismatchReport.sourcesWithoutDates > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${mismatchReport.sourcesWithoutDates} source(s) have no publication date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CorvusTextTertiary,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

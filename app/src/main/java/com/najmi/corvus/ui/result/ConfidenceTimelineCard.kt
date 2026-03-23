package com.najmi.corvus.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.ConfidencePoint
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.SectionTimeline
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConfidenceTimelineCard(
    points: List<ConfidencePoint>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SectionTimeline.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, SectionTimeline.copy(alpha = 0.1f)),
        shape = CorvusShapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "CONFIDENCE TIMELINE",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TimelineChart(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                Text(
                    text = dateFormat.format(Date(points.first().timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "TODAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TimelineChart(
    points: List<ConfidencePoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Draw grid lines (0%, 50%, 100%)
        drawLine(gridColor, Offset(0f, 0f), Offset(width, 0f), 1f)
        drawLine(gridColor, Offset(0f, height / 2), Offset(width, height / 2), 1f)
        drawLine(gridColor, Offset(0f, height), Offset(width, height), 1f)

        if (points.isEmpty()) return@Canvas

        val path = Path()
        val stepX = width / (points.size - 1).coerceAtLeast(1)
        
        points.forEachIndexed { i, point ->
            val x = i * stepX
            val y = height - (point.confidence * height)
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            
            // Draw points
            drawCircle(
                color = primaryColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

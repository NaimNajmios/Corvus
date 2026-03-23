package com.najmi.corvus.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.ConfidencePoint
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.CorvusTheme
import com.najmi.corvus.ui.theme.SectionTimeline
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun ConfidenceTimelineCard(
    points: List<ConfidencePoint>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    var selectedPoint by remember { mutableStateOf<ConfidencePoint?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CorvusTheme.colors.sectionTimeline.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, CorvusTheme.colors.sectionTimeline.copy(alpha = 0.1f)),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Y-axis labels
                Column(
                    modifier = Modifier.height(120.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("100%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                         color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text("50%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                         color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text("0%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                         color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }

                TimelineChart(
                    points = points,
                    onPointSelected = { selectedPoint = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                )
            }

            selectedPoint?.let { point ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                            CorvusShapes.small
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Confidence at step:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(point.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
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
    onPointSelected: (ConfidencePoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Canvas(
        modifier = modifier.pointerInput(points) {
            detectTapGestures { offset ->
                val width = size.width
                val stepX = width / (points.size - 1).coerceAtLeast(1)
                val tappedIndex = (offset.x / stepX).roundToInt().coerceIn(0, points.lastIndex)
                onPointSelected(points[tappedIndex])
            }
        }
    ) {
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

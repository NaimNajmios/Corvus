package com.najmi.corvus.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.ConfidencePoint
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun ConfidenceTimelineCard(
    points: List<ConfidencePoint>,
    sources: List<Source> = emptyList(),
    showSourceAttribution: Boolean = true,
    onConfidenceDrop: ((ConfidencePoint, Float, Source?, Int?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    var selectedPoint by remember { mutableStateOf<ConfidencePoint?>(null) }
    var showDropDialog by remember { mutableStateOf(false) }
    var dialogPoint by remember { mutableStateOf<ConfidencePoint?>(null) }
    var dialogPreviousConfidence by remember { mutableStateOf(0f) }

    if (showDropDialog && dialogPoint != null) {
        val contradictingSource = dialogPoint?.contradictingSourceIndex?.let { sources.getOrNull(it) }
        ConfidenceDropDialog(
            confidencePoint = dialogPoint!!,
            previousConfidence = dialogPreviousConfidence,
            contradictingSource = contradictingSource,
            contradictingSourceIndex = dialogPoint?.contradictingSourceIndex,
            allSources = sources,
            onDismiss = { showDropDialog = false }
        )
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHAIN OF VERIFICATION",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showSourceAttribution) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QuestionMark,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Tap points for details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.height(140.dp),
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
                    onPointSelected = { point ->
                        selectedPoint = point
                        val previousIndex = points.indexOf(point) - 1
                        val previousConfidence = if (previousIndex >= 0) points[previousIndex].confidence else point.confidence
                        if (point.confidence < previousConfidence - 0.05f && onConfidenceDrop != null) {
                            dialogPoint = point
                            dialogPreviousConfidence = previousConfidence
                            showDropDialog = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp)
                )
            }

            if (showSourceAttribution && points.isNotEmpty()) {
                SourceAttributionRow(
                    points = points,
                    sources = sources,
                    selectedPoint = selectedPoint,
                    onPointSelected = { point ->
                        selectedPoint = point
                        val previousIndex = points.indexOf(point) - 1
                        val previousConfidence = if (previousIndex >= 0) points[previousIndex].confidence else point.confidence
                        if (point.confidence < previousConfidence - 0.05f && onConfidenceDrop != null) {
                            dialogPoint = point
                            dialogPreviousConfidence = previousConfidence
                            showDropDialog = true
                        }
                    }
                )
            }

            selectedPoint?.let { point ->
                val previousIndex = points.indexOf(point) - 1
                val previousConfidence = if (previousIndex >= 0) points[previousIndex].confidence else null
                val hasDrop = previousConfidence != null && point.confidence < previousConfidence - 0.05f

                Surface(
                    color = if (hasDrop) VerdictFalse.copy(alpha = 0.1f) 
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                    shape = CorvusShapes.small
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = point.sourceTitle ?: "Analysis step",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            point.sourceIndex?.let { idx ->
                                Text(
                                    text = "Source [${idx + 1}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasDrop && previousConfidence != null) {
                                val drop = previousConfidence - point.confidence
                                Surface(
                                    color = VerdictFalse.copy(alpha = 0.2f),
                                    shape = CorvusShapes.extraSmall
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = VerdictFalse
                                        )
                                        Text(
                                            text = "-${(drop * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = VerdictFalse
                                        )
                                    }
                                }
                            } else if (previousConfidence != null && point.confidence > previousConfidence + 0.05f) {
                                val increase = point.confidence - previousConfidence
                                Surface(
                                    color = VerdictTrue.copy(alpha = 0.2f),
                                    shape = CorvusShapes.extraSmall
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = VerdictTrue
                                        )
                                        Text(
                                            text = "+${(increase * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = VerdictTrue
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${(point.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (hasDrop) VerdictFalse else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceAttributionRow(
    points: List<ConfidencePoint>,
    sources: List<Source>,
    selectedPoint: ConfidencePoint?,
    onPointSelected: (ConfidencePoint) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            points.take(5).forEachIndexed { index, point ->
                val isSelected = point == selectedPoint
                val hasDrop = if (index > 0) point.confidence < points[index - 1].confidence - 0.05f else false
                
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected && hasDrop -> VerdictFalse.copy(alpha = 0.3f)
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                hasDrop -> VerdictFalse.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable { onPointSelected(point) },
                    contentAlignment = Alignment.Center
                ) {
                    point.sourceIndex?.let { idx ->
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = when {
                                isSelected && hasDrop -> VerdictFalse
                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                hasDrop -> VerdictFalse.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } ?: Box(
                        modifier = Modifier.size(8.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                    )
                }
            }
            if (points.size > 5) {
                Text(
                    text = "+${points.size - 5}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Text(
            text = "TODAY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
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

            val pointColor = if (i > 0 && point.confidence < points[i - 1].confidence - 0.05f) {
                VerdictFalse
            } else {
                primaryColor
            }

            drawCircle(
                color = pointColor,
                radius = 5.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(x, y)
            )
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

package com.najmi.corvus.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.theme.CorvusShapes

@Composable
fun ConfidenceBar(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    var targetConfidence by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(confidence) {
        targetConfidence = confidence
    }

    val animatedConfidence by animateFloatAsState(
        targetValue = targetConfidence,
        animationSpec = tween(durationMillis = 600),
        label = "confidence"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CorvusShapes.extraSmall)
            .background(CorvusAccent.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedConfidence.coerceIn(0f, 1f))
                .height(4.dp)
                .background(CorvusAccent)
        )
    }
}

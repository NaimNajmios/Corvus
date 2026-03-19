package com.najmi.corvus.ui.result

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.ui.components.PipelineStepIndicator
import com.najmi.corvus.ui.theme.CorvusAccent
import kotlinx.coroutines.delay

@Composable
fun LoadingScreen(
    currentStep: PipelineStep,
    error: String? = null,
    onRetry: () -> Unit = {},
    onCancel: () -> Unit = {},
    onResultReady: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (error != null) {
            ErrorContent(
                error = error,
                onRetry = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    elapsedSeconds = 0
                    onRetry()
                }
            )
        } else {
            LoadingContent(
                currentStep = currentStep,
                elapsedSeconds = elapsedSeconds,
                onCancel = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCancel()
                }
            )
        }
    }
}

@Composable
private fun LoadingContent(
    currentStep: PipelineStep,
    elapsedSeconds: Int,
    onCancel: () -> Unit
) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeText = if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        PulsingLogo()
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "CORVUS",
            style = MaterialTheme.typography.headlineMedium,
            color = CorvusAccent
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Analysing...",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        PipelineStepIndicator(currentStep = currentStep)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            shape = MaterialTheme.shapes.small
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "ANALYSIS FAILED",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .padding(16.dp)
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = CorvusAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = MaterialTheme.shapes.small
        ) {
            Text("TRY AGAIN")
        }
    }
}

@Composable
private fun PulsingLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "C",
            style = MaterialTheme.typography.displayLarge,
            color = CorvusAccent
        )
    }
}

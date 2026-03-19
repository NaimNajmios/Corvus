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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.ui.components.PipelineStepIndicator
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.theme.CorvusTextSecondary
import com.najmi.corvus.ui.theme.CorvusVoid

@Composable
fun LoadingScreen(
    currentStep: PipelineStep,
    onResultReady: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CorvusVoid),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
                color = CorvusTextSecondary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            PipelineStepIndicator(currentStep = currentStep)
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
            .background(CorvusVoid, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "C",
            style = MaterialTheme.typography.displayLarge,
            color = CorvusAccent
        )
    }
}

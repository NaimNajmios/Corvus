package com.najmi.corvus.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.theme.CorvusAccentDim
import com.najmi.corvus.ui.theme.CorvusTextTertiary
import com.najmi.corvus.ui.theme.CorvusShapes

@Composable
fun PipelineStepIndicator(
    currentStep: PipelineStep,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        "Checking known facts" to PipelineStep.CHECKING_KNOWN_FACTS,
        "Retrieving sources" to PipelineStep.RETRIEVING_SOURCES,
        "Analyzing" to PipelineStep.ANALYZING
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        steps.forEachIndexed { index, (label, step) ->
            PipelineStepItem(
                label = label,
                status = getStepStatus(step, currentStep),
                isActive = step == currentStep
            )
        }
    }
}

@Composable
private fun PipelineStepItem(
    label: String,
    status: StepStatus,
    isActive: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(if (isActive) alpha else 1f)
                .background(
                    color = when (status) {
                        StepStatus.COMPLETED -> CorvusAccentDim
                        StepStatus.ACTIVE -> CorvusAccent
                        StepStatus.PENDING -> CorvusTextTertiary
                    },
                    shape = CorvusShapes.extraSmall
                )
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when (status) {
                StepStatus.COMPLETED -> CorvusAccentDim
                StepStatus.ACTIVE -> CorvusAccent
                StepStatus.PENDING -> CorvusTextTertiary
            }
        )
    }
}

private enum class StepStatus {
    COMPLETED, ACTIVE, PENDING
}

private fun getStepStatus(step: PipelineStep, current: PipelineStep): StepStatus {
    return when (step) {
        PipelineStep.IDLE -> StepStatus.PENDING
        PipelineStep.CHECKING_KNOWN_FACTS -> {
            when (current) {
                PipelineStep.IDLE -> StepStatus.PENDING
                PipelineStep.CHECKING_KNOWN_FACTS -> StepStatus.ACTIVE
                else -> StepStatus.COMPLETED
            }
        }
        PipelineStep.RETRIEVING_SOURCES -> {
            when (current) {
                PipelineStep.CHECKING_KNOWN_FACTS -> StepStatus.PENDING
                PipelineStep.RETRIEVING_SOURCES -> StepStatus.ACTIVE
                else -> StepStatus.COMPLETED
            }
        }
        PipelineStep.ANALYZING -> {
            when (current) {
                PipelineStep.RETRIEVING_SOURCES -> StepStatus.PENDING
                PipelineStep.ANALYZING -> StepStatus.ACTIVE
                else -> StepStatus.COMPLETED
            }
        }
        PipelineStep.DONE -> StepStatus.COMPLETED
    }
}

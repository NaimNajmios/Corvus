package com.najmi.corvus.ui.input

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.R
import com.najmi.corvus.domain.model.CorvusUiState
import com.najmi.corvus.ui.components.PipelineStepIndicator
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.VerdictFalse
import com.najmi.corvus.ui.theme.VerdictMisleading
import com.najmi.corvus.ui.viewmodel.CorvusViewModel

@Composable
fun InputScreen(
    viewModel: CorvusViewModel = hiltViewModel(),
    onAnalyze: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    StatelessInputScreen(
        uiState = uiState,
        onTextChange = { viewModel.updateInputText(it) },
        onAnalyze = {
            viewModel.analyze()
            onAnalyze()
        }
    )
}

@Composable
internal fun StatelessInputScreen(
    uiState: CorvusUiState,
    onTextChange: (String) -> Unit,
    onAnalyze: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val characterCount = uiState.inputText.length
    val maxLimit = CorvusViewModel.MAX_CLAIM_LENGTH
    val isNearLimit = characterCount >= (maxLimit - 100)
    val isAtLimit = characterCount >= maxLimit
    val remainingChars = (maxLimit - characterCount).coerceAtLeast(0)

    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme

    val characterCountColor by animateColorAsState(
        targetValue = when {
            isAtLimit -> VerdictFalse
            isNearLimit -> VerdictMisleading
            else -> colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        animationSpec = tween(300),
        label = "counterColor"
    )

    var shakeOffset by remember { mutableFloatStateOf(0f) }
    var previousLength by remember { mutableIntStateOf(0) }

    LaunchedEffect(characterCount) {
        if (previousLength == maxLimit && characterCount > maxLimit) {
            shakeOffset = 10f
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previousLength = characterCount
    }

    val shakeScale by animateFloatAsState(
        targetValue = if (shakeOffset != 0f) 0f else 1f,
        animationSpec = spring(
            dampingRatio = 0.2f,
            stiffness = 1000f
        ),
        label = "shakeScale",
        finishedListener = { shakeOffset = 0f }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            CrowLogoMark()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "See through the noise.",
                style = typography.headlineMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = { input: String ->
                    val newText = if (input.length > maxLimit) input.take(maxLimit) else input
                    onTextChange(newText)
                    
                    if (newText.length == maxLimit - 50) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                placeholder = {
                    Text(
                        text = "Paste a claim, tweet, or statement",
                        style = typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                trailingIcon = {
                    if (uiState.inputText.isNotEmpty()) {
                        IconButton(onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTextChange("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear text")
                        }
                    }
                },
                textStyle = typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colorScheme.onBackground,
                    unfocusedTextColor = colorScheme.onBackground,
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                    cursorColor = colorScheme.primary,
                    focusedContainerColor = colorScheme.background,
                    unfocusedContainerColor = colorScheme.background
                ),
                shape = CorvusShapes.small,
                visualTransformation = VisualTransformation.None
            )

            AnimatedVisibility(
                visible = uiState.inputText.isEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { -20 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -20 })
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    AssistChip(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            clipboardManager.getText()?.text?.let { clipboardText ->
                                val newText = if (clipboardText.length > maxLimit) clipboardText.take(maxLimit) else clipboardText
                                onTextChange(newText)
                            }
                        },
                        label = { Text("Paste from Clipboard") },
                        leadingIcon = {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = colorScheme.primary,
                            labelColor = colorScheme.primary
                        ),
                        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = colorScheme.outlineVariant)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(shakeScale),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = isNearLimit,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Text(
                        text = if (isAtLimit) "Limit reached!" else "$remainingChars remaining",
                        style = typography.labelSmall,
                        color = characterCountColor
                    )
                }

                Text(
                    text = "$characterCount/$maxLimit",
                    style = typography.labelSmall,
                    color = characterCountColor
                )
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error,
                    style = typography.labelSmall,
                    color = colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PipelineStepIndicator(
                    currentStep = uiState.currentStep,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            AnimatedVisibility(
                visible = !uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAnalyze()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = uiState.inputText.isNotBlank() && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                        disabledContainerColor = colorScheme.primary.copy(alpha = 0.3f),
                        disabledContentColor = colorScheme.onPrimary.copy(alpha = 0.5f)
                    ),
                    shape = CorvusShapes.small
                ) {
                    Text(
                        text = "ANALYSE",
                        style = typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun CrowLogoMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = "Corvus Logo",
            modifier = Modifier.size(64.dp)
        )
    }
}

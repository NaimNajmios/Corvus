package com.najmi.corvus.ui.input

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.ui.components.PipelineStepIndicator
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.theme.CorvusBorder
import com.najmi.corvus.ui.theme.CorvusTextPrimary
import com.najmi.corvus.ui.theme.CorvusTextSecondary
import com.najmi.corvus.ui.theme.CorvusTextTertiary
import com.najmi.corvus.ui.theme.CorvusVoid
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.viewmodel.CorvusViewModel

@Composable
fun InputScreen(
    viewModel: CorvusViewModel = hiltViewModel(),
    onAnalyze: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateToHistory) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "History",
                        tint = CorvusTextSecondary
                    )
                }
                
                CrowLogoMark()
                
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = CorvusTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "See through the noise.",
                style = MaterialTheme.typography.headlineMedium,
                color = CorvusTextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Text(
                text = "See through the noise.",
                style = MaterialTheme.typography.headlineMedium,
                color = CorvusTextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = viewModel::updateInputText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                placeholder = {
                    Text(
                        text = "Paste a claim, tweet, or statement",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CorvusTextTertiary
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CorvusTextPrimary,
                    unfocusedTextColor = CorvusTextPrimary,
                    focusedBorderColor = CorvusAccent,
                    unfocusedBorderColor = CorvusBorder,
                    cursorColor = CorvusAccent,
                    focusedContainerColor = CorvusVoid,
                    unfocusedContainerColor = CorvusVoid
                ),
                shape = CorvusShapes.small,
                visualTransformation = VisualTransformation.None
            )

            Text(
                text = "${uiState.inputText.length}/500",
                style = MaterialTheme.typography.labelSmall,
                color = CorvusTextTertiary,
                modifier = Modifier.align(Alignment.End)
            )

            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
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
                        viewModel.analyze()
                        onAnalyze()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = uiState.inputText.isNotBlank() && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CorvusAccent,
                        contentColor = CorvusVoid,
                        disabledContainerColor = CorvusAccent.copy(alpha = 0.3f),
                        disabledContentColor = CorvusVoid.copy(alpha = 0.5f)
                    ),
                    shape = CorvusShapes.small
                ) {
                    Text(
                        text = "ANALYSE",
                        style = MaterialTheme.typography.titleMedium
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
            .size(64.dp)
            .background(CorvusVoid, CorvusShapes.extraSmall)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "C",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 32.sp,
                    letterSpacing = 2.sp
                ),
                color = CorvusAccent
            )
        }
    }
}

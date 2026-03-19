package com.najmi.corvus.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.theme.CorvusSurface
import com.najmi.corvus.ui.theme.CorvusTextPrimary
import com.najmi.corvus.ui.theme.CorvusTextSecondary
import com.najmi.corvus.ui.theme.CorvusVoid
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.viewmodel.CorvusViewModel
import kotlinx.coroutines.delay

@Composable
fun ResultScreen(
    viewModel: CorvusViewModel = hiltViewModel(),
    onAnalyzeAnother: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val result = uiState.result

    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        result?.let { corvusResult ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    VerdictCard(
                        result = corvusResult,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text(
                        text = "EXPLANATION",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.sp
                        ),
                        color = CorvusTextSecondary
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CorvusSurface, CorvusShapes.medium)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = corvusResult.explanation,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CorvusTextPrimary
                        )
                    }
                }

                if (corvusResult.keyFacts.isNotEmpty()) {
                    item {
                        Text(
                            text = "KEY FACTS",
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 1.sp
                            ),
                            color = CorvusTextSecondary
                        )
                    }

                    itemsIndexed(corvusResult.keyFacts) { _, fact ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "-",
                                style = MaterialTheme.typography.bodyLarge,
                                color = CorvusAccent
                            )
                            Text(
                                text = fact,
                                style = MaterialTheme.typography.bodyLarge,
                                color = CorvusTextPrimary
                            )
                        }
                    }
                }

                if (corvusResult.sources.isNotEmpty()) {
                    item {
                        Text(
                            text = "EVIDENCE",
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 1.sp
                            ),
                            color = CorvusTextSecondary
                        )
                    }

                    itemsIndexed(corvusResult.sources) { index, source ->
                        AnimatedVisibility(
                            visible = showContent,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 280,
                                    delayMillis = 200 + (index * 80)
                                )
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 280,
                                    delayMillis = 200 + (index * 80)
                                ),
                                initialOffsetY = { it / 2 }
                            )
                        ) {
                            SourceCard(source = source)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(CorvusVoid)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.reset()
                            onAnalyzeAnother()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CorvusAccent,
                            contentColor = CorvusVoid
                        ),
                        shape = CorvusShapes.small
                    ) {
                        Text(
                            text = "ANALYSE ANOTHER",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Button(
                        onClick = {},
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CorvusSurface,
                            contentColor = CorvusTextPrimary
                        ),
                        shape = CorvusShapes.small
                    ) {
                        Text(
                            text = "SHARE",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

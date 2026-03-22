package com.najmi.corvus.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.R
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.QuoteVerdict
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.ui.history.components.HistoryAnalytics
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.viewmodel.CompareViewModel
import com.najmi.corvus.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyViewModel: HistoryViewModel = hiltViewModel(),
    compareViewModel: CompareViewModel = hiltViewModel(),
    onItemClick: (CorvusCheckResult) -> Unit,
    onCompare: () -> Unit = {}
) {
    val uiState by historyViewModel.uiState.collectAsState()
    val compareUiState by compareViewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pendingDeleteItem by remember { mutableStateOf<CorvusCheckResult?>(null) }

    LaunchedEffect(pendingDeleteItem) {
        pendingDeleteItem?.let { item ->
            val result = snackbarHostState.showSnackbar(
                message = "Item deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                historyViewModel.undoDelete()
            } else {
                historyViewModel.confirmDelete()
            }
            pendingDeleteItem = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "History",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            IconButton(
                onClick = { 
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    historyViewModel.toggleAnalytics() 
                },
                modifier = Modifier.background(
                    if (uiState.isAnalyticsVisible) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    CircleShape
                )
            ) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = "Show Analytics",
                    tint = if (uiState.isAnalyticsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        AnimatedVisibility(visible = uiState.isAnalyticsVisible) {
            HistoryAnalytics(distribution = uiState.verdictDistribution)
        }

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { historyViewModel.search(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search claims...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            shape = CorvusShapes.medium,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val verdictFilters = listOf(
                null to "All",
                "TRUE" to "True",
                "FALSE" to "False",
                "MISLEADING" to "Misleading",
                "PARTIALLY_TRUE" to "Partially True",
                "UNVERIFIABLE" to "Unverifiable"
            )
            items(verdictFilters) { (value, label) ->
                FilterChip(
                    selected = uiState.selectedVerdictFilter == value,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        historyViewModel.filterByVerdict(value)
                    },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = CorvusShapes.medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyHistoryState()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(
                    items = uiState.history,
                    key = { it.id }
                ) { item ->
                    var isRemoved by remember { mutableStateOf(false) }
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                isRemoved = true
                                historyViewModel.prepareDelete(item)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            pendingDeleteItem = item
                        }
                    }

                    AnimatedVisibility(
                        visible = !isRemoved,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val scale by animateFloatAsState(
                                    targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.8f,
                                    label = "deleteScale"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                        .background(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                            CorvusShapes.medium
                                        )
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.scale(scale)
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true
                        ) {
                            HistoryItem(
                                result = item,
                                isSelected = compareViewModel.isSelected(item.id),
                                isCompareMode = compareUiState.isCompareMode,
                                onClick = {
                                    if (compareUiState.isCompareMode) {
                                        compareViewModel.toggleSelection(item)
                                    } else {
                                        onItemClick(item)
                                    }
                                },
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    compareViewModel.toggleSelection(item)
                                },
                                onDelete = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    historyViewModel.prepareDelete(item)
                                    pendingDeleteItem = item
                                }
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = compareUiState.isCompareMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                CompareSelectionBar(
                    selectedCount = compareUiState.selectedClaims.size,
                    onClear = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        compareViewModel.clearSelection()
                    },
                    onCompare = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCompare()
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                actionColor = MaterialTheme.colorScheme.primary,
                shape = CorvusShapes.medium
            )
        }
    }
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    result: CorvusCheckResult,
    isSelected: Boolean = false,
    isCompareMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CorvusShapes.medium)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                             else MaterialTheme.colorScheme.surface
        ),
        shape = CorvusShapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = isCompareMode) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(24.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            Box(contentAlignment = Alignment.BottomEnd) {
                when (result) {
                    is CorvusCheckResult.GeneralResult -> VerdictBadgeLarge(verdict = result.verdict)
                    is CorvusCheckResult.QuoteResult -> QuoteVerdictBadgeLarge(verdict = result.quoteVerdict)
                    is CorvusCheckResult.CompositeResult -> VerdictBadgeLarge(verdict = result.compositeVerdict)
                    is CorvusCheckResult.ViralHoaxResult -> VerdictBadgeLarge(verdict = Verdict.FALSE)
                }
                
                // Harm Indicator Overlay
                val harmLevel = when (result) {
                    is CorvusCheckResult.GeneralResult -> result.harmAssessment.level
                    is CorvusCheckResult.QuoteResult -> result.harmAssessment.level
                    else -> com.najmi.corvus.domain.model.HarmLevel.NONE
                }
                
                if (harmLevel == com.najmi.corvus.domain.model.HarmLevel.HIGH) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "High Harm",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(1.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.claim,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatDate(result.checkedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    
                    // Plausibility Indicator
                    val plausibility = when (result) {
                        is CorvusCheckResult.GeneralResult -> if (result.verdict == Verdict.UNVERIFIABLE) result.plausibility?.score else null
                        is CorvusCheckResult.QuoteResult -> if (result.quoteVerdict == QuoteVerdict.UNVERIFIABLE) result.plausibility?.score else null
                        else -> null
                    }
                    
                    if (plausibility != null) {
                        Text(
                            text = " • ${plausibility.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            if (!isCompareMode) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VerdictBadgeLarge(verdict: Verdict) {
    val (color, icon) = when (verdict) {
        Verdict.TRUE -> MaterialTheme.colorScheme.primary to Icons.Default.Check
        Verdict.FALSE -> MaterialTheme.colorScheme.error to Icons.Default.Close
        Verdict.MISLEADING -> MaterialTheme.colorScheme.tertiary to Icons.Default.Info
        Verdict.PARTIALLY_TRUE -> MaterialTheme.colorScheme.secondary to Icons.Default.Info
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Default.Search
    }
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun VerdictBadge(verdict: Verdict) {
    val (color, text) = when (verdict) {
        Verdict.TRUE -> MaterialTheme.colorScheme.primary to "TRUE"
        Verdict.FALSE -> MaterialTheme.colorScheme.error to "FALSE"
        Verdict.MISLEADING -> MaterialTheme.colorScheme.tertiary to "MISLEADING"
        Verdict.PARTIALLY_TRUE -> MaterialTheme.colorScheme.secondary to "PARTIAL"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to "UNVERIFIED"
    }
    
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), CorvusShapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun QuoteVerdictBadge(verdict: QuoteVerdict) {
    val (color, text) = when (verdict) {
        QuoteVerdict.VERIFIED -> MaterialTheme.colorScheme.primary to "VERIFIED"
        QuoteVerdict.FABRICATED -> MaterialTheme.colorScheme.error to "FABRICATED"
        else -> MaterialTheme.colorScheme.tertiary to "OTHER"
    }
    
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), CorvusShapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun QuoteVerdictBadgeLarge(verdict: QuoteVerdict) {
    val (color, icon) = when (verdict) {
        QuoteVerdict.VERIFIED -> MaterialTheme.colorScheme.primary to Icons.Default.Check
        QuoteVerdict.FABRICATED -> MaterialTheme.colorScheme.error to Icons.Default.Close
        else -> MaterialTheme.colorScheme.tertiary to Icons.Default.Info
    }
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CompareSelectionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onCompare: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        tonalElevation = 8.dp,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$selectedCount Selected",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(
                onClick = onClear,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Clear")
            }
            
            androidx.compose.material3.Button(
                onClick = onCompare,
                enabled = selectedCount >= 2,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Compare,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compare")
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        CrowSearchIllustration()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No fact-checks yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Paste a claim in the home tab to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun CrowSearchIllustration() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(MaterialTheme.colorScheme.surface, CorvusShapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline, CorvusShapes.medium),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "Corvus Logo",
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(60.dp, 3.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        CorvusShapes.extraSmall
                    )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(40.dp, 3.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        CorvusShapes.extraSmall
                    )
            )
        }
    }
}

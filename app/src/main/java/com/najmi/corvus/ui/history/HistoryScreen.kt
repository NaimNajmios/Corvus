package com.najmi.corvus.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import com.najmi.corvus.ui.viewmodel.HistorySort
import com.najmi.corvus.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyViewModel: HistoryViewModel = hiltViewModel(),
    compareViewModel: CompareViewModel = hiltViewModel(),
    onItemClick: (com.najmi.corvus.domain.model.HistorySummary) -> Unit,
    onSwipeToEdit: (String) -> Unit = {},
    onCompare: () -> Unit = {}
) {
    val uiState by historyViewModel.uiState.collectAsState()
    val compareUiState by compareViewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showClearAllDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteRedundantDialog by remember { mutableStateOf(false) }
    var redundantCount by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.pendingDeleteIds) {
        if (uiState.pendingDeleteIds.isNotEmpty()) {
            val count = uiState.pendingDeleteIds.size
            val message = if (count == 1) "Item deleted" else "$count items deleted"
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                historyViewModel.undoDelete()
            } else {
                historyViewModel.confirmPendingDeletes()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = uiState.isDeleteMode,
            transitionSpec = {
                fadeIn() + slideInVertically() togetherWith fadeOut() + slideOutVertically()
            },
            label = "headerTransition"
        ) { isDeleteMode ->
            if (isDeleteMode) {
                DeleteModeHeader(
                    selectedCount = uiState.deleteSelection.size,
                    totalCount = uiState.history.size,
                    onClose = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        historyViewModel.exitDeleteMode()
                    },
                    onSelectAll = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        historyViewModel.selectAllForDeletion()
                    },
                    onDeselectAll = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        historyViewModel.deselectAllForDeletion()
                    },
                    onDelete = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showBatchDeleteDialog = true
                    }
                )
            } else {
                NormalHeader(
                    isAnalyticsVisible = uiState.isAnalyticsVisible,
                    onToggleAnalytics = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        historyViewModel.toggleAnalytics()
                    },
                    onClearAll = { showClearAllDialog = true },
                    onSelectToDelete = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        historyViewModel.enterDeleteMode()
                    },
                    onNavigateToRedundant = {
                        redundantCount = historyViewModel.getRedundantCount()
                        if (redundantCount > 0) {
                            showDeleteRedundantDialog = true
                        }
                    },
                    showOptionsMenu = showOptionsMenu,
                    onShowOptionsMenuChange = { showOptionsMenu = it }
                )
            }
        }

        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                title = { Text("Clear All History") },
                text = { Text("Are you sure you want to permanently delete all fact-check records? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            historyViewModel.clearAll()
                            showClearAllDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("CLEAR ALL")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }

        if (showBatchDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteDialog = false },
                title = { Text("Delete ${uiState.deleteSelection.size} items?") },
                text = { Text("This action cannot be undone. The selected fact-checks will be permanently removed.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            historyViewModel.deleteSelectedItems()
                            showBatchDeleteDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("DELETE")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }

        if (showDeleteRedundantDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteRedundantDialog = false },
                title = { Text("Delete Redundant Queries") },
                text = { Text("This will delete $redundantCount redundant entries, keeping only the latest one from each group. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            historyViewModel.deleteRedundantExceptLatest()
                            showDeleteRedundantDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("DELETE")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteRedundantDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
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
            
            item {
                Box {
                    AssistChip(
                        onClick = { showSortMenu = true },
                        label = { Text(uiState.currentSort.name.lowercase().capitalize()) },
                        leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.primary,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        shape = CorvusShapes.medium
                    )
                    
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        HistorySort.entries.forEach { sortItem ->
                            DropdownMenuItem(
                                text = { Text(sortItem.name.replace("_", " ").lowercase().capitalize()) },
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    historyViewModel.setSort(sortItem)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (uiState.currentSort == sortItem) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }

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
                    val isPendingDelete = item.id in uiState.pendingDeleteIds
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            when (dismissValue) {
                                SwipeToDismissBoxValue.EndToStart -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    historyViewModel.prepareDelete(item)
                                    true
                                }
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSwipeToEdit(item.claim)
                                    false
                                }
                                else -> false
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = !isPendingDelete,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val isStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                                val scale by animateFloatAsState(
                                    targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) 1f else 0.8f,
                                    label = "dismissScale"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                        .background(
                                            if (isStart) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                            CorvusShapes.medium
                                        )
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = if (isStart) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Icon(
                                        if (isStart) Icons.Default.Edit else Icons.Default.Delete,
                                        contentDescription = if (isStart) "Edit" else "Delete",
                                        tint = if (isStart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.scale(scale)
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true
                        ) {
                            HistoryItem(
                                result = item,
                                isSelected = compareViewModel.isSelected(item.id),
                                isCompareMode = compareUiState.isCompareMode,
                                isDeleteMode = uiState.isDeleteMode,
                                isDeleteSelected = item.id in uiState.deleteSelection,
                                onClick = {
                                    when {
                                        compareUiState.isCompareMode -> compareViewModel.toggleSelection(item)
                                        uiState.isDeleteMode -> historyViewModel.toggleDeleteSelection(item.id)
                                        else -> onItemClick(item)
                                    }
                                },
                                onLongClick = {
                                    try {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } catch (e: Exception) { /* ignore */ }
                                    if (uiState.isDeleteMode) {
                                        historyViewModel.toggleDeleteSelection(item.id)
                                    } else {
                                        compareViewModel.toggleSelection(item)
                                    }
                                },
                                onDelete = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    historyViewModel.prepareDelete(item)
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
                    onDelete = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        historyViewModel.deleteSelected(compareUiState.selectedClaims.map { it.id })
                        compareViewModel.clearSelection()
                    },
                    onCompare = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCompare()
                    }
                )
            }

            AnimatedVisibility(
                visible = uiState.isDeleteMode && uiState.deleteSelection.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
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
                            text = "${uiState.deleteSelection.size} Selected",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        
                        TextButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                historyViewModel.deselectAllForDeletion()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Clear")
                        }
                        
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                showBatchDeleteDialog = true
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                }
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
    result: com.najmi.corvus.domain.model.HistorySummary,
    isSelected: Boolean = false,
    isCompareMode: Boolean = false,
    isDeleteMode: Boolean = false,
    isDeleteSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isItemSelected = isCompareMode && isSelected || isDeleteMode && isDeleteSelected
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isItemSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CorvusShapes.medium)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isItemSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
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
            AnimatedVisibility(visible = isCompareMode || isDeleteMode) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(24.dp)
                        .background(
                            if (isItemSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (isItemSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isItemSelected) {
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
                val verdict = try { Verdict.valueOf(result.verdict) } catch (e: Exception) { Verdict.UNVERIFIABLE }
                val quoteVerdict = try { QuoteVerdict.valueOf(result.verdict) } catch (e: Exception) { QuoteVerdict.UNVERIFIABLE }
                
                when (result.resultType) {
                    "GENERAL" -> VerdictBadgeLarge(verdict = verdict)
                    "QUOTE" -> QuoteVerdictBadgeLarge(verdict = quoteVerdict)
                    "COMPOSITE" -> VerdictBadgeLarge(verdict = verdict)
                    "VIRAL" -> VerdictBadgeLarge(verdict = Verdict.FALSE)
                }
                
                // Harm Indicator Overlay
                val harmLevel = try { com.najmi.corvus.domain.model.HarmLevel.valueOf(result.harmLevel) } catch (e: Exception) { com.najmi.corvus.domain.model.HarmLevel.NONE }
                
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
                    val plausibility = result.plausibilityScore
                    
                    if (plausibility != null) {
                        Text(
                            text = " • $plausibility",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            if (!isCompareMode && !isDeleteMode) {
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
    onDelete: () -> Unit = {},
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
            
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
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

private fun String.capitalize() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@Composable
private fun DeleteModeHeader(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                
                Column {
                    Text(
                        text = if (selectedCount > 0) "$selectedCount selected" else "Select items",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (selectedCount == 0) {
                        Text(
                            text = "Tap items to select",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = if (selectedCount == totalCount) onDeselectAll else onSelectAll,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = if (selectedCount == totalCount) "Deselect All" else "Select All",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                
                if (selectedCount > 0) {
                    FilledIconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                    }
                }
            }
        }
    }
}

@Composable
private fun NormalHeader(
    isAnalyticsVisible: Boolean,
    onToggleAnalytics: () -> Unit,
    onClearAll: () -> Unit,
    onSelectToDelete: () -> Unit,
    onNavigateToRedundant: () -> Unit,
    showOptionsMenu: Boolean,
    onShowOptionsMenuChange: (Boolean) -> Unit
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
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleAnalytics,
                modifier = Modifier.background(
                    if (isAnalyticsVisible) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    CircleShape
                )
            ) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = "Show Analytics",
                    tint = if (isAnalyticsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }

            Box {
                IconButton(onClick = { onShowOptionsMenuChange(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { onShowOptionsMenuChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("Select to Delete") },
                        onClick = {
                            onShowOptionsMenuChange(false)
                            onSelectToDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Redundant") },
                        onClick = {
                            onShowOptionsMenuChange(false)
                            onNavigateToRedundant()
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear All History") },
                        onClick = {
                            onShowOptionsMenuChange(false)
                            onClearAll()
                        },
                        leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) }
                    )
                }
            }
        }
    }
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

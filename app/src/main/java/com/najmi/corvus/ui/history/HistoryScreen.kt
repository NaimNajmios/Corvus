package com.najmi.corvus.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.R
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.viewmodel.CompareViewModel
import com.najmi.corvus.ui.viewmodel.HistoryViewModel
import com.najmi.corvus.domain.model.QuoteVerdict
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
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

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
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background
            ),
            shape = CorvusShapes.small,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

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
                        containerColor = MaterialTheme.colorScheme.background,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            CorvusShapes.small
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
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onItemClick(item)
                                },
                                onDelete = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    historyViewModel.prepareDelete(item)
                                    pendingDeleteItem = item
                                },
                                onToggleCompare = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    compareViewModel.toggleSelection(item)
                                },
                                canToggleCompare = compareUiState.canAddMore || compareViewModel.isSelected(item.id)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = compareUiState.isCompareMode,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onBackground,
                actionColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}


@Composable
fun HistoryItem(
    result: CorvusCheckResult,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleCompare: (() -> Unit)? = null,
    canToggleCompare: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() }
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CorvusShapes.small)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = CorvusShapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (onToggleCompare != null) {
                IconButton(
                    onClick = onToggleCompare,
                    enabled = canToggleCompare,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            when (result) {
                is CorvusCheckResult.GeneralResult -> VerdictBadge(verdict = result.verdict, modifier = Modifier)
                is CorvusCheckResult.QuoteResult -> QuoteVerdictBadge(verdict = result.quoteVerdict, modifier = Modifier)
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.claim,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = formatDate(result.checkedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun VerdictBadge(verdict: Verdict, modifier: Modifier = Modifier) {
    val (color, text) = when (verdict) {
        Verdict.TRUE -> MaterialTheme.colorScheme.primary to "TRUE"
        Verdict.FALSE -> MaterialTheme.colorScheme.error to "FALSE"
        Verdict.MISLEADING -> MaterialTheme.colorScheme.tertiary to "MISLEADING"
        Verdict.PARTIALLY_TRUE -> MaterialTheme.colorScheme.secondary to "PARTIAL"
        Verdict.UNVERIFIABLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) to "UNVERIFIABLE"
        Verdict.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) to "CHECKING"
        Verdict.NOT_A_CLAIM -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) to "NOT CLAIM"
    }
    
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.2f), CorvusShapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun QuoteVerdictBadge(verdict: QuoteVerdict, modifier: Modifier = Modifier) {
    val (color, text) = when (verdict) {
        QuoteVerdict.VERIFIED -> MaterialTheme.colorScheme.primary to "VERIFIED"
        QuoteVerdict.FABRICATED -> MaterialTheme.colorScheme.error to "FABRICATED"
        QuoteVerdict.OUT_OF_CONTEXT -> MaterialTheme.colorScheme.tertiary to "OUT OF CONTEXT"
        QuoteVerdict.PARAPHRASED -> MaterialTheme.colorScheme.secondary to "PARAPHRASED"
        QuoteVerdict.MISATTRIBUTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f) to "MISATTRIBUTED"
        QuoteVerdict.SATIRE_ORIGIN -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f) to "SATIRE"
        QuoteVerdict.UNVERIFIABLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) to "UNVERIFIED"
    }
    
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.2f), CorvusShapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun CompareSelectionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onCompare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Compare,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear selection",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }
            
            androidx.compose.material3.Button(
                onClick = onCompare,
                enabled = selectedCount >= 2,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = CorvusShapes.small
            ) {
                Icon(
                    imageVector = Icons.Default.Compare,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Compare")
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
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

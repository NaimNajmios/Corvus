package com.najmi.corvus.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.domain.model.CorvusResult
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.theme.CorvusBorder
import com.najmi.corvus.ui.theme.CorvusSurface
import com.najmi.corvus.ui.theme.CorvusTextPrimary
import com.najmi.corvus.ui.theme.CorvusTextSecondary
import com.najmi.corvus.ui.theme.CorvusTextTertiary
import com.najmi.corvus.ui.theme.CorvusVoid
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onItemClick: (CorvusResult) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pendingDeleteItem by remember { mutableStateOf<CorvusResult?>(null) }

    LaunchedEffect(pendingDeleteItem) {
        pendingDeleteItem?.let { item ->
            val result = snackbarHostState.showSnackbar(
                message = "Item deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.confirmDelete()
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
            color = CorvusTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.search(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search claims...", color = CorvusTextTertiary) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = CorvusTextTertiary)
            },
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
                        viewModel.filterByVerdict(value)
                    },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CorvusAccent,
                        selectedLabelColor = CorvusVoid,
                        containerColor = CorvusVoid,
                        labelColor = CorvusTextSecondary
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
                                viewModel.prepareDelete(item)
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
                        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it })
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
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onItemClick(item)
                                },
                                onDelete = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.prepareDelete(item)
                                    pendingDeleteItem = item
                                }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = CorvusSurface,
                contentColor = CorvusTextPrimary,
                actionColor = CorvusAccent
            )
        }
    }
}

@Composable
fun HistoryItem(
    result: CorvusResult,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CorvusVoid),
        shape = CorvusShapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            VerdictBadge(verdict = result.verdict, modifier = Modifier)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.claim,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CorvusTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = formatDate(result.checkedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = CorvusTextTertiary
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = CorvusTextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun VerdictBadge(verdict: Verdict, modifier: Modifier = Modifier) {
    val (color, text) = when (verdict) {
        Verdict.TRUE -> CorvusAccent to "TRUE"
        Verdict.FALSE -> MaterialTheme.colorScheme.error to "FALSE"
        Verdict.MISLEADING -> MaterialTheme.colorScheme.tertiary to "MISLEADING"
        Verdict.PARTIALLY_TRUE -> MaterialTheme.colorScheme.secondary to "PARTIAL"
        Verdict.UNVERIFIABLE -> CorvusTextTertiary to "UNVERIFIABLE"
        Verdict.CHECKING -> CorvusTextTertiary to "CHECKING"
        Verdict.NOT_A_CLAIM -> CorvusTextTertiary to "NOT CLAIM"
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
                color = CorvusTextSecondary
            )
            Text(
                text = "Paste a claim in the home tab to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = CorvusTextTertiary
            )
        }
    }
}

@Composable
private fun CrowSearchIllustration() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(CorvusVoid, CorvusShapes.medium)
            .border(1.dp, CorvusBorder, CorvusShapes.medium),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "C",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 40.sp,
                    letterSpacing = 2.sp
                ),
                color = CorvusAccent.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(60.dp, 3.dp)
                    .background(
                        CorvusAccent.copy(alpha = 0.3f),
                        CorvusShapes.extraSmall
                    )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(40.dp, 3.dp)
                    .background(
                        CorvusTextTertiary,
                        CorvusShapes.extraSmall
                    )
            )
        }
    }
}

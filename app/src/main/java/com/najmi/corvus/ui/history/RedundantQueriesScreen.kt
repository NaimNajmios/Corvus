package com.najmi.corvus.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.data.util.RedundantGroup
import com.najmi.corvus.data.util.RedundantQueryDetector
import com.najmi.corvus.domain.model.HistorySummary
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedundantQueriesScreen(
    historyViewModel: HistoryViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by historyViewModel.uiState.collectAsState()
    val redundantGroups = remember(uiState.history) {
        RedundantQueryDetector.findRedundantQueries(uiState.history)
    }
    
    val selectedForDeletion = remember { mutableStateOf<Set<String>>(emptySet()) }
    
    val totalRedundant = RedundantQueryDetector.getTotalRedundantCount(redundantGroups)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Redundant Queries") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (redundantGroups.isEmpty()) {
                EmptyRedundantState()
            } else {
                Text(
                    text = "Found $totalRedundant redundant queries in ${redundantGroups.size} groups",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Select items to delete",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (selectedForDeletion.value.isNotEmpty()) {
                        Button(
                            onClick = { selectedForDeletion.value = emptySet() },
                            colors = ButtonDefaults.textButtonColors()
                        ) {
                            Text("Clear Selection")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(redundantGroups) { group ->
                        RedundantGroupCard(
                            group = group,
                            selectedForDeletion = selectedForDeletion.value,
                            onToggleSelection = { itemId ->
                                selectedForDeletion.value = if (itemId in selectedForDeletion.value) {
                                    selectedForDeletion.value - itemId
                                } else {
                                    selectedForDeletion.value + itemId
                                }
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
        
        if (selectedForDeletion.value.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = CircleShape,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedForDeletion.value.size} selected for deletion",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Button(
                        onClick = {
                            selectedForDeletion.value.forEach { id ->
                                historyViewModel.deleteItem(id)
                            }
                            selectedForDeletion.value = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun RedundantGroupCard(
    group: RedundantGroup,
    selectedForDeletion: Set<String>,
    onToggleSelection: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = CorvusShapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${group.duplicates.size + 1} similar queries",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                OriginalQueryItem(item = group.original, isSelected = false, onToggleSelection = null)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Duplicates:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                group.duplicates.forEach { duplicate ->
                    DuplicateQueryItem(
                        item = duplicate,
                        isSelected = duplicate.id in selectedForDeletion,
                        onToggleSelection = { onToggleSelection(duplicate.id) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun OriginalQueryItem(
    item: HistorySummary,
    isSelected: Boolean,
    onToggleSelection: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                CorvusShapes.small
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.claim,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatDate(item.checkedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Original",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DuplicateQueryItem(
    item: HistorySummary,
    isSelected: Boolean,
    onToggleSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface,
                CorvusShapes.small
            )
            .clickable { onToggleSelection() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface,
                    CircleShape
                )
                .then(
                    if (!isSelected) Modifier.background(
                        MaterialTheme.colorScheme.outline,
                        CircleShape
                    ).padding(2.dp) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.claim,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatDate(item.checkedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyRedundantState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Redundant Queries",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your history is clean with no duplicate or similar queries",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

package com.najmi.corvus.ui.history

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.domain.model.CorvusResult
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.theme.CorvusBorder
import com.najmi.corvus.ui.theme.CorvusTextPrimary
import com.najmi.corvus.ui.theme.CorvusTextSecondary
import com.najmi.corvus.ui.theme.CorvusTextTertiary
import com.najmi.corvus.ui.theme.CorvusVoid
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onItemClick: (CorvusResult) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("History", color = CorvusTextPrimary) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CorvusTextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            actions = {
                if (uiState.history.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearAll() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear all",
                            tint = CorvusTextPrimary
                        )
                    }
                }
            }
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
            val verdictFilters = listOf(null, "TRUE", "FALSE", "MISLEADING", "PARTIALLY_TRUE", "UNVERIFIABLE")
            items(verdictFilters) { verdict ->
                FilterChip(
                    selected = uiState.selectedVerdictFilter == verdict,
                    onClick = { viewModel.filterByVerdict(verdict) },
                    label = { Text(verdict?.replace("_", " ") ?: "All") },
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
                Text(
                    text = "No history yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CorvusTextTertiary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.history) { item ->
                    HistoryItem(
                        result = item,
                        onClick = { onItemClick(item) },
                        onDelete = { viewModel.deleteItem(item.id) }
                    )
                }
            }
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
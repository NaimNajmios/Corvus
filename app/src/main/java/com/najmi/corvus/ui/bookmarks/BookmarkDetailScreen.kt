package com.najmi.corvus.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.data.local.BookmarkEntity
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.viewmodel.BookmarkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkDetailScreen(
    bookmarkId: String,
    viewModel: BookmarkViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToResult: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var bookmarkWithResult by remember { mutableStateOf<Pair<BookmarkEntity, CorvusCheckResult>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var editedNotes by remember { mutableStateOf("") }
    var newTag by remember { mutableStateOf("") }
    var isEditingNotes by remember { mutableStateOf(false) }

    LaunchedEffect(bookmarkId) {
        bookmarkWithResult = viewModel.getBookmarkWithResult(bookmarkId)
        bookmarkWithResult?.let {
            editedNotes = it.first.userNotes
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmark Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            bookmarkWithResult?.let { (bookmark, _) ->
                                if (isEditingNotes) {
                                    viewModel.updateNotes(bookmark.id, editedNotes)
                                }
                                isEditingNotes = !isEditingNotes
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isEditingNotes) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditingNotes) "Save" else "Edit notes"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (bookmarkWithResult == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Bookmark not found")
            }
        } else {
            val (bookmark, result) = bookmarkWithResult!!

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    NotesSection(
                        notes = editedNotes,
                        isEditing = isEditingNotes,
                        onNotesChange = { editedNotes = it },
                        onAddTag = { tag ->
                            if (tag.isNotBlank()) {
                                viewModel.addTag(bookmark.id, tag.trim())
                                newTag = ""
                            }
                        },
                        newTag = newTag,
                        onNewTagChange = { newTag = it },
                        onRemoveTag = { tag ->
                            viewModel.removeTag(bookmark.id, tag)
                        },
                        tags = bookmark.tags.split(",").filter { it.isNotBlank() }
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CorvusShapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Original Claim",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = bookmark.claim,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InfoChip(
                            label = "Verdict",
                            value = bookmark.verdict.replace("_", " "),
                            modifier = Modifier.weight(1f)
                        )
                        InfoChip(
                            label = "Confidence",
                            value = "${(bookmark.confidence * 100).toInt()}%",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToResult(result.id) },
                        shape = CorvusShapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "View Full Result",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesSection(
    notes: String,
    isEditing: Boolean,
    onNotesChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    newTag: String,
    onNewTagChange: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    tags: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Notes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (notes.isNotBlank()) {
                    Text(
                        text = "${notes.length} characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add your notes...") },
                    minLines = 3,
                    maxLines = 6
                )
            } else {
                Text(
                    text = notes.ifBlank { "No notes added" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notes.isBlank()) 
                        MaterialTheme.colorScheme.onSurfaceVariant 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tags) { tag ->
                    InputChip(
                        selected = false,
                        onClick = { },
                        label = { Text(tag.trim()) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onRemoveTag(tag) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove tag",
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = onNewTagChange,
                        modifier = Modifier.width(100.dp),
                        placeholder = { Text("Add tag") },
                        singleLine = true,
                        trailingIcon = {
                            if (newTag.isNotBlank()) {
                                IconButton(
                                    onClick = { onAddTag(newTag) }
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add tag"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

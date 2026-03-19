package com.najmi.corvus.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.data.repository.LlmProvider
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.theme.CorvusBorder
import com.najmi.corvus.ui.theme.CorvusTextPrimary
import com.najmi.corvus.ui.theme.CorvusTextSecondary
import com.najmi.corvus.ui.theme.CorvusTextTertiary
import com.najmi.corvus.ui.theme.CorvusVoid
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showDarkModeMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Settings", color = CorvusTextPrimary) },
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
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection(title = "AI Provider") {
                SettingsItemWithDropdown(
                    icon = Icons.Default.Psychology,
                    title = "Preferred Provider",
                    subtitle = uiState.preferences.preferredProvider.name,
                    onClick = { showProviderMenu = true },
                    dropdownContent = {
                        LlmProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name) },
                                onClick = {
                                    viewModel.setPreferredProvider(provider)
                                    showProviderMenu = false
                                },
                                trailingIcon = {
                                    if (provider == uiState.preferences.preferredProvider) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = CorvusAccent)
                                    }
                                }
                            )
                        }
                    },
                    expanded = showProviderMenu,
                    onDismiss = { showProviderMenu = false }
                )
            }

            SettingsSection(title = "Language") {
                SettingsItemWithDropdown(
                    icon = Icons.Default.Language,
                    title = "Response Language",
                    subtitle = when (uiState.preferences.responseLanguage) {
                        "en" -> "English"
                        "ms" -> "Bahasa Malaysia"
                        else -> "Auto-detect"
                    },
                    onClick = { showLanguageMenu = true },
                    dropdownContent = {
                        listOf("auto" to "Auto-detect", "en" to "English", "ms" to "Bahasa Malaysia").forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    viewModel.setResponseLanguage(code)
                                    showLanguageMenu = false
                                },
                                trailingIcon = {
                                    if (code == uiState.preferences.responseLanguage) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = CorvusAccent)
                                    }
                                }
                            )
                        }
                    },
                    expanded = showLanguageMenu,
                    onDismiss = { showLanguageMenu = false }
                )
            }

            SettingsSection(title = "Appearance") {
                SettingsItemWithDropdown(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Mode",
                    subtitle = when (uiState.preferences.darkMode) {
                        true -> "On"
                        false -> "Off"
                        null -> "System"
                    },
                    onClick = { showDarkModeMenu = true },
                    dropdownContent = {
                        listOf(null to "System", true to "On", false to "Off").forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setDarkMode(value)
                                    showDarkModeMenu = false
                                },
                                trailingIcon = {
                                    if (value == uiState.preferences.darkMode) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = CorvusAccent)
                                    }
                                }
                            )
                        }
                    },
                    expanded = showDarkModeMenu,
                    onDismiss = { showDarkModeMenu = false }
                )

                SettingsToggleItem(
                    icon = Icons.Default.DarkMode,
                    title = "Animations",
                    subtitle = "Enable UI animations",
                    checked = uiState.preferences.showAnimations,
                    onCheckedChange = { viewModel.setShowAnimations(it) }
                )
            }

            SettingsSection(title = "Data") {
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Clear History",
                    subtitle = "${uiState.historyCount} items",
                    onClick = { showClearHistoryDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Corvus v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = CorvusTextTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear History?") },
            text = { Text("This will permanently delete all your fact-check history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = CorvusAccent,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = CorvusVoid),
            shape = CorvusShapes.small
        ) {
            content()
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CorvusTextSecondary
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = CorvusTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CorvusTextTertiary
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = CorvusTextTertiary
        )
    }
}

@Composable
fun SettingsItemWithDropdown(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    dropdownContent: @Composable () -> Unit,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CorvusTextSecondary
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = CorvusTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CorvusTextTertiary
            )
        }
        
        Box {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = CorvusTextTertiary
            )
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismiss
            ) {
                dropdownContent()
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CorvusTextSecondary
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = CorvusTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CorvusTextTertiary
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CorvusVoid,
                checkedTrackColor = CorvusAccent,
                uncheckedThumbColor = CorvusTextTertiary,
                uncheckedTrackColor = CorvusBorder
            )
        )
    }
}
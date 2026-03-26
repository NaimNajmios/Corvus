package com.najmi.corvus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.BuildConfig
import com.najmi.corvus.domain.model.LlmProvider
import com.najmi.corvus.ui.theme.CorvusBorder
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.CorvusVoid
import com.najmi.corvus.ui.theme.CorvusVoidLight
import com.najmi.corvus.ui.theme.ColorPalette
import com.najmi.corvus.ui.theme.Palettes
import com.najmi.corvus.ui.viewmodel.CohereQuotaInfo
import com.najmi.corvus.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showDarkModeMenu by remember { mutableStateOf(false) }
    var showPaletteMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
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
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                    },
                    expanded = showProviderMenu,
                    onDismiss = { showProviderMenu = false }
                )
            }

            SettingsSection(title = "API Keys") {
                Column(modifier = Modifier.padding(16.dp)) {
                    ApiKeyStatusRow("Gemini (AI Studio)", BuildConfig.GEMINI_API_KEY.isNotBlank())
                    Spacer(modifier = Modifier.height(8.dp))
                    ApiKeyStatusRow("Groq", BuildConfig.GROQ_API_KEY.isNotBlank())
                    Spacer(modifier = Modifier.height(8.dp))
                    ApiKeyStatusRow("Cerebras", BuildConfig.CEREBRAS_API_KEY.isNotBlank())
                    Spacer(modifier = Modifier.height(8.dp))
                    ApiKeyStatusRow("OpenRouter", BuildConfig.OPENROUTER_API_KEY.isNotBlank())
                    Spacer(modifier = Modifier.height(8.dp))
                    ApiKeyStatusRow("Mistral", BuildConfig.MISTRAL_API_KEY.isNotBlank())
                    Spacer(modifier = Modifier.height(8.dp))
                    ApiKeyStatusRow("Cohere", BuildConfig.COHERE_API_KEY.isNotBlank())
                }
            }

            SettingsSection(title = "Cohere Quota") {
                CohereQuotaCard(
                    quota = uiState.cohereQuota,
                    modifier = Modifier.padding(16.dp)
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
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                // Dark Mode
                SettingsItemWithDropdown(
                    icon = Icons.Default.DarkMode,
                    title = "Theme Mode",
                    subtitle = when (uiState.preferences.darkMode) {
                        true -> "Always Dark"
                        false -> "Always Light"
                        else -> "System Default"
                    },
                    onClick = { showDarkModeMenu = true },
                    dropdownContent = {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text("System")
                                }
                            },
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setDarkMode(null)
                                showDarkModeMenu = false
                            },
                            trailingIcon = {
                                if (uiState.preferences.darkMode == null) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DarkMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text("On")
                                }
                            },
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setDarkMode(true)
                                showDarkModeMenu = false
                            },
                            trailingIcon = {
                                if (uiState.preferences.darkMode == true) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LightMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text("Off")
                                }
                            },
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setDarkMode(false)
                                showDarkModeMenu = false
                            },
                            trailingIcon = {
                                if (uiState.preferences.darkMode == false) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    },
                    expanded = showDarkModeMenu,
                    onDismiss = { showDarkModeMenu = false },
                    trailingPreview = {
                        ThemePreviewSwatch(
                            isDark = uiState.preferences.darkMode ?: isSystemInDarkTheme()
                        )
                    }
                )

                // Color Palette
                SettingsItemWithDropdown(
                    icon = Icons.Default.Palette,
                    title = "Color Palette",
                    subtitle = uiState.preferences.colorPalette.label,
                    onClick = { showPaletteMenu = true },
                    dropdownContent = {
                        ColorPalette.values().forEach { palette ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val colors = Palettes[palette] ?: Palettes[ColorPalette.MONOCHROME]!!
                                        val isDark = isSystemInDarkTheme()
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(if (isDark) colors.primaryDark else colors.primaryLight)
                                        )
                                        Text(palette.label)
                                    }
                                },
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setColorPalette(palette)
                                    showPaletteMenu = false
                                },
                                trailingIcon = {
                                    if (uiState.preferences.colorPalette == palette) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                    },
                    expanded = showPaletteMenu,
                    onDismiss = { showPaletteMenu = false },
                    trailingPreview = {
                        PalettePreviewSwatch(palette = uiState.preferences.colorPalette)
                    }
                )

                SettingsToggleItem(
                    icon = Icons.Default.PlayArrow,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
    onDismiss: () -> Unit,
    trailingPreview: @Composable (() -> Unit)? = null
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        
        trailingPreview?.invoke()
        
        Box {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
fun PalettePreviewSwatch(palette: ColorPalette) {
    val colors = Palettes[palette] ?: Palettes[ColorPalette.MONOCHROME]!!
    val isDark = isSystemInDarkTheme()
    
    Row(
        modifier = Modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CorvusShapes.extraSmall)
                .background(if (isDark) colors.surfaceDark else colors.surfaceLight)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CorvusShapes.extraSmall)
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CorvusShapes.extraSmall)
                .background(if (isDark) colors.primaryDark else colors.primaryLight)
        )
    }
}

@Composable
fun ThemePreviewSwatch(isDark: Boolean) {
    Row(
        modifier = Modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CorvusShapes.extraSmall)
                .background(if (isDark) CorvusVoid else CorvusVoidLight)
                .border(1.dp, CorvusBorder, CorvusShapes.extraSmall)
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CorvusShapes.extraSmall)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun ApiKeyStatusRow(provider: String, isConfigured: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = provider,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            color = if (isConfigured)
                Color(0xFF10B981).copy(alpha = 0.12f)
            else
                Color(0xFFEF4444).copy(alpha = 0.12f),
            shape = CorvusShapes.extraSmall
        ) {
            Text(
                text = if (isConfigured) "Configured" else "Not set",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (isConfigured) Color(0xFF10B981) else Color(0xFFEF4444)
            )
        }
    }
}


@Composable
fun SettingsItemWithDropdownWithPreview(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    dropdownContent: @Composable () -> Unit,
    expanded: Boolean,
    onDismiss: () -> Unit,
    trailingPreview: @Composable (() -> Unit)? = null
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        
        trailingPreview?.invoke()
        
        Box {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                uncheckedTrackColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun CohereQuotaCard(
    quota: CohereQuotaInfo,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Command-R",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            QuotaProgressBar(
                used = quota.dailyCallsR,
                limit = quota.dailyLimitR
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Command-R+",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            QuotaProgressBar(
                used = quota.dailyCallsRPlus,
                limit = quota.dailyLimitRPlus
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Monthly Total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            QuotaProgressBar(
                used = quota.monthlyCalls,
                limit = quota.monthlyLimit
            )
        }

        Text(
            text = "Resets daily at midnight and monthly on the 1st",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun QuotaProgressBar(
    used: Int,
    limit: Int,
    modifier: Modifier = Modifier
) {
    val progress = (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    val color = when {
        progress >= 0.9f -> Color(0xFFEF4444)
        progress >= 0.7f -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$used/$limit",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = modifier
                .width(60.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
package com.najmi.corvus.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.ui.components.TokenUsageDashboard
import com.najmi.corvus.ui.settings.ProviderQuotaCard
import com.najmi.corvus.ui.settings.SettingsSection
import com.najmi.corvus.ui.viewmodel.TokenUsageViewModel
import com.najmi.corvus.ui.viewmodel.UsageViewModel

@Composable
fun UsageScreen(
    viewModel: UsageViewModel = hiltViewModel(),
    tokenViewModel: TokenUsageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tokenUsageState by tokenViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Usage",
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
            SettingsSection(title = "Token Analytics") {
                TokenUsageDashboard(
                    uiState = tokenUsageState,
                    modifier = Modifier.padding(16.dp)
                )
            }

            SettingsSection(title = "API Quotas") {
                if (uiState.apiQuotas.isNotEmpty()) {
                    uiState.apiQuotas.forEach { quota ->
                        ProviderQuotaCard(
                            quota = quota,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = "No API keys configured. Add an API key in Settings to track quota usage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Box(modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

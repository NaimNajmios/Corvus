package com.najmi.corvus

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.najmi.corvus.data.local.UserPreferencesRepository
import com.najmi.corvus.ui.components.ShareConfirmBottomSheet
import com.najmi.corvus.ui.theme.ColorPalette
import com.najmi.corvus.ui.theme.CorvusTheme
import com.najmi.corvus.ui.viewmodel.CorvusViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ShareBottomSheetActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val viewModel: CorvusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make activity transition transparent
        overridePendingTransition(0, 0)

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain" || sharedText.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            var isProcessing by remember { mutableStateOf(false) }

            val preferences by userPreferencesRepository.preferences.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = preferences?.darkMode ?: systemDark
            val colorPalette = preferences?.colorPalette ?: ColorPalette.MONOCHROME

            CorvusTheme(darkTheme = darkTheme, colorPalette = colorPalette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    ShareConfirmBottomSheet(
                        sharedText = sharedText,
                        isLoading = isProcessing,
                        onConfirm = {
                            isProcessing = true
                            val textToAnalyze = sharedText
                            val truncatedText = if (textToAnalyze.length > 500) {
                                textToAnalyze.take(500)
                            } else {
                                textToAnalyze
                            }
                            viewModel.analyzeInBackground(truncatedText)
                            Handler(Looper.getMainLooper()).postDelayed({
                                finishAndRemoveTask()
                            }, 400)
                        },
                        onDismiss = {
                            finishAndRemoveTask()
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun ErrorFallbackScreen(
        message: String,
        onRetry: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Something went wrong",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        // No exit animation
        overridePendingTransition(0, 0)
    }
}

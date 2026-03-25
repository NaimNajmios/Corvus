package com.najmi.corvus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            val preferences by userPreferencesRepository.preferences.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = preferences?.darkMode ?: systemDark
            val colorPalette = preferences?.colorPalette ?: ColorPalette.MONOCHROME

            CorvusTheme(darkTheme = darkTheme, colorPalette = colorPalette) {
                // Background is transparent to show the app underneath
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    ShareConfirmBottomSheet(
                        sharedText = sharedText,
                        onConfirm = {
                            val textToAnalyze = sharedText
                            val truncatedText = if (textToAnalyze.length > 500) {
                                textToAnalyze.take(500)
                            } else {
                                textToAnalyze
                            }
                            // viewModel.analyzeInBackground handles enqueuing the worker
                            viewModel.analyzeInBackground(truncatedText)
                            finishAndRemoveTask()
                        },
                        onDismiss = {
                            finishAndRemoveTask()
                        }
                    )
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

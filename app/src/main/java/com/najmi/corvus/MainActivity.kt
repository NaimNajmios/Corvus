package com.najmi.corvus

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.najmi.corvus.data.local.UserPreferencesRepository
import com.najmi.corvus.ui.theme.CorvusTheme
import com.najmi.corvus.ui.theme.ColorPalette
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    
    var sharedText by mutableStateOf<String?>(null)
    var instantAnalyze by mutableStateOf(false)
    var initialResultId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        handleIntent(intent)
        
        setContent {
            val preferences by userPreferencesRepository.preferences.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = preferences?.darkMode ?: systemDark
            val colorPalette = preferences?.colorPalette ?: ColorPalette.MONOCHROME

            CorvusTheme(darkTheme = darkTheme, colorPalette = colorPalette) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        // Handle result if needed, but for now we just proceed
                    }
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    CorvusApp(
                        sharedText = sharedText,
                        instantAnalyze = instantAnalyze,
                        initialResultId = initialResultId,
                        onSharedTextProcessed = { 
                            sharedText = null
                            instantAnalyze = false
                            initialResultId = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    // Detect if started via FactCheckShareActivity alias
                    instantAnalyze = intent.component?.className?.endsWith("FactCheckShareActivity") == true
                }
            }
        }
        
        // Handle resultId from notification
        val resultId = intent?.getStringExtra("resultId")
        if (resultId != null) {
            initialResultId = resultId
        }
    }

}

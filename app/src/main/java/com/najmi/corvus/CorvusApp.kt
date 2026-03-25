package com.najmi.corvus

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.najmi.corvus.ui.compare.CompareScreen
import com.najmi.corvus.ui.history.HistoryScreen
import com.najmi.corvus.ui.input.InputScreen
import com.najmi.corvus.ui.result.LoadingScreen
import com.najmi.corvus.ui.result.ResultScreen
import com.najmi.corvus.ui.settings.SettingsScreen
import com.najmi.corvus.ui.viewmodel.CorvusViewModel
import kotlinx.coroutines.launch

object Routes {
    const val INPUT = "input"
    const val LOADING = "loading"
    const val RESULT = "result"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val COMPARE = "compare"
}

data class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

@Composable
fun CorvusApp(
    modifier: Modifier = Modifier,
    sharedText: String? = null,
    instantAnalyze: Boolean = false,
    initialResultId: String? = null,
    onSharedTextProcessed: () -> Unit = {},
    viewModel: CorvusViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem(
            title = "Home",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            route = Routes.INPUT
        ),
        BottomNavItem(
            title = "History",
            selectedIcon = Icons.Filled.History,
            unselectedIcon = Icons.Outlined.History,
            route = Routes.HISTORY
        ),
        BottomNavItem(
            title = "Settings",
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            route = Routes.SETTINGS
        )
    )

    val showBottomBar = currentRoute in listOf(Routes.INPUT, Routes.HISTORY, Routes.SETTINGS)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sharedText, instantAnalyze) {
        if (sharedText != null) {
            val textToShow = if (sharedText.length > 500) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar(
                    message = "Text truncated to 500 characters",
                    duration = SnackbarDuration.Short
                )
                sharedText.take(500)
            } else {
                sharedText
            }
            viewModel.updateInputText(textToShow)
            
            if (instantAnalyze) {
                viewModel.analyze()
                navController.navigate(Routes.LOADING)
            } else {
                snackbarHostState.showSnackbar(
                    message = "Text received from another app",
                    duration = SnackbarDuration.Short
                )
            }
            
            onSharedTextProcessed()
        }
    }

    LaunchedEffect(initialResultId) {
        initialResultId?.let {
            viewModel.loadResultById(it)
            onSharedTextProcessed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.INPUT) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val startDestination = remember { if (initialResultId != null) Routes.RESULT else Routes.INPUT }
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable(Routes.INPUT) {
                    InputScreen(
                        viewModel = viewModel,
                        onAnalyze = {
                            navController.navigate(Routes.LOADING)
                        }
                    )
                }
                
                composable(Routes.LOADING) {
                    LoadingScreen(
                        currentStep = uiState.currentStep,
                        error = uiState.error,
                        onRetry = {
                            navController.popBackStack()
                        },
                        onCancel = {
                            viewModel.cancelAnalysis()
                            navController.popBackStack()
                        },
                        onMinimize = {
                            val activity = context as? Activity
                            activity?.moveTaskToBack(true)
                        },
                        onResultReady = {
                            navController.navigate(Routes.RESULT) {
                                popUpTo(Routes.INPUT) { inclusive = false }
                            }
                        }
                    )
                }
                
                composable(Routes.RESULT) {
                    ResultScreen(
                        viewModel = viewModel,
                        onAnalyzeAnother = {
                            navController.navigate(Routes.INPUT) {
                                popUpTo(Routes.INPUT) { inclusive = true }
                            }
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onItemClick = { result ->
                            viewModel.updateInputText(result.claim)
                            navController.navigate(Routes.INPUT)
                        },
                        onCompare = {
                            navController.navigate(Routes.COMPARE)
                        }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen()
                }
                
                composable(Routes.COMPARE) {
                    CompareScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.result, uiState.error, uiState.isLoading) {
        val route = navController.currentBackStackEntry?.destination?.route
        
        if (route == Routes.LOADING) {
            when {
                uiState.result != null && !uiState.isLoading -> {
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.INPUT) { inclusive = false }
                    }
                }
                uiState.error != null && !uiState.isLoading -> {
                    navController.popBackStack()
                }
            }
        }
    }
}

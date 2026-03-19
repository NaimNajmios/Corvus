package com.najmi.corvus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.najmi.corvus.ui.history.HistoryScreen
import com.najmi.corvus.ui.input.InputScreen
import com.najmi.corvus.ui.result.LoadingScreen
import com.najmi.corvus.ui.result.ResultScreen
import com.najmi.corvus.ui.settings.SettingsScreen
import com.najmi.corvus.ui.theme.CorvusAccent
import com.najmi.corvus.ui.viewmodel.CorvusViewModel

object Routes {
    const val INPUT = "input"
    const val LOADING = "loading"
    const val RESULT = "result"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
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
    viewModel: CorvusViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
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

    LaunchedEffect(sharedText) {
        if (sharedText != null && uiState.inputText.isEmpty()) {
            viewModel.updateInputText(sharedText)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        val indicatorColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            animationSpec = tween(200),
                            label = "indicatorColor"
                        )
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
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            color = indicatorColor,
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .then(
                                            if (selected) Modifier.border(
                                                width = 1.5.dp,
                                                color = CorvusAccent.copy(alpha = 0.6f),
                                                shape = MaterialTheme.shapes.small
                                            ) else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            label = { Text(item.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CorvusAccent,
                                selectedTextColor = CorvusAccent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
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
            NavHost(
                navController = navController,
                startDestination = Routes.INPUT
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
                        onResultReady = {
                            navController.navigate(Routes.RESULT) {
                                popUpTo(Routes.INPUT) { inclusive = true }
                            }
                        }
                    )
                }
                
                composable(Routes.RESULT) {
                    ResultScreen(
                        viewModel = viewModel,
                        onAnalyzeAnother = {
                            viewModel.reset()
                            navController.navigate(Routes.INPUT) {
                                popUpTo(Routes.INPUT) { inclusive = true }
                            }
                        },
                        onBack = {
                            viewModel.reset()
                            navController.navigate(Routes.INPUT) {
                                popUpTo(Routes.INPUT) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onItemClick = { result ->
                            viewModel.updateInputText(result.claim)
                            navController.navigate(Routes.INPUT)
                        }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen()
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
                        popUpTo(Routes.INPUT) { inclusive = true }
                    }
                }
                uiState.error != null && !uiState.isLoading -> {
                    navController.popBackStack()
                }
            }
        }
    }
}

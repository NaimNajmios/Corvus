package com.najmi.corvus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.najmi.corvus.ui.input.InputScreen
import com.najmi.corvus.ui.result.LoadingScreen
import com.najmi.corvus.ui.result.ResultScreen
import com.najmi.corvus.ui.viewmodel.CorvusViewModel

object Routes {
    const val INPUT = "input"
    const val LOADING = "loading"
    const val RESULT = "result"
}

@Composable
fun CorvusApp(
    modifier: Modifier = Modifier,
    viewModel: CorvusViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.INPUT,
        modifier = modifier
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
                onResultReady = {
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.LOADING) { inclusive = true }
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
                        popUpTo(Routes.RESULT) { inclusive = true }
                    }
                }
            )
        }
    }

    LaunchedEffect(uiState.result) {
        if (uiState.result != null && !uiState.isLoading) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute == Routes.LOADING) {
                navController.navigate(Routes.RESULT) {
                    popUpTo(Routes.LOADING) { inclusive = true }
                    popUpTo(Routes.INPUT) { inclusive = true }
                }
            }
        }
    }
}

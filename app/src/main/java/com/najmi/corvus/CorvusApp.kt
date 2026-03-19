package com.najmi.corvus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.najmi.corvus.ui.input.InputScreen
import com.najmi.corvus.ui.result.ResultScreen
import com.najmi.corvus.ui.viewmodel.CorvusViewModel

object Routes {
    const val INPUT = "input"
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
                    navController.navigate(Routes.RESULT) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                viewModel = viewModel,
                onAnalyzeAnother = {
                    viewModel.reset()
                    navController.popBackStack()
                }
            )
        }
    }
}

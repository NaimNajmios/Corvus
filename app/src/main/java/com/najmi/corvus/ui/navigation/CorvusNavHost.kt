package com.najmi.corvus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.najmi.corvus.ui.input.InputScreen
import com.najmi.corvus.ui.result.ResultScreen

object Routes {
    const val INPUT = "input"
    const val RESULT = "result"
}

@Composable
fun CorvusNavHost(
    navController: NavHostController,
    onAnalyze: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Routes.INPUT
    ) {
        composable(Routes.INPUT) {
            InputScreen(onAnalyze = onAnalyze)
        }
        composable(Routes.RESULT) {
            ResultScreen(
                onAnalyzeAnother = {
                    navController.popBackStack()
                }
            )
        }
    }
}

package com.najmi.corvus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.najmi.corvus.ui.input.InputScreen
import com.najmi.corvus.ui.result.HumanReviewScreen
import com.najmi.corvus.ui.result.ResultScreen
import com.najmi.corvus.ui.viewmodel.CorvusViewModel

object Routes {
    const val INPUT = "input"
    const val RESULT = "result"
    const val HUMAN_REVIEW = "human_review"
}

@Composable
fun CorvusNavHost(
    navController: NavHostController,
    onAnalyze: () -> Unit
) {
    val viewModel: CorvusViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

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
                },
                onRequestHumanReview = {
                    viewModel.requestHumanReview()
                    navController.navigate(Routes.HUMAN_REVIEW)
                }
            )
        }
        composable(Routes.HUMAN_REVIEW) {
            HumanReviewScreen(
                claim = uiState.result?.claim ?: "",
                onDismiss = {
                    viewModel.dismissHumanReviewScreen()
                    navController.popBackStack()
                },
                onSubmit = { additionalInfo ->
                    // Submit to API for human review
                    viewModel.dismissHumanReviewScreen()
                    navController.popBackStack()
                }
            )
        }
    }
}

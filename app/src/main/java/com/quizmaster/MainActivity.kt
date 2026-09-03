package com.quizmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.quizmaster.ui.HomeScreen
import com.quizmaster.ui.QuizListScreen
import com.quizmaster.ui.QuizScreen
import com.quizmaster.ui.theme.QuizMasterTheme
import com.quizmaster.viewmodel.QuizViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: QuizViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuizMasterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToQuizList = { navController.navigate("quizList") },
                                onNavigateToQuiz = { quizSetId ->
                                    viewModel.loadQuestions(quizSetId)
                                    navController.navigate("quiz/$quizSetId")
                                }
                            )
                        }
                        composable("quizList") {
                            QuizListScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onSelectQuiz = { quizSetId ->
                                    viewModel.loadQuestions(quizSetId)
                                    navController.navigate("quiz/$quizSetId")
                                }
                            )
                        }
                        composable("quiz/{quizSetId}") {
                            QuizScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

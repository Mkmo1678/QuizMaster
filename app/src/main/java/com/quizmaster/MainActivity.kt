package com.quizmaster

import android.content.Intent
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
import com.quizmaster.helper.ScreenCaptureHelper
import com.quizmaster.ui.HomeScreen
import com.quizmaster.ui.QuizListScreen
import com.quizmaster.ui.QuizScreen
import com.quizmaster.ui.theme.QuizMasterTheme
import com.quizmaster.viewmodel.QuizViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: QuizViewModel by viewModels()

    companion object {
        private const val REQUEST_SCREEN_CAPTURE = 1001
    }

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
                                onRequestCapturePermission = { requestScreenCapture() },
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

    private fun requestScreenCapture() {
        ScreenCaptureHelper.requestCapturePermission(this, REQUEST_SCREEN_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            ScreenCaptureHelper.onCaptureResult(this, resultCode, data)
            if (resultCode == Activity.RESULT_OK) {
                android.widget.Toast.makeText(this, "截图权限已开启", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "截图权限被拒绝", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

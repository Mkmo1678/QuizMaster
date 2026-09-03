package com.quizmaster

import android.app.Application
import com.quizmaster.data.AppDatabase

class QuizApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}

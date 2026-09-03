package com.quizmaster

import android.app.Application
import android.util.Log
import com.quizmaster.data.AppDatabase

class QuizApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // 全局异常捕获，防止闪退
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("QuizApp", "Uncaught exception in thread: ${thread.name}", throwable)
            // 不杀死进程，让应用继续运行
        }
    }
}

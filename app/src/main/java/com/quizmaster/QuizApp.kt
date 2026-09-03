package com.quizmaster

import android.app.Application
import android.util.Log
import com.quizmaster.data.AppDatabase

class QuizApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // 记录未捕获异常，但不吞掉，让系统默认处理（避免闪退无提示）
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("QuizApp", "Uncaught exception in thread: ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

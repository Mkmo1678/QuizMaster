package com.quizmaster

import android.app.Application
import android.util.Log
import com.quizmaster.data.AppDatabase
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuizApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    companion object {
        lateinit var instance: QuizApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 记录未捕获异常到文件，同时不吞掉异常
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(thread, throwable)
            } catch (e: Exception) {
                Log.e("QuizApp", "Failed to save crash log", e)
            }
            Log.e("QuizApp", "Uncaught exception in thread: ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        try {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val log = "=== Crash at $time ===\nThread: ${thread.name}\n${sw}\n\n"

            val logFile = File(cacheDir, "crash_log.txt")
            logFile.appendText(log)
            Log.d("QuizApp", "Crash log saved to: ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("QuizApp", "Failed to write crash log", e)
        }
    }

    fun getCrashLog(): String {
        return try {
            val logFile = File(cacheDir, "crash_log.txt")
            if (logFile.exists()) logFile.readText() else "无崩溃日志"
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }

    fun clearCrashLog() {
        try {
            File(cacheDir, "crash_log.txt").delete()
        } catch (e: Exception) {
        }
    }
}

package com.quizmaster.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.quizmaster.QuizApp
import com.quizmaster.data.Question
import com.quizmaster.helper.OcrUtil
import com.quizmaster.helper.QuestionMatcher
import com.quizmaster.helper.ScreenCaptureHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "FloatingWindow"
        private const val CHANNEL_ID = "quiz_master_floating"
        private const val NOTIFICATION_ID = 1001

        var currentQuizSetId: Long = -1
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var floatingButton: View? = null
    private var resultPanel: View? = null
    private var resultText: TextView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        // Android 14+ 需要指定 foregroundServiceType 为 mediaProjection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                Log.e(TAG, "startForeground with type failed", e)
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // 在前台服务中创建MediaProjection（Android 14+要求）
        val initSuccess = ScreenCaptureHelper.initMediaProjection(this)
        Log.d(TAG, "MediaProjection init in service: $initSuccess, error: ${ScreenCaptureHelper.lastError}")
        if (!initSuccess) {
            Toast.makeText(this, "截图初始化失败: ${ScreenCaptureHelper.lastError}", Toast.LENGTH_LONG).show()
        }

        isRunning = true
        showFloatingButton()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getLongExtra("quizSetId", -1)?.let {
            if (it > 0) currentQuizSetId = it
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "题库助手悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("题库助手运行中")
            .setContentText("点击悬浮按钮识别题目")
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .build()
    }

    private fun showFloatingButton() {
        if (floatingButton != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        val button = Button(this).apply {
            this.text = "识题"
            textSize = 14f
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xFF1565C0.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(button, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                        button.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        button.setOnClickListener {
            recognizeQuestion()
        }

        floatingButton = button
        windowManager.addView(button, params)
    }

    private fun recognizeQuestion() {
        if (!ScreenCaptureHelper.hasPermission()) {
            Toast.makeText(this, "请先在应用内开启截图权限", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "正在识别题目...", Toast.LENGTH_SHORT).show()

        serviceScope.launch {
            try {
                // 1. 截图
                val bitmap = ScreenCaptureHelper.captureScreen(this@FloatingWindowService)

                if (bitmap == null) {
                    showResult("截图失败，请检查截图权限")
                    return@launch
                }

                // 2. OCR识别
                val ocrText = withContext(Dispatchers.IO) {
                    OcrUtil.recognizeText(bitmap)
                }

                Log.d(TAG, "OCR text: ${ocrText.take(200)}")

                if (ocrText.isBlank()) {
                    showResult("未识别到文字")
                    return@launch
                }

                // 3. 加载题库
                val questions = loadQuestions()

                if (questions.isEmpty()) {
                    showResult("题库为空，请先导入题库")
                    return@launch
                }

                // 4. 匹配题目
                val matches = withContext(Dispatchers.Default) {
                    QuestionMatcher.matchQuestion(ocrText, questions)
                }

                if (matches.isEmpty()) {
                    showResult("未在题库中找到匹配题目\n\n识别内容:\n${ocrText.take(200)}")
                    return@launch
                }

                // 5. 显示结果
                val bestMatch = matches[0]
                val resultText = buildResultText(bestMatch.question, bestMatch.score)
                showResult(resultText, bestMatch.question)

            } catch (e: Exception) {
                Log.e(TAG, "Recognition failed", e)
                showResult("识别出错: ${e.message}")
            }
        }
    }

    private suspend fun loadQuestions(): List<Question> {
        return if (currentQuizSetId > 0) {
            val db = (application as QuizApp).database
            withContext(Dispatchers.IO) {
                db.questionDao().getQuestionsBySetOnce(currentQuizSetId)
            }
        } else {
            emptyList()
        }
    }

    private fun buildResultText(question: Question, score: Double): String {
        val sb = StringBuilder()
        sb.appendLine("匹配度: ${(score * 100).toInt()}%")
        sb.appendLine()
        sb.appendLine("题目: ${question.content.take(100)}")
        sb.appendLine()

        when (question.type) {
            com.quizmaster.data.QuestionType.SINGLE_CHOICE,
            com.quizmaster.data.QuestionType.MULTIPLE_CHOICE -> {
                val answerContent = question.answerContent.ifBlank { question.answer }
                sb.appendLine("正确答案: $answerContent")
            }
            com.quizmaster.data.QuestionType.TRUE_FALSE -> {
                sb.appendLine("正确答案: ${question.answer}")
            }
            com.quizmaster.data.QuestionType.FILL_BLANK -> {
                sb.appendLine("答案: ${question.answerContent.ifBlank { question.answer }}")
            }
            com.quizmaster.data.QuestionType.SHORT_ANSWER,
            com.quizmaster.data.QuestionType.ESSAY -> {
                sb.appendLine("参考答案:")
                sb.appendLine(question.answer.take(300))
            }
        }

        if (question.analysis.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("解析: ${question.analysis.take(100)}")
        }

        return sb.toString()
    }

    private fun showResult(text: String, question: Question? = null) {
        hideResultPanel()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM
        params.y = 100

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(32, 24, 32, 24)
            elevation = 16f
        }

        val titleView = TextView(this).apply {
            this.text = "题目答案"
            textSize = 18f
            setTextColor(0xFF1565C0.toInt())
            paint.isFakeBoldText = true
        }

        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(0xFF333333.toInt())
            setLineSpacing(8f, 1f)
        }
        scrollView.addView(textView)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val copyButton = Button(this).apply {
            this.text = "复制答案"
            textSize = 13f
            setOnClickListener {
                val answer = question?.answerContent?.ifBlank { question.answer } ?: text
                copyToClipboard(answer)
                Toast.makeText(this@FloatingWindowService, "已复制", Toast.LENGTH_SHORT).show()
            }
        }

        val closeButton = Button(this).apply {
            this.text = "关闭"
            textSize = 13f
            setOnClickListener {
                hideResultPanel()
            }
        }

        buttonLayout.addView(copyButton)
        buttonLayout.addView(closeButton)

        panel.addView(titleView)
        panel.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            600
        ))
        panel.addView(buttonLayout)

        resultText = textView
        resultPanel = panel
        windowManager.addView(panel, params)
    }

    private fun hideResultPanel() {
        resultPanel?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        resultPanel = null
        resultText = null
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("答案", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        floatingButton?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        hideResultPanel()
        floatingButton = null
    }
}

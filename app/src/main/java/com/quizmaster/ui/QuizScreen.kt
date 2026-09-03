package com.quizmaster.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quizmaster.data.Question
import com.quizmaster.data.QuestionType
import com.quizmaster.parser.QuestionParser
import com.quizmaster.viewmodel.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: QuizViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val questions by viewModel.currentQuestions.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val userAnswers by viewModel.userAnswers.collectAsState()
    val showAnswer by viewModel.showAnswer.collectAsState()

    if (questions.isEmpty()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("答题") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val currentQuestion = questions[currentIndex]
    val userAnswer = userAnswers[currentQuestion.id] ?: ""

    // 显示答案时自动勾选正确选项
    LaunchedEffect(showAnswer, currentQuestion.id) {
        if (showAnswer) {
            val autoAnswer = when (currentQuestion.type) {
                QuestionType.SINGLE_CHOICE -> {
                    val options = QuestionParser.parseOptions(currentQuestion.options)
                    val labels = listOf("A", "B", "C", "D", "E", "F", "G", "H")
                    options.indexOfFirst { isOptionCorrect(it, currentQuestion.answerContent) }
                        .takeIf { it >= 0 }?.let { labels[it] } ?: ""
                }
                QuestionType.MULTIPLE_CHOICE -> {
                    val options = QuestionParser.parseOptions(currentQuestion.options)
                    val labels = listOf("A", "B", "C", "D", "E", "F", "G", "H")
                    val correctAnswers = currentQuestion.answerContent.split("|||").map { it.trim() }
                    options.mapIndexedNotNull { idx, opt ->
                        if (correctAnswers.any { isOptionCorrect(opt, it) }) labels[idx] else null
                    }.joinToString("")
                }
                QuestionType.TRUE_FALSE -> currentQuestion.answer
                QuestionType.FILL_BLANK -> currentQuestion.answerContent.ifBlank { currentQuestion.answer }
                else -> ""
            }
            if (autoAnswer.isNotBlank()) {
                viewModel.setAnswer(currentQuestion.id, autoAnswer)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "第 ${currentIndex + 1} / ${questions.size} 题",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            getTypeName(currentQuestion.type),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentIndex = currentIndex,
                total = questions.size,
                onPrev = { viewModel.prevQuestion() },
                onNext = { viewModel.nextQuestion() },
                onJump = { viewModel.jumpTo(it) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 进度条
            LinearProgressIndicator(
                progress = (currentIndex + 1).toFloat() / questions.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 题型标签
            AssistChip(
                onClick = {},
                label = { Text(getTypeName(currentQuestion.type)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = getTypeColor(currentQuestion.type),
                    labelColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 题干
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    text = currentQuestion.content,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 答题区域
            when (currentQuestion.type) {
                QuestionType.SINGLE_CHOICE -> SingleChoiceQuestion(
                    question = currentQuestion,
                    selectedAnswer = userAnswer,
                    showAnswer = showAnswer,
                    onSelect = { viewModel.setAnswer(currentQuestion.id, it) }
                )
                QuestionType.MULTIPLE_CHOICE -> MultipleChoiceQuestion(
                    question = currentQuestion,
                    selectedAnswers = userAnswer,
                    showAnswer = showAnswer,
                    onSelect = { viewModel.setAnswer(currentQuestion.id, it) }
                )
                QuestionType.TRUE_FALSE -> TrueFalseQuestion(
                    question = currentQuestion,
                    selectedAnswer = userAnswer,
                    showAnswer = showAnswer,
                    onSelect = { viewModel.setAnswer(currentQuestion.id, it) }
                )
                QuestionType.FILL_BLANK -> FillBlankQuestion(
                    question = currentQuestion,
                    showAnswer = showAnswer,
                    userAnswer = userAnswer,
                    onAnswerChange = { viewModel.setAnswer(currentQuestion.id, it) }
                )
                QuestionType.SHORT_ANSWER, QuestionType.ESSAY -> ShortAnswerQuestion(
                    question = currentQuestion,
                    showAnswer = showAnswer,
                    context = context
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 显示答案按钮
            OutlinedButton(
                onClick = { viewModel.toggleShowAnswer() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (showAnswer) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showAnswer) "隐藏答案" else "显示答案")
            }

            // 答案和解析
            if (showAnswer) {
                Spacer(modifier = Modifier.height(16.dp))
                AnswerCard(question = currentQuestion, context = context)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SingleChoiceQuestion(
    question: Question,
    selectedAnswer: String,
    showAnswer: Boolean,
    onSelect: (String) -> Unit
) {
    val options = QuestionParser.parseOptions(question.options)
    val optionLabels = listOf("A", "B", "C", "D", "E", "F", "G", "H")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { index, option ->
            val label = optionLabels.getOrElse(index) { (index + 65).toChar().toString() }
            val isSelected = selectedAnswer == label
            // 按答案内容匹配，而不是按字母
            val isCorrect = showAnswer && isOptionCorrect(option, question.answerContent)
            val isWrong = showAnswer && isSelected && !isCorrect

            OptionCard(
                label = label,
                text = option,
                isSelected = isSelected,
                isCorrect = isCorrect,
                isWrong = isWrong,
                onClick = { onSelect(label) }
            )
        }
    }
}

// 判断选项是否是正确答案（按内容模糊匹配）
fun isOptionCorrect(option: String, answerContent: String): Boolean {
    if (answerContent.isBlank()) return false
    val opt = option.trim()
    val ans = answerContent.trim()
    // 精确匹配
    if (opt == ans) return true
    // 去除空格后匹配
    if (opt.replace(" ", "") == ans.replace(" ", "")) return true
    // 包含匹配（答案内容在选项中）
    if (opt.contains(ans) || ans.contains(opt)) return true
    return false
}

@Composable
fun MultipleChoiceQuestion(
    question: Question,
    selectedAnswers: String,
    showAnswer: Boolean,
    onSelect: (String) -> Unit
) {
    val options = QuestionParser.parseOptions(question.options)
    val optionLabels = listOf("A", "B", "C", "D", "E", "F", "G", "H")
    val selectedSet = selectedAnswers.toCharArray().toSet()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "多选题（可多选）",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.forEachIndexed { index, option ->
            val label = optionLabels.getOrElse(index) { (index + 65).toChar().toString() }
            val isSelected = selectedSet.contains(label[0])
            // 多选题按内容匹配
            val correctAnswers = question.answerContent.split("|||").map { it.trim() }
            val isCorrect = showAnswer && correctAnswers.any { isOptionCorrect(option, it) }
            val isWrong = showAnswer && isSelected && !isCorrect

            OptionCard(
                label = label,
                text = option,
                isSelected = isSelected,
                isCorrect = isCorrect,
                isWrong = isWrong,
                onClick = {
                    val newSet = selectedSet.toMutableSet()
                    if (newSet.contains(label[0])) {
                        newSet.remove(label[0])
                    } else {
                        newSet.add(label[0])
                    }
                    onSelect(newSet.sorted().joinToString(""))
                }
            )
        }
        if (selectedAnswers.isNotBlank()) {
            Text(
                "已选: $selectedAnswers",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun TrueFalseQuestion(
    question: Question,
    selectedAnswer: String,
    showAnswer: Boolean,
    onSelect: (String) -> Unit
) {
    val options = listOf("正确" to Icons.Default.Check, "错误" to Icons.Default.Close)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEach { (text, icon) ->
            val isSelected = selectedAnswer == text
            val isCorrect = showAnswer && question.answer == text
            val isWrong = showAnswer && isSelected && !isCorrect

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(text) },
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isCorrect -> Color(0xFFE8F5E9)
                        isWrong -> Color(0xFFFFEBEE)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                border = if (isSelected || isCorrect || isWrong)
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = when {
                            isCorrect -> Color(0xFF2E7D32)
                            isWrong -> Color(0xFFC62828)
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun FillBlankQuestion(
    question: Question,
    showAnswer: Boolean,
    userAnswer: String,
    onAnswerChange: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = userAnswer,
            onValueChange = onAnswerChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("你的答案") },
            placeholder = { Text("输入填空答案...") },
            minLines = 2
        )
        if (showAnswer) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8F5E9)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "正确答案",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        question.answer,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ShortAnswerQuestion(
    question: Question,
    showAnswer: Boolean,
    context: Context
) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "参考答案",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Button(
                        onClick = {
                            copyToClipboard(context, question.answer)
                            Toast.makeText(context, "答案已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("复制答案", fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (showAnswer || question.type == QuestionType.SHORT_ANSWER || question.type == QuestionType.ESSAY) {
                    Text(
                        question.answer.ifBlank { "暂无参考答案，请自行作答后点击下方按钮复制你的答案" },
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    )
                } else {
                    Text(
                        "点击「显示答案」查看参考答案，或使用上方按钮直接复制",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (question.analysis.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF8E1)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "解析",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF57F17),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        question.analysis,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OptionCard(
    label: String,
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    isWrong: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isCorrect -> Color(0xFFE8F5E9)
        isWrong -> Color(0xFFFFEBEE)
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isCorrect -> Color(0xFF2E7D32)
        isWrong -> Color(0xFFC62828)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val labelBg = when {
        isCorrect -> Color(0xFF2E7D32)
        isWrong -> Color(0xFFC62828)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected || isCorrect || isWrong) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(labelBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected || isCorrect || isWrong) Color.White
                    else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.weight(1f)
            )
            if (isCorrect) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32)
                )
            } else if (isWrong) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = null,
                    tint = Color(0xFFC62828)
                )
            }
        }
    }
}

@Composable
fun AnswerCard(question: Question, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "正确答案: ${if (question.answerContent.isNotBlank()) question.answerContent.replace("|||", "、") else question.answer}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0),
                    fontSize = 15.sp
                )
                if (question.type == QuestionType.SHORT_ANSWER || question.type == QuestionType.ESSAY) {
                    TextButton(onClick = {
                        copyToClipboard(context, question.answer.ifBlank { question.answerContent })
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制")
                    }
                }
            }
            if (question.analysis.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "解析: ${question.analysis}",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentIndex: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit
) {
    var showJumpDialog by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onPrev,
                enabled = currentIndex > 0
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("上一题")
            }

            TextButton(onClick = { showJumpDialog = true }) {
                Text("${currentIndex + 1} / $total", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onNext,
                enabled = currentIndex < total - 1
            ) {
                Text("下一题")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showJumpDialog) {
        var jumpText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("跳转到题目") },
            text = {
                OutlinedTextField(
                    value = jumpText,
                    onValueChange = { jumpText = it.filter { c -> c.isDigit() } },
                    label = { Text("输入题号 (1-$total)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = jumpText.toIntOrNull()
                    if (target != null && target in 1..total) {
                        onJump(target - 1)
                        showJumpDialog = false
                    }
                }) {
                    Text("跳转")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

fun getTypeName(type: QuestionType): String {
    return when (type) {
        QuestionType.SINGLE_CHOICE -> "单选题"
        QuestionType.MULTIPLE_CHOICE -> "多选题"
        QuestionType.TRUE_FALSE -> "判断题"
        QuestionType.FILL_BLANK -> "填空题"
        QuestionType.SHORT_ANSWER -> "简答题"
        QuestionType.ESSAY -> "问答题"
    }
}

fun getTypeColor(type: QuestionType): Color {
    return when (type) {
        QuestionType.SINGLE_CHOICE -> Color(0xFF1565C0)
        QuestionType.MULTIPLE_CHOICE -> Color(0xFF6A1B9A)
        QuestionType.TRUE_FALSE -> Color(0xFF00838F)
        QuestionType.FILL_BLANK -> Color(0xFFEF6C00)
        QuestionType.SHORT_ANSWER -> Color(0xFF2E7D32)
        QuestionType.ESSAY -> Color(0xFFC62828)
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("题库答案", text)
    clipboard.setPrimaryClip(clip)
}

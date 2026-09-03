package com.quizmaster.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quizmaster.util.RootUtil
import com.quizmaster.viewmodel.QuizViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: QuizViewModel,
    onRequestCapturePermission: () -> Unit,
    onNavigateToQuizList: () -> Unit,
    onNavigateToQuiz: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var rootStatus by remember { mutableStateOf<Boolean?>(null) }
    var floatingEnabled by remember { mutableStateOf(com.quizmaster.service.FloatingWindowService.isRunning) }
    var selectedQuizSetId by remember { mutableStateOf(-1L) }
    val importResult by viewModel.importResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val quizSets by viewModel.quizSets.collectAsState()

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            android.widget.Toast.makeText(context, "未选择文件", android.widget.Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        try {
            val fileName = getFileName(context, uri) ?: "题库.txt"
            android.widget.Toast.makeText(context, "正在解析: $fileName", android.widget.Toast.LENGTH_SHORT).show()
            viewModel.importFile(uri, fileName)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "选择文件出错: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        rootStatus = RootUtil.isDeviceRooted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("题库助手", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Root状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (rootStatus == true)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (rootStatus == true) Icons.Default.CheckCircle
                        else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (rootStatus == true)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (rootStatus == true) "设备已Root" else "设备未Root",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (rootStatus == true)
                                "可访问受保护目录的题库文件"
                            else
                                "仍可正常使用，仅无法访问系统保护目录",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (rootStatus == true) {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch {
                                RootUtil.requestRoot()
                            }
                        }) {
                            Text("获取权限")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 导入按钮
            Button(
                onClick = {
                    fileLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    Text("正在解析，请稍候...", fontSize = 16.sp)
                } else {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入题库文件", fontSize = 18.sp)
                }
            }

            Text(
                text = "支持 TXT / DOCX / PDF / XLSX 格式",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 导入结果提示
            importResult?.let { result ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearImportResult() },
                    title = { Text(if (result.success) "导入成功" else "导入失败") },
                    text = { Text(result.message) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.clearImportResult()
                            if (result.success) {
                                onNavigateToQuiz(result.quizSetId)
                            }
                        }) {
                            Text(if (result.success) "开始答题" else "确定")
                        }
                    },
                    dismissButton = {
                        if (result.success) {
                            TextButton(onClick = { viewModel.clearImportResult() }) {
                                Text("稍后")
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 功能入口
            OutlinedButton(
                onClick = onNavigateToQuizList,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("我的题库 (${quizSets.size})", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 悬浮窗答题助手
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "悬浮窗答题助手",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "在其他APP/网页上答题时，悬浮窗可截图识别题目，自动匹配题库显示答案",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 选择题库
                    Text("选择题库:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    quizSets.forEach { set ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedQuizSetId = set.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedQuizSetId == set.id,
                                onClick = { selectedQuizSetId = set.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${set.name} (${set.questionCount}题)", fontSize = 14.sp)
                        }
                    }
                    if (quizSets.isEmpty()) {
                        Text("请先导入题库", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 权限状态显示
                    val hasCapturePermission = com.quizmaster.helper.ScreenCaptureHelper.hasPermission()
                    val captureError = com.quizmaster.helper.ScreenCaptureHelper.lastError
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hasCapturePermission) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (hasCapturePermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (hasCapturePermission) "截图权限：已开启" else "截图权限：未开启",
                            fontSize = 13.sp,
                            color = if (hasCapturePermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    if (captureError.isNotBlank() && !hasCapturePermission) {
                        Text(
                            "错误: $captureError",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // 权限按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRequestCapturePermission,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Screenshot, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (hasCapturePermission) "重新授权截图" else "截图权限", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("无障碍", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 悬浮窗开关
                    Button(
                        onClick = {
                            if (floatingEnabled) {
                                context.stopService(android.content.Intent(context, com.quizmaster.service.FloatingWindowService::class.java))
                                floatingEnabled = false
                                android.widget.Toast.makeText(context, "悬浮窗已关闭", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                if (selectedQuizSetId <= 0) {
                                    android.widget.Toast.makeText(context, "请先选择题库", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (!com.quizmaster.helper.ScreenCaptureHelper.hasPermission()) {
                                    android.widget.Toast.makeText(context, "请先开启截图权限", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val intent = android.content.Intent(context, com.quizmaster.service.FloatingWindowService::class.java)
                                intent.putExtra("quizSetId", selectedQuizSetId)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                                floatingEnabled = true
                                android.widget.Toast.makeText(context, "悬浮窗已开启，切换到答题页面点击识题", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (floatingEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (floatingEnabled) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (floatingEnabled) "关闭悬浮窗" else "开启悬浮窗", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 崩溃日志
            var showCrashLog by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showCrashLog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("查看崩溃日志", fontSize = 14.sp)
            }

            if (showCrashLog) {
                val crashLog = (context.applicationContext as com.quizmaster.QuizApp).getCrashLog()
                AlertDialog(
                    onDismissRequest = { showCrashLog = false },
                    title = { Text("崩溃日志") },
                    text = {
                        Column {
                            Text(crashLog, fontSize = 11.sp, modifier = Modifier.heightIn(max = 300.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = {
                                (context.applicationContext as com.quizmaster.QuizApp).clearCrashLog()
                                showCrashLog = false
                                android.widget.Toast.makeText(context, "已清空", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Text("清空日志")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("crash_log", crashLog))
                            android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Text("复制")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 功能说明
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "功能说明",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FeatureItem(
                        icon = Icons.Default.CheckCircle,
                        title = "选择题自动填充",
                        desc = "单选题/多选题/判断题点击选项即自动选中"
                    )
                    FeatureItem(
                        icon = Icons.Default.Edit,
                        title = "填空题自动显示",
                        desc = "一键显示正确答案，快速核对"
                    )
                    FeatureItem(
                        icon = Icons.Default.ContentCopy,
                        title = "简答题一键复制",
                        desc = "简答题/问答题提供复制按钮，方便粘贴提交"
                    )
                    FeatureItem(
                        icon = Icons.Default.AutoFixHigh,
                        title = "智能题型识别",
                        desc = "自动识别单选、多选、判断、填空、简答、问答"
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return it.getString(nameIndex)
                }
            }
        }
        uri.lastPathSegment?.split("/")?.lastOrNull() ?: "题库.txt"
    } catch (e: Exception) {
        uri.lastPathSegment?.split("/")?.lastOrNull() ?: "题库.txt"
    }
}

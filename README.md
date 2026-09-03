# 题库助手 (QuizMaster)

一个功能强大的安卓题库应用，支持导入多种格式题库文件，自动识别题型，选择题/填空题自动填充，简答题/问答题一键复制。

## 功能特性

- **多格式导入**：支持 TXT、DOCX、PDF、XLSX/XLS 格式
- **智能识别**：自动识别单选题、多选题、判断题、填空题、简答题、问答题
- **自动填充**：选择题点击选项即自动选中，填空题一键显示答案
- **一键复制**：简答题/问答题提供复制按钮，方便粘贴提交
- **Root支持**：支持获取Root权限，可访问受保护目录的题库文件
- **本地存储**：题库保存在本地，无需联网
- **题目导航**：支持上一题/下一题/跳转到指定题目

## 项目结构

```
QuizMaster/
├── app/
│   ├── src/main/
│   │   ├── java/com/quizmaster/
│   │   │   ├── data/           # 数据层（实体、DAO、数据库）
│   │   │   ├── parser/         # 题目解析器
│   │   │   ├── ui/             # UI界面
│   │   │   ├── util/           # 工具类（Root、文件导入）
│   │   │   └── viewmodel/      # ViewModel
│   │   ├── res/                # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/workflows/          # GitHub Actions 云编译
├── 示例题库_接触网.txt          # 示例题库文件
└── build.gradle.kts
```

## 编译方法

### 方法一：GitHub Actions 云编译（推荐，无需本地环境）

1. 在 GitHub 上创建新仓库
2. 将本项目所有文件上传到仓库
3. 进入仓库的 **Actions** 标签页
4. 点击 **Build APK** 工作流，再点击 **Run workflow**
5. 等待编译完成（约3-5分钟）
6. 编译完成后，在工作流详情页的 **Artifacts** 区域下载 `quizmaster-debug-apk`
7. 解压下载的 zip 文件，里面就是 APK 安装包

### 方法二：Android Studio 本地编译

1. 安装 [Android Studio](https://developer.android.com/studio)（最新版）
2. 安装 JDK 17（Android Studio 自带）
3. 打开 Android Studio，选择 **Open an existing project**
4. 选择本项目的 `QuizMaster` 文件夹
5. 等待 Gradle 同步完成（首次会下载依赖，需要几分钟）
6. 点击菜单 **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
7. 编译完成后，APK 文件位于：
   `app/build/outputs/apk/debug/app-debug.apk`

### 方法三：命令行编译

1. 安装 JDK 17 和 Gradle 8.5+
2. 进入项目目录
3. 首次运行生成 Gradle Wrapper：
   ```bash
   gradle wrapper --gradle-version 8.5
   ```
4. 编译 Debug APK：
   ```bash
   # Windows
   gradlew.bat assembleDebug

   # Linux/Mac
   ./gradlew assembleDebug
   ```
5. APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

## 安装使用

1. 将 APK 文件传到手机
2. 在手机上点击 APK 文件进行安装（需要允许安装未知来源应用）
3. 打开应用
4. 点击 **导入题库文件**，选择题库文件
5. 导入成功后点击 **开始答题**
6. 选择题：点击选项自动选中
7. 填空题：点击 **显示答案** 查看正确答案
8. 简答题/问答题：点击 **复制答案** 按钮复制到剪贴板

## 题库格式说明

### TXT 格式示例

```

### Excel 格式

Excel 文件第一行为表头，支持自动识别以下列名：
- 题目/题干/内容/question
- 选项A/选项B/选项C/选项D
- 答案/answer
- 解析/analysis
- 题型/类型/type

## Root 权限说明

- 应用会自动检测设备是否已 Root
- 已 Root 设备可点击 **获取权限** 按钮请求 Root 权限
- 获取 Root 权限后可访问系统保护目录中的题库文件
- 未 Root 设备仍可正常使用所有功能，仅无法访问系统保护目录

## 技术栈

- **语言**：Kotlin
- **UI框架**：Jetpack Compose + Material 3
- **数据库**：Room
- **架构**：MVVM + ViewModel + Flow
- **文档解析**：Apache POI（Word/Excel）、PDFBox-Android（PDF）
- **最低SDK**：Android 7.0 (API 24)
- **目标SDK**：Android 14 (API 34)

## 常见问题

**Q: 导入后题目识别不正确怎么办？**
A: 请检查题库格式是否符合上述规范，确保每题有明确的题号和答案标记。TXT 格式识别效果最好。

**Q: PDF 文件导入后乱码？**
A: 部分扫描版 PDF 无法提取文字，请使用文字版 PDF 或先转换为 TXT/DOCX 格式。

**Q: 安装时提示解析包错误？**
A: 请确认手机 Android 版本在 7.0 以上，且 APK 文件下载完整。

**Q: 如何获取 Root 权限？**
A: 需要手机已解锁 Bootloader 并刷入 Magisk 等 Root 管理工具，应用会自动请求授权。

## 许可证

本项目仅供学习和个人使用。

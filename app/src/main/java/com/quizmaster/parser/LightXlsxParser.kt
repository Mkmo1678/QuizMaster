package com.quizmaster.parser

import android.util.Log
import com.quizmaster.data.QuestionType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * 轻量级XLSX解析器，不依赖Apache POI，避免Android上的内存崩溃
 * xlsx本质是zip文件，直接解析内部XML
 */
object LightXlsxParser {

    private const val TAG = "LightXlsxParser"

    fun parse(inputStream: InputStream): List<RawQuestion> {
        // 先保存到临时文件，因为ZipFile需要文件路径
        val tempFile = java.io.File.createTempFile("quiz_", ".xlsx")
        tempFile.deleteOnExit()
        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return try {
            parseZipFile(tempFile.absolutePath)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse xlsx", e)
            throw e
        } finally {
            tempFile.delete()
        }
    }

    private fun parseZipFile(path: String): List<RawQuestion> {
        val zipFile = ZipFile(path)
        val questions = mutableListOf<RawQuestion>()

        try {
            // 1. 读取共享字符串
            val sharedStrings = readSharedStrings(zipFile)

            // 2. 读取工作簿，获取sheet名称和顺序
            val sheets = readWorkbookSheets(zipFile)

            // 3. 逐个解析sheet
            sheets.forEachIndexed { index, sheetName ->
                try {
                    val sheetQuestions = parseSheet(zipFile, index + 1, sheetName, sharedStrings)
                    questions.addAll(sheetQuestions)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse sheet: $sheetName", e)
                }
            }
        } finally {
            zipFile.close()
        }

        return questions
    }

    private fun readSharedStrings(zipFile: ZipFile): List<String> {
        val strings = mutableListOf<String>()
        val entry = zipFile.getEntry("xl/sharedStrings.xml") ?: return strings

        zipFile.getInputStream(entry).use { stream ->
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            var eventType = parser.eventType
            var inSi = false
            var inT = false
            val currentString = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "si" -> {
                                inSi = true
                                currentString.clear()
                            }
                            "t" -> inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT && inSi) {
                            currentString.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "si" -> {
                                inSi = false
                                strings.add(currentString.toString())
                            }
                            "t" -> inT = false
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return strings
    }

    private fun readWorkbookSheets(zipFile: ZipFile): List<String> {
        val sheets = mutableListOf<String>()
        val entry = zipFile.getEntry("xl/workbook.xml") ?: return listOf("Sheet1")

        zipFile.getInputStream(entry).use { stream ->
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name")
                    if (name != null) sheets.add(name)
                }
                eventType = parser.next()
            }
        }
        return sheets
    }

    private fun parseSheet(
        zipFile: ZipFile,
        sheetIndex: Int,
        sheetName: String,
        sharedStrings: List<String>
    ): List<RawQuestion> {
        val entry = zipFile.getEntry("xl/worksheets/sheet$sheetIndex.xml")
            ?: return emptyList()

        val type = detectSheetType(sheetName)
        val rows = mutableListOf<MutableList<String>>()

        zipFile.getInputStream(entry).use { stream ->
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            var currentRow = mutableListOf<String>()
            var currentCellValue = StringBuilder()
            var cellType = ""
            var inV = false
            var inIs = false
            var inT = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> currentRow = mutableListOf()
                            "c" -> {
                                cellType = parser.getAttributeValue(null, "t") ?: ""
                                currentCellValue.clear()
                            }
                            "v" -> inV = true
                            "is" -> inIs = true
                            "t" -> inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inV || (inIs && inT)) {
                            currentCellValue.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "v" -> inV = false
                            "is" -> inIs = false
                            "t" -> inT = false
                            "c" -> {
                                val value = currentCellValue.toString()
                                val cellValue = when (cellType) {
                                    "s" -> {
                                        val idx = value.toIntOrNull()
                                        if (idx != null && idx < sharedStrings.size) sharedStrings[idx] else value
                                    }
                                    "str" -> value
                                    "b" -> if (value == "1") "TRUE" else "FALSE"
                                    else -> {
                                        // 数字类型，尝试转成整数显示
                                        val d = value.toDoubleOrNull()
                                        if (d != null && d == d.toLong().toDouble()) d.toLong().toString() else value
                                    }
                                }
                                currentRow.add(cellValue)
                            }
                            "row" -> rows.add(currentRow)
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        if (rows.size < 3) return emptyList()

        // 第二行是表头（索引1）
        val headers = rows[1]
        val colMap = detectColumns(headers)
        val questions = mutableListOf<RawQuestion>()

        // 从第三行开始（索引2）
        for (i in 2 until rows.size) {
            val cells = rows[i]
            if (cells.all { it.isBlank() }) continue

            val q = RawQuestion()
            q.type = type
            q.content = getSafe(cells, colMap["content"] ?: 0)
            q.analysis = getSafe(cells, colMap["analysis"] ?: -1)

            when (type) {
                QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE -> {
                    val optionCols = listOf(
                        colMap["optionA"], colMap["optionB"],
                        colMap["optionC"], colMap["optionD"],
                        colMap["optionE"], colMap["optionF"]
                    ).mapNotNull { it }
                    for (col in optionCols) {
                        val opt = getSafe(cells, col)
                        if (opt.isNotBlank()) q.options.add(opt)
                    }
                    val answerLetter = getSafe(cells, colMap["answer"] ?: -1).uppercase().trim()
                    q.answer = answerLetter
                    val letters = answerLetter.replace(Regex("[^A-H]"), "")
                    if (letters.isNotEmpty()) {
                        val contents = letters.map { it - 'A' }
                            .filter { it < q.options.size }
                            .map { q.options[it].trim() }
                        q.answerContent = contents.joinToString("|||")
                    }
                }
                QuestionType.TRUE_FALSE -> {
                    val ans = getSafe(cells, colMap["answer"] ?: -1).trim()
                    q.answer = when {
                        ans in listOf("正确", "对", "√", "T", "TRUE", "是", "真") -> "正确"
                        ans in listOf("错误", "错", "×", "F", "FALSE", "否", "假") -> "错误"
                        else -> ans
                    }
                }
                QuestionType.FILL_BLANK -> {
                    q.answer = getSafe(cells, colMap["answer"] ?: -1)
                    q.answerContent = q.answer
                }
                QuestionType.SHORT_ANSWER, QuestionType.ESSAY -> {
                    q.answer = getSafe(cells, colMap["answer"] ?: -1)
                    q.answerContent = q.answer
                }
            }

            if (q.content.isNotBlank()) {
                questions.add(q)
            }
        }

        return questions
    }

    private fun detectSheetType(sheetName: String): QuestionType {
        return when {
            sheetName.contains("多选") -> QuestionType.MULTIPLE_CHOICE
            sheetName.contains("单选") -> QuestionType.SINGLE_CHOICE
            sheetName.contains("判断") -> QuestionType.TRUE_FALSE
            sheetName.contains("填空") -> QuestionType.FILL_BLANK
            sheetName.contains("简答") -> QuestionType.SHORT_ANSWER
            sheetName.contains("问答") || sheetName.contains("论述") -> QuestionType.ESSAY
            else -> QuestionType.SINGLE_CHOICE
        }
    }

    private fun detectColumns(headers: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        headers.forEachIndexed { index, header ->
            val h = header.replace("*", "").trim()
            when {
                h == "题目" || h == "题干" || h == "小题题目" -> map["content"] = index
                h == "参考答案" || h == "答案" -> map["answer"] = index
                h == "题目解析" || h == "解析" -> map["analysis"] = index
                h == "选项A" || h == "A" -> map["optionA"] = index
                h == "选项B" || h == "B" -> map["optionB"] = index
                h == "选项C" || h == "C" -> map["optionC"] = index
                h == "选项D" || h == "D" -> map["optionD"] = index
                h == "选项E" || h == "E" -> map["optionE"] = index
                h == "选项F" || h == "F" -> map["optionF"] = index
            }
        }
        return map
    }

    private fun getSafe(list: List<String>, index: Int): String {
        return if (index >= 0 && index < list.size) list[index] else ""
    }
}

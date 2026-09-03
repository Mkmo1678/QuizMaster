package com.quizmaster.parser

import com.quizmaster.data.QuestionType
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object XlsxParser {

    fun parse(inputStream: InputStream): List<RawQuestion> {
        val workbook = WorkbookFactory.create(inputStream)
        val questions = mutableListOf<RawQuestion>()

        for (sheet in workbook) {
            val sheetName = sheet.sheetName
            val rows = sheet.toList()
            if (rows.size < 3) continue

            // 第二行是表头（索引1）
            val headers = rows[1].map { getCellString(it).trim() }
            if (headers.isEmpty()) continue

            // 根据sheet名称判断题型
            val type = detectSheetType(sheetName)

            // 识别列索引
            val colMap = detectColumns(headers)

            // 从第三行开始读取数据（索引2）
            for (i in 2 until rows.size) {
                val row = rows[i]
                val cells = row.map { getCellString(it) }
                if (cells.all { it.isBlank() }) continue

                val q = RawQuestion()
                q.type = type
                q.content = getSafe(cells, colMap["content"] ?: 0)
                q.analysis = getSafe(cells, colMap["analysis"] ?: -1)

                when (type) {
                    QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE -> {
                        // 读取选项
                        val optionCols = listOf(
                            colMap["optionA"], colMap["optionB"],
                            colMap["optionC"], colMap["optionD"],
                            colMap["optionE"], colMap["optionF"]
                        ).mapNotNull { it }
                        for (col in optionCols) {
                            val opt = getSafe(cells, col)
                            if (opt.isNotBlank()) {
                                q.options.add(opt)
                            }
                        }
                        // 答案是字母，需要转换成内容
                        val answerLetter = getSafe(cells, colMap["answer"] ?: -1).uppercase().trim()
                        q.answer = answerLetter
                        // 转换答案字母为内容
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
        }

        workbook.close()
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

    private fun getCellString(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                val d = cell.numericCellValue
                if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> try { cell.stringCellValue.trim() } catch (e: Exception) { "" }
            else -> ""
        }
    }
}

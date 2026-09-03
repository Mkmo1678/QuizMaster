package com.quizmaster.parser

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object XlsxParser {
    data class ColumnMapping(
        val contentCol: Int = 0,
        val optionACol: Int = -1,
        val optionBCol: Int = -1,
        val optionCCol: Int = -1,
        val optionDCol: Int = -1,
        val answerCol: Int = -1,
        val analysisCol: Int = -1,
        val typeCol: Int = -1
    )

    fun parse(inputStream: InputStream): List<RawQuestion> {
        val workbook = WorkbookFactory.create(inputStream)
        val questions = mutableListOf<RawQuestion>()

        for (sheet in workbook) {
            val rows = sheet.toList()
            if (rows.isEmpty()) continue

            // 自动识别列映射（基于表头）
            val headerRow = rows.first()
            val mapping = detectColumns(headerRow.map { getCellString(it) })

            // 从第二行开始读取
            for (i in 1 until rows.size) {
                val row = rows[i]
                val cells = row.map { getCellString(it) }
                if (cells.all { it.isBlank() }) continue

                val q = RawQuestion()
                q.content = getSafe(cells, mapping.contentCol)

                if (mapping.optionACol >= 0) q.options.add(getSafe(cells, mapping.optionACol))
                if (mapping.optionBCol >= 0) q.options.add(getSafe(cells, mapping.optionBCol))
                if (mapping.optionCCol >= 0) q.options.add(getSafe(cells, mapping.optionCCol))
                if (mapping.optionDCol >= 0) q.options.add(getSafe(cells, mapping.optionDCol))

                if (mapping.answerCol >= 0) q.answer = getSafe(cells, mapping.answerCol)
                if (mapping.analysisCol >= 0) q.analysis = getSafe(cells, mapping.analysisCol)

                // 题型识别
                if (mapping.typeCol >= 0) {
                    when (getSafe(cells, mapping.typeCol)) {
                        "单选", "单选题", "single" -> q.type = QuestionType.SINGLE_CHOICE
                        "多选", "多选题", "multiple" -> q.type = QuestionType.MULTIPLE_CHOICE
                        "判断", "判断题", "tf" -> q.type = QuestionType.TRUE_FALSE
                        "填空", "填空题", "fill" -> q.type = QuestionType.FILL_BLANK
                        "简答", "简答题", "short" -> q.type = QuestionType.SHORT_ANSWER
                        "问答", "问答题", "essay" -> q.type = QuestionType.ESSAY
                    }
                }

                if (q.content.isNotBlank()) {
                    QuestionParser.parseText(q.content).firstOrNull()?.let { parsed ->
                        if (q.options.isEmpty()) q.options = parsed.options
                        if (q.answer.isBlank()) q.answer = parsed.answer
                    }
                    questions.add(q)
                }
            }
        }

        workbook.close()
        return questions
    }

    private fun detectColumns(headers: List<String>): ColumnMapping {
        var mapping = ColumnMapping()
        headers.forEachIndexed { index, header ->
            val h = header.lowercase()
            when {
                h.contains("题目") || h.contains("题干") || h.contains("内容") || h == "question" ->
                    mapping = mapping.copy(contentCol = index)
                h.contains("选项a") || h == "a" || h.contains("a选项") ->
                    mapping = mapping.copy(optionACol = index)
                h.contains("选项b") || h == "b" || h.contains("b选项") ->
                    mapping = mapping.copy(optionBCol = index)
                h.contains("选项c") || h == "c" || h.contains("c选项") ->
                    mapping = mapping.copy(optionCCol = index)
                h.contains("选项d") || h == "d" || h.contains("d选项") ->
                    mapping = mapping.copy(optionDCol = index)
                h.contains("答案") || h == "answer" ->
                    mapping = mapping.copy(answerCol = index)
                h.contains("解析") || h == "analysis" ->
                    mapping = mapping.copy(analysisCol = index)
                h.contains("题型") || h.contains("类型") || h == "type" ->
                    mapping = mapping.copy(typeCol = index)
            }
        }
        return mapping
    }

    private fun getSafe(list: List<String>, index: Int): String {
        return if (index >= 0 && index < list.size) list[index] else ""
    }

    private fun getCellString(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> cell.numericCellValue.toLong().toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> try { cell.stringCellValue.trim() } catch (e: Exception) { "" }
            else -> ""
        }
    }
}

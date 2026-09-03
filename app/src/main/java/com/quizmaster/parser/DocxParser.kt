package com.quizmaster.parser

import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream

object DocxParser {
    fun parse(inputStream: InputStream): List<RawQuestion> {
        val document = XWPFDocument(inputStream)
        val sb = StringBuilder()

        for (paragraph in document.paragraphs) {
            val text = paragraph.text
            if (text.isNotBlank()) {
                sb.appendLine(text)
            }
        }

        // 也读取表格中的内容
        for (table in document.tables) {
            for (row in table.rows) {
                for (cell in row.tableCells) {
                    val cellText = cell.text.trim()
                    if (cellText.isNotBlank()) {
                        sb.appendLine(cellText)
                    }
                }
            }
        }

        document.close()
        return QuestionParser.parseText(sb.toString())
    }
}

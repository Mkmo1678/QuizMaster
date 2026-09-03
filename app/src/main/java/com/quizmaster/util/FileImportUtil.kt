package com.quizmaster.util

import android.content.Context
import android.net.Uri
import com.quizmaster.parser.DocxParser
import com.quizmaster.parser.PdfParser
import com.quizmaster.parser.RawQuestion
import com.quizmaster.parser.TxtParser
import com.quizmaster.parser.XlsxParser
import java.io.InputStream

object FileImportUtil {

    enum class FileType {
        TXT, DOCX, PDF, XLSX, XLS, UNKNOWN
    }

    fun detectFileType(fileName: String): FileType {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".txt") -> FileType.TXT
            lower.endsWith(".docx") || lower.endsWith(".doc") -> FileType.DOCX
            lower.endsWith(".pdf") -> FileType.PDF
            lower.endsWith(".xlsx") -> FileType.XLSX
            lower.endsWith(".xls") -> FileType.XLS
            else -> FileType.UNKNOWN
        }
    }

    fun parseFile(context: Context, uri: Uri, fileName: String): List<RawQuestion> {
        val type = detectFileType(fileName)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("无法打开文件")

        return try {
            when (type) {
                FileType.TXT -> TxtParser.parse(inputStream)
                FileType.DOCX -> DocxParser.parse(inputStream)
                FileType.PDF -> PdfParser.parse(inputStream)
                FileType.XLSX, FileType.XLS -> XlsxParser.parse(inputStream)
                FileType.UNKNOWN -> {
                    // 尝试按文本解析
                    TxtParser.parse(inputStream)
                }
            }
        } finally {
            inputStream.close()
        }
    }

    fun getSupportedExtensions(): List<String> {
        return listOf(".txt", ".docx", ".doc", ".pdf", ".xlsx", ".xls")
    }

    fun getMimeTypes(): Array<String> {
        return arrayOf(
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "*/*"
        )
    }
}

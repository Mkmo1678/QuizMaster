package com.quizmaster.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.quizmaster.parser.DocxParser
import com.quizmaster.parser.LightXlsxParser
import com.quizmaster.parser.PdfParser
import com.quizmaster.parser.RawQuestion
import com.quizmaster.parser.TxtParser
import java.io.InputStream

object FileImportUtil {

    private const val TAG = "FileImportUtil"

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
        Log.d(TAG, "Parsing file: $fileName, type: $type")

        // 先读取文件内容到字节数组，避免多次打开流
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("无法打开文件")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to read file", e)
            throw Exception("无法读取文件: ${e.message}")
        }

        // TXT文件直接解析，不依赖外部库
        if (type == FileType.TXT || type == FileType.UNKNOWN) {
            return try {
                val text = bytesToString(bytes)
                TxtParser.parseString(text)
            } catch (e: Throwable) {
                Log.e(TAG, "Txt parse failed", e)
                if (type == FileType.UNKNOWN) {
                    throw Exception("文件解析失败，请确认是支持的格式")
                }
                throw e
            }
        }

        // 其他格式先用对应解析器，失败则回退到文本提取
        return try {
            when (type) {
                FileType.DOCX -> DocxParser.parse(bytes.inputStream())
                FileType.PDF -> PdfParser.parse(bytes.inputStream())
                FileType.XLSX, FileType.XLS -> LightXlsxParser.parse(bytes.inputStream())
                else -> TxtParser.parseString(bytesToString(bytes))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Primary parser failed for $type, trying text fallback", e)
            // 回退：尝试从字节中提取文本
            try {
                val text = bytesToString(bytes)
                val result = TxtParser.parseString(text)
                if (result.isNotEmpty()) {
                    Log.d(TAG, "Text fallback succeeded, found ${result.size} questions")
                    result
                } else {
                    throw Exception("解析失败: ${e.message}，且文本回退未识别到题目")
                }
            } catch (e2: Throwable) {
                Log.e(TAG, "All parsing methods failed", e2)
                throw Exception("文件解析失败: ${e.message}")
            }
        }
    }

    private fun bytesToString(bytes: ByteArray): String {
        // 尝试多种编码
        return try {
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                String(bytes, charset("GBK"))
            } catch (e2: Exception) {
                String(bytes, Charsets.ISO_8859_1)
            }
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

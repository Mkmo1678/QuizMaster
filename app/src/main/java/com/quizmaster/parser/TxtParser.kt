package com.quizmaster.parser

import java.io.InputStream
import java.nio.charset.Charset

object TxtParser {
    fun parse(inputStream: InputStream): List<RawQuestion> {
        val bytes = inputStream.readBytes()
        // 尝试多种编码
        val text = try {
            String(bytes, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            try {
                String(bytes, Charset.forName("GBK"))
            } catch (e2: Exception) {
                String(bytes, Charset.defaultCharset())
            }
        }
        return QuestionParser.parseText(text)
    }

    fun parseString(text: String): List<RawQuestion> {
        return QuestionParser.parseText(text)
    }
}

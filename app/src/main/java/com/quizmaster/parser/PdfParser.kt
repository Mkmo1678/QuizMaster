package com.quizmaster.parser

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

object PdfParser {
    fun parse(inputStream: InputStream): List<RawQuestion> {
        val document = PDDocument.load(inputStream)
        val stripper = PDFTextStripper()
        val text = stripper.getText(document)
        document.close()
        return QuestionParser.parseText(text)
    }
}

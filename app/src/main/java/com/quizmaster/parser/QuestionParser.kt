package com.quizmaster.parser

import com.quizmaster.data.Question
import com.quizmaster.data.QuestionType
import java.util.regex.Pattern

data class RawQuestion(
    var type: QuestionType = QuestionType.SHORT_ANSWER,
    var content: String = "",
    var options: MutableList<String> = mutableListOf(),
    var answer: String = "",
    var analysis: String = ""
)

object QuestionParser {

    // 题目编号模式：1.  一、  (1)  【1】  第1题
    private val questionNumberPatterns = listOf(
        Pattern.compile("^\\s*(\\d{1,3})[.、．]\\s*"),
        Pattern.compile("^\\s*[（(]\\s*(\\d{1,3})\\s*[）)]\\s*"),
        Pattern.compile("^\\s*[【\\[]\\s*(\\d{1,3})\\s*[】\\]]\\s*"),
        Pattern.compile("^\\s*第\\s*(\\d{1,3})\\s*题\\s*[.、．]?\\s*"),
        Pattern.compile("^\\s*([一二三四五六七八九十百]+)[、.．]\\s*")
    )

    // 选项模式：A.  A、  A)  (A)
    private val optionPattern = Pattern.compile("^\\s*([A-Ha-h])[.、．)）]\\s*(.+)$")

    // 答案标记
    private val answerMarkers = listOf(
        "答案：", "答案:", "参考答案：", "参考答案:",
        "【答案】", "[答案]", "答：", "答:",
        "解：", "解:", "解析：", "解析:"
    )

    fun parseText(text: String): List<RawQuestion> {
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n").trim()
        val lines = normalized.split("\n")
        val questions = mutableListOf<RawQuestion>()
        var current = RawQuestion()
        var inOptions = false
        var inAnswer = false
        var inAnalysis = false

        fun flush() {
            if (current.content.isNotBlank() || current.options.isNotEmpty()) {
                current.content = current.content.trim()
                current.answer = current.answer.trim()
                current.analysis = current.analysis.trim()
                detectType(current)
                questions.add(current)
            }
            current = RawQuestion()
            inOptions = false
            inAnswer = false
            inAnalysis = false
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                if (inAnswer && current.answer.isNotBlank()) {
                    inAnswer = false
                }
                continue
            }

            // 检测是否是新题目开始
            val isNewQuestion = questionNumberPatterns.any { it.matcher(trimmed).find() }
            if (isNewQuestion && (current.content.isNotBlank() || current.options.isNotEmpty())) {
                flush()
            }

            // 检测答案标记
            var answerFound = false
            for (marker in answerMarkers) {
                val idx = trimmed.indexOf(marker)
                if (idx >= 0) {
                    val afterMarker = trimmed.substring(idx + marker.length).trim()
                    if (current.answer.isBlank()) {
                        current.answer = afterMarker
                    } else {
                        current.answer += "\n$afterMarker"
                    }
                    inAnswer = true
                    inAnalysis = marker.contains("解析")
                    answerFound = true
                    break
                }
            }
            if (answerFound) continue

            // 检测选项
            val optionMatcher = optionPattern.matcher(trimmed)
            if (optionMatcher.find()) {
                val optionText = optionMatcher.group(2)?.trim() ?: ""
                current.options.add(optionText)
                inOptions = true
                inAnswer = false
                continue
            }

            // 继续答案内容
            if (inAnswer) {
                current.answer += "\n$trimmed"
                continue
            }

            // 继续解析内容
            if (inAnalysis) {
                current.analysis += "\n$trimmed"
                continue
            }

            // 题干内容
            if (inOptions && current.options.isNotEmpty()) {
                // 选项的续行
                current.options[current.options.size - 1] += " $trimmed"
            } else {
                if (current.content.isBlank()) {
                    // 去掉题号
                    var content = trimmed
                    for (pattern in questionNumberPatterns) {
                        val m = pattern.matcher(content)
                        if (m.find()) {
                            content = content.substring(m.end()).trim()
                            break
                        }
                    }
                    current.content = content
                } else {
                    current.content += "\n$trimmed"
                }
            }
        }
        flush()
        return questions
    }

    private fun detectType(q: RawQuestion) {
        val content = q.content
        val answer = q.answer.uppercase().trim()

        // 判断题：答案是对/错/√/×/T/F/正确/错误
        if (answer.matches(Regex("^[√×对错TF]\\s*$")) ||
            answer in listOf("正确", "错误", "对", "错", "T", "F", "TRUE", "FALSE") ||
            content.contains("判断") || content.contains("是否正确")) {
            q.type = QuestionType.TRUE_FALSE
            if (answer in listOf("√", "对", "正确", "T", "TRUE")) q.answer = "正确"
            if (answer in listOf("×", "错", "错误", "F", "FALSE")) q.answer = "错误"
            return
        }

        // 多选题：答案包含多个字母
        if (q.options.size >= 2) {
            val cleanAnswer = answer.replace(Regex("[^A-Ha-h]"), "")
            if (cleanAnswer.length >= 2) {
                q.type = QuestionType.MULTIPLE_CHOICE
                q.answer = cleanAnswer.uppercase().toCharArray().sorted().joinToString("")
                return
            }
            if (cleanAnswer.length == 1) {
                q.type = QuestionType.SINGLE_CHOICE
                q.answer = cleanAnswer.uppercase()
                return
            }
        }

        // 填空题：题干中有横线、括号空位
        if (content.contains("___") || content.contains("——") ||
            content.contains(Regex("[（(]\\s*[）)]")) ||
            content.contains("填空")) {
            q.type = QuestionType.FILL_BLANK
            return
        }

        // 选择题：有选项但答案未识别
        if (q.options.isNotEmpty()) {
            q.type = QuestionType.SINGLE_CHOICE
            return
        }

        // 简答题 vs 问答题
        if (content.contains("简答") || content.length < 50) {
            q.type = QuestionType.SHORT_ANSWER
        } else {
            q.type = QuestionType.ESSAY
        }
    }

    fun toEntity(raw: RawQuestion, quizSetId: Long, index: Int): Question {
        return Question(
            quizSetId = quizSetId,
            type = raw.type,
            content = raw.content,
            options = raw.options.joinToString("|||"),
            answer = raw.answer,
            analysis = raw.analysis,
            orderIndex = index
        )
    }

    fun parseOptions(optionsStr: String): List<String> {
        if (optionsStr.isBlank()) return emptyList()
        return optionsStr.split("|||").filter { it.isNotBlank() }
    }
}

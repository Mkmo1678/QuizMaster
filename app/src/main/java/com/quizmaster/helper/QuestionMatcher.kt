package com.quizmaster.helper

import com.quizmaster.data.Question
import com.quizmaster.data.QuestionType
import com.quizmaster.parser.QuestionParser

object QuestionMatcher {

    /**
     * 在题库中搜索匹配的题目
     * @param ocrText OCR识别出的屏幕文字
     * @param questions 题库中的所有题目
     * @return 匹配到的题目列表，按匹配度排序
     */
    fun matchQuestion(ocrText: String, questions: List<Question>): List<MatchResult> {
        if (ocrText.isBlank() || questions.isEmpty()) return emptyList()

        // 清理OCR文本，去除多余空格和换行
        val cleanOcr = ocrText.replace(Regex("\\s+"), "").trim()

        val results = mutableListOf<MatchResult>()

        for (question in questions) {
            val cleanQuestion = question.content.replace(Regex("\\s+"), "").trim()
            if (cleanQuestion.isBlank()) continue

            // 计算匹配度
            val similarity = calculateSimilarity(cleanOcr, cleanQuestion)

            // 如果题干包含在OCR文本中，匹配度更高
            val containsMatch = cleanOcr.contains(cleanQuestion) || cleanQuestion.contains(cleanOcr.take(50))

            val finalScore = if (containsMatch) (similarity + 0.5).coerceAtMost(1.0) else similarity

            if (finalScore > 0.3) {
                results.add(MatchResult(question, finalScore))
            }
        }

        return results.sortedByDescending { it.score }
    }

    /**
     * 从OCR文本中提取题目和选项
     */
    fun extractQuestionAndOptions(ocrText: String): ExtractedQuestion? {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // 找选项行（A. B. C. D. 开头）
        val optionPattern = Regex("^([A-H])[.、．)）]\\s*(.+)$")
        val options = mutableMapOf<String, String>()
        val questionLines = mutableListOf<String>()

        for (line in lines) {
            val match = optionPattern.find(line)
            if (match != null) {
                val letter = match.groupValues[1].uppercase()
                val text = match.groupValues[2].trim()
                options[letter] = text
            } else {
                questionLines.add(line)
            }
        }

        val questionText = questionLines.joinToString(" ").trim()
        if (questionText.isBlank()) return null

        return ExtractedQuestion(
            question = questionText,
            options = options
        )
    }

    /**
     * 计算两个字符串的相似度（基于字符级别的编辑距离和包含度）
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        // 取较短的字符串进行匹配
        val shorter = if (s1.length < s2.length) s1 else s2
        val longer = if (s1.length >= s2.length) s1 else s2

        // 如果短字符串是长字符串的子串，返回较高相似度
        if (longer.contains(shorter)) {
            return 0.7 + (shorter.length.toDouble() / longer.length) * 0.3
        }

        // 计算公共字符比例
        val set1 = s1.toSet()
        val set2 = s2.toSet()
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        val jaccard = if (union > 0) intersection.toDouble() / union else 0.0

        // 计算最长公共子串比例
        val lcsLength = longestCommonSubstringLength(s1, s2)
        val lcsRatio = lcsLength.toDouble() / minOf(s1.length, s2.length)

        return jaccard * 0.4 + lcsRatio * 0.6
    }

    private fun longestCommonSubstringLength(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        var max = 0
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                if (s1[i - 1] == s2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                    max = maxOf(max, dp[i][j])
                }
            }
        }
        return max
    }

    data class MatchResult(
        val question: Question,
        val score: Double
    )

    data class ExtractedQuestion(
        val question: String,
        val options: Map<String, String>
    )
}

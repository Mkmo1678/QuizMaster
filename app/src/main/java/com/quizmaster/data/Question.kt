package com.quizmaster.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class QuestionType {
    SINGLE_CHOICE,   // 单选题
    MULTIPLE_CHOICE, // 多选题
    TRUE_FALSE,      // 判断题
    FILL_BLANK,      // 填空题
    SHORT_ANSWER,    // 简答题
    ESSAY            // 问答题
}

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizSetId: Long,
    val type: QuestionType,
    val content: String,           // 题干
    val options: String = "",      // 选项，用 ||| 分隔
    val answer: String = "",       // 答案（字母或文字）
    val answerContent: String = "", // 答案对应的选项内容（用于选项打乱后匹配）
    val analysis: String = "",     // 解析
    val orderIndex: Int = 0,
    val isCollected: Boolean = false,
    val isWrong: Boolean = false
)

@Entity(tableName = "quiz_sets")
data class QuizSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceFile: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val questionCount: Int = 0
)

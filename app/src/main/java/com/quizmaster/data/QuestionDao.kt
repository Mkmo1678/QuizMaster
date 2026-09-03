package com.quizmaster.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: Question): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Update
    suspend fun updateQuestion(question: Question)

    @Delete
    suspend fun deleteQuestion(question: Question)

    @Query("SELECT * FROM questions WHERE quizSetId = :quizSetId ORDER BY orderIndex")
    fun getQuestionsBySet(quizSetId: Long): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE quizSetId = :quizSetId ORDER BY orderIndex")
    suspend fun getQuestionsBySetOnce(quizSetId: Long): List<Question>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionById(id: Long): Question?

    @Query("SELECT COUNT(*) FROM questions WHERE quizSetId = :quizSetId")
    suspend fun countBySet(quizSetId: Long): Int

    @Query("DELETE FROM questions WHERE quizSetId = :quizSetId")
    suspend fun deleteBySet(quizSetId: Long)
}

@Dao
interface QuizSetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizSet(quizSet: QuizSet): Long

    @Update
    suspend fun updateQuizSet(quizSet: QuizSet)

    @Delete
    suspend fun deleteQuizSet(quizSet: QuizSet)

    @Query("SELECT * FROM quiz_sets ORDER BY createdAt DESC")
    fun getAllQuizSets(): Flow<List<QuizSet>>

    @Query("SELECT * FROM quiz_sets WHERE id = :id")
    suspend fun getQuizSetById(id: Long): QuizSet?

    @Query("DELETE FROM quiz_sets WHERE id = :id")
    suspend fun deleteQuizSetById(id: Long)
}

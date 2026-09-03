package com.quizmaster.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromQuestionType(type: QuestionType): String = type.name

    @TypeConverter
    fun toQuestionType(value: String): QuestionType = QuestionType.valueOf(value)
}

@Database(
    entities = [Question::class, QuizSet::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun quizSetDao(): QuizSetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quiz_master_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

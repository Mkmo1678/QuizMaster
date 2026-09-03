package com.quizmaster.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quizmaster.QuizApp
import com.quizmaster.data.Question
import com.quizmaster.data.QuizSet
import com.quizmaster.parser.QuestionParser
import com.quizmaster.util.FileImportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuizViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as QuizApp).database
    private val questionDao = db.questionDao()
    private val quizSetDao = db.quizSetDao()

    private val _quizSets = MutableStateFlow<List<QuizSet>>(emptyList())
    val quizSets: StateFlow<List<QuizSet>> = _quizSets.asStateFlow()

    private val _currentQuestions = MutableStateFlow<List<Question>>(emptyList())
    val currentQuestions: StateFlow<List<Question>> = _currentQuestions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _userAnswers = MutableStateFlow<Map<Long, String>>(emptyMap())
    val userAnswers: StateFlow<Map<Long, String>> = _userAnswers.asStateFlow()

    private val _showAnswer = MutableStateFlow(false)
    val showAnswer: StateFlow<Boolean> = _showAnswer.asStateFlow()

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val questionCount: Int = 0,
        val quizSetId: Long = 0
    )

    init {
        loadQuizSets()
    }

    fun loadQuizSets() {
        viewModelScope.launch {
            quizSetDao.getAllQuizSets().collect { sets ->
                _quizSets.value = sets
            }
        }
    }

    fun importFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val rawQuestions = withContext(Dispatchers.IO) {
                    FileImportUtil.parseFile(getApplication(), uri, fileName)
                }

                if (rawQuestions.isEmpty()) {
                    _importResult.value = ImportResult(false, "未识别到题目，请检查文件格式")
                    _isLoading.value = false
                    return@launch
                }

                val setName = fileName.substringBeforeLast(".")
                val quizSetId = withContext(Dispatchers.IO) {
                    val id = quizSetDao.insertQuizSet(
                        QuizSet(name = setName, sourceFile = fileName, questionCount = rawQuestions.size)
                    )
                    val entities = rawQuestions.mapIndexed { index, raw ->
                        QuestionParser.toEntity(raw, id, index)
                    }
                    questionDao.insertQuestions(entities)
                    id
                }

                _importResult.value = ImportResult(
                    true,
                    "成功导入 ${rawQuestions.size} 道题目",
                    rawQuestions.size,
                    quizSetId
                )
                loadQuizSets()
            } catch (e: Exception) {
                _importResult.value = ImportResult(false, "导入失败: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    fun loadQuestions(quizSetId: Long) {
        viewModelScope.launch {
            _currentIndex.value = 0
            _userAnswers.value = emptyMap()
            _showAnswer.value = false
            questionDao.getQuestionsBySet(quizSetId).collect { questions ->
                _currentQuestions.value = questions
            }
        }
    }

    fun setAnswer(questionId: Long, answer: String) {
        _userAnswers.value = _userAnswers.value.toMutableMap().apply {
            put(questionId, answer)
        }
    }

    fun nextQuestion() {
        if (_currentIndex.value < _currentQuestions.value.size - 1) {
            _currentIndex.value++
            _showAnswer.value = false
        }
    }

    fun prevQuestion() {
        if (_currentIndex.value > 0) {
            _currentIndex.value--
            _showAnswer.value = false
        }
    }

    fun jumpTo(index: Int) {
        if (index in 0 until _currentQuestions.value.size) {
            _currentIndex.value = index
            _showAnswer.value = false
        }
    }

    fun toggleShowAnswer() {
        _showAnswer.value = !_showAnswer.value
    }

    fun deleteQuizSet(quizSetId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                questionDao.deleteBySet(quizSetId)
                quizSetDao.deleteQuizSetById(quizSetId)
            }
        }
    }

    fun clearImportResult() {
        _importResult.value = null
    }
}

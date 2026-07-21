package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessage
import com.example.data.StudyRepository
import com.example.data.Task
import com.example.data.WeakTopic
import com.example.data.FocusSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class StudyViewModel(
    private val repository: StudyRepository,
    private val context: android.content.Context
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("studylicious_prefs", android.content.Context.MODE_PRIVATE)
    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val sessionListAdapter = moshi.adapter<List<FocusSession>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, FocusSession::class.java)
    )

    private val _xpPoints = MutableStateFlow(sharedPrefs.getInt("xp_points", 120))
    val xpPoints: StateFlow<Int> = _xpPoints.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(sharedPrefs.getBoolean("is_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userName = MutableStateFlow(sharedPrefs.getString("user_name", "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow(sharedPrefs.getString("user_email", "") ?: "")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userProfilePic = MutableStateFlow(sharedPrefs.getString("user_profile_pic", "🌸") ?: "🌸")
    val userProfilePic: StateFlow<String> = _userProfilePic.asStateFlow()

    fun login(name: String, email: String, profilePic: String) {
        viewModelScope.launch {
            _userName.value = name
            _userEmail.value = email
            _userProfilePic.value = profilePic
            _isLoggedIn.value = true
            sharedPrefs.edit()
                .putBoolean("is_logged_in", true)
                .putString("user_name", name)
                .putString("user_email", email)
                .putString("user_profile_pic", profilePic)
                .apply()
        }
    }

    fun logout() {
        viewModelScope.launch {
            _isLoggedIn.value = false
            sharedPrefs.edit()
                .putBoolean("is_logged_in", false)
                .apply()
        }
    }

    private val _dailyTargetHours = MutableStateFlow(sharedPrefs.getInt("daily_target_hours", 4))
    val dailyTargetHours: StateFlow<Int> = _dailyTargetHours.asStateFlow()

    fun setDailyTargetHours(hours: Int) {
        viewModelScope.launch {
            _dailyTargetHours.value = hours
            sharedPrefs.edit().putInt("daily_target_hours", hours).apply()
        }
    }

    private val _appTheme = MutableStateFlow(sharedPrefs.getString("app_theme", "Pastel Rainbow") ?: "Pastel Rainbow")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            _appTheme.value = theme
            sharedPrefs.edit().putString("app_theme", theme).apply()
        }
    }

    private val _focusSessions = MutableStateFlow<List<FocusSession>>(loadFocusSessions())
    val focusSessions: StateFlow<List<FocusSession>> = _focusSessions.asStateFlow()

    private fun generateDefaultFocusSessions(): List<FocusSession> {
        val sessions = mutableListOf<FocusSession>()
        
        // 7 days distribution of study hours matching the image:
        // Day 0 (6 days ago): 4.5 hours -> 270 minutes
        // Day 1 (5 days ago): 2.5 hours -> 150 minutes
        // Day 2 (4 days ago): 5.5 hours -> 330 minutes
        // Day 3 (3 days ago): 6.5 hours -> 390 minutes
        // Day 4 (2 days ago): 4.0 hours -> 240 minutes
        // Day 5 (1 day ago):  5.0 hours -> 300 minutes
        // Day 6 (Today):      8.0 hours -> 480 minutes
        // Total = 36 hours exactly!
        val daysData = listOf(
            listOf(90 to "Physics Revision", 90 to "Maths Exercise", 90 to "English essay"), // 6 days ago
            listOf(90 to "History Reading", 60 to "Self-study"), // 5 days ago
            listOf(120 to "Chemistry Lab", 90 to "Biology Diagrams", 120 to "Maths Past Papers"), // 4 days ago
            listOf(150 to "Computer Science", 120 to "Physics Prep", 120 to "Language Arts"), // 3 days ago
            listOf(120 to "Accounting Practice", 120 to "Geography Study"), // 2 days ago
            listOf(180 to "Economics Review", 120 to "Revision"), // 1 day ago
            listOf(240 to "Weekend Study Sprint", 240 to "Maths Marathon") // Today
        )
        
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        for (i in 0 until 7) {
            val targetCal = Calendar.getInstance().apply {
                add(Calendar.DATE, -(6 - i))
                set(Calendar.HOUR_OF_DAY, 14)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val tasks = daysData[i]
            tasks.forEachIndexed { taskIndex, (duration, name) ->
                val taskTime = targetCal.timeInMillis + (taskIndex * 3600000L) // space them out
                sessions.add(
                    FocusSession(
                        id = "default_${i}_${taskIndex}",
                        taskTitle = name,
                        durationMinutes = duration,
                        xpEarned = duration / 2,
                        timestamp = taskTime,
                        dateString = sdf.format(java.util.Date(taskTime))
                    )
                )
            }
        }
        return sessions
    }

    private fun loadFocusSessions(): List<FocusSession> {
        val json = sharedPrefs.getString("focus_sessions", null)
        return if (json != null) {
            try {
                sessionListAdapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            val defaults = generateDefaultFocusSessions()
            try {
                val defaultJson = sessionListAdapter.toJson(defaults)
                sharedPrefs.edit().putString("focus_sessions", defaultJson).apply()
            } catch (e: Exception) {}
            defaults
        }
    }

    fun addXp(points: Int) {
        viewModelScope.launch {
            val current = _xpPoints.value
            val next = current + points
            _xpPoints.value = next
            sharedPrefs.edit().putInt("xp_points", next).apply()
        }
    }

    fun logFocusSession(taskTitle: String, durationMinutes: Int, xpEarned: Int) {
        viewModelScope.launch {
            addXp(xpEarned)
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val dateStr = sdf.format(java.util.Date())
            val newSession = FocusSession(
                id = java.util.UUID.randomUUID().toString(),
                taskTitle = taskTitle,
                durationMinutes = durationMinutes,
                xpEarned = xpEarned,
                timestamp = System.currentTimeMillis(),
                dateString = dateStr
            )
            val updated = listOf(newSession) + _focusSessions.value
            _focusSessions.value = updated
            try {
                val json = sessionListAdapter.toJson(updated)
                sharedPrefs.edit().putString("focus_sessions", json).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val allTasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val streakState = repository.streakState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val allWeakTopics: StateFlow<List<WeakTopic>> = repository.allWeakTopics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allTodoItems: StateFlow<List<com.example.data.TodoItem>> = repository.allTodoItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _dailyQuote = MutableStateFlow("One step at a time, one page at a time. Matric is yours to conquer! 🌟")
    val dailyQuote: StateFlow<String> = _dailyQuote.asStateFlow()

    private val _quoteLoading = MutableStateFlow(false)
    val quoteLoading: StateFlow<Boolean> = _quoteLoading.asStateFlow()

    private val _coachLoading = MutableStateFlow(false)
    val coachLoading: StateFlow<Boolean> = _coachLoading.asStateFlow()

    private val _scanLoading = MutableStateFlow(false)
    val scanLoading: StateFlow<Boolean> = _scanLoading.asStateFlow()

    private val _todoScanLoading = MutableStateFlow(false)
    val todoScanLoading: StateFlow<Boolean> = _todoScanLoading.asStateFlow()

    private val _mistakeLoading = MutableStateFlow(false)
    val mistakeLoading: StateFlow<Boolean> = _mistakeLoading.asStateFlow()

    private val _isRescheduling = MutableStateFlow(false)
    val isRescheduling: StateFlow<Boolean> = _isRescheduling.asStateFlow()

    init {
        // Prepopulate with mock Matric data on startup if database is empty
        viewModelScope.launch {
            repository.prefillDatabaseWithMatricData()
            fetchNewQuote()
        }
    }

    fun fetchNewQuote() {
        viewModelScope.launch {
            _quoteLoading.value = true
            try {
                _dailyQuote.value = repository.getMotivationalQuote()
            } catch (e: Exception) {
                // Keep default
            } finally {
                _quoteLoading.value = false
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            repository.completeTaskAndCheckStreak(task)
        }
    }

    fun rescheduleSickDay() {
        viewModelScope.launch {
            _isRescheduling.value = true
            try {
                repository.rescheduleTasksForSickDay()
            } catch (e: Exception) {
                // Error handling
            } finally {
                _isRescheduling.value = false
            }
        }
    }

    fun autoBalanceTasks() {
        viewModelScope.launch {
            _isRescheduling.value = true
            try {
                repository.autoBalanceAllTasks()
            } catch (e: Exception) {
                // Error handling
            } finally {
                _isRescheduling.value = false
            }
        }
    }

    fun addNewTask(
        title: String,
        subject: String,
        chapter: String,
        taskType: String,
        estimatedMinutes: Int,
        workloadScore: Int,
        daysFromNow: Int
    ) {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DATE, daysFromNow)
            cal.set(Calendar.HOUR_OF_DAY, 16) // due afternoon
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val newTask = Task(
                title = title,
                subject = subject,
                chapter = chapter,
                taskType = taskType,
                dueDate = cal.timeInMillis,
                estimatedMinutes = estimatedMinutes,
                workloadScore = workloadScore
            )
            repository.insertTask(newTask)
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteTaskById(id)
        }
    }

    fun sendCoachMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _coachLoading.value = true
            try {
                repository.sendMessageToCoach(text)
            } catch (e: Exception) {
                // error logged or handled
            } finally {
                _coachLoading.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }

    fun scanHomeworkText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _scanLoading.value = true
            try {
                repository.scanHomeworkText(text)
            } catch (e: Exception) {
                // handled
            } finally {
                _scanLoading.value = false
            }
        }
    }

    fun analyzeMistakesText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _mistakeLoading.value = true
            try {
                repository.analyzeTestPaperMistakes(text)
            } catch (e: Exception) {
                // handled
            } finally {
                _mistakeLoading.value = false
            }
        }
    }

    // --- To-Do Screen Operations ---
    fun addTodoItem(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val newItem = com.example.data.TodoItem(title = title)
            repository.insertTodoItem(newItem)
        }
    }

    fun toggleTodoItemCompletion(todoItem: com.example.data.TodoItem) {
        viewModelScope.launch {
            val updated = todoItem.copy(isCompleted = !todoItem.isCompleted)
            repository.updateTodoItem(updated)
        }
    }

    fun deleteTodoItem(id: Long) {
        viewModelScope.launch {
            repository.deleteTodoItemById(id)
        }
    }

    fun scanTodoListText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _todoScanLoading.value = true
            try {
                repository.scanTodoListText(text)
            } catch (e: Exception) {
                // handled
            } finally {
                _todoScanLoading.value = false
            }
        }
    }
}

class StudyViewModelFactory(
    private val repository: StudyRepository,
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudyViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

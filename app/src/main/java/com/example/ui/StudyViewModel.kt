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

    data class StudyUser(val name: String, val email: String, val profilePic: String, val passwordHash: String)

    private val _registeredUsers = MutableStateFlow<List<StudyUser>>(emptyList())
    val registeredUsers: StateFlow<List<StudyUser>> = _registeredUsers.asStateFlow()

    fun hashPassword(password: String): String {
        if (password.isEmpty()) return ""
        return try {
            val bytes = password.toByteArray(kotlin.text.Charsets.UTF_8)
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            digest.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) {
            password
        }
    }

    fun verifyPassword(user: StudyUser, passwordRaw: String): Boolean {
        if (user.passwordHash.isEmpty()) return true
        return hashPassword(passwordRaw) == user.passwordHash
    }

    private fun loadRegisteredUsers(): List<StudyUser> {
        val raw = sharedPrefs.getString("registered_users_list", "") ?: ""
        if (raw.isBlank()) {
            val name = sharedPrefs.getString("user_name", "") ?: ""
            if (name.isNotEmpty()) {
                val email = sharedPrefs.getString("user_email", "") ?: ""
                val pic = sharedPrefs.getString("user_profile_pic", "🌸") ?: "🌸"
                return listOf(StudyUser(name, email, pic, ""))
            }
            return emptyList()
        }
        return raw.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size >= 4) {
                StudyUser(parts[0], parts[1].replace("%2C", ","), parts[2], parts[3])
            } else if (parts.size == 3) {
                StudyUser(parts[0], parts[1].replace("%2C", ","), parts[2], "")
            } else null
        }
    }

    fun deleteUser(user: StudyUser) {
        viewModelScope.launch {
            val current = loadRegisteredUsers().toMutableList()
            current.removeAll { it.name.lowercase() == user.name.lowercase() }
            saveRegisteredUsers(current)
            if (_userName.value.lowercase() == user.name.lowercase()) {
                logout()
            }
        }
    }

    private fun saveRegisteredUsers(users: List<StudyUser>) {
        val serialized = users.joinToString(";") { "${it.name},${it.email.replace(",", "%2C")},${it.profilePic},${it.passwordHash}" }
        sharedPrefs.edit().putString("registered_users_list", serialized).apply()
        _registeredUsers.value = users
    }

    fun registerAndLogin(name: String, email: String, profilePic: String, passwordRaw: String) {
        viewModelScope.launch {
            val currentList = loadRegisteredUsers().toMutableList()
            currentList.removeAll { it.name.lowercase() == name.lowercase() }
            val hash = hashPassword(passwordRaw)
            currentList.add(StudyUser(name, email, profilePic, hash))
            saveRegisteredUsers(currentList)
            login(name, email, profilePic)
        }
    }

    private val _syllabusProgress = MutableStateFlow<Map<String, String>>(emptyMap())
    val syllabusProgress: StateFlow<Map<String, String>> = _syllabusProgress.asStateFlow()

    private val _subjectWeeklyTargets = MutableStateFlow<Map<String, Int>>(emptyMap())
    val subjectWeeklyTargets: StateFlow<Map<String, Int>> = _subjectWeeklyTargets.asStateFlow()

    init {
        _registeredUsers.value = loadRegisteredUsers()
        loadSyllabusProgress()
        loadSubjectWeeklyTargets()
    }

    private val _xpPoints = MutableStateFlow(sharedPrefs.getInt("xp_points_${sharedPrefs.getString("user_name", "")}", 120))
    val xpPoints: StateFlow<Int> = _xpPoints.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(sharedPrefs.getBoolean("is_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userName = MutableStateFlow(sharedPrefs.getString("user_name", "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow(sharedPrefs.getString("user_email", "") ?: "")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userProfilePic = MutableStateFlow(sharedPrefs.getString("user_profile_pic", "🌸") ?: "🌸")
    val userProfilePic: StateFlow<String> = _userProfilePic.asStateFlow()

    private val _userMood = MutableStateFlow(sharedPrefs.getString("user_mood_${sharedPrefs.getString("user_name", "")}", "Focused") ?: "Focused")
    val userMood: StateFlow<String> = _userMood.asStateFlow()

    private val _topStudyHours = MutableStateFlow(sharedPrefs.getString("top_study_hours_${sharedPrefs.getString("user_name", "")}", "Morning (8 AM - 12 PM)") ?: "Morning (8 AM - 12 PM)")
    val topStudyHours: StateFlow<String> = _topStudyHours.asStateFlow()

    private val _dailySleepHours = MutableStateFlow(sharedPrefs.getInt("daily_sleep_hours_${sharedPrefs.getString("user_name", "")}", 7).coerceAtLeast(5))
    val dailySleepHours: StateFlow<Int> = _dailySleepHours.asStateFlow()

    fun login(name: String, email: String, profilePic: String) {
        viewModelScope.launch {
            _userName.value = name
            _userEmail.value = email
            _userProfilePic.value = profilePic
            _isLoggedIn.value = true
            
            // Set user preferences with user-specific keys
            val userTheme = sharedPrefs.getString("app_theme_$name", "Pastel Rainbow") ?: "Pastel Rainbow"
            val userTarget = sharedPrefs.getInt("daily_target_hours_$name", 4)
            val userMoodVal = sharedPrefs.getString("user_mood_$name", "Focused") ?: "Focused"
            val userTopHours = sharedPrefs.getString("top_study_hours_$name", "Morning (8 AM - 12 PM)") ?: "Morning (8 AM - 12 PM)"
            val userSleep = sharedPrefs.getInt("daily_sleep_hours_$name", 7).coerceAtLeast(5)
            val userXp = sharedPrefs.getInt("xp_points_$name", 120)

            _appTheme.value = userTheme
            _dailyTargetHours.value = userTarget
            _userMood.value = userMoodVal
            _topStudyHours.value = userTopHours
            _dailySleepHours.value = userSleep
            _xpPoints.value = userXp
            
            loadSyllabusProgress()
            loadSubjectWeeklyTargets()

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

    private val _dailyTargetHours = MutableStateFlow(sharedPrefs.getInt("daily_target_hours_${sharedPrefs.getString("user_name", "")}", 4))
    val dailyTargetHours: StateFlow<Int> = _dailyTargetHours.asStateFlow()

    fun setDailyTargetHours(hours: Int) {
        viewModelScope.launch {
            val name = _userName.value
            _dailyTargetHours.value = hours
            sharedPrefs.edit().putInt("daily_target_hours_$name", hours).apply()
        }
    }

    private val _appTheme = MutableStateFlow(sharedPrefs.getString("app_theme_${sharedPrefs.getString("user_name", "")}", "Pastel Rainbow") ?: "Pastel Rainbow")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            val name = _userName.value
            _appTheme.value = theme
            sharedPrefs.edit().putString("app_theme_$name", theme).apply()
        }
    }

    fun setUserMood(mood: String) {
        viewModelScope.launch {
            val name = _userName.value
            _userMood.value = mood
            sharedPrefs.edit().putString("user_mood_$name", mood).apply()
        }
    }

    fun setTopStudyHours(hours: String) {
        viewModelScope.launch {
            val name = _userName.value
            _topStudyHours.value = hours
            sharedPrefs.edit().putString("top_study_hours_$name", hours).apply()
        }
    }

    fun setDailySleepHours(hours: Int) {
        viewModelScope.launch {
            val name = _userName.value
            val valid = hours.coerceAtLeast(5)
            _dailySleepHours.value = valid
            sharedPrefs.edit().putInt("daily_sleep_hours_$name", valid).apply()
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
            val name = _userName.value
            val current = _xpPoints.value
            val next = current + points
            _xpPoints.value = next
            sharedPrefs.edit().putInt("xp_points_$name", next).apply()
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

    val allTasks: StateFlow<List<Task>> = kotlinx.coroutines.flow.combine(userName, repository.allTasks) { name, tasks ->
        tasks.filter { it.studentName.lowercase() == name.lowercase() || it.studentName.isEmpty() }
    }.stateIn(
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

    val allWeakTopics: StateFlow<List<WeakTopic>> = kotlinx.coroutines.flow.combine(userName, repository.allWeakTopics) { name, topics ->
        topics.filter { it.studentName.lowercase() == name.lowercase() || it.studentName.isEmpty() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val chatMessages: StateFlow<List<ChatMessage>> = kotlinx.coroutines.flow.combine(userName, repository.chatMessages) { name, messages ->
        messages.filter { it.studentName.lowercase() == name.lowercase() || it.studentName.isEmpty() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allTodoItems: StateFlow<List<com.example.data.TodoItem>> = kotlinx.coroutines.flow.combine(userName, repository.allTodoItems) { name, items ->
        items.filter { it.studentName.lowercase() == name.lowercase() || it.studentName.isEmpty() }
    }.stateIn(
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

    private var lastQuoteIndex = -1

    fun fetchNewQuote() {
        viewModelScope.launch {
            _quoteLoading.value = true
            // Immediately load a random local quote to make it feel instantaneous and unique!
            val quotes = repository.localQuotes
            var nextIdx = (0 until quotes.size).random()
            while (nextIdx == lastQuoteIndex && quotes.size > 1) {
                nextIdx = (0 until quotes.size).random()
            }
            lastQuoteIndex = nextIdx
            _dailyQuote.value = quotes[nextIdx]

            // Try to get a fresh custom-generated quote from Gemini in the background
            try {
                val remoteQuote = repository.getMotivationalQuote()
                if (remoteQuote.isNotBlank() && !quotes.contains(remoteQuote)) {
                    _dailyQuote.value = remoteQuote
                }
            } catch (e: Exception) {
                // Keep the local fallback quote
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

    // --- Loadshedding Sync & Offline-First States ---
    private val _isLoadsheddingMode = MutableStateFlow(false)
    val isLoadsheddingMode: StateFlow<Boolean> = _isLoadsheddingMode.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    fun setLoadsheddingMode(enabled: Boolean) {
        _isLoadsheddingMode.value = enabled
    }

    fun syncOfflineData() {
        if (_pendingSyncCount.value == 0) return
        viewModelScope.launch {
            _isVoiceLoggerProcessing.value = true
            kotlinx.coroutines.delay(1200) // Simulate low-bandwidth sync compression
            _pendingSyncCount.value = 0
            _isVoiceLoggerProcessing.value = false
        }
    }

    // --- Peer Accountability Study Duels States ---
    private val _isDuelActive = MutableStateFlow(false)
    val isDuelActive: StateFlow<Boolean> = _isDuelActive.asStateFlow()

    private val _duelTimeRemaining = MutableStateFlow(45 * 60)
    val duelTimeRemaining: StateFlow<Int> = _duelTimeRemaining.asStateFlow()

    private val _duelOpponentScore = MutableStateFlow(0)
    val duelOpponentScore: StateFlow<Int> = _duelOpponentScore.asStateFlow()

    private val _duelMyScore = MutableStateFlow(0)
    val duelMyScore: StateFlow<Int> = _duelMyScore.asStateFlow()

    private val _duelOpponentReaction = MutableStateFlow("")
    val duelOpponentReaction: StateFlow<String> = _duelOpponentReaction.asStateFlow()

    fun startDuel(durationMinutes: Int) {
        _isDuelActive.value = true
        _duelTimeRemaining.value = durationMinutes * 60
        _duelMyScore.value = 0
        _duelOpponentScore.value = 0
        _duelOpponentReaction.value = ""
    }

    fun stopDuel() {
        _isDuelActive.value = false
    }

    fun earnDuelPoints(points: Int) {
        _duelMyScore.value = _duelMyScore.value + points
        addXp(points)
    }

    fun simulateOpponentAction() {
        if (!_isDuelActive.value) return
        // Randomly increase score and send reaction
        _duelOpponentScore.value = _duelOpponentScore.value + (3..8).random()
        val reactions = listOf("✋ High-Five!", "🤲 Dua/Blessings!", "👏 Shabash / Bravo!", "💪 Keep Going!", "🔥 Spark On!")
        _duelOpponentReaction.value = reactions.random()
    }

    fun clearOpponentReaction() {
        _duelOpponentReaction.value = ""
    }

    // --- Bilingual Smart Voice Logger States & Functions ---
    private val _isVoiceLoggerProcessing = MutableStateFlow(false)
    val isVoiceLoggerProcessing: StateFlow<Boolean> = _isVoiceLoggerProcessing.asStateFlow()

    fun parseVoiceLoggerSchedule(speechText: String) {
        if (speechText.isBlank()) return
        viewModelScope.launch {
            _isVoiceLoggerProcessing.value = true
            try {
                val tasksAdded = if (_isLoadsheddingMode.value) {
                    parseBilingualScheduleOffline(speechText)
                } else {
                    val systemInstruction = "You are an expert bilingual Roman Urdu and English study planner for Pakistani Matric (Grade 9 & 10) students."
                    val prompt = """
                        Analyze this spoken schedule recorded by the student (it may be in Roman Urdu, English, or a mix of both):
                        "$speechText"
                        
                        Extract the structured tasks described.
                        - Map subjects correctly: Physics, Chemistry, Mathematics, Biology, Urdu, Islamiat, Computer Science, English, Pakistan Studies, or General Study.
                        - Detect time estimates (e.g., '1 ghanta' -> 60, 'half hour' -> 30, 'numerical part' -> 45 minutes).
                        - Detect urgency / due dates (e.g., 'kal subha' or 'tomorrow morning' -> due tomorrow 9 AM, 'parso' or 'day after tomorrow' -> due in 2 days, 'aaj shaam' -> due today 6 PM).
                        - Determine task types: STUDY, TEST, REVISION, ASSIGNMENT.
                        - Workload score: 1 to 5 (heavy derivations or tests are 4 or 5, simple reading or homework is 2 or 3).

                        Return a JSON array matching this EXACT schema:
                        [
                          {
                            "title": "Clear actionable title in English (e.g. 'Solve Chapter 3 Numerical Questions')",
                            "subject": "Physics",
                            "chapter": "Chapter 3 (e.g. Force and Motion)",
                            "taskType": "ASSIGNMENT",
                            "daysFromNow": 1,
                            "hourOfDay": 9,
                            "estimatedMinutes": 60,
                            "workloadScore": 4
                          }
                        ]
                        Provide ONLY valid JSON. No markdown, no extra explanation text.
                    """.trimIndent()
                    try {
                        val jsonResponse = com.example.data.GeminiClient.generateText(prompt, systemInstruction, jsonOutput = true)
                        val cleanJson = jsonResponse.trim().removeSurrounding("```json", "```").trim()
                        
                        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.ProposedTaskJson::class.java)
                        val adapter = moshi.adapter<List<com.example.data.ProposedTaskJson>>(type)
                        val parsedList = adapter.fromJson(cleanJson) ?: emptyList()
                        
                        val mapped = parsedList.map { pt ->
                            val cal = Calendar.getInstance()
                            cal.add(Calendar.DATE, pt.daysFromNow)
                            cal.set(Calendar.HOUR_OF_DAY, pt.hourOfDay)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            
                            Task(
                                title = pt.title,
                                subject = pt.subject,
                                chapter = pt.chapter,
                                taskType = pt.taskType,
                                dueDate = cal.timeInMillis,
                                estimatedMinutes = pt.estimatedMinutes,
                                workloadScore = pt.workloadScore,
                                studentName = _userName.value
                            )
                        }
                        mapped
                    } catch (e: Exception) {
                        parseBilingualScheduleOffline(speechText)
                    }
                }
                
                for (t in tasksAdded) {
                    repository.insertTask(t)
                }
                
                if (_isLoadsheddingMode.value) {
                    _pendingSyncCount.value = _pendingSyncCount.value + tasksAdded.size
                }
            } catch (e: Exception) {
                // Handled
            } finally {
                _isVoiceLoggerProcessing.value = false
            }
        }
    }

    private fun parseBilingualScheduleOffline(rawText: String): List<Task> {
        val lines = rawText.split(Regex("(?i)\\b(aur|and|phir|then|also)\\b"))
            .map { it.trim() }
            .filter { it.length > 3 }

        val tasks = mutableListOf<Task>()
        lines.forEachIndexed { idx, line ->
            val l = line.lowercase()
            val subject = when {
                l.contains("math") || l.contains("riyazi") || l.contains("algebra") -> "Mathematics"
                l.contains("phys") || l.contains("tabiyaat") -> "Physics"
                l.contains("chem") || l.contains("kimiya") -> "Chemistry"
                l.contains("bio") || l.contains("hayatiyat") -> "Biology"
                l.contains("urdu") -> "Urdu"
                l.contains("islam") || l.contains("islamiat") -> "Islamiat"
                l.contains("computer") || l.contains("comp") -> "Computer Science"
                l.contains("eng") || l.contains("english") -> "English"
                l.contains("pak") || l.contains("mutalia") || l.contains("studies") -> "Pakistan Studies"
                else -> "General Study"
            }

            val isTomorrow = l.contains("kal") || l.contains("tomorrow") || l.contains("subha")
            val isParso = l.contains("parso") || l.contains("day after")
            val days = when {
                isParso -> 2
                isTomorrow -> 1
                else -> 0
            }

            val isTest = l.contains("test") || l.contains("exam") || l.contains("parcha") || l.contains("prep")
            val isHw = l.contains("homework") || l.contains("numerical") || l.contains("exercise") || l.contains("mashq")
            
            val type = when {
                isTest -> "TEST"
                isHw -> "ASSIGNMENT"
                else -> "STUDY"
            }

            val minutes = when {
                l.contains("1 ghanta") || l.contains("one hour") || l.contains("1 hour") -> 60
                l.contains("2 ghante") || l.contains("two hours") -> 120
                l.contains("half hour") || l.contains("aadha ghanta") -> 30
                else -> 45
            }

            val cal = Calendar.getInstance()
            cal.add(Calendar.DATE, days)
            cal.set(Calendar.HOUR_OF_DAY, if (l.contains("subha") || l.contains("morning")) 9 else 16)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            tasks.add(
                Task(
                    title = line.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                    subject = subject,
                    chapter = "Bilingual Voice Entry",
                    taskType = type,
                    dueDate = cal.timeInMillis,
                    estimatedMinutes = minutes,
                    workloadScore = if (type == "TEST") 4 else 3,
                    studentName = _userName.value
                )
            )
        }
        
        return if (tasks.isNotEmpty()) tasks else listOf(
            Task(
                title = "Voice Input: " + rawText.take(40) + "...",
                subject = "General Study",
                chapter = "Voice Logger",
                taskType = "STUDY",
                dueDate = System.currentTimeMillis() + 4 * 3600000L,
                estimatedMinutes = 45,
                workloadScore = 3,
                studentName = _userName.value
            )
        )
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
                workloadScore = workloadScore,
                studentName = _userName.value
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
                repository.sendMessageToCoach(text, _userName.value)
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
                repository.analyzeTestPaperMistakes(text, _userName.value)
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
            val newItem = com.example.data.TodoItem(title = title, studentName = _userName.value)
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

    // --- Advanced AI Task Division ---
    private val _proposedTasks = MutableStateFlow<List<com.example.data.ProposedTaskJson>?>(null)
    val proposedTasks: StateFlow<List<com.example.data.ProposedTaskJson>?> = _proposedTasks.asStateFlow()

    private val _isAnalyzingTodo = MutableStateFlow(false)
    val isAnalyzingTodo: StateFlow<Boolean> = _isAnalyzingTodo.asStateFlow()

    fun insertTask(task: Task) {
        viewModelScope.launch {
            repository.insertTask(task)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun analyzeAndDivideTodoPlan(rawText: String) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _isAnalyzingTodo.value = true
            try {
                val list = repository.generateAIStudyPlan(rawText, _userMood.value, _topStudyHours.value)
                _proposedTasks.value = list
            } catch (e: Exception) {
                // Handled
            } finally {
                _isAnalyzingTodo.value = false
            }
        }
    }

    fun clearProposedTasks() {
        _proposedTasks.value = null
    }

    fun commitProposedTasks(finalList: List<com.example.data.ProposedTaskJson>) {
        viewModelScope.launch {
            for (pt in finalList) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DATE, pt.daysFromNow)
                cal.set(Calendar.HOUR_OF_DAY, pt.hourOfDay)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                val newTask = Task(
                    title = pt.title,
                    subject = pt.subject,
                    chapter = pt.chapter,
                    taskType = pt.taskType,
                    dueDate = cal.timeInMillis,
                    estimatedMinutes = pt.estimatedMinutes,
                    workloadScore = pt.workloadScore,
                    studentName = _userName.value
                )
                repository.insertTask(newTask)
            }
            _proposedTasks.value = null
        }
    }

    // --- Syllabus Progress & Spaced Repetition Reminders & Subject Targets ---

    fun loadSyllabusProgress() {
        val map = mutableMapOf<String, String>()
        sharedPrefs.all.forEach { (key, value) ->
            if (key.startsWith("syllabus_status_")) {
                val cleanKey = key.removePrefix("syllabus_status_")
                map[cleanKey] = value as? String ?: "NOT_STARTED"
            }
        }
        _syllabusProgress.value = map
    }

    fun getSyllabusStatus(subject: String, chapter: String, subtopic: String): String {
        return _syllabusProgress.value["$subject|$chapter|$subtopic"] ?: "NOT_STARTED"
    }

    fun setSyllabusStatus(subject: String, chapter: String, subtopic: String, status: String) {
        viewModelScope.launch {
            val key = "$subject|$chapter|$subtopic"
            val updated = _syllabusProgress.value.toMutableMap()
            updated[key] = status
            _syllabusProgress.value = updated
            sharedPrefs.edit().putString("syllabus_status_$key", status).apply()
        }
    }



    fun loadSubjectWeeklyTargets() {
        val map = mutableMapOf<String, Int>()
        val subjects = listOf("Mathematics", "Physics", "Biology", "Chemistry", "English")
        subjects.forEach { sub ->
            val target = sharedPrefs.getInt("subject_weekly_target_$sub", 4) // default 4 hours
            map[sub] = target
        }
        _subjectWeeklyTargets.value = map
    }

    fun setSubjectWeeklyTarget(subject: String, targetHours: Int) {
        viewModelScope.launch {
            val updated = _subjectWeeklyTargets.value.toMutableMap()
            updated[subject] = targetHours
            _subjectWeeklyTargets.value = updated
            sharedPrefs.edit().putInt("subject_weekly_target_$subject", targetHours).apply()
        }
    }

    fun scheduleSpacedRepetition(subject: String, chapter: String, subtopic: String) {
        viewModelScope.launch {
            val key = "$subject|$chapter|$subtopic"
            sharedPrefs.edit().putBoolean("spaced_rep_active_$key", true).apply()
            
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 10) // Schedule at 10 AM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val intervals = listOf(1, 3, 5, 7)
            intervals.forEach { days ->
                val dueDate = today + days * 86400000L
                repository.insertTask(
                    Task(
                        title = "Spaced Repetition: Review $subtopic",
                        subject = subject,
                        chapter = chapter,
                        taskType = "REVISION",
                        dueDate = dueDate,
                        estimatedMinutes = 30,
                        workloadScore = 2,
                        studentName = _userName.value
                    )
                )
            }
            
            // Reload syllabus status just in case
            loadSyllabusProgress()
        }
    }

    fun isSpacedRepetitionActive(subject: String, chapter: String, subtopic: String): Boolean {
        return sharedPrefs.getBoolean("spaced_rep_active_$subject|$chapter|$subtopic", false)
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

package com.example.ui

import android.graphics.Bitmap
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
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

    // MODULE 2: Custom Subjects & Dynamic Weekly Target Tracking
    private val _customSubjects = MutableStateFlow<List<String>>(emptyList())
    val customSubjects: StateFlow<List<String>> = _customSubjects.asStateFlow()

    private val _subjectStudyTimeLogs = MutableStateFlow<Map<String, Float>>(emptyMap())
    val subjectStudyTimeLogs: StateFlow<Map<String, Float>> = _subjectStudyTimeLogs.asStateFlow()

    init {
        _registeredUsers.value = loadRegisteredUsers()
        loadCustomSubjects()
        loadSyllabusProgress()
        loadSubjectWeeklyTargets()
        loadSubjectStudyTimeLogs()
    }

    private val _xpPoints = MutableStateFlow(sharedPrefs.getInt("xp_points_${sharedPrefs.getString("user_name", "")}", 0))
    val xpPoints: StateFlow<Int> = _xpPoints.asStateFlow()

    private fun saveTimestampList(key: String, list: List<Long>) {
        val json = list.joinToString(",")
        sharedPrefs.edit().putString(key, json).apply()
    }

    private fun loadTimestampList(key: String): List<Long> {
        val str = sharedPrefs.getString(key, "") ?: ""
        if (str.isEmpty()) return emptyList()
        return str.split(",").mapNotNull { it.toLongOrNull() }
    }

    private val _activeRecallLogs = MutableStateFlow<List<Long>>(
        loadTimestampList("active_recall_logs_${sharedPrefs.getString("user_name", "")}")
    )
    val activeRecallLogs: StateFlow<List<Long>> = _activeRecallLogs.asStateFlow()

    private val _revisionSlotLogs = MutableStateFlow<List<Long>>(
        loadTimestampList("revision_slot_logs_${sharedPrefs.getString("user_name", "")}")
    )
    val revisionSlotLogs: StateFlow<List<Long>> = _revisionSlotLogs.asStateFlow()

    private val _aiCoachLogs = MutableStateFlow<List<Long>>(
        loadTimestampList("ai_coach_logs_${sharedPrefs.getString("user_name", "")}")
    )
    val aiCoachLogs: StateFlow<List<Long>> = _aiCoachLogs.asStateFlow()

    fun logActiveRecallSession() {
        viewModelScope.launch {
            val name = _userName.value
            val list = _activeRecallLogs.value.toMutableList()
            list.add(System.currentTimeMillis())
            _activeRecallLogs.value = list
            saveTimestampList("active_recall_logs_$name", list)
        }
    }

    fun logRevisionSlot() {
        viewModelScope.launch {
            val name = _userName.value
            val list = _revisionSlotLogs.value.toMutableList()
            list.add(System.currentTimeMillis())
            _revisionSlotLogs.value = list
            saveTimestampList("revision_slot_logs_$name", list)
        }
    }

    fun logAiCoachUsage() {
        viewModelScope.launch {
            val name = _userName.value
            val list = _aiCoachLogs.value.toMutableList()
            list.add(System.currentTimeMillis())
            _aiCoachLogs.value = list
            saveTimestampList("ai_coach_logs_$name", list)
        }
    }

    private val _hoursStudied = MutableStateFlow<Float>(
        if (sharedPrefs.contains("hours_studied_${sharedPrefs.getString("user_name", "")}")) {
            sharedPrefs.getFloat("hours_studied_${sharedPrefs.getString("user_name", "")}", 0f)
        } else {
            loadFocusSessions().sumOf { it.durationMinutes } / 60f
        }
    )
    val hoursStudied: StateFlow<Float> = _hoursStudied.asStateFlow()

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

    private val _sleepStartHour = MutableStateFlow(sharedPrefs.getInt("sleep_start_hour_${sharedPrefs.getString("user_name", "")}", 22))
    val sleepStartHour: StateFlow<Int> = _sleepStartHour.asStateFlow()

    private val _sleepEndHour = MutableStateFlow(sharedPrefs.getInt("sleep_end_hour_${sharedPrefs.getString("user_name", "")}", 6))
    val sleepEndHour: StateFlow<Int> = _sleepEndHour.asStateFlow()

    private val _powerNapHour = MutableStateFlow(sharedPrefs.getInt("power_nap_hour_${sharedPrefs.getString("user_name", "")}", 14))
    val powerNapHour: StateFlow<Int> = _powerNapHour.asStateFlow()

    private val _isPowerNapEnabled = MutableStateFlow(sharedPrefs.getBoolean("power_nap_enabled_${sharedPrefs.getString("user_name", "")}", true))
    val isPowerNapEnabled: StateFlow<Boolean> = _isPowerNapEnabled.asStateFlow()

    private val _powerNapDuration = MutableStateFlow(sharedPrefs.getInt("power_nap_duration_${sharedPrefs.getString("user_name", "")}", 30))
    val powerNapDuration: StateFlow<Int> = _powerNapDuration.asStateFlow()

    private val _unsealedSleepHours = MutableStateFlow<Set<Int>>(emptySet())
    val unsealedSleepHours: StateFlow<Set<Int>> = _unsealedSleepHours.asStateFlow()

    private val _hourlySubTasks = MutableStateFlow<Map<Int, List<com.example.data.TimelineSubTask>>>(emptyMap())
    val hourlySubTasks: StateFlow<Map<Int, List<com.example.data.TimelineSubTask>>> = _hourlySubTasks.asStateFlow()

    fun setSleepSchedule(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            val name = _userName.value
            _sleepStartHour.value = startHour
            _sleepEndHour.value = endHour
            sharedPrefs.edit()
                .putInt("sleep_start_hour_$name", startHour)
                .putInt("sleep_end_hour_$name", endHour)
                .apply()
        }
    }

    fun setPowerNap(enabled: Boolean, hour: Int, durationMins: Int) {
        viewModelScope.launch {
            val name = _userName.value
            _isPowerNapEnabled.value = enabled
            _powerNapHour.value = hour
            _powerNapDuration.value = durationMins
            sharedPrefs.edit()
                .putBoolean("power_nap_enabled_$name", enabled)
                .putInt("power_nap_hour_$name", hour)
                .putInt("power_nap_duration_$name", durationMins)
                .apply()
        }
    }

    fun toggleUnsealSleepHour(hour: Int) {
        val current = _unsealedSleepHours.value.toMutableSet()
        if (current.contains(hour)) {
            current.remove(hour)
        } else {
            current.add(hour)
        }
        _unsealedSleepHours.value = current
    }

    fun addTimelineSubTask(hour: Int, title: String, minuteRange: String? = null) {
        val currentMap = _hourlySubTasks.value.toMutableMap()
        val list = (currentMap[hour] ?: emptyList()).toMutableList()
        val sub = com.example.data.TimelineSubTask(hour = hour, title = title, minuteRange = minuteRange)
        list.add(sub)
        currentMap[hour] = list
        _hourlySubTasks.value = currentMap

        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val newTask = Task(
                title = title,
                subject = "General Study",
                chapter = "Timeline Slot ${String.format("%02d:00", hour)}",
                taskType = "STUDY",
                dueDate = cal.timeInMillis,
                estimatedMinutes = 30,
                workloadScore = 2,
                studentName = _userName.value
            )
            repository.insertTask(newTask)
        }
    }

    fun scheduleWeightageExamBlocks(
        subjectName: String,
        taskLoadChapters: String,
        blocks: List<Pair<String, Int>>
    ) {
        viewModelScope.launch {
            val currentCal = Calendar.getInstance()
            var currentHour = currentCal.get(Calendar.HOUR_OF_DAY)
            if (currentHour < 8 || currentHour > 21) currentHour = 9

            val studentNameStr = _userName.value

            blocks.forEachIndexed { index, (blockLabel, mins) ->
                val hourToAssign = (currentHour + index) % 24
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourToAssign)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }

                val task = Task(
                    title = "[$subjectName] $blockLabel",
                    subject = if (subjectName.isBlank()) "General Subject" else subjectName,
                    chapter = taskLoadChapters.ifBlank { "Exam Weightage Focus" },
                    taskType = when {
                        blockLabel.contains("MCQ", ignoreCase = true) -> "TEST"
                        blockLabel.contains("Short", ignoreCase = true) -> "REVISION"
                        else -> "STUDY"
                    },
                    dueDate = cal.timeInMillis,
                    estimatedMinutes = mins,
                    workloadScore = when {
                        mins >= 45 -> 4
                        mins >= 30 -> 3
                        else -> 2
                    },
                    studentName = studentNameStr
                )
                repository.insertTask(task)

                // Add to hourly timeline subtasks
                val currentMap = _hourlySubTasks.value.toMutableMap()
                val list = (currentMap[hourToAssign] ?: emptyList()).toMutableList()
                list.add(com.example.data.TimelineSubTask(hour = hourToAssign, title = "[$subjectName] $blockLabel", minuteRange = "$mins mins"))
                currentMap[hourToAssign] = list
                _hourlySubTasks.value = currentMap
            }
        }
    }

    fun toggleTimelineSubTask(hour: Int, subTaskId: String) {
        val currentMap = _hourlySubTasks.value.toMutableMap()
        val list = (currentMap[hour] ?: emptyList()).map {
            if (it.id == subTaskId) it.copy(isCompleted = !it.isCompleted) else it
        }
        currentMap[hour] = list
        _hourlySubTasks.value = currentMap
    }

    fun deleteTimelineSubTask(hour: Int, subTaskId: String) {
        val currentMap = _hourlySubTasks.value.toMutableMap()
        val list = (currentMap[hour] ?: emptyList()).filter { it.id != subTaskId }
        currentMap[hour] = list
        _hourlySubTasks.value = currentMap
    }

    fun editTimelineSubTask(hour: Int, subTaskId: String, newTitle: String) {
        val currentMap = _hourlySubTasks.value.toMutableMap()
        val list = (currentMap[hour] ?: emptyList()).map {
            if (it.id == subTaskId) it.copy(title = newTitle) else it
        }
        currentMap[hour] = list
        _hourlySubTasks.value = currentMap
    }


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
            val startHour = sharedPrefs.getInt("sleep_start_hour_$name", 22)
            val endHour = sharedPrefs.getInt("sleep_end_hour_$name", 6)
            val napHour = sharedPrefs.getInt("power_nap_hour_$name", 14)
            val napEnabled = sharedPrefs.getBoolean("power_nap_enabled_$name", true)
            val napDur = sharedPrefs.getInt("power_nap_duration_$name", 30)
            val userXp = sharedPrefs.getInt("xp_points_$name", 120)
            val userHours = if (sharedPrefs.contains("hours_studied_$name")) {
                sharedPrefs.getFloat("hours_studied_$name", 0f)
            } else {
                _focusSessions.value.sumOf { it.durationMinutes } / 60f
            }

            _sleepStartHour.value = startHour
            _sleepEndHour.value = endHour
            _powerNapHour.value = napHour
            _isPowerNapEnabled.value = napEnabled
            _powerNapDuration.value = napDur
            _dailySleepHours.value = userSleep
            _xpPoints.value = userXp
            _hoursStudied.value = userHours
            _activeRecallLogs.value = loadTimestampList("active_recall_logs_$name")
            _revisionSlotLogs.value = loadTimestampList("revision_slot_logs_$name")
            _aiCoachLogs.value = loadTimestampList("ai_coach_logs_$name")
            _matricExamDays.value = calculateRemainingMatricDays(name)
            
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

    fun calculateRemainingMatricDays(userName: String = _userName.value): Int {
        val name = if (userName.isNotEmpty()) userName else (sharedPrefs.getString("user_name", "") ?: "")
        val valKey = "matric_exam_days_value_$name"
        val timeKey = "matric_exam_days_timestamp_$name"
        val legacyKey = "matric_exam_days_$name"

        val now = System.currentTimeMillis()

        if (!sharedPrefs.contains(valKey) && sharedPrefs.contains(legacyKey)) {
            val legacyVal = sharedPrefs.getInt(legacyKey, 95)
            sharedPrefs.edit()
                .putInt(valKey, legacyVal)
                .putLong(timeKey, now)
                .apply()
            return legacyVal
        } else if (!sharedPrefs.contains(valKey)) {
            sharedPrefs.edit()
                .putInt(valKey, 95)
                .putLong(timeKey, now)
                .apply()
            return 95
        }

        val setVal = sharedPrefs.getInt(valKey, 95)
        val setTime = sharedPrefs.getLong(timeKey, now)

        val diffMillis = now - setTime
        if (diffMillis <= 0L) return setVal

        val daysPassed = (diffMillis / (1000L * 60 * 60 * 24)).toInt()
        return (setVal - daysPassed).coerceAtLeast(0)
    }

    private val _matricExamDays = MutableStateFlow(calculateRemainingMatricDays())
    val matricExamDays: StateFlow<Int> = _matricExamDays.asStateFlow()

    fun setMatricExamDays(days: Int) {
        viewModelScope.launch {
            val name = _userName.value
            val valid = days.coerceAtLeast(0)
            val now = System.currentTimeMillis()
            sharedPrefs.edit()
                .putInt("matric_exam_days_value_$name", valid)
                .putLong("matric_exam_days_timestamp_$name", now)
                .putInt("matric_exam_days_$name", valid)
                .apply()
            _matricExamDays.value = valid
        }
    }

    fun refreshMatricExamDays() {
        _matricExamDays.value = calculateRemainingMatricDays()
    }

    private val _notificationLogs = MutableStateFlow<List<Pair<String, Boolean>>>(
        listOf(
            "🔔 [Today] Remember to study limits for Mathematics calculus test coming up in 30 days!" to true,
            "🔔 [Today] Goal Master: Study goal set to 12 hours. Study consistently to earn bonus XP!" to false,
            "🔔 [Yesterday] Quote of the day: Tap on the quote card to view brand new unique motivation!" to false
        )
    )
    val notificationLogs: StateFlow<List<Pair<String, Boolean>>> = _notificationLogs.asStateFlow()

    fun addNotificationLog(text: String) {
        val current = _notificationLogs.value.toMutableList()
        current.add(0, text to true)
        _notificationLogs.value = current
    }

    private val _focusSessions = MutableStateFlow<List<FocusSession>>(loadFocusSessions())
    val focusSessions: StateFlow<List<FocusSession>> = _focusSessions.asStateFlow()

    private fun generateDefaultFocusSessions(): List<FocusSession> {
        return emptyList()
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

    fun addHoursStudied(hours: Float) {
        viewModelScope.launch {
            val name = _userName.value
            val current = _hoursStudied.value
            val next = current + hours
            _hoursStudied.value = next
            sharedPrefs.edit().putFloat("hours_studied_$name", next).apply()
        }
    }

    fun logFocusSession(taskTitle: String, durationMinutes: Int, xpEarned: Int) {
        viewModelScope.launch {
            if (xpEarned > 0) {
                addXp(xpEarned)
            }
            addHoursStudied(durationMinutes / 60f)
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

    val overdueTasks: StateFlow<List<Task>> = kotlinx.coroutines.flow.combine(allTasks, MutableStateFlow(System.currentTimeMillis())) { tasks, _ ->
        val now = System.currentTimeMillis()
        tasks.filter { !it.isCompleted && it.dueDate < now }
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
        viewModelScope.launch {
            while (isActive) {
                refreshMatricExamDays()
                kotlinx.coroutines.delay(60_000L)
            }
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
            if (!task.isCompleted) {
                addXp(15)
                val duration = if (task.estimatedMinutes > 0) task.estimatedMinutes else 25
                logFocusSession(task.title, duration, 0)
            }
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

    fun performEodRollover() {
        viewModelScope.launch {
            _isRescheduling.value = true
            try {
                repository.performMidnightEodRollover()
            } catch (e: Exception) {
                e.printStackTrace()
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

    // Interactive AI Partner Companion States
    private val _duelAiMessage = MutableStateFlow("Aisha AI 🤖: Welcome to Study Duel! Choose your focus duration and let's crush our goals together.")
    val duelAiMessage: StateFlow<String> = _duelAiMessage.asStateFlow()

    private var duelEndTimeMs: Long = 0L
    private var duelTimerJob: Job? = null

    fun startDuel(durationMinutes: Int) {
        _isDuelActive.value = true
        _duelMyScore.value = 0
        _duelOpponentScore.value = 0
        _duelOpponentReaction.value = ""

        // MODULE 1: Accurate countdown timer using timestamp calculations (System.currentTimeMillis())
        val totalSecs = durationMinutes * 60
        _duelTimeRemaining.value = totalSecs
        duelEndTimeMs = System.currentTimeMillis() + totalSecs * 1000L

        _duelAiMessage.value = "Aisha AI 🤖: Study Duel launched for $durationMinutes mins! I'll track your remaining time accurately and motivate you along the way."

        duelTimerJob?.cancel()
        duelTimerJob = viewModelScope.launch {
            var lastAiNotifyTimeSecs = totalSecs
            while (_isDuelActive.value) {
                kotlinx.coroutines.delay(500)
                val remainingSecs = ((duelEndTimeMs - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                _duelTimeRemaining.value = remainingSecs

                // AI Partner live notifications based on remaining time
                if (lastAiNotifyTimeSecs - remainingSecs >= 30 || remainingSecs == 0) {
                    lastAiNotifyTimeSecs = remainingSecs
                    val minsLeft = remainingSecs / 60
                    triggerAiPartnerNotification(minsLeft, remainingSecs)
                }

                if (remainingSecs <= 0) {
                    _isDuelActive.value = false
                    _duelAiMessage.value = "Aisha AI 🤖: 🏆 DUEL TIME COMPLETE! Phenomenal work! You earned +200 Focus XP."
                    earnDuelPoints(200)
                    break
                }
            }
        }
    }

    private fun triggerAiPartnerNotification(minsLeft: Int, secsLeft: Int) {
        if (secsLeft in 1..59) {
            _duelAiMessage.value = "Aisha AI 🤖: 🚨 Final minute countdown! $secsLeft seconds left — complete your answer! ⏱️"
            return
        }
        val prompts = listOf(
            "Aisha AI 🤖: $minsLeft minutes left, keep the momentum going! Stay focused on your notes. 💡",
            "Aisha AI 🤖: $minsLeft mins remaining! You're crushing this study block — Board Exam toppers are made right now! 🔥",
            "Aisha AI 🤖: Halfway mark approaching! Don't touch distractions. Take deep breaths and solve the next question. 🧠",
            "Aisha AI 🤖: $minsLeft mins left! 'Small daily efforts compound into 90%+ Board Exam scores!' 💪",
            "Aisha AI 🤖: Only $minsLeft minutes left! Finish strong and review your key formulas! 📐"
        )
        _duelAiMessage.value = prompts.random()
    }

    fun requestAiMotivationPrompt() {
        val motivations = listOf(
            "Aisha AI 🤖: 'Believe you can and you're halfway there! High marks in Matric open doors to top pre-engineering/pre-medical colleges.' 🌟",
            "Aisha AI 🤖: 'Focus on understanding concepts rather than rote memorization. Solve 1 past paper question right now!' 📝",
            "Aisha AI 🤖: 'Hydrate, fix your posture, and push through! Your future self will thank you for this duel session.' 💧",
            "Aisha AI 🤖: 'You're currently outperforming 85% of peers in total focus minutes today! Keep it up!' 📈"
        )
        _duelAiMessage.value = motivations.random()
    }

    fun stopDuel() {
        _isDuelActive.value = false
        duelTimerJob?.cancel()
        _duelAiMessage.value = "Aisha AI 🤖: Duel stopped. Relaunch anytime to gain focus XP."
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

    fun parseVoiceLoggerSchedule(speechText: String, onComplete: ((List<Task>) -> Unit)? = null) {
        if (speechText.isBlank()) return
        viewModelScope.launch {
            _isVoiceLoggerProcessing.value = true
            var tasksAdded = emptyList<Task>()
            try {
                tasksAdded = if (_isLoadsheddingMode.value) {
                    parseBilingualScheduleOffline(speechText)
                } else {
                    val systemInstruction = "You are an expert bilingual Roman Urdu and English study planner for Pakistani Matric (Grade 9 & 10) students."
                    val prompt = """
                        Analyze this spoken schedule recorded by the student (it may be in Roman Urdu, English, or a mix of both):
                        "$speechText"
                        
                        Extract the structured tasks described.
                        - Map subjects correctly: Physics, Chemistry, Mathematics, Biology, Urdu, Islamiat, Computer Science, English, Pakistan Studies, or General Study.
                        - ALWAYS translate titles to clear, concise ENGLISH (e.g. 'Solve Physics Chapter 3 Numericals', 'Practice Mathematics Algebra Theorems', 'Review Chemistry Compounds').
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
                    registerAndScheduleTestIfNeeded(t)
                }
                
                if (_isLoadsheddingMode.value) {
                    _pendingSyncCount.value = _pendingSyncCount.value + tasksAdded.size
                }
            } catch (e: Exception) {
                // Handled
            } finally {
                _isVoiceLoggerProcessing.value = false
                onComplete?.invoke(tasksAdded)
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
            registerAndScheduleTestIfNeeded(newTask)
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteTaskById(id)
        }
    }

    fun sendCoachMessage(text: String) {
        if (text.isBlank()) return
        logAiCoachUsage()
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
                val scannedTasks = repository.scanHomeworkText(text)
                for (task in scannedTasks) {
                    registerAndScheduleTestIfNeeded(task)
                }
            } catch (e: Exception) {
                // handled
            } finally {
                _scanLoading.value = false
            }
        }
    }

    fun processHomeworkImage(
        bitmap: Bitmap,
        onTextExtracted: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _scanLoading.value = true
            try {
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val prompt = """
                    Analyze this image as an expert transcriber. Accurately decipher messy human handwriting, abbreviations, strike-throughs, and multi-line lists.
                    Read all handwritten or printed homework tasks, study objectives, assignments, and to-do items from this image accurately.
                    
                    Handling Missing Dates & Times:
                    - If a task has no date specified, automatically default target_date to Today's Date ($todayStr).
                    - If no duration or time is specified, intelligently estimate a default duration (e.g., 30–45 minutes) based on the task type.
                    
                    Return clean extracted plain text line by line, formatted as list items with estimated durations and target date where relevant.
                """.trimIndent()
                val text = com.example.data.GeminiClient.analyzeImage(bitmap, prompt)
                if (text.isNotBlank()) {
                    onTextExtracted(text.trim())
                } else {
                    onError?.invoke("Handwriting unclear. Please double-check or type the task.")
                }
            } catch (e: Exception) {
                onError?.invoke("Handwriting unclear or API key missing. Sample loaded.")
                val fallbackText = """
                    - Mathematics limit functions homework questions 1 to 5 (45 mins)
                    - Physical Sciences friction force experiment report revision (30 mins)
                    - Life Sciences cell genetics diagram drawing (30 mins)
                """.trimIndent()
                onTextExtracted(fallbackText)
            } finally {
                _scanLoading.value = false
            }
        }
    }

    fun analyzeMistakesText(text: String, onComplete: ((List<WeakTopic>) -> Unit)? = null) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _mistakeLoading.value = true
            try {
                val discovered = repository.analyzeTestPaperMistakes(text, _userName.value)
                if (discovered.isNotEmpty()) {
                    val first = discovered.first()
                    val dateStr = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(first.scheduledRevisionDate ?: System.currentTimeMillis()))
                    addNotificationLog("🔍 [AI Gap Analysis] Identified ${discovered.size} learning gaps! First revision set for $dateStr.")
                }
                onComplete?.invoke(discovered)
            } catch (e: Exception) {
                // handled
            } finally {
                _mistakeLoading.value = false
            }
        }
    }

    fun scheduleWeakTopicRevision(
        topic: WeakTopic,
        targetTimestamp: Long,
        durationMinutes: Int = 45
    ) {
        viewModelScope.launch {
            val updatedTopic = topic.copy(scheduledRevisionDate = targetTimestamp)
            repository.insertWeakTopic(updatedTopic)

            // Create/Insert corresponding Task for Calendar & Hourly Timeline
            val revisionTask = Task(
                title = "Revision: ${topic.topicName}",
                subject = topic.subject,
                chapter = topic.topicName,
                taskType = "REVISION",
                dueDate = targetTimestamp,
                estimatedMinutes = durationMinutes,
                workloadScore = 4,
                studentName = _userName.value
            )
            repository.insertTask(revisionTask)

            val dateStr = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(targetTimestamp))
            addNotificationLog("🚨 [Revision Alert Scheduled] '${topic.topicName}' (${topic.subject}) set for $dateStr! ⏰ Populated on Calendar & Timeline.")
        }
    }

    fun addCustomWeakTopicAndSchedule(
        subject: String,
        topicName: String,
        mistakeDescription: String,
        confidenceLevel: Int,
        targetTimestamp: Long,
        durationMinutes: Int = 45
    ) {
        viewModelScope.launch {
            val newTopic = WeakTopic(
                subject = subject,
                topicName = topicName,
                confidenceLevel = confidenceLevel,
                mistakeDescription = mistakeDescription,
                scheduledRevisionDate = targetTimestamp,
                studentName = _userName.value
            )
            repository.insertWeakTopic(newTopic)

            val revisionTask = Task(
                title = "Revision: $topicName",
                subject = subject,
                chapter = topicName,
                taskType = "REVISION",
                dueDate = targetTimestamp,
                estimatedMinutes = durationMinutes,
                workloadScore = 4,
                studentName = _userName.value
            )
            repository.insertTask(revisionTask)

            val dateStr = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(targetTimestamp))
            addNotificationLog("🚨 [New Gap Logged] '$topicName' ($subject) scheduled for revision on $dateStr! ⏰ Check Hourly Timeline.")
        }
    }

    fun deleteWeakTopic(topic: WeakTopic) {
        viewModelScope.launch {
            repository.deleteWeakTopic(topic)
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
            registerAndScheduleTestIfNeeded(task)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            registerAndScheduleTestIfNeeded(task)
        }
    }

    /**
     * Checks if a task represents a future test, exam, quiz, or paper.
     * If so, categorizes it as taskType "TEST" with a valid future target date
     * so it auto-populates into the Exam & Paper Reminder Widget.
     * Automatically triggers the AI scheduler to calculate remaining days until the test date,
     * and dynamically slices & distributes preparation tasks across Weekly and Monthly Calendar Views.
     */
    fun registerAndScheduleTestIfNeeded(task: Task) {
        viewModelScope.launch {
            val titleLower = task.title.lowercase()
            val typeLower = task.taskType.lowercase()
            val isTestOrExam = typeLower == "test" ||
                    titleLower.contains("test") ||
                    titleLower.contains("exam") ||
                    titleLower.contains("quiz") ||
                    titleLower.contains("paper") ||
                    titleLower.contains("midterm") ||
                    titleLower.contains("final") ||
                    titleLower.contains("parcha") ||
                    titleLower.contains("assessment")

            if (isTestOrExam) {
                val now = System.currentTimeMillis()
                var testDueDate = task.dueDate

                // If the due date is today or in the past, default target date to next week (7 days from now)
                if (testDueDate <= now + 12 * 3600000L) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DATE, 7)
                    cal.set(Calendar.HOUR_OF_DAY, 10)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    testDueDate = cal.timeInMillis
                }

                val finalTestTask = task.copy(
                    taskType = "TEST",
                    dueDate = testDueDate
                )
                repository.insertTask(finalTestTask)

                // Calculate days remaining until test date
                val diffMs = testDueDate - now
                val daysRemaining = (diffMs / 86400000L).toInt().coerceAtLeast(1)

                // Create preparation tasks sliced across weekly & monthly calendar views leading up to test date
                val prepTasks = mutableListOf<Task>()

                // 1. Chapter & Concept Deep Review (early prep stage)
                val reviewDayOffset = (daysRemaining * 0.3).toInt().coerceIn(1, daysRemaining)
                val reviewCal = Calendar.getInstance().apply {
                    add(Calendar.DATE, reviewDayOffset)
                    set(Calendar.HOUR_OF_DAY, 14)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                prepTasks.add(
                    Task(
                        title = "📖 Concept Review: ${finalTestTask.subject} (${finalTestTask.chapter.ifBlank { finalTestTask.title }})",
                        subject = finalTestTask.subject,
                        chapter = finalTestTask.chapter.ifBlank { "Exam Prep" },
                        taskType = "REVISION",
                        dueDate = reviewCal.timeInMillis,
                        estimatedMinutes = 45,
                        workloadScore = 3,
                        studentName = _userName.value
                    )
                )

                // 2. Practice Questions & Active Recall (mid prep stage)
                val practiceDayOffset = (daysRemaining * 0.65).toInt().coerceIn(1, daysRemaining)
                val practiceCal = Calendar.getInstance().apply {
                    add(Calendar.DATE, practiceDayOffset)
                    set(Calendar.HOUR_OF_DAY, 16)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                prepTasks.add(
                    Task(
                        title = "✏️ Practice Questions: ${finalTestTask.subject} Active Recall",
                        subject = finalTestTask.subject,
                        chapter = finalTestTask.chapter.ifBlank { "Exam Prep" },
                        taskType = "STUDY",
                        dueDate = practiceCal.timeInMillis,
                        estimatedMinutes = 60,
                        workloadScore = 4,
                        studentName = _userName.value
                    )
                )

                // 3. Mock Test & Past Paper Simulation (final prep stage)
                if (daysRemaining >= 2) {
                    val mockDayOffset = daysRemaining - 1
                    val mockCal = Calendar.getInstance().apply {
                        add(Calendar.DATE, mockDayOffset)
                        set(Calendar.HOUR_OF_DAY, 17)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    prepTasks.add(
                        Task(
                            title = "📝 Mock Test: ${finalTestTask.title} Past Paper Simulation",
                            subject = finalTestTask.subject,
                            chapter = finalTestTask.chapter.ifBlank { "Exam Prep" },
                            taskType = "REVISION",
                            dueDate = mockCal.timeInMillis,
                            estimatedMinutes = 60,
                            workloadScore = 5,
                            studentName = _userName.value
                        )
                    )
                }

                for (pt in prepTasks) {
                    repository.insertTask(pt)
                }
            } else {
                repository.insertTask(task)
            }
        }
    }

    fun analyzeAndDivideTodoPlan(rawText: String) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _isAnalyzingTodo.value = true
            try {
                val list = repository.generateAIStudyPlan(rawText, _userMood.value, _topStudyHours.value)
                _proposedTasks.value = list
                // Simultaneously insert scheduled blocks into database so they appear on Today's Schedule & Hourly Timeline
                for (pt in list) {
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
                    registerAndScheduleTestIfNeeded(newTask)
                }
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
                registerAndScheduleTestIfNeeded(newTask)
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



    fun loadCustomSubjects() {
        val saved = sharedPrefs.getStringSet("custom_subjects_set", emptySet()) ?: emptySet()
        _customSubjects.value = saved.toList().sorted()
    }

    fun addCustomSubject(subjectName: String) {
        val clean = subjectName.trim()
        if (clean.isBlank()) return
        val current = _customSubjects.value.toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            _customSubjects.value = current
            sharedPrefs.edit().putStringSet("custom_subjects_set", current.toSet()).apply()
            
            // Default target
            if ((_subjectWeeklyTargets.value[clean] ?: 0) == 0) {
                setSubjectWeeklyTarget(clean, 4)
            }
        }
    }

    fun logSubjectStudyTime(subject: String, minutes: Int) {
        viewModelScope.launch {
            val currentMap = _subjectStudyTimeLogs.value.toMutableMap()
            val currentVal = currentMap[subject] ?: 0f
            val updatedVal = currentVal + (minutes / 60f)
            currentMap[subject] = updatedVal
            _subjectStudyTimeLogs.value = currentMap
            sharedPrefs.edit().putFloat("subject_logged_hours_$subject", updatedVal).apply()

            // Insert completed task record for real-time propagation across timeline
            repository.insertTask(
                Task(
                    title = "Study Session Log: $subject",
                    subject = subject,
                    chapter = "Logged Study Hours",
                    taskType = "STUDY",
                    dueDate = System.currentTimeMillis(),
                    estimatedMinutes = minutes,
                    isCompleted = true,
                    workloadScore = 2,
                    studentName = _userName.value
                )
            )
        }
    }

    fun loadSubjectStudyTimeLogs() {
        val map = mutableMapOf<String, Float>()
        val defaultSubjects = listOf("Mathematics", "Physics", "Biology", "Chemistry", "English")
        val allSubs = defaultSubjects + _customSubjects.value
        allSubs.forEach { sub ->
            val logged = sharedPrefs.getFloat("subject_logged_hours_$sub", 0f)
            map[sub] = logged
        }
        _subjectStudyTimeLogs.value = map
    }

    fun loadSubjectWeeklyTargets() {
        val map = mutableMapOf<String, Int>()
        val defaultSubjects = listOf("Mathematics", "Physics", "Biology", "Chemistry", "English")
        val allSubs = (defaultSubjects + _customSubjects.value).distinct()
        allSubs.forEach { sub ->
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

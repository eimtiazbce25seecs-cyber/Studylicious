package com.example.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.JsonClass
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StudyRepository(
    private val taskDao: TaskDao,
    private val streakDao: StreakDao,
    private val weakTopicDao: WeakTopicDao,
    private val chatDao: ChatDao,
    private val todoDao: TodoDao
) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val streakState: Flow<StreakState?> = streakDao.getStreak()
    val allWeakTopics: Flow<List<WeakTopic>> = weakTopicDao.getAllWeakTopics()
    val chatMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()
    val allTodoItems: Flow<List<TodoItem>> = todoDao.getAllTodoItems()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // --- Task operations ---
    suspend fun insertWeakTopic(weakTopic: WeakTopic) = withContext(Dispatchers.IO) {
        weakTopicDao.insertWeakTopic(weakTopic)
    }

    suspend fun deleteWeakTopic(weakTopic: WeakTopic) = withContext(Dispatchers.IO) {
        weakTopicDao.deleteWeakTopic(weakTopic)
    }

    suspend fun insertTask(task: Task) = withContext(Dispatchers.IO) {
        taskDao.insertTask(task)
    }

    suspend fun insertTasks(tasks: List<Task>) = withContext(Dispatchers.IO) {
        taskDao.insertTasks(tasks)
    }

    suspend fun updateTask(task: Task) = withContext(Dispatchers.IO) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: Task) = withContext(Dispatchers.IO) {
        taskDao.deleteTask(task)
    }

    suspend fun deleteTaskById(id: Long) = withContext(Dispatchers.IO) {
        taskDao.deleteTaskById(id)
    }

    // --- Streak operations ---
    suspend fun checkStreakValidity() = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val yesterdayStr = getYesterdayDateString()
        val currentStreakState = streakDao.getStreak().firstOrNull() ?: StreakState()
        if (currentStreakState.lastActiveDate.isNotEmpty() &&
            currentStreakState.lastActiveDate != todayStr &&
            currentStreakState.lastActiveDate != yesterdayStr) {
            // Full calendar day missed -> Reset streak to ZERO
            streakDao.insertStreak(
                currentStreakState.copy(currentStreak = 0)
            )
        }
    }

    suspend fun completeTaskAndCheckStreak(task: Task) = withContext(Dispatchers.IO) {
        val updatedTask = task.copy(isCompleted = !task.isCompleted)
        taskDao.updateTask(updatedTask)

        // Streak logic
        if (updatedTask.isCompleted) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val currentStreakState = streakDao.getStreak().firstOrNull() ?: StreakState()

            if (currentStreakState.lastActiveDate == todayStr) {
                // Streak already updated today
            } else {
                val yesterdayStr = getYesterdayDateString()
                val isConsecutive = currentStreakState.lastActiveDate == yesterdayStr || currentStreakState.lastActiveDate.isEmpty()
                val newStreak = if (isConsecutive) currentStreakState.currentStreak + 1 else 1
                val newBest = if (newStreak > currentStreakState.bestStreak) newStreak else currentStreakState.bestStreak

                streakDao.insertStreak(
                    currentStreakState.copy(
                        currentStreak = newStreak,
                        bestStreak = newBest,
                        lastActiveDate = todayStr
                    )
                )
            }
        }
    }

    private fun getYesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    // --- AI Smart Rescheduling ---
    suspend fun rescheduleTasksForSickDay() = withContext(Dispatchers.IO) {
        val tasksList = taskDao.getAllTasks().firstOrNull() ?: emptyList()
        val todayStart = getStartOfDayTimestamp(0)
        
        // Find tasks that are incomplete and either due today or overdue
        val unfinishedTasks = tasksList.filter { !it.isCompleted && it.dueDate <= todayStart + 86400000L }
        if (unfinishedTasks.isEmpty()) return@withContext

        // We want to shift these tasks to the future 5 days, spreading them onto light-load days.
        // Step 1: Calculate daily loads for next 5 days (excluding today, which is the sick day)
        val dayLoads = mutableMapOf<Int, Int>() // offset (1 to 5) -> current workload sum
        for (offset in 1..5) {
            val dayStart = getStartOfDayTimestamp(offset)
            val dayEnd = dayStart + 86400000L
            val tasksOnDay = tasksList.filter { it.dueDate in dayStart until dayEnd }
            dayLoads[offset] = tasksOnDay.sumOf { it.workloadScore }
        }

        // Step 2: Distribute unfinished tasks
        for (task in unfinishedTasks) {
            // Find the day with the minimum workload
            val lightestDayOffset = dayLoads.minByOrNull { it.value }?.key ?: 1
            
            val newDueDate = getStartOfDayTimestamp(lightestDayOffset) + 12 * 3600000L // Due at noon on that day
            val updatedTask = task.copy(
                dueDate = newDueDate,
                rescheduledCount = task.rescheduledCount + 1
            )
            
            taskDao.updateTask(updatedTask)
            
            // Update local tracking of workloads
            dayLoads[lightestDayOffset] = (dayLoads[lightestDayOffset] ?: 0) + task.workloadScore
        }
    }

    suspend fun autoBalanceAllTasks() = withContext(Dispatchers.IO) {
        val tasksList = taskDao.getAllTasks().firstOrNull() ?: emptyList()
        val incompleteTasks = tasksList.filter { !it.isCompleted }
        if (incompleteTasks.isEmpty()) return@withContext

        // Distribute all incomplete tasks across the next 7 days (offsets 0 to 6) to balance daily workload
        val dayLoads = mutableMapOf<Int, Int>() // offset -> workload score sum
        val dayTaskCounts = mutableMapOf<Int, Int>() // offset -> count of tasks assigned
        for (offset in 0..6) {
            dayLoads[offset] = 0
            dayTaskCounts[offset] = 0
        }

        // Greedy LPT (Longest Processing Time) Scheduling: sort descending by workloadScore to distribute heavy tasks first
        val sortedTasks = incompleteTasks.sortedByDescending { it.workloadScore }

        for (task in sortedTasks) {
            // Find the day with the minimum workload
            val lightestDayOffset = dayLoads.minByOrNull { it.value }?.key ?: 0
            val taskIndexOnDay = dayTaskCounts[lightestDayOffset] ?: 0

            // Sequential start hours: 9 AM, 11:30 AM, 2 PM, 4:30 PM, 7 PM
            val startHour = when (taskIndexOnDay % 5) {
                0 -> 9.0f
                1 -> 11.5f
                2 -> 14.0f
                3 -> 16.5f
                else -> 19.0f
            }
            val hourMillis = (startHour * 3600000L).toLong()
            val newDueDate = getStartOfDayTimestamp(lightestDayOffset) + hourMillis

            val updatedTask = task.copy(dueDate = newDueDate)
            taskDao.updateTask(updatedTask)

            dayLoads[lightestDayOffset] = (dayLoads[lightestDayOffset] ?: 0) + task.workloadScore
            dayTaskCounts[lightestDayOffset] = taskIndexOnDay + 1
        }
    }

    private fun getStartOfDayTimestamp(dayOffset: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, dayOffset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    suspend fun performMidnightEodRollover() = withContext(Dispatchers.IO) {
        val tasksList = taskDao.getAllTasks().firstOrNull() ?: emptyList()
        val todayStart = getStartOfDayTimestamp(0)
        val todayEnd = todayStart + 86400000L - 1 // End of today (23:59:59)

        // Find all incomplete tasks due today or overdue
        val uncompletedTasks = tasksList.filter { !it.isCompleted && it.dueDate <= todayEnd }
        if (uncompletedTasks.isEmpty()) return@withContext

        // Tomorrow start timestamp (dayOffset = 1)
        val tomorrowStart = getStartOfDayTimestamp(1)
        
        // Evaluate available morning/afternoon slots for tomorrow
        val availableHours = listOf(8, 10, 12, 14, 15, 17)

        uncompletedTasks.forEachIndexed { index, task ->
            val hour = availableHours[index % availableHours.size]
            val newDueDate = tomorrowStart + (hour * 3600000L)
            val updatedTask = task.copy(
                dueDate = newDueDate,
                rescheduledCount = task.rescheduledCount + 1
            )
            taskDao.updateTask(updatedTask)
        }
    }

    // --- AI Coach Tab ---
    suspend fun sendMessageToCoach(userText: String, studentName: String): String = withContext(Dispatchers.IO) {
        // Save user message
        val userMessage = ChatMessage(content = userText, sender = "USER", studentName = studentName)
        chatDao.insertMessage(userMessage)

        val historyList = chatDao.getAllMessages().firstOrNull() ?: emptyList()
        val userFilteredHistory = historyList.filter { it.studentName == studentName }
        val promptBuilder = StringBuilder()
        promptBuilder.append("You are 'Studyly', an enthusiastic, extremely cute, friendly, and smart robot AI study companion tailored for Matric (Grade 9 and 10) students in South Africa. ")
        promptBuilder.append("Your design vibe is playful productivity like Notion and Duolingo. Keep your answers encouraging, practical, and split into clear, easy-to-digest bullet points. ")
        promptBuilder.append("Use cheerful emojis (e.g. 🌟, ✨, 🧠, 📚, 🚀) and occasional South African student slang (like 'Howzit', 'Lekker', 'sharp sharp', ' Matric exams').\n\n")
        promptBuilder.append("Conversation History:\n")
        
        // Limit context to last 10 messages to avoid token issues
        val contextHistory = userFilteredHistory.takeLast(10)
        for (msg in contextHistory) {
            promptBuilder.append("${msg.sender}: ${msg.content}\n")
        }
        promptBuilder.append("USER: $userText\nCOACH (Studyly):")

        val coachReplyText = try {
            GeminiClient.generateText(
                prompt = promptBuilder.toString(),
                systemInstruction = "You are Studyly, the Matric study coach robot."
            )
        } catch (e: Exception) {
            "Oh no! My neural gears got slightly stuck. 🥺 Let's try that again! Double check if you have connected your Gemini API Key in the AI Studio Secrets panel. 🔑 Spark on!"
        }

        // Save coach message
        val coachMessage = ChatMessage(content = coachReplyText, sender = "COACH", studentName = studentName)
        chatDao.insertMessage(coachMessage)
        
        coachReplyText
    }

    suspend fun clearChat() = withContext(Dispatchers.IO) {
        chatDao.clearChat()
    }

    // --- Daily motivational quotes ---
    val localQuotes = listOf(
        "\"The only way to do great work is to love what you do.\" — Steve Jobs 💡",
        "\"It always seems impossible until it's done.\" — Nelson Mandela 🇿🇦💪",
        "\"Success is not final, failure is not fatal: it is the courage to continue that counts.\" — Winston Churchill 🎯",
        "\"Believe you can and you're halfway there.\" — Theodore Roosevelt ✨",
        "\"The future belongs to those who believe in the beauty of their dreams.\" — Eleanor Roosevelt 🌟",
        "\"Do not watch the clock; do what it does. Keep going.\" — Sam Levenson ⏱️",
        "\"You miss 100% of the shots you don't take.\" — Wayne Gretzky 🏒🏆",
        "\"Start where you are. Use what you have. Do what you can.\" — Arthur Ashe 📚",
        "\"The secret of getting ahead is getting started.\" — Mark Twain 📝",
        "\"Our greatest weakness lies in giving up. The most certain way to succeed is always to try just one more time.\" — Thomas A. Edison 💡⚡",
        "\"Education is the most powerful weapon which you can use to change the world.\" — Nelson Mandela 🌍🎓",
        "\"The beautiful thing about learning is that no one can take it away from you.\" — B.B. King 🧠",
        "\"I find that the harder I work, the more luck I seem to have.\" — Thomas Jefferson 🍀✏️",
        "\"Don't let what you cannot do interfere with what you can do.\" — John Wooden 🏀✨",
        "\"The only limit to our realization of tomorrow will be our doubts of today.\" — Franklin D. Roosevelt 🌅",
        "\"A person who never made a mistake never tried anything new.\" — Albert Einstein 🧪",
        "\"Everything you've ever wanted is on the other side of fear.\" — George Addair 💪🚀",
        "\"There are no shortcuts to any place worth going.\" — Beverly Sills 🛣️",
        "\"Success is the sum of small efforts, repeated day in and day out.\" — Robert Collier ⏳",
        "\"It is not that I'm so smart, but I stay with questions much longer.\" — Albert Einstein 💭",
        "\"Failure is the opportunity to begin again more intelligently.\" — Henry Ford 🔄",
        "\"You don't have to be great to start, but you have to start to be great.\" — Zig Ziglar 🚀",
        "\"The mind is not a vessel to be filled, but a fire to be kindled.\" — Plutarch 🔥",
        "\"Your talent determines what you can do. Your motivation determines how much you are willing to do. Your attitude determines how well you do it.\" — Lou Holtz 📈",
        "\"Don't wish it were easier. Wish you were better.\" — Jim Rohn 🌟",
        "\"The capacity to learn is a gift; the ability to learn is a skill; the willingness to learn is a choice.\" — Brian Herbert 🎁",
        "\"Ninety-nine percent of the failures come from people who have the habit of making excuses.\" — George Washington Carver 🛑",
        "\"There is no substitute for hard work.\" — Thomas A. Edison 🛠️",
        "\"Study hard what interests you the most in the most undisciplined, irreverent and original manner possible.\" — Richard Feynman 🔬🎨",
        "\"No need to rush. No need to sparkle. No need to be anybody but oneself.\" — Virginia Woolf 🌸",
        "\"Live as if you were to die tomorrow. Learn as if you were to live forever.\" — Mahatma Gandhi 📖✨",
        "\"Only those who dare to fail greatly can ever achieve greatly.\" — Robert F. Kennedy ☄️",
        "\"Genius is 1% inspiration, 99% perspiration.\" — Thomas A. Edison 🧠💦",
        "\"You are capable of more than you know. Keep studying, one page at a time!\" 📖✨",
        "\"Small progress every day adds up to big results. You are on the right track!\" 🚀🔥",
        "\"Your exam scores do not define your worth. Just do your best and let your light shine!\" 🌟💛",
        "\"Focus on the progress, not the perfection. You've got this!\" 💪📈"
    )

    suspend fun getMotivationalQuote(): String = withContext(Dispatchers.IO) {
        val prompt = "Generate a single, breathtakingly motivating and cute 1-sentence quote specifically for a South African Matric Grade 9 or 10 student studying hard for exams. Include 1-2 positive emojis. Keep it under 20 words."
        try {
            GeminiClient.generateText(prompt)
        } catch (e: Exception) {
            localQuotes.random()
        }
    }

    // --- AI Smart Task Entry & Scanner ---
    suspend fun scanHomeworkText(rawText: String): List<Task> = withContext(Dispatchers.IO) {
        val systemInstruction = "You are a Matric Grade 9 & 10 study parser. Analyze the homework raw text and extract structured tasks."
        val prompt = """
            Analyze this raw homework/syllabus text:
            "$rawText"
            
            Extract key tasks suitable for a Matric student study schedule.
            Return a JSON array containing objects matching this EXACT schema:
            [
              {
                "title": "Short descriptive title of the study task",
                "subject": "The school subject (e.g., Mathematics, Physical Sciences, Life Sciences, English, History, Accounting)",
                "chapter": "Specific chapter or topic (e.g., Calculus, Organic Chemistry, Genetics, Balance Sheets)",
                "taskType": "STUDY" or "TEST" or "ASSIGNMENT" or "REVISION",
                "estimatedMinutes": integer estimated duration (e.g. 30, 45, 60, 90),
                "workloadScore": integer from 1 to 5 (indicating how mentally heavy/complex the task is)
              }
            ]
            Provide ONLY valid JSON output. No markdown block, no extra characters.
        """.trimIndent()

        try {
            val jsonResponse = GeminiClient.generateText(prompt, systemInstruction, jsonOutput = true)
            // Clean up backticks if any
            val cleanJson = jsonResponse.trim().removeSurrounding("```json", "```").trim()
            
            val type = Types.newParameterizedType(List::class.java, TaskJson::class.java)
            val adapter = moshi.adapter<List<TaskJson>>(type)
            val parsedList = adapter.fromJson(cleanJson) ?: emptyList()
            
            // Map parsed objects to Database Task models with upcoming due dates
            val upcomingTasks = parsedList.mapIndexed { idx, parsed ->
                Task(
                    title = parsed.title,
                    subject = parsed.subject,
                    chapter = parsed.chapter,
                    taskType = parsed.taskType,
                    dueDate = getStartOfDayTimestamp(0) + 12 * 3600000L, // Due today at 12:00 PM for Today's Schedule
                    estimatedMinutes = parsed.estimatedMinutes,
                    workloadScore = parsed.workloadScore
                )
            }
            
            taskDao.insertTasks(upcomingTasks)
            upcomingTasks
        } catch (e: Exception) {
            // Smart local fallback: parse user's typed lines directly!
            val lines = rawText.split("\n")
                .map { line ->
                    line.trim()
                        .replace(Regex("^[\\s\\-*•#\\d\\.)+\\[\\]]+"), "") // remove bullets, numbers, dashes
                        .trim()
                }
                .filter { it.isNotBlank() && it.length >= 3 }
            
            val fallbackTasks = if (lines.isNotEmpty()) {
                lines.mapIndexed { idx, line ->
                    // Guess subject
                    val lower = line.lowercase()
                    val subject = when {
                        lower.contains("math") || lower.contains("calc") || lower.contains("algebra") || lower.contains("geom") -> "Mathematics"
                        lower.contains("phys") || lower.contains("chem") || lower.contains("science") || lower.contains("force") || lower.contains("acid") -> "Physical Sciences"
                        lower.contains("bio") || lower.contains("life") || lower.contains("genetics") || lower.contains("cell") -> "Life Sciences"
                        lower.contains("eng") || lower.contains("read") || lower.contains("poem") || lower.contains("play") -> "English"
                        lower.contains("hist") || lower.contains("war") || lower.contains("apartheid") -> "History"
                        lower.contains("acc") || lower.contains("balance") || lower.contains("ledger") || lower.contains("tax") -> "Accounting"
                        else -> "General Study"
                    }
                    val chapter = when (subject) {
                        "Mathematics" -> "Calculus & Algebra"
                        "Physical Sciences" -> "Matter & Energy"
                        "Life Sciences" -> "Environmental & Genetics"
                        "English" -> "Language & Literature"
                        "History" -> "South African & Global History"
                        "Accounting" -> "Financial Statements"
                        else -> "Revision Topic"
                    }
                    val type = when {
                        lower.contains("test") || lower.contains("exam") || lower.contains("quiz") -> "TEST"
                        lower.contains("assign") || lower.contains("project") || lower.contains("homework") || lower.contains("hw") -> "ASSIGNMENT"
                        lower.contains("revise") || lower.contains("review") -> "REVISION"
                        else -> "STUDY"
                    }
                    Task(
                        title = line,
                        subject = subject,
                        chapter = chapter,
                        taskType = type,
                        dueDate = getStartOfDayTimestamp(0) + (8 + (idx % 10)) * 3600000L, // Due Today at daytime hours
                        estimatedMinutes = if (type == "TEST") 60 else 45,
                        workloadScore = if (type == "TEST") 4 else 3
                    )
                }
            } else {
                listOf(
                    Task(
                        title = "Review scanned homework concepts",
                        subject = "Mathematics",
                        chapter = "Calculus Integration",
                        taskType = "STUDY",
                        dueDate = getStartOfDayTimestamp(0) + 10 * 3600000L,
                        estimatedMinutes = 45,
                        workloadScore = 3
                    ),
                    Task(
                        title = "Solve homework questions",
                        subject = "Physical Sciences",
                        chapter = "Organic Molecules",
                        taskType = "ASSIGNMENT",
                        dueDate = getStartOfDayTimestamp(0) + 14 * 3600000L,
                        estimatedMinutes = 60,
                        workloadScore = 4
                    )
                )
            }
            taskDao.insertTasks(fallbackTasks)
            fallbackTasks
        }
    }

    // --- AI Test Paper Mistake Analyzer ---
    suspend fun analyzeTestPaperMistakes(testText: String, studentName: String): List<WeakTopic> = withContext(Dispatchers.IO) {
        val systemInstruction = "You are an expert South African Matric tutor specializing in detecting learning gaps from student mistake descriptions."
        val prompt = """
            Analyze the following student test feedback / mistakes text:
            "$testText"
            
            Identify the core learning gaps (weak topics) and recommend structured corrections.
            Return a JSON array of weak topics matching this EXACT schema:
            [
              {
                "subject": "The school subject",
                "topicName": "The specific weak topic/chapter name",
                "confidenceLevel": integer from 1 to 5 (1 is extremely weak, 5 is moderately strong),
                "mistakeDescription": "Detailed encouraging description of the gap found and how to fix it"
              }
            ]
            Provide ONLY valid JSON output. No markdown, no extra characters.
        """.trimIndent()

        try {
            val jsonResponse = GeminiClient.generateText(prompt, systemInstruction, jsonOutput = true)
            val cleanJson = jsonResponse.trim().removeSurrounding("```json", "```").trim()
            
            val type = Types.newParameterizedType(List::class.java, WeakTopicJson::class.java)
            val adapter = moshi.adapter<List<WeakTopicJson>>(type)
            val parsedList = adapter.fromJson(cleanJson) ?: emptyList()

            val discoveredTopics = parsedList.map { parsed ->
                WeakTopic(
                    subject = parsed.subject,
                    topicName = parsed.topicName,
                    confidenceLevel = parsed.confidenceLevel,
                    mistakeDescription = parsed.mistakeDescription,
                    scheduledRevisionDate = getStartOfDayTimestamp(3) + 10 * 3600000L, // Auto-scheduled for 3 days from now morning
                    studentName = studentName
                )
            }

            // Also auto-schedule a REVISION task in the tasks list for the weakest ones!
            for (topic in discoveredTopics) {
                weakTopicDao.insertWeakTopic(topic)
                
                // Add task
                taskDao.insertTask(
                    Task(
                        title = "Revision: ${topic.topicName}",
                        subject = topic.subject,
                        chapter = topic.topicName,
                        taskType = "REVISION",
                        dueDate = topic.scheduledRevisionDate ?: (getStartOfDayTimestamp(3) + 10 * 3600000L),
                        estimatedMinutes = 45,
                        workloadScore = 4,
                        studentName = studentName
                    )
                )
            }

            discoveredTopics
        } catch (e: Exception) {
            val fallbackTopics = listOf(
                WeakTopic(
                    subject = "Mathematics",
                    topicName = "Trigonometric Equations",
                    confidenceLevel = 2,
                    mistakeDescription = "Struggling with general solutions. Let's practice quadrant steps and reference angles.",
                    scheduledRevisionDate = getStartOfDayTimestamp(3) + 15 * 3600000L,
                    studentName = studentName
                )
            )
            for (topic in fallbackTopics) {
                weakTopicDao.insertWeakTopic(topic)
                taskDao.insertTask(
                    Task(
                        title = "Revision: ${topic.topicName}",
                        subject = topic.subject,
                        chapter = topic.topicName,
                        taskType = "REVISION",
                        dueDate = topic.scheduledRevisionDate!!,
                        estimatedMinutes = 45,
                        workloadScore = 4,
                        studentName = studentName
                    )
                )
            }
            fallbackTopics
        }
    }

    // --- Database Prepopulation ---
    suspend fun prefillDatabaseWithMatricData() = withContext(Dispatchers.IO) {
        checkStreakValidity()
        val existingMessages = chatDao.getAllMessages().firstOrNull() ?: emptyList()
        if (existingMessages.isEmpty()) {
            chatDao.insertMessage(
                ChatMessage(
                    content = "Howzit Matric! 🌟 I'm Studyly, your cute AI study coach robot. Ask me any conceptual question, request a practice quiz, or let me design a lekker study timetable! Let's crush this final year! 🚀",
                    sender = "COACH"
                )
            )
        }
    }

    // --- To-Do operations ---
    suspend fun insertTodoItem(todoItem: TodoItem) = withContext(Dispatchers.IO) {
        todoDao.insertTodoItem(todoItem)
    }

    suspend fun updateTodoItem(todoItem: TodoItem) = withContext(Dispatchers.IO) {
        todoDao.updateTodoItem(todoItem)
    }

    suspend fun deleteTodoItemById(id: Long) = withContext(Dispatchers.IO) {
        todoDao.deleteTodoItemById(id)
    }

    suspend fun scanTodoListText(rawText: String): List<TodoItem> = withContext(Dispatchers.IO) {
        val systemInstruction = "You are a study helper parser. Extract simple to-do list items from raw text."
        val prompt = """
            Analyze this raw to-do list text or schedule:
            "$rawText"
            
            Extract a list of distinct, simple study tasks or to-do list actions that a Matric student needs to do.
            Keep the task titles short and actionable (e.g. "Do math assignment", "Read act 3 of english play", "Clean study desk").
            Return a JSON array containing objects matching this EXACT schema:
            [
              {
                "title": "Short descriptive title of the task"
              }
            ]
            Provide ONLY valid JSON output. No markdown block, no extra characters.
        """.trimIndent()

        try {
            val jsonResponse = GeminiClient.generateText(prompt, systemInstruction, jsonOutput = true)
            val cleanJson = jsonResponse.trim().removeSurrounding("```json", "```").trim()
            
            val type = Types.newParameterizedType(List::class.java, TodoJson::class.java)
            val adapter = moshi.adapter<List<TodoJson>>(type)
            val parsedList = adapter.fromJson(cleanJson) ?: emptyList()
            
            val todoItems = parsedList.map { parsed ->
                TodoItem(title = parsed.title)
            }
            todoDao.insertTodoItems(todoItems)
            todoItems
        } catch (e: Exception) {
            // Smart local fallback: split and parse the user's typed lines directly!
            val lines = rawText.split(Regex("[\n;,]"))
                .map { line ->
                    line.trim()
                        .replace(Regex("^[\\s\\-*•#\\d\\.)+\\[\\]]+"), "") // remove bullets, numbers, dashes, checkboxes
                        .trim()
                }
                .filter { it.isNotBlank() && it.length >= 3 }
            
            val todoItems = if (lines.isNotEmpty()) {
                lines.map { TodoItem(title = it) }
            } else if (rawText.isNotBlank()) {
                listOf(TodoItem(title = rawText.trim()))
            } else {
                listOf(
                    TodoItem(title = "Study session: Practice 3 key concepts"),
                    TodoItem(title = "Review recent homework questions")
                )
            }
            todoDao.insertTodoItems(todoItems)
            todoItems
        }
    }

    suspend fun generateAIStudyPlan(rawText: String, mood: String, topStudyHours: String): List<ProposedTaskJson> = withContext(Dispatchers.IO) {
        val systemInstruction = "You are a Matric Grade 9 & 10 expert study planner. Intelligently analyze the user's study topics, handwritten lists, or test guidelines, and generate a divided hourly, weekly, or monthly study schedule."
        val prompt = """
            Analyze this study guideline, scanned list, or topic objectives:
            "$rawText"
            
            Produce an intelligent multi-factor hourly and calendar breakdown of study tasks.
            - If there is a test or exam mentioned (even if it's 1 month later!), you MUST divide the syllabus progressively from today (day 0) up to that test date, placing a preparation review every few days, and placing the final "TEST" task on the exam day.
            - Group or map tasks into these subjects: Mathematics, Physical Sciences, Life Sciences, English, History, Accounting, or General Study.
            - Assign each task to a specific day offset (`daysFromNow`: 0 for today, 1 for tomorrow, up to 30 days) and hour of the day (`hourOfDay`: 8 to 20).
            - Distribute tasks evenly to prevent overload.
            
            MULTI-FACTOR PERSONALIZATION & CONSTRAINT INPUTS:
            - Student's Current Mood: $mood
            - Student's Peak Focus Hours: $topStudyHours
            - Hard Constraints: Sleep Hours (23:00 to 06:00) and Prayer Times (13:00 Dhuhr, 16:00 Asr, 19:00 Maghrib) are hard locked. DO NOT schedule tasks during these hours.
            
            MOOD & BEHAVIOR RULES:
            - Energetic: Prioritize high-brain-power analytical subjects (Mathematics, Physical Sciences) and heavy test preparations during peak focus hours.
            - Focused: Prioritize problem sets, practice exercises, and Active Recall / Spaced Repetition slots.
            - Calm: Schedule large, deep-focus flow tasks and structured reading.
            - Tired: Schedule lighter low-cognitive tasks (English, History) and include a 'Mandatory Rest & Brain Reset Break' task.
            
            Return a JSON array containing objects matching this EXACT schema:
            [
              {
                "title": "Actionable task name (e.g., 'Revise Calculus Limits part 1', 'Practice Calculus exercises')",
                "subject": "Mathematics",
                "chapter": "Calculus",
                "taskType": "STUDY",
                "daysFromNow": 0,
                "hourOfDay": 9,
                "estimatedMinutes": 45,
                "workloadScore": 3
              }
            ]
            Provide ONLY valid JSON output. No markdown block, no extra characters.
        """.trimIndent()

        // Helper to adjust hour away from hard blocks (Sleep hours & Prayer times)
        fun sanitizeHour(hour: Int): Int {
            var h = hour
            val locked = listOf(23, 0, 1, 2, 3, 4, 5, 6, 13, 16, 19)
            while (h in locked) {
                h = (h + 1) % 24
                if (h < 7) h = 7
            }
            return h
        }

        try {
            val jsonResponse = GeminiClient.generateText(prompt, systemInstruction, jsonOutput = true)
            val cleanJson = jsonResponse.trim().removeSurrounding("```json", "```").trim()
            
            val type = Types.newParameterizedType(List::class.java, ProposedTaskJson::class.java)
            val adapter = moshi.adapter<List<ProposedTaskJson>>(type)
            val parsedList = adapter.fromJson(cleanJson) ?: emptyList()
            
            // Post-process adjustments for mood, peak focus, and hard blocks
            parsedList.map { task ->
                val safeHour = sanitizeHour(task.hourOfDay)
                if (mood.equals("Tired", ignoreCase = true)) {
                    val isHeavy = task.subject.uppercase() in listOf("MATHEMATICS", "MATH", "PHYSICAL SCIENCES", "PHYSICS")
                    val isLight = task.subject.uppercase() in listOf("ENGLISH", "HISTORY", "ACCOUNTING", "LANGUAGES", "GENERAL STUDY")
                    if (isHeavy && safeHour >= 15) {
                        task.copy(hourOfDay = sanitizeHour(9))
                    } else if (isLight && safeHour < 12) {
                        task.copy(hourOfDay = sanitizeHour(17))
                    } else {
                        task.copy(hourOfDay = safeHour)
                    }
                } else {
                    task.copy(hourOfDay = safeHour)
                }
            }
        } catch (e: Exception) {
            // Local Fallback Parser
            val lines = rawText.split("\n")
                .map { it.trim().replace(Regex("^[\\s\\-*•#\\d\\.)+\\[\\]]+"), "").trim() }
                .filter { it.isNotBlank() && it.length >= 3 }
            
            val proposed = mutableListOf<ProposedTaskJson>()
            
            var isTestMonthLater = false
            var testSubject = "General Study"
            var testChapter = "Revision Topic"
            
            for (line in lines) {
                val lower = line.lowercase()
                if (lower.contains("test") || lower.contains("exam") || lower.contains("paper") || lower.contains("month") || lower.contains("30 days")) {
                    isTestMonthLater = true
                    testSubject = when {
                        lower.contains("math") || lower.contains("calc") -> "Mathematics"
                        lower.contains("phys") || lower.contains("chem") || lower.contains("science") -> "Physical Sciences"
                        lower.contains("bio") || lower.contains("life") -> "Life Sciences"
                        lower.contains("eng") -> "English"
                        lower.contains("hist") -> "History"
                        lower.contains("acc") -> "Accounting"
                        else -> "General Study"
                    }
                    testChapter = when (testSubject) {
                        "Mathematics" -> "Calculus & Algebra"
                        "Physical Sciences" -> "Matter & Energy"
                        "Life Sciences" -> "Environmental & Genetics"
                        "English" -> "Language & Literature"
                        "History" -> "South African & Global History"
                        "Accounting" -> "Financial Statements"
                        else -> "Revision Topic"
                    }
                }
            }
            
            if (isTestMonthLater) {
                // Generate a 1-month progressive preparation syllabus plan!
                val topics = listOf(
                    "Chapter 1: Foundations & Core Concepts Review",
                    "Chapter 2: Critical Definitions & Formulas",
                    "Practice Exercise Section A (Easy/Medium)",
                    "Mid-term Review & Conceptual Weaknesses Check",
                    "Practice Exercise Section B (Hard Exam Questions)",
                    "Solve Past matric exam paper questions",
                    "Final Formula Sheet & Active Recall Checklist",
                    "Actual $testSubject Exam/Test"
                )
                
                val intervals = listOf(1, 3, 7, 10, 14, 20, 26, 30) // spread across 30 days
                topics.forEachIndexed { index, topic ->
                    val day = intervals.getOrElse(index) { index * 4 }
                    proposed.add(
                        ProposedTaskJson(
                            title = topic,
                            subject = testSubject,
                            chapter = testChapter,
                            taskType = if (index == topics.lastIndex) "TEST" else "STUDY",
                            daysFromNow = day,
                            hourOfDay = 9 + (index % 3) * 2, // 9, 11, 13
                            estimatedMinutes = if (index == topics.lastIndex) 120 else 60,
                            workloadScore = if (index == topics.lastIndex) 5 else 3
                        )
                    )
                }
            } else {
                // Schedule generic tasks spread over the next week
                lines.forEachIndexed { index, line ->
                    val lower = line.lowercase()
                    val subject = when {
                        lower.contains("math") || lower.contains("calc") -> "Mathematics"
                        lower.contains("phys") || lower.contains("chem") -> "Physical Sciences"
                        lower.contains("bio") || lower.contains("life") -> "Life Sciences"
                        lower.contains("eng") -> "English"
                        lower.contains("hist") -> "History"
                        lower.contains("acc") -> "Accounting"
                        else -> "General Study"
                    }
                    val type = when {
                        lower.contains("test") || lower.contains("exam") -> "TEST"
                        lower.contains("assign") || lower.contains("hw") || lower.contains("homework") -> "ASSIGNMENT"
                        lower.contains("revise") -> "REVISION"
                        else -> "STUDY"
                    }
                    proposed.add(
                        ProposedTaskJson(
                            title = line,
                            subject = subject,
                            chapter = "Topics & Practice",
                            taskType = type,
                            daysFromNow = index / 2, // 2 tasks per day
                            hourOfDay = 10 + (index % 2) * 3, // 10:00 AM, 01:00 PM
                            estimatedMinutes = 45,
                            workloadScore = 3
                        )
                    )
                }
            }
            
            // Adjust local offline fallback for mood & study hours
            proposed.map { task ->
                if (mood.equals("Tired", ignoreCase = true)) {
                    val isHeavy = task.subject.uppercase() in listOf("MATHEMATICS", "MATH", "PHYSICAL SCIENCES", "PHYSICS")
                    val isLight = task.subject.uppercase() in listOf("ENGLISH", "HISTORY", "ACCOUNTING", "LANGUAGES", "GENERAL STUDY")
                    if (isHeavy && task.hourOfDay >= 15) {
                        task.copy(hourOfDay = 9) // morning
                    } else if (isLight && task.hourOfDay < 12) {
                        task.copy(hourOfDay = 17) // evening
                    } else {
                        task
                    }
                } else {
                    task
                }
            }
        }
    }
}

// Helper JSON data classes for Moshi conversions
@JsonClass(generateAdapter = true)
data class ProposedTaskJson(
    val title: String,
    val subject: String,
    val chapter: String,
    val taskType: String,
    val daysFromNow: Int,
    val hourOfDay: Int,
    val estimatedMinutes: Int,
    val workloadScore: Int
)

@JsonClass(generateAdapter = true)
data class TaskJson(
    val title: String,
    val subject: String,
    val chapter: String,
    val taskType: String,
    val estimatedMinutes: Int,
    val workloadScore: Int
)

@JsonClass(generateAdapter = true)
data class WeakTopicJson(
    val subject: String,
    val topicName: String,
    val confidenceLevel: Int,
    val mistakeDescription: String
)

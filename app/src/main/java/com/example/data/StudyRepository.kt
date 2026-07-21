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

    // --- AI Coach Tab ---
    suspend fun sendMessageToCoach(userText: String): String = withContext(Dispatchers.IO) {
        // Save user message
        val userMessage = ChatMessage(content = userText, sender = "USER")
        chatDao.insertMessage(userMessage)

        val historyList = chatDao.getAllMessages().firstOrNull() ?: emptyList()
        val promptBuilder = StringBuilder()
        promptBuilder.append("You are 'Studyly', an enthusiastic, extremely cute, friendly, and smart robot AI study companion tailored for Matric (Grade 9 and 10) students in South Africa. ")
        promptBuilder.append("Your design vibe is playful productivity like Notion and Duolingo. Keep your answers encouraging, practical, and split into clear, easy-to-digest bullet points. ")
        promptBuilder.append("Use cheerful emojis (e.g. 🌟, ✨, 🧠, 📚, 🚀) and occasional South African student slang (like 'Howzit', 'Lekker', 'sharp sharp', ' Matric exams').\n\n")
        promptBuilder.append("Conversation History:\n")
        
        // Limit context to last 10 messages to avoid token issues
        val contextHistory = historyList.takeLast(10)
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
        val coachMessage = ChatMessage(content = coachReplyText, sender = "COACH")
        chatDao.insertMessage(coachMessage)
        
        coachReplyText
    }

    suspend fun clearChat() = withContext(Dispatchers.IO) {
        chatDao.clearChat()
    }

    // --- Daily motivational quotes ---
    suspend fun getMotivationalQuote(): String = withContext(Dispatchers.IO) {
        val prompt = "Generate a single, breathtakingly motivating and cute 1-sentence quote specifically for a South African Matric Grade 9 or 10 student studying hard for exams. Include 1-2 positive emojis. Keep it under 20 words."
        try {
            GeminiClient.generateText(prompt)
        } catch (e: Exception) {
            "You are doing amazing! One page, one subject at a time. You've got this Matric in the bag! 🌟✨"
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
                    dueDate = getStartOfDayTimestamp(idx + 1) + 16 * 3600000L, // Due afternoon of successive future days
                    estimatedMinutes = parsed.estimatedMinutes,
                    workloadScore = parsed.workloadScore
                )
            }
            
            taskDao.insertTasks(upcomingTasks)
            upcomingTasks
        } catch (e: Exception) {
            // Fallback: parse a few simple items manually or return defaults
            val fallbackTasks = listOf(
                Task(
                    title = "Review scanned homework concepts",
                    subject = "Mathematics",
                    chapter = "Calculus Integration",
                    taskType = "STUDY",
                    dueDate = getStartOfDayTimestamp(1) + 14 * 3600000L,
                    estimatedMinutes = 45,
                    workloadScore = 3
                ),
                Task(
                    title = "Solve homework questions",
                    subject = "Physical Sciences",
                    chapter = "Organic Molecules",
                    taskType = "ASSIGNMENT",
                    dueDate = getStartOfDayTimestamp(2) + 16 * 3600000L,
                    estimatedMinutes = 60,
                    workloadScore = 4
                )
            )
            taskDao.insertTasks(fallbackTasks)
            fallbackTasks
        }
    }

    // --- AI Test Paper Mistake Analyzer ---
    suspend fun analyzeTestPaperMistakes(testText: String): List<WeakTopic> = withContext(Dispatchers.IO) {
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
                    scheduledRevisionDate = getStartOfDayTimestamp(3) + 10 * 3600000L // Auto-scheduled for 3 days from now morning
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
                        workloadScore = 4
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
                    scheduledRevisionDate = getStartOfDayTimestamp(3) + 15 * 3600000L
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
                        workloadScore = 4
                    )
                )
            }
            fallbackTopics
        }
    }

    // --- Database Prepopulation ---
    suspend fun prefillDatabaseWithMatricData() = withContext(Dispatchers.IO) {
        val existingTasks = taskDao.getAllTasks().firstOrNull() ?: emptyList()
        if (existingTasks.isNotEmpty()) return@withContext

        // Prepopulate tasks
        val today = getStartOfDayTimestamp(0)
        val prepTasks = listOf(
            Task(
                title = "Study Calculus Limits & Continuity",
                subject = "Mathematics",
                chapter = "Differential Calculus",
                taskType = "STUDY",
                dueDate = today + 14 * 3600000L, // Today afternoon
                estimatedMinutes = 45,
                workloadScore = 3
            ),
            Task(
                title = "Solve Mechanics Exam Paper",
                subject = "Physical Sciences",
                chapter = "Vertical Projectile Motion",
                taskType = "ASSIGNMENT",
                dueDate = today + 16 * 3600000L, // Today evening
                estimatedMinutes = 60,
                workloadScore = 4
            ),
            Task(
                title = "Life Sciences Weekly Class Test",
                subject = "Life Sciences",
                chapter = "DNA Replication",
                taskType = "TEST",
                dueDate = getStartOfDayTimestamp(1) + 9 * 3600000L, // Tomorrow morning
                estimatedMinutes = 45,
                workloadScore = 5
            ),
            Task(
                title = "Study Ledger Accounts & Cash Flow",
                subject = "Accounting",
                chapter = "Financial Statements",
                taskType = "STUDY",
                dueDate = getStartOfDayTimestamp(2) + 15 * 3600000L, // 2 days from now
                estimatedMinutes = 90,
                workloadScore = 4
            ),
            Task(
                title = "Read Hamlet Act 3 Analysis",
                subject = "English Home Language",
                chapter = "Shakespearean Drama",
                taskType = "STUDY",
                dueDate = getStartOfDayTimestamp(4) + 11 * 3600000L, // 4 days from now
                estimatedMinutes = 30,
                workloadScore = 2
            )
        )
        taskDao.insertTasks(prepTasks)

        // Prepopulate streak
        streakDao.insertStreak(
            StreakState(
                currentStreak = 4,
                bestStreak = 8,
                lastActiveDate = getYesterdayDateString()
            )
        )

        // Prepopulate weak topics
        weakTopicDao.insertWeakTopic(
            WeakTopic(
                subject = "Physical Sciences",
                topicName = "Organic Chemistry Nomenclature",
                confidenceLevel = 2,
                mistakeDescription = "Confusing IUPAC names of esters and aldehydes. Scheduled deep practice of suffixes.",
                scheduledRevisionDate = getStartOfDayTimestamp(2) + 10 * 3600000L
            )
        )

        // Prepopulate chat welcome message
        chatDao.insertMessage(
            ChatMessage(
                content = "Howzit Matric! 🌟 I'm Studyly, your cute AI study coach robot. Ask me any conceptual question, request a practice quiz, or let me design a lekker study timetable! Let's crush this final year! 🚀",
                sender = "COACH"
            )
        )
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
            // Fallback
            val fallbackTodos = listOf(
                TodoItem(title = "Revise physical sciences formulae"),
                TodoItem(title = "Practice 3 calculus questions")
            )
            todoDao.insertTodoItems(fallbackTodos)
            fallbackTodos
        }
    }
}

// Helper JSON data classes for Moshi conversions
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

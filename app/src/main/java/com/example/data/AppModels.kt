package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val studentName: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class TodoJson(
    val title: String
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val chapter: String,
    val taskType: String, // "STUDY", "TEST", "ASSIGNMENT", "REVISION"
    val dueDate: Long,
    val estimatedMinutes: Int,
    val isCompleted: Boolean = false,
    val rescheduledCount: Int = 0,
    val workloadScore: Int = 3, // 1 to 5 indicating complexity
    val studentName: String = ""
)

@Entity(tableName = "streak")
data class StreakState(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveDate: String = "", // "yyyy-MM-dd"
    val studentName: String = ""
)

@Entity(tableName = "weak_topics")
data class WeakTopic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val topicName: String,
    val confidenceLevel: Int, // 1 to 5 (lower means weaker)
    val mistakeDescription: String,
    val scheduledRevisionDate: Long? = null,
    val studentName: String = ""
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val sender: String, // "USER" or "COACH"
    val timestamp: Long = System.currentTimeMillis(),
    val studentName: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class FocusSession(
    val id: String,
    val taskTitle: String,
    val durationMinutes: Int,
    val xpEarned: Int,
    val timestamp: Long,
    val dateString: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class TimelineSubTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val hour: Int,
    val title: String,
    val isCompleted: Boolean = false,
    val minuteRange: String? = null
)



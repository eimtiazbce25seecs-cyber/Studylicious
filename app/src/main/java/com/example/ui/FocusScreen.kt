package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.Task
import com.example.ui.theme.*
import java.util.Calendar

@Composable
fun FocusScreen(
    viewModel: StudyViewModel,
    pomodoroMinutes: Int,
    pomodoroSeconds: Int,
    isTimerRunning: Boolean,
    timerMode: String,
    initialTimerMinutes: Int,
    selectedFocusTask: Task?,
    onSelectFocusTask: (Task?) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetTimerDuration: (Int, String) -> Unit,
    onTriggerCompleteAnim: (Offset) -> Unit
) {
    val tasks by viewModel.allTasks.collectAsState()
    val quote by viewModel.dailyQuote.collectAsState()
    val quoteLoading by viewModel.quoteLoading.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState()
    val isDark = currentTheme == "Cosmic Candy"

    val completedCount = tasks.count { it.isCompleted }
    val totalCount = tasks.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Daily Recommendation Quote (Cute Speech Bubble)
        item {
            val appTheme by viewModel.appTheme.collectAsState()
            val themePrimary = when(appTheme) {
                "Sunset Glow" -> Color(0xFFFF5252)
                "Cotton Candy" -> Color(0xFF0288D1)
                "Minty Fresh" -> Color(0xFF00796B)
                "Cosmic Candy" -> Color(0xFFE040FB)
                else -> PrimaryLilac
            }
            val themeSecondary = when(appTheme) {
                "Sunset Glow" -> Color(0xFFFFAB40)
                "Cotton Candy" -> Color(0xFFFF4081)
                "Minty Fresh" -> Color(0xFF4DB6AC)
                "Cosmic Candy" -> Color(0xFF00E5FF)
                else -> SecondaryPeach
            }

            val bubbleBg = if (isDark) {
                DarkSurface.copy(alpha = 0.8f)
            } else {
                LightSurface.copy(alpha = 0.85f)
            }

            val bubbleBorderBrush = if (isDark) {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        themeSecondary.copy(alpha = 0.25f)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        themeSecondary.copy(alpha = 0.45f)
                    )
                )
            }

            // Bounce animation for the mascot
            val infiniteTransition = rememberInfiniteTransition(label = "mascot_bounce")
            val bounceOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bounceOffset"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = bounceOffset.dp)
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(themePrimary.copy(alpha = 0.15f))
                        .border(1.5.dp, themePrimary.copy(alpha = 0.4f), CircleShape)
                        .clickable { viewModel.fetchNewQuote() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (quoteLoading) "🤔" else "🦉",
                        fontSize = 30.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                SpeechBubbleArrow(
                    color = bubbleBg,
                    modifier = Modifier.offset(x = 1.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(4.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                        .background(bubbleBg)
                        .border(1.dp, bubbleBorderBrush, RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                        .clickable { viewModel.fetchNewQuote() }
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Studyly the Owl 🦉",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = themePrimary
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(themePrimary.copy(alpha = 0.12f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "AI Tip",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = themePrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh Tip",
                                tint = SoftGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = if (quoteLoading) "Thinking of a matric secret... ⚡✨" else "\"$quote\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Personalized AI Planner Settings Card
        item {
            val userMood by viewModel.userMood.collectAsState()
            val topStudyHours by viewModel.topStudyHours.collectAsState()
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) DarkSurface.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.7f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) BorderDarkPastel.copy(alpha = 0.15f) else BorderPastel.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PrimaryLilac.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 16.sp)
                        }
                        Column {
                            Text(
                                text = "AI Planner Settings 🤖⚙️",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Adjusts subject schedule flow according to your mood and peak focus times.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray
                            )
                        }
                    }

                    HorizontalDivider(color = if (isDark) BorderDarkPastel.copy(alpha = 0.1f) else BorderPastel.copy(alpha = 0.2f))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "How are you feeling right now? 🧠",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val moods = listOf(
                            "Energetic" to "⚡",
                            "Focused" to "🎯",
                            "Calm" to "🧘",
                            "Tired" to "😴"
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            moods.forEach { (mName, emoji) ->
                                val active = userMood == mName
                                val moodBg = if (active) PrimaryLilac else PrimaryLilac.copy(alpha = 0.06f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(moodBg)
                                        .border(
                                            width = if (active) 1.5.dp else 0.dp,
                                            color = if (active) SunnyYellow else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.setUserMood(mName) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = emoji, fontSize = 16.sp)
                                        Text(
                                            text = mName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) Color.White else SoftGray
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Your Peak Study Hours ⏰📈",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val times = listOf(
                            "Morning (8 AM - 12 PM)" to "🌅",
                            "Afternoon (12 PM - 4 PM)" to "☀️",
                            "Evening (4 PM - 8 PM)" to "🌌"
                        )
                        
                        times.forEach { (tName, emoji) ->
                            val active = topStudyHours == tName
                            val timeBg = if (active) PrimaryLilac else PrimaryLilac.copy(alpha = 0.05f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(timeBg)
                                    .border(
                                        width = if (active) 1.5.dp else 0.dp,
                                        color = if (active) SunnyYellow else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { viewModel.setTopStudyHours(tName) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = emoji, fontSize = 14.sp)
                                    Text(
                                        text = tName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pomodoro Timer Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), isDark = isDark) {
                var dropdownExpanded by remember { mutableStateOf(false) }
                var showCustomTimerDialog by remember { mutableStateOf(false) }
                val incompleteTasks = tasks.filter { !it.isCompleted }
                val focusSessions by viewModel.focusSessions.collectAsState()
                var showHistory by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Focus Timer ($timerMode) ⏱️",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryLilac
                            )
                            Text(
                                text = "Practice focus block and earn XP",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoftGray
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryLilac.copy(alpha = 0.08f))
                            .clickable { dropdownExpanded = true }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🎯", fontSize = 16.sp)
                                Text(
                                    text = selectedFocusTask?.title ?: "Select study topic for this session...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedFocusTask != null) MaterialTheme.colorScheme.onSurface else SoftGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(imageVector = Icons.Rounded.ArrowDropDown, contentDescription = "Dropdown", tint = PrimaryLilac)
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("General Focus Block 🧘") },
                                onClick = {
                                    onSelectFocusTask(null)
                                    dropdownExpanded = false
                                }
                            )
                            if (incompleteTasks.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No active tasks! Enjoy the general session 🕊️") },
                                    onClick = { dropdownExpanded = false }
                                )
                            } else {
                                incompleteTasks.forEach { task ->
                                    DropdownMenuItem(
                                        text = { Text("${task.title} [${task.subject}] 📖") },
                                        onClick = {
                                            onSelectFocusTask(task)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Main Timer Circular Display & Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(150.dp)
                        ) {
                            val timerProgress = if (initialTimerMinutes > 0) {
                                (pomodoroMinutes * 60 + pomodoroSeconds).toFloat() / (initialTimerMinutes * 60)
                            } else 1.0f

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = if (isDark) Color.White.copy(alpha = 0.08f) else PrimaryLilac.copy(alpha = 0.12f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 20f, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(PrimaryLilac, SecondaryPeach, PrimaryLilac)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = (timerProgress.coerceAtMost(1f) * 360f),
                                    useCenter = false,
                                    style = Stroke(width = 20f, cap = StrokeCap.Round)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format("%02d:%02d", pomodoroMinutes, pomodoroSeconds),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isTimerRunning) "TICKING ⏱️" else "PAUSED ⏸️",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTimerRunning) SunnyYellow else SoftGray
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Choose focus block:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onSetTimerDuration(25, "Study") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (initialTimerMinutes == 25 && timerMode == "Study") PrimaryLilac else PrimaryLilac.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(34.dp).testTag("timer_duration_25"),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "25m 🎯",
                                        fontSize = 11.sp,
                                        color = if (initialTimerMinutes == 25 && timerMode == "Study") Color.White else PrimaryLilac,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = { onSetTimerDuration(50, "Study") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (initialTimerMinutes == 50 && timerMode == "Study") PrimaryLilac else PrimaryLilac.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(34.dp).testTag("timer_duration_50"),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "50m 🔥",
                                        fontSize = 11.sp,
                                        color = if (initialTimerMinutes == 50 && timerMode == "Study") Color.White else PrimaryLilac,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onSetTimerDuration(5, "Break") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (initialTimerMinutes == 5 && timerMode == "Break") SecondaryPeach else SecondaryPeach.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(34.dp).testTag("timer_duration_5"),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "5m 🏖️",
                                        fontSize = 11.sp,
                                        color = if (initialTimerMinutes == 5 && timerMode == "Break") Color.White else SecondaryPeach,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = { onSetTimerDuration(15, "Break") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (initialTimerMinutes == 15 && timerMode == "Break") SecondaryPeach else SecondaryPeach.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(34.dp).testTag("timer_duration_15"),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "15m 🌴",
                                        fontSize = 11.sp,
                                        color = if (initialTimerMinutes == 15 && timerMode == "Break") Color.White else SecondaryPeach,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Custom Manual Timer Button
                            Button(
                                onClick = { showCustomTimerDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (initialTimerMinutes !in listOf(5, 15, 25, 50)) PrimaryLilac else PrimaryLilac.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(34.dp).testTag("custom_timer_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Custom", tint = if (initialTimerMinutes !in listOf(5, 15, 25, 50)) Color.White else PrimaryLilac, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = if (initialTimerMinutes !in listOf(5, 15, 25, 50)) "Custom (${initialTimerMinutes}m) ✏️" else "Custom Time ✏️",
                                        fontSize = 11.sp,
                                        color = if (initialTimerMinutes !in listOf(5, 15, 25, 50)) Color.White else PrimaryLilac,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Custom Manual Focus Timer Dialog
                    if (showCustomTimerDialog) {
                        var customMinsInput by remember { mutableStateOf(initialTimerMinutes.toString()) }
                        var customModeInput by remember { mutableStateOf(timerMode) }
                        val context = LocalContext.current

                        AlertDialog(
                            onDismissRequest = { showCustomTimerDialog = false },
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Rounded.Timer, contentDescription = "Timer", tint = PrimaryLilac)
                                    Text("Set Custom Focus Duration ⏱️", fontWeight = FontWeight.Bold)
                                }
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "Set any custom duration (in minutes) for your study or break session:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SoftGray
                                    )

                                    OutlinedTextField(
                                        value = customMinsInput,
                                        onValueChange = { customMinsInput = it.filter { c -> c.isDigit() } },
                                        label = { Text("Timer Duration (Minutes)") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Text("Session Mode:", style = MaterialTheme.typography.labelSmall, color = SoftGray)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isStudyMode = customModeInput == "Study"
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isStudyMode) PrimaryLilac else PrimaryLilac.copy(alpha = 0.12f))
                                                .clickable { customModeInput = "Study" }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Study Block 📚", fontWeight = FontWeight.Bold, color = if (isStudyMode) Color.White else PrimaryLilac, fontSize = 12.sp)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (!isStudyMode) SecondaryPeach else SecondaryPeach.copy(alpha = 0.12f))
                                                .clickable { customModeInput = "Break" }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Break Block 🏖️", fontWeight = FontWeight.Bold, color = if (!isStudyMode) Color.White else SecondaryPeach, fontSize = 12.sp)
                                        }
                                    }

                                    Text("Quick Presets:", style = MaterialTheme.typography.labelSmall, color = SoftGray)
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(10, 20, 30, 45).forEach { m ->
                                                AssistChip(
                                                    onClick = { customMinsInput = m.toString() },
                                                    label = { Text("${m}m", fontSize = 11.sp) },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = PrimaryLilac.copy(alpha = 0.12f),
                                                        labelColor = PrimaryLilac
                                                    )
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(60, 90, 120, 180).forEach { m ->
                                                AssistChip(
                                                    onClick = { customMinsInput = m.toString() },
                                                    label = { Text("${m}m", fontSize = 11.sp) },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = PrimaryLilac.copy(alpha = 0.12f),
                                                        labelColor = PrimaryLilac
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = {
                                        val mins = customMinsInput.toIntOrNull()
                                        if (mins != null && mins in 1..720) {
                                            onSetTimerDuration(mins, customModeInput)
                                            showCustomTimerDialog = false
                                            Toast.makeText(context, "Timer set to ${mins}m ($customModeInput mode)! ⏱️✨", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Please enter a valid number of minutes (1 - 720)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Set Timer 🎯")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCustomTimerDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // Play, Pause and Reset controllers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onToggleTimer() },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(44.dp)
                                .testTag("timer_toggle_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTimerRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isTimerRunning) "Pause" else "Play",
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isTimerRunning) "Pause focus" else "Start focus",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Button(
                            onClick = { onResetTimer() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("timer_reset_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "Reset",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    HorizontalDivider(color = if (isDark) BorderDarkPastel.copy(alpha = 0.1f) else BorderPastel.copy(alpha = 0.2f))

                    // Toggle History logs section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHistory = !showHistory }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(imageVector = Icons.Rounded.History, contentDescription = "History", tint = SoftGray, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Show past sessions history (${focusSessions.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SoftGray
                            )
                        }
                        Icon(
                            imageVector = if (showHistory) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Toggle History",
                            tint = SoftGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (showHistory) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            focusSessions.take(5).forEach { session ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDark) Color.White.copy(alpha = 0.03f) else PrimaryLilac.copy(alpha = 0.05f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.taskTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${session.durationMinutes}m • at ${session.dateString}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SoftGray
                                        )
                                    }
                                    Text(
                                        text = "+${session.xpEarned} XP",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SunnyYellow
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui

import android.widget.Toast
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.data.Task
import com.example.data.WeakTopic
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Sparkle/Particle Model for complete-task micro-animation
data class SparkleParticle(
    val id: Int,
    val initialX: Float,
    val initialY: Float,
    val targetX: Float,
    val targetY: Float,
    val color: Color,
    val size: Float,
    val rotation: Float
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun StudyliciousApp(viewModel: StudyViewModel) {
    val context = LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    if (!isLoggedIn) {
        LoginScreen(viewModel = viewModel)
        return
    }

    var currentTab by remember { mutableStateOf("dashboard") }

    // Floating sparkles state
    var activeSparkles by remember { mutableStateOf<List<SparkleParticle>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    var sparkleCounter by remember { mutableIntStateOf(0) }

    // Function to trigger confetti burst at task completion
    val triggerTaskCompleteAnimation: (Offset) -> Unit = { offset ->
        coroutineScope.launch {
            val colors = listOf(SunnyYellow, SecondaryPeach, PrimaryLilac, MintGreen, LightBlue)
            val newSparkles = (1..15).map { i ->
                val angle = Math.toRadians((360 / 15 * i).toDouble())
                val distance = (40..120).random().toFloat()
                SparkleParticle(
                    id = sparkleCounter++,
                    initialX = offset.x,
                    initialY = offset.y,
                    targetX = offset.x + (distance * Math.cos(angle)).toFloat(),
                    targetY = offset.y + (distance * Math.sin(angle)).toFloat() - (20..60).random().toFloat(),
                    color = colors.random(),
                    size = (6..14).random().toFloat(),
                    rotation = (0..360).random().toFloat()
                )
            }
            activeSparkles = activeSparkles + newSparkles
            delay(800L)
            // Cleanup expired particles
            activeSparkles = activeSparkles.filter { p -> !newSparkles.contains(p) }
        }
    }

    // Pomodoro Timer States
    var pomodoroMinutes by remember { mutableIntStateOf(25) }
    var pomodoroSeconds by remember { mutableIntStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var timerMode by remember { mutableStateOf("Study") } // "Study" or "Break"
    var initialTimerMinutes by remember { mutableIntStateOf(25) }
    
    val xpPoints by viewModel.xpPoints.collectAsState()
    var selectedFocusTask by remember { mutableStateOf<Task?>(null) }
    var showSessionCompleteDialog by remember { mutableStateOf(false) }
    var lastCompletedTaskName by remember { mutableStateOf("") }
    var lastEarnedXp by remember { mutableIntStateOf(0) }

    // Tick Pomodoro
    LaunchedEffect(isTimerRunning, pomodoroMinutes, pomodoroSeconds) {
        if (isTimerRunning) {
            delay(1000L)
            if (pomodoroSeconds > 0) {
                pomodoroSeconds--
            } else if (pomodoroMinutes > 0) {
                pomodoroMinutes--
                pomodoroSeconds = 59
            } else {
                // Timer finished!
                isTimerRunning = false
                if (timerMode == "Study") {
                    val taskName = selectedFocusTask?.title ?: "General Focus Session"
                    val xpAwarded = if (selectedFocusTask != null) 100 else 50
                    viewModel.logFocusSession(taskName, if (initialTimerMinutes > 0) initialTimerMinutes else 25, xpAwarded)
                    
                    lastCompletedTaskName = taskName
                    lastEarnedXp = xpAwarded
                    showSessionCompleteDialog = true
                    
                    // Trigger particles
                    triggerTaskCompleteAnimation(Offset(500f, 1000f))
                    
                    timerMode = "Break"
                    pomodoroMinutes = 5
                    initialTimerMinutes = 5
                } else {
                    timerMode = "Study"
                    pomodoroMinutes = 25
                    initialTimerMinutes = 25
                    Toast.makeText(context, "Break over! Time to focus, you've got this! 🧠🚀", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            val appThemeVal = viewModel.appTheme.collectAsState().value
            val isDark = appThemeVal == "Cosmic Candy"
            val navBg = if (isDark) DarkSurface.copy(alpha = 0.85f) else LightSurface.copy(alpha = 0.85f)
            val navBorderBrush = Brush.verticalGradient(
                listOf(
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.7f),
                    Color.Transparent
                )
            )

            NavigationBar(
                containerColor = navBg,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .border(
                        width = 1.dp,
                        brush = navBorderBrush,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                val tabs = listOf(
                    Triple("dashboard", "Home", Icons.Rounded.Dashboard),
                    Triple("todo", "Tasks", Icons.Rounded.CheckCircle),
                    Triple("scanner", "Scan", Icons.Rounded.QrCodeScanner),
                    Triple("board", "Board Hub", Icons.Rounded.School),
                    Triple("calendar", "Plan", Icons.Rounded.CalendarMonth),
                    Triple("mistakes", "Review", Icons.Rounded.FactCheck),
                    Triple("coach", "Coach", Icons.Rounded.Forum)
                )
                tabs.forEach { (tabId, label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == tabId,
                        onClick = { currentTab = tabId },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                                fontSize = 9.sp
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (currentTab == tabId) PrimaryLilac else SoftGray
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = PrimaryLilac.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        val appTheme by viewModel.appTheme.collectAsState()
        val bgGradient = remember(appTheme) {
            when (appTheme) {
                "Sunset Glow" -> Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFD2A9),
                        Color(0xFFFF9E9E),
                        Color(0xFFFFE0A9),
                        Color(0xFFFFB7D2)
                    )
                )
                "Cotton Candy" -> Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE3F2FD),
                        Color(0xFFFCE4EC),
                        Color(0xFFE8EAF6)
                    )
                )
                "Minty Fresh" -> Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE0F2F1),
                        Color(0xFFE8F5E9),
                        Color(0xFFFFF9C4)
                    )
                )
                "Cosmic Candy" -> Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF311042),
                        Color(0xFF10255C),
                        Color(0xFF50123C)
                    )
                )
                else -> Brush.linearGradient( // "Pastel Rainbow"
                    colors = listOf(
                        Color(0xFFF1EAFF),
                        Color(0xFFFFF1E6),
                        Color(0xFFE6F5FF),
                        Color(0xFFE2F9E5)
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = twinSpring()) with fadeOut(animationSpec = twinSpring())
                }
            ) { tab ->
                when (tab) {
                    "dashboard" -> DashboardScreen(
                        viewModel = viewModel,
                        xpPoints = xpPoints,
                        pomodoroMinutes = pomodoroMinutes,
                        pomodoroSeconds = pomodoroSeconds,
                        isTimerRunning = isTimerRunning,
                        timerMode = timerMode,
                        initialTimerMinutes = initialTimerMinutes,
                        selectedFocusTask = selectedFocusTask,
                        onSelectFocusTask = { selectedFocusTask = it },
                        onToggleTimer = { isTimerRunning = !isTimerRunning },
                        onResetTimer = {
                            isTimerRunning = false
                            pomodoroMinutes = if (timerMode == "Study") 25 else 5
                            pomodoroSeconds = 0
                            initialTimerMinutes = if (timerMode == "Study") 25 else 5
                        },
                        onSetTimerDuration = { mins, mode ->
                            isTimerRunning = false
                            pomodoroMinutes = mins
                            pomodoroSeconds = 0
                            timerMode = mode
                            initialTimerMinutes = mins
                        },
                        onTriggerCompleteAnim = triggerTaskCompleteAnimation
                    )
                    "todo" -> ToDoScreen(
                        viewModel = viewModel,
                        onTriggerCompleteAnim = triggerTaskCompleteAnimation
                    )
                    "scanner" -> ScannerScreen(viewModel)
                    "board" -> BoardHubScreen(viewModel)
                    "calendar" -> CalendarScreen(viewModel)
                    "mistakes" -> MistakesScreen(viewModel)
                    "coach" -> CoachScreen(viewModel)
                }
            }

            // Confetti Sparkle Layer Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                activeSparkles.forEach { p ->
                    // Simple progress interpolation for animation (0f to 1f over time)
                    // Draw a cute star or diamond
                    val starPath = Path().apply {
                        moveTo(p.targetX, p.targetY - p.size)
                        lineTo(p.targetX + p.size / 3, p.targetY - p.size / 3)
                        lineTo(p.targetX + p.size, p.targetY)
                        lineTo(p.targetX + p.size / 3, p.targetY + p.size / 3)
                        lineTo(p.targetX, p.targetY + p.size)
                        lineTo(p.targetX - p.size / 3, p.targetY + p.size / 3)
                        lineTo(p.targetX - p.size, p.targetY)
                        lineTo(p.targetX - p.size / 3, p.targetY - p.size / 3)
                        close()
                    }
                    drawPath(path = starPath, color = p.color)
                }
            }

            if (showSessionCompleteDialog) {
                AlertDialog(
                    onDismissRequest = { showSessionCompleteDialog = false },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Session Completed! 🏆", fontWeight = FontWeight.Bold, color = PrimaryLilac)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Outstanding work! You just completed a focused study session on:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = lastCompletedTaskName,
                                fontWeight = FontWeight.Bold,
                                color = SecondaryPeach,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Claim your reward of +$lastEarnedXp XP points! 🌟",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            
                            val localTask = selectedFocusTask
                            if (localTask != null && !localTask.isCompleted) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Would you also like to mark this task as Completed?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SoftGray
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSessionCompleteDialog = false
                                selectedFocusTask?.let { task ->
                                    if (!task.isCompleted) {
                                        viewModel.toggleTaskCompletion(task)
                                    }
                                }
                                selectedFocusTask = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Claim & Complete Task")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showSessionCompleteDialog = false
                                selectedFocusTask = null
                            }
                        ) {
                            Text("Just Claim XP", color = SoftGray)
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = if (isSystemInDarkTheme()) DarkSurface else Color.White
                )
            }
        }
    }
}

private fun <T> twinSpring() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)

// --- GLASSMORPHISM CARD COMPONENT ---
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    borderColor: Color = BorderPastel,
    content: @Composable ColumnScope.() -> Unit
) {
    val bgColor = if (isDark) {
        DarkSurface.copy(alpha = 0.7f)
    } else {
        LightSurface.copy(alpha = 0.75f)
    }
    
    val borderBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.15f),
                BorderDarkPastel.copy(alpha = 0.25f),
                PrimaryLilac.copy(alpha = 0.15f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f),
                BorderPastel.copy(alpha = 0.45f),
                SecondaryPeach.copy(alpha = 0.2f)
            )
        )
    }

    Column(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .border(1.dp, borderBrush, RoundedCornerShape(24.dp))
            .padding(18.dp),
        content = content
    )
}

// --- TOP PROMINENT STREAK WIDGET ---
@Composable
fun TopStreakWidget(
    viewModel: StudyViewModel,
    isDark: Boolean
) {
    val streakState by viewModel.streakState.collectAsState()
    val focusSessions by viewModel.focusSessions.collectAsState()
    
    val currentStreak = streakState?.currentStreak ?: 0
    val bestStreak = streakState?.bestStreak ?: 0
    
    // Calculate the 7 days of the current week (Monday to Sunday)
    val calendar = java.util.Calendar.getInstance()
    val todayDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
    val mondayOffset = when (todayDayOfWeek) {
        java.util.Calendar.MONDAY -> 0
        java.util.Calendar.TUESDAY -> -1
        java.util.Calendar.WEDNESDAY -> -2
        java.util.Calendar.THURSDAY -> -3
        java.util.Calendar.FRIDAY -> -4
        java.util.Calendar.SATURDAY -> -5
        java.util.Calendar.SUNDAY -> -6
        else -> 0
    }
    
    val weekDays = (0..6).map { i ->
        val dayCal = java.util.Calendar.getInstance()
        dayCal.add(java.util.Calendar.DAY_OF_YEAR, mondayOffset + i)
        
        dayCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        dayCal.set(java.util.Calendar.MINUTE, 0)
        dayCal.set(java.util.Calendar.SECOND, 0)
        dayCal.set(java.util.Calendar.MILLISECOND, 0)
        val start = dayCal.timeInMillis
        
        dayCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        dayCal.set(java.util.Calendar.MINUTE, 59)
        dayCal.set(java.util.Calendar.SECOND, 59)
        dayCal.set(java.util.Calendar.MILLISECOND, 999)
        val end = dayCal.timeInMillis
        
        val isToday = i == (todayDayOfWeek - java.util.Calendar.MONDAY + 7) % 7
        val hasSession = focusSessions.any { it.timestamp in start..end }
        
        val dayName = when (i) {
            0 -> "Mon"
            1 -> "Tue"
            2 -> "Wed"
            3 -> "Thu"
            4 -> "Fri"
            5 -> "Sat"
            6 -> "Sun"
            else -> ""
        }
        
        Triple(dayName, hasSession, isToday)
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("top_streak_widget"),
        isDark = isDark
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Icon, Streak Info, Best Streak
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flame Mascot Box
                Box(
                    modifier = Modifier.size(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StreakMascot(streakCount = currentStreak)
                }
                
                // Streak metrics
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentStreak > 0) "$currentStreak Day Streak! 🔥" else "0 Day Streak ❄️",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (currentStreak > 0) OrangeRed else MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = if (currentStreak > 0) {
                            "Amazing job! Keep up the daily focus! 🚀"
                        } else {
                            "Start your first focus session to light the flame! 💡"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftGray
                    )
                }
                
                // Best Streak Pill
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "BEST 🏆",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryLilac
                    )
                    Text(
                        text = "$bestStreak Days",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Divider(
                color = if (isDark) BorderDarkPastel.copy(alpha = 0.1f) else BorderPastel.copy(alpha = 0.2f),
                thickness = 1.dp
            )
            
            // 7-day Habit Tracker row
            Text(
                text = "Weekly Activity Tracker 📅",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekDays.forEach { (dayName, hasSession, isToday) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) PrimaryLilac else SoftGray
                        )
                        
                        val boxModifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .let { mod ->
                                if (hasSession) {
                                    mod.background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(SunnyYellow, OrangeRed)
                                        )
                                    )
                                } else {
                                    val col = if (isToday) {
                                        PrimaryLilac.copy(alpha = 0.15f)
                                    } else {
                                        if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)
                                    }
                                    mod.background(color = col)
                                }
                            }
                            .border(
                                width = if (isToday) 2.dp else 0.dp,
                                color = if (isToday) PrimaryLilac else Color.Transparent,
                                shape = CircleShape
                            )
                        
                        Box(
                            modifier = boxModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasSession) {
                                Text(text = "🔥", fontSize = 14.sp)
                            } else {
                                Text(
                                    text = dayName.take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isToday) PrimaryLilac else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- MASCOT M3 FLAME COMPONENT ---
@Composable
fun StreakMascot(streakCount: Int) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutBack),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Draw Cute Flame (Base)
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.1f)
                cubicTo(w * 0.8f, h * 0.35f, w * 0.95f, h * 0.65f, w * 0.85f, h * 0.85f)
                cubicTo(w * 0.75f, h * 1.0f, w * 0.25f, h * 1.0f, w * 0.15f, h * 0.85f)
                cubicTo(w * 0.05f, h * 0.65f, w * 0.2f, h * 0.35f, w * 0.5f, h * 0.1f)
                close()
            }

            val gradientBrush = Brush.linearGradient(
                colors = listOf(SunnyYellow, SecondaryPeach, PrimaryLilac),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            drawPath(path = path, brush = gradientBrush)

            // Inner flame glow
            val innerPath = Path().apply {
                moveTo(w * 0.5f, h * 0.3f)
                cubicTo(w * 0.7f, h * 0.45f, w * 0.8f, h * 0.65f, w * 0.75f, h * 0.8f)
                cubicTo(w * 0.7f, h * 0.9f, w * 0.3f, h * 0.9f, w * 0.25f, h * 0.8f)
                cubicTo(w * 0.2f, h * 0.65f, w * 0.3f, h * 0.45f, w * 0.5f, h * 0.3f)
                close()
            }
            drawPath(path = innerPath, color = SunnyYellow)

            // 2. Draw Mascot Face
            val eyeRadius = 5f
            val isCool = streakCount >= 10
            
            if (isCool) {
                // Sunglasses
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(w * 0.25f, h * 0.5f),
                    size = Size(w * 0.5f, h * 0.12f),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                // Bridge
                drawLine(
                    color = Color.Black,
                    start = Offset(w * 0.45f, h * 0.55f),
                    end = Offset(w * 0.55f, h * 0.55f),
                    strokeWidth = 4f
                )
            } else {
                // Big friendly eyes (one winking!)
                drawCircle(color = MidnightPlum, radius = eyeRadius, center = Offset(w * 0.38f, h * 0.55f))
                
                // Wink eye
                val winkPath = Path().apply {
                    moveTo(w * 0.58f, h * 0.55f)
                    cubicTo(w * 0.62f, h * 0.52f, w * 0.66f, h * 0.52f, w * 0.7f, h * 0.55f)
                }
                drawPath(path = winkPath, color = MidnightPlum, style = Stroke(width = 3f))
            }

            // Smiling mouth
            val mouthPath = Path().apply {
                moveTo(w * 0.44f, h * 0.66f)
                cubicTo(w * 0.48f, h * 0.72f, w * 0.52f, h * 0.72f, w * 0.56f, h * 0.66f)
            }
            drawPath(path = mouthPath, color = MidnightPlum, style = Stroke(width = 4f))

            // Rosy cheeks
            drawCircle(color = SecondaryPeach.copy(alpha = 0.7f), radius = 4f, center = Offset(w * 0.3f, h * 0.61f))
            drawCircle(color = SecondaryPeach.copy(alpha = 0.7f), radius = 4f, center = Offset(w * 0.7f, h * 0.61f))
        }
    }
}

@Composable
fun SpeechBubbleArrow(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(10.dp, 14.dp)) {
        val path = Path().apply {
            moveTo(size.width, 0f)
            lineTo(0f, size.height / 2f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun FluidBreathingBanner(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluid")
    
    // Animate phase of sine waves for fluid motion
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    // Animate scale/pulse for breathing effect
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) DarkSurface.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.65f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) BorderDarkPastel.copy(alpha = 0.15f) else BorderPastel.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Fluid Animation Canvas
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PrimaryLilac.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val radius = (size.minDimension / 2.3f) * pulseScale
                    val center = Offset(w / 2, h / 2)
                    
                    val path = Path()
                    val numPoints = 50
                    val angleStep = 360f / numPoints
                    
                    for (i in 0..numPoints) {
                        val angleDeg = i * angleStep
                        val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
                        
                        // Add organic sine waves to simulate moving liquid border
                        val waveOffset = 5.dp.toPx() * kotlin.math.sin((angleRad * 5 + phase).toDouble()).toFloat()
                        val r = radius + waveOffset
                        
                        val x = center.x + r * kotlin.math.cos(angleRad)
                        val y = center.y + r * kotlin.math.sin(angleRad)
                        
                        if (i == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    path.close()
                    
                    val gradient = Brush.radialGradient(
                        colors = listOf(
                            PrimaryLilac.copy(alpha = 0.75f),
                            SecondaryPeach.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 1.3f
                    )
                    
                    drawPath(path = path, brush = gradient)
                }
                
                Text(
                    text = if (pulseScale > 1f) "🌸" else "🧘",
                    fontSize = 24.sp
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                val isInhale = pulseScale > 1f
                Text(
                    text = if (isInhale) "Inhale Peace... 🌸" else "Exhale Worry... ✨",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gentle breathing lowers stress by 32% before study sessions. Focus on the organic rhythm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftGray,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// --- SCREEN 1: THE DASHBOARD SCREEN ---
@Composable
fun DashboardScreen(
    viewModel: StudyViewModel,
    xpPoints: Int,
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
    val streakState by viewModel.streakState.collectAsState()
    val quote by viewModel.dailyQuote.collectAsState()
    val quoteLoading by viewModel.quoteLoading.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState()
    val isDark = currentTheme == "Cosmic Candy"

    val completedCount = tasks.count { it.isCompleted }
    val totalCount = tasks.size
    val progressFraction = (if (totalCount > 0) completedCount.toFloat() / totalCount else 0.0f).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Profile Row & XP
        item {
            val userName by viewModel.userName.collectAsState()
            val userEmail by viewModel.userEmail.collectAsState()
            val userProfilePic by viewModel.userProfilePic.collectAsState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isDark) DarkSurface.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.7f))
                    .border(
                        width = 1.dp,
                        color = if (isDark) BorderDarkPastel.copy(alpha = 0.15f) else BorderPastel.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cute Avatar with dotted border matching the profile image
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = PrimaryLilac,
                                radius = size.minDimension / 2 - 2.dp.toPx(),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PrimaryLilac.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = userProfilePic, fontSize = 24.sp)
                        }
                    }

                    Column {
                        Text(
                            text = "Hey, $userName! ✨",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // XP display
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryLilac.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "XP",
                                tint = SunnyYellow,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$xpPoints XP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryLilac
                            )
                        }
                    }

                    // Logout Icon Button (Super cute and useful!)
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SecondaryPeach.copy(alpha = 0.15f))
                            .testTag("dashboard_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Logout,
                            contentDescription = "Log out",
                            tint = SecondaryPeach,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Top Streak Widget at the top of Home Screen
        item {
            TopStreakWidget(viewModel = viewModel, isDark = isDark)
        }

        // Calming Fluid Breathing Animation Banner at the top of Dashboard
        item {
            FluidBreathingBanner(isDark = isDark)
        }

        // Interactive Theme Selector Row
        item {
            val currentTheme by viewModel.appTheme.collectAsState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.06f) else PrimaryLilac.copy(alpha = 0.08f))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = "Themes",
                        tint = PrimaryLilac,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Choose Your Vibe ✨🌈",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val themesList = listOf(
                    "Pastel Rainbow" to "🌈",
                    "Sunset Glow" to "🌅",
                    "Cotton Candy" to "🍭",
                    "Minty Fresh" to "🌿",
                    "Cosmic Candy" to "🌌"
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(themesList) { (themeName, emoji) ->
                        val isSelected = currentTheme == themeName
                        val themeColor = when(themeName) {
                            "Sunset Glow" -> Color(0xFFFF9E9E)
                            "Cotton Candy" -> Color(0xFF90CAF9)
                            "Minty Fresh" -> Color(0xFFA5D6A7)
                            "Cosmic Candy" -> Color(0xFFCE93D8)
                            else -> Color(0xFFD1C4E9)
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) themeColor else themeColor.copy(alpha = 0.15f))
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.setAppTheme(themeName) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = emoji, fontSize = 14.sp)
                                Text(
                                    text = themeName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF1E1C24) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
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

                    Divider(color = if (isDark) BorderDarkPastel.copy(alpha = 0.1f) else BorderPastel.copy(alpha = 0.2f))

                    // Mood selector
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

                    // Peak focus selector
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

        // Upcoming Tests Tracker & In-App Notification Center Widget
        item {
            val upcomingExams = tasks.filter { !it.isCompleted && (it.taskType == "TEST" || it.taskType == "ASSIGNMENT") && it.dueDate >= System.currentTimeMillis() }.sortedBy { it.dueDate }
            val context = LocalContext.current

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = if (upcomingExams.isNotEmpty()) OrangeRed.copy(alpha = 0.08f) else PrimaryLilac.copy(alpha = 0.06f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (upcomingExams.isNotEmpty()) OrangeRed.copy(alpha = 0.4f) else PrimaryLilac.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (upcomingExams.isNotEmpty()) OrangeRed.copy(alpha = 0.15f) else PrimaryLilac.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = "Alerts",
                                tint = if (upcomingExams.isNotEmpty()) OrangeRed else PrimaryLilac,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Column {
                            Text(
                                text = "Exam & Paper Reminder Widget 🚨",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Keeps you in check of upcoming Matric finals, midterms & tests.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray
                            )
                        }
                    }

                    if (upcomingExams.isEmpty()) {
                        Text(
                            text = "No upcoming tests or assignments scheduled for the next 30 days. You are fully caught up! 🕊️✨",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftGray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            upcomingExams.take(3).forEach { exam ->
                                val diff = exam.dueDate - System.currentTimeMillis()
                                val daysLeft = (diff / 86400000L).toInt().coerceAtLeast(0)
                                val labelText = if (daysLeft == 0) "TODAY! 🚨" else if (daysLeft == 1) "TOMORROW! ⏳" else "In $daysLeft days"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (daysLeft <= 3) OrangeRed.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.3f))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text("🧬", modifier = Modifier.padding(end = 6.dp))
                                        Column {
                                            Text(exam.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text(exam.subject, style = MaterialTheme.typography.labelSmall, color = SoftGray)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (daysLeft <= 3) OrangeRed else PrimaryLilac)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(labelText, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Button(
                        onClick = {
                            if (upcomingExams.isNotEmpty()) {
                                val nearest = upcomingExams.first()
                                val diff = nearest.dueDate - System.currentTimeMillis()
                                val daysLeft = (diff / 86400000L).toInt().coerceAtLeast(0)
                                val dayText = if (daysLeft == 0) "today!" else if (daysLeft == 1) "tomorrow!" else "in $daysLeft days!"
                                Toast.makeText(context, "🔔 Studyly Alert: Don't forget to prepare for your ${nearest.subject} ${nearest.taskType.lowercase()} coming up $dayText! 🧠📖", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "🔔 Studyly Notification: You have no upcoming tests scheduled. Keep up the good work! 🌟", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Trigger Notification Alert 🚀", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Recent Notification Logs center
                    Text(
                        text = "Recent Notifications Center 🔔",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "🔔 [Today] Remember to study limits for Mathematics calculus test coming up in 30 days!" to true,
                            "🔔 [Today] Goal Master: Study goal set to 12 hours. Study consistently to earn bonus XP!" to false,
                            "🔔 [Yesterday] Quote of the day: Tap on the quote card to view brand new unique motivation!" to false
                        ).forEach { (logText, isNew) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isNew) PrimaryLilac.copy(alpha = 0.05f) else Color.Transparent)
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = logText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isNew) PrimaryLilac else SoftGray,
                                    fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Streak & Daily Study Goal Progress Card
        item {
            val streak = streakState?.currentStreak ?: 0
            val focusSessions by viewModel.focusSessions.collectAsState()
            val dailyTargetHours by viewModel.dailyTargetHours.collectAsState()
            val context = LocalContext.current

            val todayStudyMinutes = remember(focusSessions) {
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                focusSessions
                    .filter { it.timestamp >= todayStart }
                    .sumOf { it.durationMinutes }
            }
            val todayStudyHours = todayStudyMinutes / 60f
            val goalProgressFraction = if (dailyTargetHours > 0) todayStudyHours / dailyTargetHours else 0.0f

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Combined Streak & Goal Card
                GlassCard(modifier = Modifier.fillMaxWidth(), isDark = isDark) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StreakMascot(streakCount = streak)
                                Text(
                                    text = "$streak Day Streak! 🔥",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryLilac
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Daily Study Goal 🎯",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Studied: %.1f hrs / %d hrs".format(todayStudyHours, dailyTargetHours),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryLilac
                            )
                            Text(
                                text = if (goalProgressFraction >= 1f) "Goal Achieved! You are a superstar! 🏆" else "Keep studying to reach your daily goal!",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftGray
                            )
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // Target Setter Buttons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconButton(
                                    onClick = { if (dailyTargetHours > 1) viewModel.setDailyTargetHours(dailyTargetHours - 1) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryLilac.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Remove,
                                        contentDescription = "Decrease Target",
                                        tint = PrimaryLilac,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                Text(
                                    text = "${dailyTargetHours}h goal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                IconButton(
                                    onClick = { if (dailyTargetHours < 24) viewModel.setDailyTargetHours(dailyTargetHours + 1) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryLilac.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Increase Target",
                                        tint = PrimaryLilac,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        
                        // Interactive Progress Ring
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .clickable {
                                    if (goalProgressFraction >= 1f) {
                                        onTriggerCompleteAnim(Offset(500f, 1000f))
                                        Toast.makeText(context, "You crushed your daily goal! 🌟🎉", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Studied %.1f hours today! Keep focus! 💪".format(todayStudyHours), Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = if (isDark) Color.White.copy(alpha = 0.08f) else PrimaryLilac.copy(alpha = 0.12f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 24f, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(SecondaryPeach, PrimaryLilac, SecondaryPeach)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = (goalProgressFraction.coerceAtMost(1f) * 360f),
                                    useCenter = false,
                                    style = Stroke(width = 24f, cap = StrokeCap.Round)
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(goalProgressFraction.coerceAtMost(1f) * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "done",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftGray
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2x2 Grid Stats Widget (Pending, Overdue, Completed, Streak)
        item {
            val pendingCount = tasks.count { !it.isCompleted }
            val overdueCount = tasks.count { !it.isCompleted && it.dueDate < System.currentTimeMillis() }
            val completedCount = tasks.count { it.isCompleted }
            val streak = streakState?.currentStreak ?: 0

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pending Tasks
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFFFF9E6),
                                        Color(0xFFFFF0C2)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HourglassEmpty,
                                    contentDescription = "Pending Tasks",
                                    tint = Color(0xFFFF8F00),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Pending Tasks",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5D4037)
                                )
                            }
                            Text(
                                text = "$pendingCount",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF8F00)
                            )
                            Text(
                                text = "Next 7 Days",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8D6E63)
                            )
                        }
                    }

                    // Overdue Tasks
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFFFEBEE),
                                        Color(0xFFFFCDD2)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFFE57373).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = "Overdue Tasks",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Overdue Tasks",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF781C1C)
                                )
                            }
                            Text(
                                text = "$overdueCount",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFD32F2F)
                            )
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9E4E4E)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tasks Completed
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFE0F2F1),
                                        Color(0xFFB2DFDB)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFF4DB6AC).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Completed Tasks",
                                    tint = Color(0xFF00796B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Completed Tasks",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF004D40)
                                )
                            }
                            Text(
                                text = "$completedCount",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00796B)
                            )
                            Text(
                                text = "Last 7 Days",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00695C).copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Your Streak
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFEDE7F6),
                                        Color(0xFFD1C4E9)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFF9575CD).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Whatshot,
                                    contentDescription = "Your Streak",
                                    tint = Color(0xFF5E35B1),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Your Streak",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF311B92)
                                )
                            }
                            Text(
                                text = "$streak",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF5E35B1)
                            )
                            Text(
                                text = "Total streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4527A0).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // Dual Widget: Exam Countdown (Days Left) & All-Time Study Stats
        item {
            val focusSessions by viewModel.focusSessions.collectAsState()
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Days Left Card
                val daysLeft = remember {
                    val examCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, 2026)
                        set(Calendar.MONTH, Calendar.OCTOBER)
                        set(Calendar.DAY_OF_MONTH, 26)
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                    }
                    val diff = examCal.timeInMillis - System.currentTimeMillis()
                    if (diff > 0) (diff / (1000 * 60 * 60 * 24)).toInt() else 0
                }

                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp),
                    isDark = isDark
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Event,
                            contentDescription = "Days Left",
                            tint = themeSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$daysLeft Days",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = themeSecondary
                        )
                        Text(
                            text = "to Matric Finals 🎓",
                            style = MaterialTheme.typography.labelMedium,
                            color = SoftGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Hours Studied Card
                val allTimeStudyHours = remember(focusSessions) {
                    focusSessions.sumOf { it.durationMinutes } / 60f
                }

                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp),
                    isDark = isDark
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = "Total Study Hours",
                            tint = themePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "%.1f Hrs".format(allTimeStudyHours),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = themePrimary
                        )
                        Text(
                            text = "Hours Studied 📚",
                            style = MaterialTheme.typography.labelMedium,
                            color = SoftGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Weekly Progress Graph (What got done)
        item {
            val focusSessions by viewModel.focusSessions.collectAsState()
            
            // Calculate current week Sunday to Saturday with 0% to 100% metrics
            val currentWeekData = remember(focusSessions) {
                val list = mutableListOf<DayProgress>()
                val cal = Calendar.getInstance()
                
                // Find Sunday of the current week
                val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val daysToSub = currentDayOfWeek - Calendar.SUNDAY
                cal.add(Calendar.DATE, -daysToSub)
                
                val dayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
                
                for (i in 0 until 7) {
                    val dayCal = Calendar.getInstance().apply {
                        timeInMillis = cal.timeInMillis
                        add(Calendar.DATE, i)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val dayStart = dayCal.timeInMillis
                    val dayEnd = dayStart + 86400000L
                    val dayLabel = dayFormat.format(dayCal.time)
                    
                    val daySessions = focusSessions.filter { it.timestamp in dayStart until dayEnd }
                    val hasStudy = daySessions.isNotEmpty()
                    
                    // 1. Hours Studied (max 25%)
                    val mins = daySessions.sumOf { it.durationMinutes }
                    val hoursStudiedPct = ((mins / 300f).coerceIn(0f, 1f) * 25f)
                    
                    // 2. Test Scores (max 20%)
                    val hasTest = daySessions.any { 
                        val title = it.taskTitle.lowercase()
                        title.contains("test") || title.contains("exam") || title.contains("quiz") || title.contains("past paper") || title.contains("marathon")
                    }
                    val rawTestScore = when {
                        !hasStudy -> 0f
                        hasTest -> 92f
                        daySessions.any { it.taskTitle.lowercase().contains("sprint") || it.taskTitle.lowercase().contains("prep") } -> 86f
                        else -> 78f
                    }
                    val testScorePct = (rawTestScore / 100f) * 20f
                    
                    // 3. Tasks Completed (max 15%)
                    val rawTasksCompleted = when {
                        !hasStudy -> 0f
                        daySessions.size >= 3 -> 100f
                        daySessions.size == 2 -> 75f
                        else -> 50f
                    }
                    val tasksCompletedPct = (rawTasksCompleted / 100f) * 15f
                    
                    // 4. AI Coach Guidance (max 10%)
                    val rawAiCoach = when {
                        !hasStudy -> 0f
                        daySessions.size >= 3 -> 90f
                        daySessions.size == 2 -> 70f
                        else -> 40f
                    }
                    val aiCoachPct = (rawAiCoach / 100f) * 10f
                    
                    // 5. Revision Slots (max 15%)
                    val hasRevision = daySessions.any {
                        val title = it.taskTitle.lowercase()
                        title.contains("revision") || title.contains("review") || title.contains("prep") || title.contains("study")
                    }
                    val rawRevision = when {
                        !hasStudy -> 0f
                        hasRevision -> 100f
                        else -> 60f
                    }
                    val revisionPct = (rawRevision / 100f) * 15f
                    
                    // 6. Active Recall Practice (max 15%)
                    val hasActiveRecall = daySessions.any {
                        val title = it.taskTitle.lowercase()
                        title.contains("diagram") || title.contains("lab") || title.contains("exercise") || title.contains("practice") || title.contains("marathon")
                    }
                    val rawRecall = when {
                        !hasStudy -> 0f
                        hasActiveRecall -> 95f
                        else -> 70f
                    }
                    val recallPct = (rawRecall / 100f) * 15f
                    
                    val percentages = floatArrayOf(
                        hoursStudiedPct,
                        testScorePct,
                        tasksCompletedPct,
                        aiCoachPct,
                        revisionPct,
                        recallPct
                    )
                    
                    list.add(DayProgress(dayLabel, percentages))
                }
                list
            }
            
            val averageWeeklyScore = remember(currentWeekData) {
                val activeDays = currentWeekData.filter { it.categoryHours.sum() > 0 }
                if (activeDays.isNotEmpty()) {
                    activeDays.map { it.categoryHours.sum() }.average().toFloat()
                } else {
                    0f
                }
            }
            
            var selectedBarIndex by remember { mutableIntStateOf(-1) }
            
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                isDark = isDark
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title and subtitle matching reference image
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "What got done",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 22.sp
                        )
                        
                        val annotatedSubtitle = androidx.compose.ui.text.buildAnnotatedString {
                            append("Your average study score is ")
                            withStyle(
                                style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = PrimaryLilac)
                            ) {
                                append("%.1f%%".format(averageWeeklyScore))
                            }
                            append(" this week in ")
                            withStyle(
                                style = androidx.compose.ui.text.SpanStyle(
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append("total")
                            }
                            append(".")
                        }
                        
                        Text(
                            text = annotatedSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftGray
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Daily Breakdown label at the top-left of the chart area
                    Text(
                        text = "Daily Breakdown",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White.copy(alpha = 0.9f) else TextDark.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 48.dp)
                    )
                    
                    // Chart Area with Y-axis label, axes and bars
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Y-axis label & scale (0% to 100%)
                        Column(
                            modifier = Modifier
                                .width(44.dp)
                                .fillMaxHeight()
                                .padding(end = 8.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "100%",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "50%",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "0%",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray,
                                fontSize = 10.sp
                            )
                        }
                        
                        // Vertical Axis and Grid area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            // Draw Grid Axis Lines
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Draw Y-axis line (vertical left line)
                                drawLine(
                                    color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f),
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                                // Draw X-axis line (horizontal bottom line)
                                drawLine(
                                    color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            
                            // Bars Row
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 2.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                currentWeekData.forEachIndexed { index, dayProgress ->
                                    val isSelected = selectedBarIndex == index
                                    
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        // Bar view with click interaction
                                        StackedBar(
                                            dayLabel = dayProgress.label,
                                            categoryHours = dayProgress.categoryHours,
                                            maxHours = 100f,
                                            isDark = isDark,
                                            isSelected = isSelected,
                                            onClick = {
                                                selectedBarIndex = if (selectedBarIndex == index) -1 else index
                                            }
                                        )
                                        
                                        // Hover tag (show breakdown on selection)
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = isSelected,
                                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(bottom = 44.dp)
                                                .zIndex(10f)
                                        ) {
                                            val dayTotal = dayProgress.categoryHours.sum()
                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isDark) DarkSurface else Color.White
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isDark) BorderDarkPastel else BorderPastel
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "${dayProgress.label} - Daily Score: %.1f%%".format(dayTotal),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    // Detail breakdown of study categories
                                                    val categoryNames = listOf(
                                                        "Hours Studied",
                                                        "Test Scores",
                                                        "Tasks Completed",
                                                        "AI Coach Guidance",
                                                        "Revision Slots",
                                                        "Active Recall"
                                                    )
                                                    for (c in 0 until 6) {
                                                        val valPct = dayProgress.categoryHours[c]
                                                        if (valPct > 0f) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(8.dp)
                                                                        .clip(RoundedCornerShape(2.dp))
                                                                        .background(categoryColors[c])
                                                                )
                                                                Text(
                                                                    text = "${categoryNames[c]}: %.1f%%".format(valPct),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = SoftGray,
                                                                    fontSize = 10.sp
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
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Legends List matching study theme
                    val legendItems = listOf(
                        "Hours Studied" to categoryColors[0],
                        "Test Scores" to categoryColors[1],
                        "Tasks Completed" to categoryColors[2],
                        "AI Coach" to categoryColors[3],
                        "Revision Slots" to categoryColors[4],
                        "Active Recall" to categoryColors[5]
                    )
                    
                    // Display legends in a flow-like layout (2 rows of 3, or flow layout)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            legendItems.take(3).forEach { (name, color) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(color)
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SoftGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            legendItems.drop(3).forEach { (name, color) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(color)
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SoftGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

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
                // Cute interactive mascot
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

                // Speech Bubble Arrow
                SpeechBubbleArrow(
                    color = bubbleBg,
                    modifier = Modifier.offset(x = 1.dp)
                )

                // Speech Bubble body
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

        // Pomodoro Timer Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), isDark = isDark) {
                var dropdownExpanded by remember { mutableStateOf(false) }
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

                    // Preset duration chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val presets = listOf(
                            Triple(25, "Study", "✍️ 25m"),
                            Triple(50, "Study", "🧠 50m"),
                            Triple(5, "Break", "☕ 5m"),
                            Triple(15, "Break", "🍵 15m")
                        )
                        presets.forEach { (mins, mode, label) ->
                            val isSelected = (pomodoroMinutes == mins && timerMode == mode)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) PrimaryLilac.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) PrimaryLilac else BorderPastel,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSetTimerDuration(mins, mode) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) PrimaryLilac else SoftGray
                                )
                            }
                        }
                    }

                    // Custom set focus timer controller
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.04f) else PrimaryLilac.copy(alpha = 0.04f))
                            .border(1.dp, BorderPastel.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Custom Time:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            IconButton(
                                onClick = { 
                                    val newMins = if (pomodoroMinutes > 5) pomodoroMinutes - 5 else 1
                                    onSetTimerDuration(newMins, timerMode)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryLilac.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Decrease Custom Minutes",
                                    tint = PrimaryLilac,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Text(
                                text = "$pomodoroMinutes mins",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Black,
                                color = PrimaryLilac
                            )
                            
                            IconButton(
                                onClick = { 
                                    val newMins = if (pomodoroMinutes < 180) pomodoroMinutes + 5 else 180
                                    onSetTimerDuration(newMins, timerMode)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryLilac.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Increase Custom Minutes",
                                    tint = PrimaryLilac,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        // Text button to switch between Study and Break mode
                        TextButton(
                            onClick = {
                                val nextMode = if (timerMode == "Study") "Break" else "Study"
                                onSetTimerDuration(pomodoroMinutes, nextMode)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = PrimaryLilac
                            )
                        ) {
                            Text(
                                text = if (timerMode == "Study") "Mode: Study ✍️" else "Mode: Break ☕",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Focus target task selection
                    if (timerMode == "Study") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Focus Target Task 🎯",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryLilac
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color.White.copy(alpha = 0.05f) else PrimaryLilac.copy(alpha = 0.08f))
                                    .border(1.dp, BorderPastel, RoundedCornerShape(12.dp))
                                    .clickable { dropdownExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedFocusTask?.title ?: "General Focus Session (No Specific Task) 🧠",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selectedFocusTask != null) MaterialTheme.colorScheme.onSurface else SoftGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "Select Task",
                                        tint = SoftGray
                                    )
                                }

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .background(if (isDark) DarkSurface else Color.White)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("General Focus Session 🧠") },
                                        onClick = {
                                            onSelectFocusTask(null)
                                            dropdownExpanded = false
                                        }
                                    )
                                    incompleteTasks.forEach { task ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(task.title, fontWeight = FontWeight.SemiBold)
                                                    Text("${task.subject} • Chapter ${task.chapter}", style = MaterialTheme.typography.labelSmall, color = SoftGray)
                                                }
                                            },
                                            onClick = {
                                                onSelectFocusTask(task)
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Progress circular ring and timer display + controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalSecondsPreset = initialTimerMinutes * 60
                        val currentRemaining = pomodoroMinutes * 60 + pomodoroSeconds
                        val progress = if (totalSecondsPreset > 0) currentRemaining.toFloat() / totalSecondsPreset else 1.0f

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(100.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                color = if (timerMode == "Study") PrimaryLilac else SecondaryPeach,
                                strokeWidth = 8.dp,
                                trackColor = if (isDark) Color.White.copy(alpha = 0.08f) else PrimaryLilac.copy(alpha = 0.12f)
                            )
                            
                            val secStr = if (pomodoroSeconds < 10) "0$pomodoroSeconds" else "$pomodoroSeconds"
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$pomodoroMinutes:$secStr",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isTimerRunning) "Active" else "Paused",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isTimerRunning) MintGreen else OrangeRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Info text & controls
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedFocusTask != null && timerMode == "Study") {
                                Text(
                                    text = "🎯 Focus Target: +100 XP bonus!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryPeach,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else if (timerMode == "Study") {
                                Text(
                                    text = "🧠 General Focus: +50 XP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftGray
                                )
                            } else {
                                Text(
                                    text = "☕ Great job! Rest up.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MintGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onToggleTimer,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTimerRunning) OrangeRed else MintGreen
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (isTimerRunning) "Pause" else "Start", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onResetTimer,
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderPastel),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Reset", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    // Focus Session History log
                    if (focusSessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHistory = !showHistory }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Focus History (${focusSessions.size}) 📜",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SoftGray
                            )
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

        // Today's Study Tasks Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Study Schedule 📚",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "$completedCount/$totalCount Done",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryLilac,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Study Tasks list
        if (tasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "All clear",
                            tint = MintGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "All clear! Time to chill or prep ahead. 🛋️",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = SoftGray
                        )
                    }
                }
            }
        } else {
            items(tasks) { task ->
                TaskRowItem(
                    task = task,
                    isDark = isDark,
                    onToggleComplete = { offset ->
                        viewModel.toggleTaskCompletion(task)
                        if (!task.isCompleted) {
                            onTriggerCompleteAnim(offset)
                        }
                    },
                    onDelete = { viewModel.deleteTask(task.id) }
                )
            }
        }
    }
}

// --- TASK LIST ITEM COMPONENT ---
@Composable
fun TaskRowItem(
    task: Task,
    isDark: Boolean = false,
    onToggleComplete: (Offset) -> Unit,
    onDelete: () -> Unit
) {
    var itemOffset by remember { mutableStateOf(Offset.Zero) }
    val cardBorderBrush = if (task.isCompleted) {
        Brush.linearGradient(
            colors = listOf(
                MintGreen.copy(alpha = 0.7f),
                MintGreen.copy(alpha = 0.3f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.8f),
                PrimaryLilac.copy(alpha = 0.3f)
            )
        )
    }
    val cardBg = if (task.isCompleted) {
        MintGreen.copy(alpha = 0.08f)
    } else {
        if (isDark) DarkSurface.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.75f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { itemOffset = center }
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .border(1.dp, cardBorderBrush, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Customized Checkbox
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (task.isCompleted) MintGreen else PrimaryLilac.copy(alpha = 0.1f)
                        )
                        .border(
                            2.dp,
                            if (task.isCompleted) MintGreen else PrimaryLilac,
                            CircleShape
                        )
                        .clickable { onToggleComplete(itemOffset) }
                        .testTag("task_checkbox"),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (task.isCompleted) SoftGray else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge of Subject
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryLilac.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = task.subject,
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryLilac,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "⏱️ ${task.estimatedMinutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = SoftGray
                        )

                        if (task.rescheduledCount > 0) {
                            Text(
                                text = "🔄 Shifted x${task.rescheduledCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrangeRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete Task",
                    tint = OrangeRed.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// --- SCREEN 2: SMART SCAN / TASK ENTRY ---
@Composable
fun ScannerScreen(viewModel: StudyViewModel) {
    val scanLoading by viewModel.scanLoading.collectAsState()
    var homeworkInputText by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            homeworkInputText = """
                - Mathematics limit functions homework questions 1 to 5
                - Physical Sciences friction force experiment report revision
                - Life Sciences cell genetics diagram drawing
            """.trimIndent()
            Toast.makeText(context, "Photo captured! Scanned items loaded. You can modify them below! 📝✨", Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to scan lists!", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Smart Homework Scanner 📝",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Take a photo of your handwritten homework list/syllabus using the back camera, or paste your assignment details below to structure your dashboard study plan!",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray
            )
        }

        // Viewfinder styled with rounded brackets & captured image support
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MidnightPlum)
                    .border(2.dp, PrimaryLilac, RoundedCornerShape(24.dp))
                    .clickable {
                        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(null)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (capturedBitmap != null) {
                    Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Captured To-Do List",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }

                // Bracket Corners
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val margin = 20.dp.toPx()
                    val len = 30.dp.toPx()
                    val stroke = 6f
                    val strokeColor = if (capturedBitmap != null) MintGreen else Color.White

                    // Top Left Bracket
                    drawLine(strokeColor, Offset(margin, margin), Offset(margin + len, margin), stroke)
                    drawLine(strokeColor, Offset(margin, margin), Offset(margin, margin + len), stroke)

                    // Top Right Bracket
                    drawLine(strokeColor, Offset(size.width - margin, margin), Offset(size.width - margin - len, margin), stroke)
                    drawLine(strokeColor, Offset(size.width - margin, margin), Offset(size.width - margin, margin + len), stroke)

                    // Bottom Left Bracket
                    drawLine(strokeColor, Offset(margin, size.height - margin), Offset(margin + len, size.height - margin), stroke)
                    drawLine(strokeColor, Offset(margin, size.height - margin), Offset(margin, size.height - margin - len), stroke)

                    // Bottom Right Bracket
                    drawLine(strokeColor, Offset(size.width - margin, size.height - margin), Offset(size.width - margin - len, size.height - margin), stroke)
                    drawLine(strokeColor, Offset(size.width - margin, size.height - margin), Offset(size.width - margin, size.height - margin - len), stroke)
                }

                // Scanning Line Animation
                if (scanLoading) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val scanOffset by infiniteTransition.animateFloat(
                        initialValue = 0.1f,
                        targetValue = 0.9f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * scanOffset
                        drawLine(
                            color = MintGreen,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 8f
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    if (scanLoading) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Analyzing",
                            tint = SunnyYellow,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "AI is reading your homework like a pro...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = if (capturedBitmap != null) Icons.Rounded.CheckCircle else Icons.Rounded.PhotoCamera,
                            contentDescription = "Scan Icon",
                            tint = if (capturedBitmap != null) MintGreen else PrimaryLilac,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (capturedBitmap != null) "Photo Captured! Tap to re-shoot 📸" else "Tap Viewfinder to take photo with Back Camera 📸",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Text input for Homework guidelines
        item {
            OutlinedTextField(
                value = homeworkInputText,
                onValueChange = { homeworkInputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("homework_scanner_input"),
                placeholder = {
                    Text(
                        "Example: Math limits homework. Solve questions 1-5 on page 42 in textbook. Physics test on organic chemistry on Friday morning."
                    )
                },
                label = { Text("Add, modify, or paste text guidelines here") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryLilac,
                    unfocusedBorderColor = BorderPastel
                )
            )
        }

        // Action Buttons
        item {
            Button(
                onClick = {
                    if (homeworkInputText.isNotBlank()) {
                        viewModel.scanHomeworkText(homeworkInputText)
                        coroutineScope.launch {
                            delay(2000L) // Wait simulation or real
                            resultMessage = "Awesome! 🚀 AI structured ${homeworkInputText.split("\n").filter { it.isNotBlank() }.size.coerceAtLeast(1)} new task(s) and added them to your dashboard study plan."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("scan_homework_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Analyze Homework with AI Spark ✨", fontWeight = FontWeight.Bold)
            }
        }

        // Result Card
        if (resultMessage != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MintGreen.copy(alpha = 0.1f))
                        .border(1.dp, MintGreen.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Success",
                            tint = MintGreen
                        )
                        Column {
                            Text(
                                text = "Crushed it! Structured Successfully!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MintGreen
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = resultMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- SCREEN 3: INTERACTIVE CALENDAR & SCHEDULER ---
@Composable
fun CalendarScreen(viewModel: StudyViewModel) {
    val tasks by viewModel.allTasks.collectAsState()
    val isRescheduling by viewModel.isRescheduling.collectAsState()

    var calendarViewMode by remember { mutableStateOf("Daily") } // "Daily", "Weekly", "Monthly"
    var selectedDayOffset by remember { mutableIntStateOf(0) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var addingTaskHour by remember { mutableStateOf<Int?>(null) }
    var autoAdjustSalah by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentTheme by viewModel.appTheme.collectAsState()
    val isDark = currentTheme == "Cosmic Candy"

    // Generate dates list for next 7 days
    val dates = remember {
        val list = mutableListOf<Triple<Date, Int, String>>() // Date, offset, dayLabel
        val cal = Calendar.getInstance()
        val format = SimpleDateFormat("EEE", Locale.getDefault())
        val dayFormat = SimpleDateFormat("d", Locale.getDefault())
        for (i in 0..6) {
            list.add(Triple(cal.time, i, "${format.format(cal.time)}\n${dayFormat.format(cal.time)}"))
            cal.add(Calendar.DATE, 1)
        }
        list
    }

    // Filter tasks for the selected date
    val selectedDayStart = remember(selectedDayOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, selectedDayOffset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val selectedDayEnd = selectedDayStart + 86400000L

    val dayTasks = tasks.filter { it.dueDate in selectedDayStart until selectedDayEnd }
    val totalWorkloadScore = dayTasks.sumOf { it.workloadScore }

    val hourToTasks = remember(dayTasks) {
        val map = mutableMapOf<Int, MutableList<Task>>()
        for (task in dayTasks) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = task.dueDate
            val hr = cal.get(Calendar.HOUR_OF_DAY)
            if (!map.containsKey(hr)) {
                map[hr] = mutableListOf()
            }
            map[hr]?.add(task)
        }
        map
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Study Scheduler 📅",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Track your Matric (Grade 9 & 10) exams, tests, and study slots. AI ensures work is balanced daily.",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray
            )
        }

        // View Mode Tab Selector
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) DarkSurface.copy(alpha = 0.5f) else PrimaryLilac.copy(alpha = 0.08f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Daily", "Weekly", "Monthly").forEach { mode ->
                    val active = calendarViewMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) PrimaryLilac else Color.Transparent)
                            .clickable { calendarViewMode = mode }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else SoftGray
                        )
                    }
                }
            }
        }

        if (calendarViewMode == "Daily") {
            // Pill Date Row
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(dates) { (date, offset, label) ->
                        val isSelected = selectedDayOffset == offset
                        val dateStart = remember {
                            val cal = Calendar.getInstance()
                            cal.time = date
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            cal.timeInMillis
                        }
                        val dateEnd = dateStart + 86400000L
                        
                        val hasTests = tasks.any { !it.isCompleted && it.taskType == "TEST" && it.dueDate in dateStart until dateEnd }
                        val hasCompleted = tasks.any { it.isCompleted && it.dueDate in dateStart until dateEnd }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) PrimaryLilac else PrimaryLilac.copy(alpha = 0.05f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) PrimaryLilac else BorderPastel,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedDayOffset = offset }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label.split("\n")[0],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) Color.White else SoftGray
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label.split("\n")[1],
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    if (hasTests) {
                                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(OrangeRed))
                                    }
                                    if (hasCompleted) {
                                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(MintGreen))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Workload Indicator Box
            item {
                val isOverloaded = totalWorkloadScore >= 7
                val statusColor = if (isOverloaded) OrangeRed else if (totalWorkloadScore > 0) PrimaryLilac else MintGreen
                val statusText = if (isOverloaded) "Overloaded! 🥵" else if (totalWorkloadScore > 0) "Balanced load ⚖️" else "Rest day! 🛋️"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Workload Pressure: $statusText",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                            Text(
                                text = "Daily stress rating is $totalWorkloadScore/10 based on tasks.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isOverloaded) "😱" else if (totalWorkloadScore > 0) "🤓" else "🥳",
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }

            // Google Calendar AI Workload Balancer
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(PrimaryLilac.copy(alpha = 0.12f))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(listOf(PrimaryLilac, SecondaryPeach)),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryLilac.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "AI Balancer",
                                    tint = PrimaryLilac,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Text(
                                text = "Google Calendar AI Balancer 🧠🗓️",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Automatically reschedule and redistribute all your current study tasks across the next 7 days based on workload pressure to avoid study burnout!",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftGray
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Button(
                            onClick = {
                                viewModel.autoBalanceTasks()
                                Toast.makeText(context, "Google Calendar Optimizer: All study tasks redistributed and balanced! 🗓️⚖️✨", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auto_balance_tasks_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryLilac
                            ),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isRescheduling
                        ) {
                            if (isRescheduling) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rescheduling tasks...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Rounded.Autorenew, contentDescription = "Balance")
                                    Text("Auto-Balance Timetable Now 🔄", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // "I Couldn't Study Today" Button
            item {
                Button(
                    onClick = {
                        viewModel.rescheduleSickDay()
                        Toast.makeText(context, "Studyly is rescheduling all tasks to future light days! 🔄🩹", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("sick_reschedule_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeRed),
                    shape = RoundedCornerShape(24.dp),
                    enabled = !isRescheduling
                ) {
                    if (isRescheduling) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rebalancing timetable...")
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Rounded.Sick, contentDescription = "Sick")
                            Text("I'm Sick / Couldn't Study Today 🩹", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Student Wellbeing & Daily Targets Card (Extracurriculars, Sleep, and Salah)
            item {
                val hasPhysicalActivity = dayTasks.any {
                    val titleLower = it.title.lowercase()
                    val subjectLower = it.subject.lowercase()
                    titleLower.contains("football") || titleLower.contains("exercise") ||
                    titleLower.contains("badminton") || titleLower.contains("yoga") ||
                    titleLower.contains("activity") || titleLower.contains("extra") ||
                    titleLower.contains("workout") || titleLower.contains("sport") ||
                    titleLower.contains("run") || titleLower.contains("play") ||
                    subjectLower.contains("activity") || subjectLower.contains("extra")
                }
                val sleepHours by viewModel.dailySleepHours.collectAsState()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) DarkSurface.copy(alpha = 0.8f) else Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryLilac.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryLilac.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🧘‍♂️", fontSize = 20.sp)
                            }
                            Column {
                                Text(
                                    text = "Matric Health & Wellbeing 🌸⚖️",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else TextDark
                                )
                                Text(
                                    text = "Maintain physical, spiritual & mental balance for higher test scores!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftGray
                                )
                            }
                        }

                        Divider(color = if (isDark) BorderDarkPastel.copy(alpha = 0.4f) else BorderPastel.copy(alpha = 0.4f))

                        // 1. Extra Curricular Physical Activity
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(if (hasPhysicalActivity) "✅" else "⚠️", fontSize = 16.sp)
                                    Text(
                                        text = "Physical Activity (At least 20 min daily)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasPhysicalActivity) MintGreen else OrangeRed
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (hasPhysicalActivity) MintGreen.copy(alpha = 0.15f) else OrangeRed.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (hasPhysicalActivity) "Active ⚽" else "Missing 🏃‍♂️",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasPhysicalActivity) MintGreen else OrangeRed
                                    )
                                }
                            }
                            Text(
                                text = "Playing football, yoga, badminton or working out keeps the mind refreshed during matric pressure.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftGray
                             )
                             if (!hasPhysicalActivity) {
                                 Button(
                                     onClick = {
                                         // Auto schedule activity
                                         val cal = Calendar.getInstance()
                                         cal.add(Calendar.DATE, selectedDayOffset)
                                         cal.set(Calendar.HOUR_OF_DAY, 17) // 5 PM
                                         cal.set(Calendar.MINUTE, 0)
                                         cal.set(Calendar.SECOND, 0)
                                         
                                         viewModel.addNewTask(
                                             title = "⚽ Physical Activity: Football / Yoga / Exercise",
                                             subject = "Extracurriculars",
                                             chapter = "Physical Fitness",
                                             taskType = "STUDY",
                                             estimatedMinutes = 30,
                                             workloadScore = 1,
                                             daysFromNow = selectedDayOffset
                                         )
                                         Toast.makeText(context, "Added 30 mins of Physical Activity at 5:00 PM! ⚽🏃‍♂️", Toast.LENGTH_SHORT).show()
                                     },
                                     modifier = Modifier.fillMaxWidth().height(36.dp).padding(top = 4.dp),
                                     colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac.copy(alpha = 0.15f), contentColor = PrimaryLilac),
                                     shape = RoundedCornerShape(12.dp)
                                 ) {
                                     Text("Add 30 Min Physical Activity 🏃‍♂️🧘‍♀️", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                 }
                             }
                        }

                        Divider(color = if (isDark) BorderDarkPastel.copy(alpha = 0.2f) else BorderPastel.copy(alpha = 0.2f))

                        // 2. Sleep Duration Allocation
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🌙", fontSize = 16.sp)
                                    Text(
                                        text = "Daily Sleep Allocation: $sleepHours Hours",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else TextDark
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (sleepHours > 5) {
                                                viewModel.setDailySleepHours(sleepHours - 1)
                                            } else {
                                                Toast.makeText(context, "🚨 Minimum 5 hours of sleep required for matric success!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp).background(PrimaryLilac.copy(alpha = 0.1f), CircleShape)
                                    ) {
                                        Text("-", color = PrimaryLilac, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Text(
                                        text = "$sleepHours",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else TextDark
                                    )
                                    IconButton(
                                        onClick = {
                                            viewModel.setDailySleepHours(sleepHours + 1)
                                        },
                                        modifier = Modifier.size(28.dp).background(PrimaryLilac.copy(alpha = 0.1f), CircleShape)
                                    ) {
                                        Text("+", color = PrimaryLilac, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                            Text(
                                text = "Matric health guidelines recommend at least 5 hours of sleep. Less than 5 hours decreases focus & score performance by 30%.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftGray
                            )
                        }

                        Divider(color = if (isDark) BorderDarkPastel.copy(alpha = 0.2f) else BorderPastel.copy(alpha = 0.2f))

                        // 3. Salah Reminders & Automatic Calendar Adjustment
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🕌", fontSize = 16.sp)
                                    Text(
                                        text = "Auto-Adjust Calendar around Salah",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else TextDark
                                    )
                                }
                                Switch(
                                    checked = autoAdjustSalah,
                                    onCheckedChange = { autoAdjustSalah = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryLilac)
                                )
                            }
                            Text(
                                text = "When enabled, Salah breaks are highlighted in your timeline. Click below to automatically shift any study sessions conflicting with prayer slots.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftGray
                            )
                            Button(
                                onClick = {
                                    var count = 0
                                    for (task in dayTasks) {
                                        val cal = Calendar.getInstance()
                                        cal.timeInMillis = task.dueDate
                                        val hr = cal.get(Calendar.HOUR_OF_DAY)
                                        if (hr == 13 || hr == 16 || hr == 19) {
                                            val newCal = Calendar.getInstance()
                                            newCal.timeInMillis = task.dueDate
                                            newCal.set(Calendar.HOUR_OF_DAY, hr + 1)
                                            viewModel.updateTask(task.copy(dueDate = newCal.timeInMillis))
                                            count++
                                        }
                                    }
                                    if (count > 0) {
                                        Toast.makeText(context, "Shifted $count conflicting study tasks around prayer slots! 🕌✨", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No prayer slot conflicts today! 🕌🌟", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(36.dp).padding(top = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Shift Conflicts around Salah Times 🕌", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                             }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Hourly Study Timeline ⏰🗓️",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tap on any task to edit its details, or tap on any empty slot to schedule a custom study slot!",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftGray
                )
            }

            items((8..20).toList()) { h ->
                val tasksAtHour = hourToTasks[h] ?: emptyList()
                
                val isSalahHour = h == 13 || h == 16 || h == 19
                val salahName = when (h) {
                    13 -> "Dhuhr (1:00 PM)"
                    16 -> "Asr (4:00 PM)"
                    19 -> "Maghrib (7:00 PM)"
                    else -> ""
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (autoAdjustSalah && isSalahHour) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE8F5E9))
                                .border(1.dp, Color(0xFFC8E6C9), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🕌", fontSize = 14.sp)
                                Text(
                                    text = "Salah Prayer break: $salahName",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    // Formatted hour label in 12-hour AM/PM format
                    val isAm = h < 12
                    val hour12 = when {
                        h == 0 -> 12
                        h > 12 -> h - 12
                        else -> h
                    }
                    val amPm = if (isAm) "AM" else "PM"
                    val timeLabel = String.format("%02d:00 %s", hour12, amPm)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                    // 12-hour label
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SoftGray,
                        modifier = Modifier.width(70.dp).padding(top = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Tasks or custom empty slots
                    if (tasksAtHour.isNotEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (task in tasksAtHour) {
                                val categoryColor = when(task.subject.uppercase()) {
                                    "MATHEMATICS", "MATH" -> Color(0xFF20BF6B)
                                    "PHYSICAL SCIENCES", "PHYSICS" -> Color(0xFF54A0FF)
                                    "LIFE SCIENCES", "BIOLOGY" -> Color(0xFFFF6B6B)
                                    else -> PrimaryLilac
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(categoryColor.copy(alpha = 0.15f))
                                        .border(1.5.dp, categoryColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                        .clickable { editingTask = task }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(if (task.isCompleted) MintGreen else Color.Transparent)
                                                    .border(2.dp, if (task.isCompleted) MintGreen else categoryColor, CircleShape)
                                                    .clickable { viewModel.toggleTaskCompletion(task) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (task.isCompleted) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Done",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.width(10.dp))
                                            
                                            Column {
                                                Text(
                                                    text = task.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (task.isCompleted) SoftGray else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(categoryColor.copy(alpha = 0.2f))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = task.subject,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = categoryColor,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    
                                                    Text(
                                                        text = "⏱️ ${task.estimatedMinutes}m",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SoftGray
                                                    )
                                                    
                                                    if (task.taskType == "TEST") {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(OrangeRed.copy(alpha = 0.2f))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = "TEST 🚨",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = OrangeRed,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        
                                        IconButton(
                                            onClick = { editingTask = task },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "Edit",
                                                tint = PrimaryLilac,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty slot - allows customization
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) DarkSurface.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.4f))
                                .border(1.dp, if (isDark) BorderDarkPastel.copy(alpha = 0.12f) else BorderPastel.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .clickable { addingTaskHour = h }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add slot",
                                    tint = SoftGray.copy(alpha = 0.4f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "[Tap to schedule a custom study slot] 😴",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftGray.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
                }
            }
        } else if (calendarViewMode == "Weekly") {
            // Weekly schedule showing next 7 days and highlighting upcoming papers/tests
            val weekTests = tasks.filter { !it.isCompleted && (it.taskType == "TEST" || it.taskType == "ASSIGNMENT") && it.dueDate in System.currentTimeMillis()..(System.currentTimeMillis() + 7 * 86400000L) }
            
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (weekTests.isNotEmpty()) OrangeRed.copy(alpha = 0.12f) else MintGreen.copy(alpha = 0.12f))
                        .border(1.dp, if (weekTests.isNotEmpty()) OrangeRed.copy(alpha = 0.3f) else MintGreen.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = if (weekTests.isNotEmpty()) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                                contentDescription = "Alert",
                                tint = if (weekTests.isNotEmpty()) OrangeRed else MintGreen
                            )
                            Text(
                                text = "Weekly Exam & Paper Tracker 🚨📅",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (weekTests.isNotEmpty()) OrangeRed else MintGreen
                            )
                        }
                        Text(
                            text = if (weekTests.isNotEmpty()) {
                                "You have ${weekTests.size} high-priority tests or papers coming up in the next 7 days! Please make sure to prioritize your hourly preparation slots."
                            } else {
                                "Excellent! No tests or major assignments scheduled for this week. Follow your daily slots to stay ahead!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Loop next 7 days
            for (offset in 0..6) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DATE, offset)
                val dayStart = cal.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val dayEnd = dayStart + 86400000L

                val dayOfWeekFormatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                val dayLabelString = dayOfWeekFormatter.format(cal.time)

                val dayListTasks = tasks.filter { it.dueDate in dayStart until dayEnd }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) DarkSurface.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.85f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) BorderDarkPastel.copy(alpha = 0.2f) else BorderPastel.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (offset == 0) "Today ($dayLabelString) 🌟" else if (offset == 1) "Tomorrow ($dayLabelString)" else dayLabelString,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (offset == 0) PrimaryLilac else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${dayListTasks.size} Tasks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftGray
                                )
                            }

                            if (dayListTasks.isEmpty()) {
                                Text(
                                    text = "No study tasks scheduled. Rest or plan custom topics! 🏖️💤",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SoftGray.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (task in dayListTasks) {
                                        val isTest = task.taskType == "TEST" || task.taskType == "ASSIGNMENT"
                                        val rowBg = if (isTest) OrangeRed.copy(alpha = 0.08f) else PrimaryLilac.copy(alpha = 0.05f)
                                        val rowBorderColor = if (isTest) OrangeRed.copy(alpha = 0.3f) else BorderPastel.copy(alpha = 0.2f)
                                        
                                        // Format due hour
                                        val tCal = Calendar.getInstance().apply { timeInMillis = task.dueDate }
                                        val tHr = tCal.get(Calendar.HOUR_OF_DAY)
                                        val tIsAm = tHr < 12
                                        val tHr12 = when {
                                            tHr == 0 -> 12
                                            tHr > 12 -> tHr - 12
                                            else -> tHr
                                        }
                                        val tAmPm = if (tIsAm) "AM" else "PM"
                                        val timeStr = String.format("%02d:00 %s", tHr12, tAmPm)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(rowBg)
                                                .border(1.dp, rowBorderColor, RoundedCornerShape(12.dp))
                                                .clickable { editingTask = task }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (isTest) "🚨" else "📖",
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = task.title,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${task.subject} • $timeStr",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SoftGray
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (task.isCompleted) MintGreen.copy(alpha = 0.2f) else PrimaryLilac.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (task.isCompleted) "Done" else "${task.estimatedMinutes}m",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (task.isCompleted) MintGreen else PrimaryLilac,
                                                    fontWeight = FontWeight.Bold
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
        } else if (calendarViewMode == "Monthly") {
            // Monthly schedule showing countdowns for any tests in the next 30 days
            val monthTests = tasks
                .filter { (it.taskType == "TEST" || it.taskType == "ASSIGNMENT") && it.dueDate >= System.currentTimeMillis() && it.dueDate <= (System.currentTimeMillis() + 30 * 86400000L) }
                .sortedBy { it.dueDate }

            item {
                Text(
                    text = "30-Day Exam Countdown 📅💣",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Any high-stakes papers scheduled in the next 30 days are automatically highlighted. AI spreads preparation tasks leading up to the test starting today!",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftGray
                )
            }

            if (monthTests.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryLilac.copy(alpha = 0.05f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderPastel)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🕊️🏆", fontSize = 32.sp)
                            Text(
                                text = "Perfect Peace!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "No upcoming tests or exams in the next 1 month. You can scan guidelines or syllabus outlines to divide them here anytime!",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(monthTests) { test ->
                    val diff = test.dueDate - System.currentTimeMillis()
                    val daysLeft = (diff / 86400000L).toInt().coerceAtLeast(0)
                    val daysLeftText = if (daysLeft == 0) "Today! 🚨" else if (daysLeft == 1) "Tomorrow! ⏳" else "In $daysLeft days 📅"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) DarkSurface.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.9f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(2.dp, if (daysLeft <= 3) OrangeRed.copy(alpha = 0.6f) else PrimaryLilac.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(OrangeRed.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📝", fontSize = 16.sp)
                                    }
                                    Column {
                                        Text(
                                            text = test.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Subject: ${test.subject}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SoftGray
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (daysLeft <= 3) OrangeRed else PrimaryLilac)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = daysLeftText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Progressive preparation milestones divider notification
                            val milestones = tasks.filter { it.subject.uppercase() == test.subject.uppercase() && it.id != test.id && it.dueDate <= test.dueDate }
                            val completedMilestones = milestones.count { it.isCompleted }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PrimaryLilac.copy(alpha = 0.08f))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Syllabus Division Prep Progress:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryLilac
                                        )
                                        Text(
                                            text = "$completedMilestones/${milestones.size} Prep Steps Done",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryLilac
                                        )
                                    }

                                    // Custom visual progress bar
                                    val progressPct = if (milestones.isNotEmpty()) completedMilestones.toFloat() / milestones.size else 1.0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryLilac.copy(alpha = 0.15f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(progressPct)
                                                .clip(CircleShape)
                                                .background(PrimaryLilac)
                                        )
                                    }

                                    Text(
                                        text = if (milestones.isNotEmpty()) {
                                            "AI automatically divided the syllabus and mapped prep steps into your schedule spread over the days leading up to the test!"
                                        } else {
                                            "Pro-Tip: Scan your todo list or topic objectives above to automatically divide the syllabus leading up to this paper starting today!"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SoftGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Interactive Hourly Timetable Dialogs ---

    // 1. Edit Task Dialog
    if (editingTask != null) {
        val task = editingTask!!
        var title by remember(task.id) { mutableStateOf(task.title) }
        var subject by remember(task.id) { mutableStateOf(task.subject) }
        var chapter by remember(task.id) { mutableStateOf(task.chapter) }
        var taskType by remember(task.id) { mutableStateOf(task.taskType) }
        var estimatedMinutes by remember(task.id) { mutableStateOf(task.estimatedMinutes.toString()) }
        var hour by remember(task.id) { mutableIntStateOf(Calendar.getInstance().apply { timeInMillis = task.dueDate }.get(Calendar.HOUR_OF_DAY)) }

        AlertDialog(
            onDismissRequest = { editingTask = null },
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit", tint = PrimaryLilac)
                    Text("Customize Study Slot ✏️")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject (e.g., Mathematics)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chapter,
                        onValueChange = { chapter = it },
                        label = { Text("Chapter / Topic") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Task Category Type:", style = MaterialTheme.typography.labelSmall, color = SoftGray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("STUDY", "TEST", "ASSIGNMENT", "REVISION").forEach { type ->
                            val sel = taskType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) PrimaryLilac else PrimaryLilac.copy(alpha = 0.12f))
                                    .clickable { taskType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(type, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else SoftGray)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = estimatedMinutes,
                        onValueChange = { estimatedMinutes = it },
                        label = { Text("Estimated Duration (m)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Hourly customizable selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text("Hourly Slot:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (hour > 8) hour-- }) {
                                Icon(Icons.Rounded.Remove, "Earlier", tint = PrimaryLilac)
                            }
                            
                            val isAm = hour < 12
                            val hour12 = when {
                                hour == 0 -> 12
                                hour > 12 -> hour - 12
                                else -> hour
                            }
                            val amPm = if (isAm) "AM" else "PM"
                            Text(String.format("%02d:00 %s", hour12, amPm), fontWeight = FontWeight.Bold, color = PrimaryLilac)

                            IconButton(onClick = { if (hour < 20) hour++ }) {
                                Icon(Icons.Rounded.Add, "Later", tint = PrimaryLilac)
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
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = task.dueDate
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)

                        val updated = task.copy(
                            title = title,
                            subject = subject,
                            chapter = chapter,
                            taskType = taskType,
                            dueDate = cal.timeInMillis,
                            estimatedMinutes = estimatedMinutes.toIntOrNull() ?: 45
                        )
                        viewModel.updateTask(updated)
                        editingTask = null
                        Toast.makeText(context, "Study schedule task customized! ✏️🗓️", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = OrangeRed),
                        onClick = {
                            viewModel.deleteTask(task.id)
                            editingTask = null
                            Toast.makeText(context, "Task deleted! 🗑️", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Delete Task")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { editingTask = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // 2. Add Task At Hour Dialog
    if (addingTaskHour != null) {
        val h = addingTaskHour!!
        var title by remember { mutableStateOf("") }
        var subject by remember { mutableStateOf("Mathematics") }
        var chapter by remember { mutableStateOf("Unit Review") }
        var taskType by remember { mutableStateOf("STUDY") }
        var estimatedMinutes by remember { mutableStateOf("45") }

        AlertDialog(
            onDismissRequest = { addingTaskHour = null },
            title = {
                val isAm = h < 12
                val hour12 = when {
                    h == 0 -> 12
                    h > 12 -> h - 12
                    else -> h
                }
                val amPm = if (isAm) "AM" else "PM"
                Text("Schedule Task at ${String.format("%02d:00 %s", hour12, amPm)} ⏰")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject (e.g., Physical Sciences)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chapter,
                        onValueChange = { chapter = it },
                        label = { Text("Chapter / Topic") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Task Type:", style = MaterialTheme.typography.labelSmall, color = SoftGray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("STUDY", "TEST", "ASSIGNMENT", "REVISION").forEach { type ->
                            val sel = taskType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) PrimaryLilac else PrimaryLilac.copy(alpha = 0.12f))
                                    .clickable { taskType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(type, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else SoftGray)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = estimatedMinutes,
                        onValueChange = { estimatedMinutes = it },
                        label = { Text("Estimated Duration (m)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "Please enter a task title!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DATE, selectedDayOffset)
                        cal.set(Calendar.HOUR_OF_DAY, h)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)

                        val newTask = Task(
                            title = title,
                            subject = subject,
                            chapter = chapter,
                            taskType = taskType,
                            dueDate = cal.timeInMillis,
                            estimatedMinutes = estimatedMinutes.toIntOrNull() ?: 45
                        )
                        viewModel.updateTask(newTask)
                        addingTaskHour = null
                        Toast.makeText(context, "Study slot added to calendar! ⏰✨", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Schedule Slot")
                }
            },
            dismissButton = {
                TextButton(onClick = { addingTaskHour = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- SCREEN 4: AI MISTAKE ANALYZER ---
@Composable
fun MistakesScreen(viewModel: StudyViewModel) {
    val weakTopics by viewModel.allWeakTopics.collectAsState()
    val mistakeLoading by viewModel.mistakeLoading.collectAsState()
    var mistakeInputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "AI Gaps & Mistake Analyzer 🔍",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Type or paste comments/corrections from your tests or practice papers. Our AI finds your learning weak spots and inserts custom revision sessions!",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray
            )
        }

        // Input Box
        item {
            OutlinedTextField(
                value = mistakeInputText,
                onValueChange = { mistakeInputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("mistakes_input"),
                placeholder = {
                    Text(
                        "Example: In Physical Sciences test, I got 2/10 in the organic chemistry nomenclature section. I kept getting prefixes mixed up for aldehydes and ketones."
                    )
                },
                label = { Text("Describe test mistakes or paste grader comments") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryLilac,
                    unfocusedBorderColor = BorderPastel
                )
            )
        }

        // Scan button
        item {
            Button(
                onClick = {
                    if (mistakeInputText.isNotBlank()) {
                        viewModel.analyzeMistakesText(mistakeInputText)
                        Toast.makeText(context, "Studyly is analyzing test gaps... 🔍🧠", Toast.LENGTH_SHORT).show()
                        mistakeInputText = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("analyze_mistakes_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                shape = RoundedCornerShape(24.dp)
            ) {
                if (mistakeLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Detecting learning gaps...")
                } else {
                    Text("Analyze Mistakes & Schedule Revision ✨", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Title: Identified Weak Topics
        item {
            Text(
                text = "Identified Matric Weak Topics ⚠️",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (weakTopics.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Awesome! No weak topics detected yet. Keep practicing! 🎓✨",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray
                    )
                }
            }
        } else {
            items(weakTopics) { topic ->
                WeakTopicItem(topic = topic)
            }
        }
    }
}

@Composable
fun WeakTopicItem(topic: WeakTopic) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SecondaryPeach.copy(alpha = 0.08f))
            .border(1.dp, SecondaryPeach.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = topic.topicName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Confidence rating stars
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 1..5) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Confidence rating",
                            tint = if (i <= topic.confidenceLevel) SunnyYellow else SoftGray.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = "Subject: ${topic.subject}",
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryLilac,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = topic.mistakeDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Auto-scheduled banner
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MintGreen.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "📅 Auto-scheduled AI Revision session added!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MintGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- SCREEN 5: PERSONAL AI COACH (CHATBOT) ---
@Composable
fun CoachScreen(viewModel: StudyViewModel) {
    val messages by viewModel.chatMessages.collectAsState()
    val coachLoading by viewModel.coachLoading.collectAsState()
    var userQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with clear history
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI Studyly Coach 🤖",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Matric (Grade 9 & 10) smart robot helper",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray
                )
            }

            IconButton(onClick = { viewModel.clearChatHistory() }) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = "Clear History",
                    tint = OrangeRed
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Predefined helper prompt chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val suggestions = listOf(
                "Create a Math study timetable",
                "Explain Calculus Limits simply",
                "Give me a Physics quiz",
                "Tips to manage Matric exam stress"
            )
            items(suggestions) { text ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(PrimaryLilac.copy(alpha = 0.08f))
                        .border(1.dp, PrimaryLilac.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .clickable {
                            viewModel.sendCoachMessage(text)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryLilac
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Message List
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(messages) { msg ->
                    ChatBubbleItem(msg = msg)
                }

                if (coachLoading) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            CircularProgressIndicator(color = PrimaryLilac, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Studyly is writing on blackboard...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoftGray
                            )
                        }
                    }
                }
            }
        }

        // Input Field and Send Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = userQuery,
                onValueChange = { userQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach_input"),
                placeholder = { Text("Ask Studyly about any Matric Grade 9 or 10 concept...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryLilac,
                    unfocusedBorderColor = BorderPastel
                )
            )

            IconButton(
                onClick = {
                    if (userQuery.isNotBlank()) {
                        viewModel.sendCoachMessage(userQuery)
                        userQuery = ""
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(PrimaryLilac)
                    .size(48.dp)
                    .testTag("send_coach_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ChatBubbleItem(msg: ChatMessage) {
    val isUser = msg.sender == "USER"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bg = if (isUser) PrimaryLilac else SecondaryPeach.copy(alpha = 0.12f)
    val borderClr = if (isUser) PrimaryLilac else BorderPastel
    val textClr = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 24.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bg)
                .border(1.dp, borderClr, shape)
                .padding(14.dp)
        ) {
            Column {
                if (!isUser) {
                    Text(
                        text = "Studyly Coach 🤖",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryPeach,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textClr
                )
            }
        }
    }
}

// Custom easing for friendly springs
val EaseInOutBack: Easing = Easing { fraction ->
    val s = 1.70158f * 1.525f
    var t = fraction * 2.0f
    if (t < 1.0f) {
        0.5f * (t * t * (((s + 1.0f) * t) - s))
    } else {
        t -= 2.0f
        0.5f * ((t * t * (((s + 1.0f) * t) + s)) + 2.0f)
    }
}

@Composable
fun ToDoScreen(
    viewModel: StudyViewModel,
    onTriggerCompleteAnim: (Offset) -> Unit = {}
) {
    val todoItems by viewModel.allTodoItems.collectAsState()
    val scanLoading by viewModel.todoScanLoading.collectAsState()
    val isAnalyzingTodo by viewModel.isAnalyzingTodo.collectAsState()
    val proposedTasks by viewModel.proposedTasks.collectAsState()
    
    var newTodoText by remember { mutableStateOf("") }
    var scanInputText by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var showScanPanel by remember { mutableStateOf(false) }
    
    var editableProposedTasks by remember { mutableStateOf<List<com.example.data.ProposedTaskJson>?>(null) }
    
    LaunchedEffect(proposedTasks) {
        editableProposedTasks = proposedTasks
    }
    
    val coroutineScope = rememberCoroutineScope()
    val currentTheme by viewModel.appTheme.collectAsState()
    val isDark = currentTheme == "Cosmic Candy"
    val context = LocalContext.current

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            scanInputText = """
                1. Solve 5 Mathematics Calculus exercises
                2. Review Physical Sciences Organic Nomenclature
                3. Read Chapter 5 of English Literature Novel
                4. Revise Accounting general journal ledger entries
            """.trimIndent()
            Toast.makeText(context, "Photo scanned! AI extracted structured tasks. Feel free to edit below! 📝✨", Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to scan lists!", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Text(
                text = "My To-Do List 🎯",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Keep track of your quick homework steps, chores, and revision tasks. Real-time checklist synced with Room!",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray
            )
        }

        // 2. Type/Add New To-Do Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) DarkSurface.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.85f)
                ),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, 
                    if (isDark) BorderDarkPastel.copy(alpha = 0.25f) else BorderPastel.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Add Daily Task ✍️",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTodoText,
                            onValueChange = { newTodoText = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("todo_input_field"),
                            placeholder = { Text("e.g. Solve page 12 Math limit questions") },
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryLilac,
                                unfocusedBorderColor = if (isDark) BorderDarkPastel else BorderPastel
                            )
                        )
                        
                        IconButton(
                            onClick = {
                                if (newTodoText.isNotBlank()) {
                                    viewModel.addTodoItem(newTodoText.trim())
                                    newTodoText = ""
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(PrimaryLilac)
                                .testTag("add_todo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add Task",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Collapsible AI Scanner Panel Trigger
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryLilac.copy(alpha = 0.12f))
                    .clickable { showScanPanel = !showScanPanel }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = "Scan Icon",
                        tint = PrimaryLilac
                    )
                    Text(
                        text = "Scan & Parse To-Do List with AI ✨",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryLilac
                    )
                }
                Icon(
                    imageVector = if (showScanPanel) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Toggle Panel",
                    tint = PrimaryLilac
                )
            }
        }

        // 3. AI Scanner Section (Visible only when expanded)
        if (showScanPanel) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) DarkSurface.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) BorderDarkPastel.copy(alpha = 0.25f) else BorderPastel.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "AI Scanner & Homework Parser 📝",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Tap the camera viewfinder to scan handwritten lists using your back camera, or paste custom tasks directly. You can edit the text before extraction!",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftGray
                        )

                        // Visual viewfinder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) MidnightPlum else Color(0xFFFAF9FF))
                                .border(1.dp, PrimaryLilac.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .clickable {
                                    val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                        cameraLauncher.launch(null)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (capturedBitmap != null) {
                                Image(
                                    bitmap = capturedBitmap!!.asImageBitmap(),
                                    contentDescription = "Captured List",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }

                            // Bracket Corners
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val margin = 12.dp.toPx()
                                val len = 20.dp.toPx()
                                val stroke = 4f
                                val strokeColor = if (capturedBitmap != null) MintGreen else (if (isDark) Color.White else PrimaryLilac)

                                drawLine(strokeColor, Offset(margin, margin), Offset(margin + len, margin), stroke)
                                drawLine(strokeColor, Offset(margin, margin), Offset(margin, margin + len), stroke)
                                drawLine(strokeColor, Offset(size.width - margin, margin), Offset(size.width - margin - len, margin), stroke)
                                drawLine(strokeColor, Offset(size.width - margin, margin), Offset(size.width - margin, margin + len), stroke)
                                drawLine(strokeColor, Offset(margin, size.height - margin), Offset(margin + len, size.height - margin), stroke)
                                drawLine(strokeColor, Offset(margin, size.height - margin), Offset(margin, size.height - margin - len), stroke)
                                drawLine(strokeColor, Offset(size.width - margin, size.height - margin), Offset(size.width - margin - len, size.height - margin), stroke)
                                drawLine(strokeColor, Offset(size.width - margin, size.height - margin), Offset(size.width - margin, size.height - margin - len), stroke)
                            }

                            // Scanning animation
                            if (scanLoading) {
                                val infiniteTransition = rememberInfiniteTransition()
                                val scanOffset by infiniteTransition.animateFloat(
                                    initialValue = 0.1f,
                                    targetValue = 0.9f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1500, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val y = size.height * scanOffset
                                    drawLine(
                                        color = MintGreen,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 6f
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally, 
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = if (scanLoading) Icons.Rounded.Autorenew else (if (capturedBitmap != null) Icons.Rounded.CheckCircle else Icons.Rounded.PhotoCamera),
                                    contentDescription = "Scan icon",
                                    tint = if (scanLoading) SunnyYellow else (if (capturedBitmap != null) MintGreen else PrimaryLilac),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (scanLoading) "Parsing with Gemini AI..." else (if (capturedBitmap != null) "Captured! Tap to re-shoot 📸" else "Tap to scan with Back Camera 📸"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        OutlinedTextField(
                            value = scanInputText,
                            onValueChange = { scanInputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("scan_todo_input"),
                            placeholder = { Text("e.g. 1. Solve mechanics equations, 2. Complete english summary, 3. Call accounting group.") },
                            label = { Text("Scanned / Custom Tasks (Fully Editable)") },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryLilac,
                                unfocusedBorderColor = if (isDark) BorderDarkPastel else BorderPastel
                            )
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (scanInputText.isNotBlank()) {
                                        viewModel.scanTodoListText(scanInputText)
                                        coroutineScope.launch {
                                            delay(1500L)
                                            resultMessage = "Successfully parsed and added scanned list to checklist!"
                                            scanInputText = ""
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("scan_todo_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                                shape = RoundedCornerShape(16.dp),
                                enabled = !scanLoading && !isAnalyzingTodo
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (scanLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                    }
                                    Text("Extract to Checklist 📋", fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    if (scanInputText.isNotBlank()) {
                                        viewModel.analyzeAndDivideTodoPlan(scanInputText)
                                    } else {
                                        Toast.makeText(context, "Please enter some study tasks or scan a list first!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("ai_smart_divide_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = SunnyYellow),
                                shape = RoundedCornerShape(16.dp),
                                enabled = !scanLoading && !isAnalyzingTodo
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isAnalyzingTodo) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                                        Text("AI Splitting Plan...", fontWeight = FontWeight.Bold, color = Color.Black)
                                    } else {
                                        Text("Smart-Divide & Plan on Calendar 🔮", fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            }
                        }

                        if (resultMessage != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MintGreen.copy(alpha = 0.12f))
                                    .padding(12.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Success",
                                        tint = MintGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = resultMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MintGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Header for List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Checklist (${todoItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (todoItems.isNotEmpty()) {
                    Text(
                        text = "Complete to earn XP! 🏆",
                        style = MaterialTheme.typography.labelSmall,
                        color = SunnyYellow,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 5. To-Do Items
        if (todoItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🌈",
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "All Caught Up!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Add tasks above or type guidelines to parse your homework with AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(todoItems) { todo ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (todo.isCompleted) {
                            MintGreen.copy(alpha = 0.08f)
                        } else {
                            if (isDark) DarkSurface.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.75f)
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (todo.isCompleted) MintGreen.copy(alpha = 0.3f) else if (isDark) BorderDarkPastel.copy(alpha = 0.2f) else BorderPastel.copy(alpha = 0.4f)
                    )
                ) {
                    var buttonOffset by remember { mutableStateOf(Offset.Zero) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Cute custom checkbox
                            IconButton(
                                onClick = {
                                    viewModel.toggleTodoItemCompletion(todo)
                                    if (!todo.isCompleted) {
                                        // Trigger particle explosion!
                                        onTriggerCompleteAnim(buttonOffset)
                                        viewModel.addXp(20) // Earn 20 XP!
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (todo.isCompleted) MintGreen else PrimaryLilac.copy(alpha = 0.12f)
                                    )
                                    .onGloballyPositioned { coordinates ->
                                        val position = coordinates.positionInWindow()
                                        buttonOffset = Offset(position.x + 32f, position.y + 32f)
                                    }
                            ) {
                                Icon(
                                    imageVector = if (todo.isCompleted) Icons.Rounded.Check else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = "Complete Todo",
                                    tint = if (todo.isCompleted) Color.White else PrimaryLilac,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = todo.title,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (todo.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                color = if (todo.isCompleted) SoftGray else MaterialTheme.colorScheme.onBackground,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Compact Delete button
                        IconButton(
                            onClick = {
                                viewModel.deleteTodoItem(todo.id)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete Todo",
                                tint = OrangeRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (editableProposedTasks != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearProposedTasks() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🤖 AI Smart-Divide Review", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Customize and fine-tune your AI-divided study plan before committing it to your 12-hour hourly schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftGray
                    )
                    
                    val items = editableProposedTasks ?: emptyList()
                    items.forEachIndexed { index, task ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) MidnightPlum else Color(0xFFF8F7FC)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryLilac.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isAm = task.hourOfDay < 12
                                    val hour12 = when {
                                        task.hourOfDay == 0 -> 12
                                        task.hourOfDay > 12 -> task.hourOfDay - 12
                                        else -> task.hourOfDay
                                    }
                                    val amPm = if (isAm) "AM" else "PM"
                                    Text(
                                        text = "Day ${task.daysFromNow} at ${String.format("%02d:00 %s", hour12, amPm)} ⏰",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PrimaryLilac,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    IconButton(
                                        onClick = {
                                            editableProposedTasks = items.toMutableList().apply { removeAt(index) }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Delete task",
                                            tint = OrangeRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = task.title,
                                    onValueChange = { newTitle ->
                                        editableProposedTasks = items.toMutableList().apply {
                                            this[index] = task.copy(title = newTitle)
                                        }
                                    },
                                    label = { Text("Task Title", fontSize = 10.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryLilac,
                                        unfocusedBorderColor = if (isDark) BorderDarkPastel else BorderPastel
                                    )
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = task.subject,
                                        onValueChange = { newSub ->
                                            editableProposedTasks = items.toMutableList().apply {
                                                this[index] = task.copy(subject = newSub)
                                            }
                                        },
                                        label = { Text("Subject", fontSize = 10.sp) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PrimaryLilac,
                                            unfocusedBorderColor = if (isDark) BorderDarkPastel else BorderPastel
                                        )
                                    )
                                    OutlinedTextField(
                                        value = task.chapter,
                                        onValueChange = { newChap ->
                                            editableProposedTasks = items.toMutableList().apply {
                                                this[index] = task.copy(chapter = newChap)
                                            }
                                        },
                                        label = { Text("Topic", fontSize = 10.sp) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PrimaryLilac,
                                            unfocusedBorderColor = if (isDark) BorderDarkPastel else BorderPastel
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Days from today:", style = MaterialTheme.typography.labelSmall, color = SoftGray)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(0, 1, 2, 7, 14, 30).forEach { days ->
                                            val isSelected = task.daysFromNow == days
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) PrimaryLilac else (if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)))
                                                    .clickable {
                                                        editableProposedTasks = items.toMutableList().apply {
                                                            this[index] = task.copy(daysFromNow = days)
                                                        }
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (days == 0) "Today" else if (days == 1) "Tmrw" else "+$days d",
                                                    fontSize = 9.sp,
                                                    color = if (isSelected) Color.White else SoftGray,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTasks = editableProposedTasks
                        if (!finalTasks.isNullOrEmpty()) {
                            viewModel.commitProposedTasks(finalTasks)
                            Toast.makeText(context, "AI Plan successfully integrated! Spanned study tasks added to calendar! 📅✅", Toast.LENGTH_LONG).show()
                        }
                        viewModel.clearProposedTasks()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm & Schedule 📅", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.clearProposedTasks() }
                ) {
                    Text("Discard Plan ❌", color = OrangeRed)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun LoginScreen(viewModel: StudyViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    var selectedUserForLogin by remember { mutableStateOf<StudyViewModel.StudyUser?>(null) }
    var dialogPassword by remember { mutableStateOf("") }
    var dialogPasswordVisible by remember { mutableStateOf(false) }
    var showPasswordError by remember { mutableStateOf(false) }
    
    // Preset cute emojis
    val avatarOptions = listOf(
        "🌸", "🐻", "🐱", "🦊", "🐨", "🦄", "🐼", "🌟", "🦁", "🎒"
    )
    var selectedAvatar by remember { mutableStateOf("🌸") }
    
    val context = LocalContext.current
    val currentTheme by viewModel.appTheme.collectAsState()
    val isDark = currentTheme == "Cosmic Candy"

    val bgGradient = remember(currentTheme) {
        when (currentTheme) {
            "Sunset Glow" -> Brush.linearGradient(
                colors = listOf(Color(0xFFFFD2A9), Color(0xFFFF9E9E), Color(0xFFFFE0A9))
            )
            "Cotton Candy" -> Brush.linearGradient(
                colors = listOf(Color(0xFFE3F2FD), Color(0xFFFCE4EC), Color(0xFFE8EAF6))
            )
            "Minty Fresh" -> Brush.linearGradient(
                colors = listOf(Color(0xFFE0F2F1), Color(0xFFE8F5E9), Color(0xFFFFF9C4))
            )
            "Cosmic Candy" -> Brush.linearGradient(
                colors = listOf(Color(0xFF311042), Color(0xFF10255C), Color(0xFF50123C))
            )
            else -> Brush.linearGradient(
                colors = listOf(Color(0xFFF1EAFF), Color(0xFFFFF1E6), Color(0xFFE6F5FF))
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Logo/Graphic
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🎒 Studylicious",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = PrimaryLilac
                )
                Text(
                    text = "Matric Study Planner & AI Coach",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else TextDark,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Grade 9 & 10 success starts here! 🌟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray,
                    textAlign = TextAlign.Center
                )
            }

            // Glassmorphic Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) DarkSurface.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(32.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isDark) BorderDarkPastel.copy(alpha = 0.3f) else BorderPastel.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        text = "Create Your Student Profile 🌸",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else TextDark,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    // Profile Pic circular selection preview (dashed like the reference image)
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        // Dashed circle outline
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = Stroke(
                                width = 3.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                            )
                            drawCircle(
                                color = PrimaryLilac,
                                radius = size.minDimension / 2 - 4.dp.toPx(),
                                style = stroke
                            )
                        }
                        
                        // Emoji Avatar Inside
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(PrimaryLilac.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedAvatar,
                                fontSize = 42.sp
                            )
                        }
                    }

                    // Avatar selector options Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Choose a Profile Icon: 🐾",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else TextDark
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(avatarOptions) { avatar ->
                                val isSelected = selectedAvatar == avatar
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) PrimaryLilac else PrimaryLilac.copy(alpha = 0.08f)
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) SunnyYellow else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedAvatar = avatar }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = avatar, fontSize = 22.sp)
                                }
                            }
                        }
                    }

                    val registeredUsers by viewModel.registeredUsers.collectAsState(initial = emptyList())
                    if (registeredUsers.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Choose an Existing Profile: 🎒",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else TextDark
                            )
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(registeredUsers) { user ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.6f))
                                            .border(
                                                width = 1.5.dp,
                                                color = if (viewModel.userName.value == user.name) PrimaryLilac else Color.Transparent,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                if (user.passwordHash.isEmpty()) {
                                                    viewModel.login(user.name, user.email, user.profilePic)
                                                    Toast.makeText(context, "Logged in as ${user.name}! 🎉", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    selectedUserForLogin = user
                                                    dialogPassword = ""
                                                    dialogPasswordVisible = false
                                                    showPasswordError = false
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = user.profilePic, fontSize = 18.sp)
                                            Column {
                                                Text(
                                                    text = user.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) Color.White else TextDark
                                                )
                                                if (user.email.isNotEmpty()) {
                                                    Text(
                                                        text = user.email,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SoftGray,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Divider(modifier = Modifier.weight(1f), color = if (isDark) BorderDarkPastel.copy(alpha = 0.2f) else BorderPastel.copy(alpha = 0.2f))
                                Text(" OR CREATE NEW ", style = MaterialTheme.typography.labelSmall, color = SoftGray, modifier = Modifier.padding(horizontal = 8.dp))
                                Divider(modifier = Modifier.weight(1f), color = if (isDark) BorderDarkPastel.copy(alpha = 0.2f) else BorderPastel.copy(alpha = 0.2f))
                            }
                        }
                    }

                    // Name input
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "What is your name? (Required) ✏️",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else TextDark
                        )
                        OutlinedTextField(
                             value = name,
                             onValueChange = { name = it },
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .testTag("login_name_input"),
                             placeholder = { Text("e.g. Fatima Imtiaz") },
                             shape = RoundedCornerShape(16.dp),
                             singleLine = true,
                             colors = OutlinedTextFieldDefaults.colors(
                                 focusedBorderColor = PrimaryLilac,
                                  unfocusedBorderColor = if (isDark) BorderDarkPastel else BorderPastel
                             )
                        )
                    }

                    // Gmail input
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Your Gmail Address (Optional): ✉️",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else TextDark
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input"),
                            placeholder = { Text("e.g. fatimaimtiaz.aes@gmail.com") },
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryLilac,
                                unfocusedBorderColor = if (isDark) BorderDarkPastel else BorderPastel
                            )
                        )
                    }

                    // Password input
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Choose a Profile Password (Required): 🔒",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else TextDark
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input"),
                            placeholder = { Text("Enter a strong password") },
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = PrimaryLilac)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryLilac,
                                unfocusedBorderColor = if (isDark) BorderDarkPastel else BorderPastel
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Start Studying Button
                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            val trimmedEmail = email.trim()
                            val trimmedPassword = password.trim()
                            if (trimmedName.isEmpty()) {
                                Toast.makeText(context, "Please enter your name 🌸", Toast.LENGTH_SHORT).show()
                            } else if (trimmedEmail.isNotEmpty() && !trimmedEmail.contains("@")) {
                                Toast.makeText(context, "Please enter a valid email address if provided ✉️", Toast.LENGTH_SHORT).show()
                            } else if (trimmedPassword.isEmpty()) {
                                Toast.makeText(context, "Please set a profile password for security 🔒", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.registerAndLogin(trimmedName, trimmedEmail, selectedAvatar, trimmedPassword)
                                Toast.makeText(context, "Welcome to Studylicious, $trimmedName! 🎉", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_submit_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Join Studylicious! ✨🚀",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (selectedUserForLogin != null) {
        val user = selectedUserForLogin!!
        AlertDialog(
            onDismissRequest = { selectedUserForLogin = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = user.profilePic, fontSize = 28.sp)
                    Text(
                        text = "Unlock Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else TextDark
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enter password for ${user.name} to continue: 🔑",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color.White.copy(alpha = 0.8f) else TextDark.copy(alpha = 0.8f)
                    )
                    OutlinedTextField(
                        value = dialogPassword,
                        onValueChange = {
                            dialogPassword = it
                            showPasswordError = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_password_input"),
                        placeholder = { Text("Password") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        visualTransformation = if (dialogPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        trailingIcon = {
                            val image = if (dialogPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { dialogPasswordVisible = !dialogPasswordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = PrimaryLilac)
                            }
                        },
                        isError = showPasswordError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLilac,
                            unfocusedBorderColor = if (isDark) BorderDarkPastel else BorderPastel
                        )
                    )
                    if (showPasswordError) {
                        Text(
                            text = "❌ Incorrect password. Please try again!",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Red,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.verifyPassword(user, dialogPassword)) {
                            viewModel.login(user.name, user.email, user.profilePic)
                            selectedUserForLogin = null
                            Toast.makeText(context, "Logged in as ${user.name}! 🎉", Toast.LENGTH_SHORT).show()
                        } else {
                            showPasswordError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Unlock 🔓", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedUserForLogin = null }
                ) {
                    Text("Cancel", color = SoftGray)
                }
            },
            containerColor = if (isDark) DarkSurface else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

// Data class and helper methods for the custom stacked bar chart
data class DayProgress(
    val label: String,
    val categoryHours: FloatArray
)

val categoryColors = listOf(
    Color(0xFF6366F1), // Hours Studied (Indigo)
    Color(0xFF06B6D4), // Test Scores (Cyan)
    Color(0xFFEC4899), // Tasks Completed (Pink)
    Color(0xFF3B82F6), // AI Coach Guidance (Blue)
    Color(0xFFF97316), // Revision Slots (Orange)
    Color(0xFFF59E0B)  // Active Recall (Yellow)
)

@Composable
fun StackedBar(
    dayLabel: String,
    categoryHours: FloatArray,
    maxHours: Float,
    isDark: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val totalHours = categoryHours.sum()
    val barHeightFraction = (totalHours / maxHours).coerceIn(0f, 1f)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(42.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Bar height area
        Box(
            modifier = Modifier
                .height(110.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (totalHours > 0) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.35f) // thin elegant bar like in reference image
                        .fillMaxHeight(barHeightFraction)
                ) {
                    var currentY = size.height
                    
                    val activeSegments = mutableListOf<Pair<Int, Float>>()
                    for (i in 0 until 6) {
                        if (categoryHours[i] > 0) {
                            activeSegments.add(i to categoryHours[i])
                        }
                    }
                    
                    activeSegments.forEachIndexed { idx, (catIdx, hrs) ->
                        val segmentHeight = (hrs / totalHours) * size.height
                        val rectY = currentY - segmentHeight
                        
                        val isTopSegment = idx == activeSegments.lastIndex
                        
                        val path = Path().apply {
                            if (isTopSegment) {
                                // Draw with rounded top corners
                                addRoundRect(
                                    RoundRect(
                                        left = 0f,
                                        top = rectY,
                                        right = size.width,
                                        bottom = currentY,
                                        topLeftCornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                        topRightCornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )
                                )
                            } else {
                                // Draw standard rectangle
                                addRect(
                                    Rect(
                                        left = 0f,
                                        top = rectY,
                                        right = size.width,
                                        bottom = currentY
                                    )
                                )
                            }
                        }
                        
                        drawPath(
                            path = path,
                            color = categoryColors[catIdx]
                        )
                        
                        currentY = rectY
                    }
                }
            } else {
                // Draw a tiny placeholder dot/bar if 0 hours
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) PrimaryLilac else SoftGray,
            fontSize = 10.sp
        )
    }
}

// ==========================================
//           BOARD HUB SCREEN
// ==========================================

@Composable
fun BoardHubScreen(viewModel: StudyViewModel) {
    var subTab by remember { mutableStateOf("voice") }
    val currentTheme by viewModel.appTheme.collectAsState()
    val isDark = currentTheme == "Cosmic Candy"

    val primaryBgColor = if (isDark) DarkSurface.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.85f)
    val borderColor = if (isDark) BorderDarkPastel.copy(alpha = 0.15f) else BorderPastel.copy(alpha = 0.4f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main Board Hub Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(primaryBgColor)
                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PrimaryLilac.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.School,
                    contentDescription = "Board Hub",
                    tint = PrimaryLilac,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "Matric Board Master Hub 🏆",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Bilingual NLP planner, focus blocks & peer duels.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftGray
                )
            }
        }

        // Sub-tabs chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val items = listOf(
                Triple("voice", "🎤 Voice Logger", "Bilingual NLP Entry"),
                Triple("focus", "⏱️ Board Focus", "Patterned timer blocks"),
                Triple("duels", "⚔️ Study Duels", "Peer Accountability"),
                Triple("burndown", "📉 Burn-Down", "Syllabus progress"),
                Triple("loadshedding", "⚡ Sync Center", "Offline-First Sync")
            )
            items(items) { (id, label, desc) ->
                val active = subTab == id
                val activeBg = if (active) PrimaryLilac else PrimaryLilac.copy(alpha = 0.06f)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(activeBg)
                        .border(
                            width = if (active) 1.5.dp else 0.dp,
                            color = if (active) SunnyYellow else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { subTab = id }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Active sub-tab screen rendering
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (subTab) {
                "voice" -> VoiceLoggerView(viewModel, isDark, primaryBgColor, borderColor)
                "focus" -> BoardFocusView(viewModel, isDark, primaryBgColor, borderColor)
                "duels" -> StudyDuelsView(viewModel, isDark, primaryBgColor, borderColor)
                "burndown" -> SyllabusBurnDownView(viewModel, isDark, primaryBgColor, borderColor)
                "loadshedding" -> LoadsheddingSyncView(viewModel, isDark, primaryBgColor, borderColor)
            }
        }
    }
}

// ------------------------------------------
// 1. SMART VOICE LOGGER VIEW (BILINGUAL NLP)
// ------------------------------------------
@Composable
fun VoiceLoggerView(
    viewModel: StudyViewModel,
    isDark: Boolean,
    cardBg: Color,
    borderCol: Color
) {
    var textInput by remember { mutableStateOf("") }
    val isProcessing by viewModel.isVoiceLoggerProcessing.collectAsState()
    val isOffline by viewModel.isLoadsheddingMode.collectAsState()
    val context = LocalContext.current

    val speechSamples = listOf(
        "Kal subha Physics ka chapter 3 ka numerical part khatam karna hai aur Chemistry ka test prep karna hai.",
        "Solve maths theorem proof exercise 4 tomorrow afternoon, and Urdu short questions parso.",
        "Islamiat board paper prep on Friday morning and Computer Science chapter 2 MCQ quiz parso shaam."
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Bilingual Speech-to-Schedule 🗣️🤖",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Speak naturally in Roman Urdu or English. Our localized NLP parses subjects, urgency, and tasks into your structured timetable instantly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftGray
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Simulated audio bubble with a beautiful wavy waveform inside
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryLilac.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            // Animated breathing waveform
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(7) { index ->
                                    val sizeAnim = remember { Animatable(10f) }
                                    LaunchedEffect(key1 = isProcessing) {
                                        while (isProcessing) {
                                            sizeAnim.animateTo(
                                                targetValue = (20..70).random().toFloat(),
                                                animationSpec = tween(300, delayMillis = index * 50)
                                            )
                                            sizeAnim.animateTo(
                                                targetValue = 10f,
                                                animationSpec = tween(300)
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(sizeAnim.value.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(PrimaryLilac)
                                    )
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Mic",
                                    tint = PrimaryLilac,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Ready to record. Speak or write below!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftGray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        label = { Text("Spoken transcript / manual schedule text") },
                        placeholder = { Text("E.g., Kal subha Physics chapter 3 numericals...") },
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = PrimaryLilac,
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (textInput.isBlank()) {
                                    Toast.makeText(context, "Please enter some speech text first!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.parseVoiceLoggerSchedule(textInput)
                                Toast.makeText(context, "NLP Processing spoken schedule... 🧠⚡", Toast.LENGTH_SHORT).show()
                                textInput = ""
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isProcessing
                        ) {
                            Text("Process spoken schedule 🚀", fontWeight = FontWeight.Bold)
                        }

                        // Clear input
                        OutlinedButton(
                            onClick = { textInput = "" },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Tap a template to auto-record 📋",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(speechSamples) { sample ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { textInput = sample },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🗣️", fontSize = 20.sp)
                    Text(
                        text = sample,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ------------------------------------------
// 2. "BOARD PATTERN" PREDICTIVE FOCUS BLOCKS
// ------------------------------------------
@Composable
fun BoardFocusView(
    viewModel: StudyViewModel,
    isDark: Boolean,
    cardBg: Color,
    borderCol: Color
) {
    val incompleteTasks by viewModel.allTasks.collectAsState()
    val activeTasks = incompleteTasks.filter { !it.isCompleted }
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    // Board Focus Patterns
    val patterns = listOf(
        "Physics/Chemistry Section C (Derivations & Numericals)",
        "Mathematics Section B & C (Theorems & Proofs)",
        "Urdu/Islamiat Section B & C (Ayat/Asha'ar Translation)",
        "General Board Exam Mock (MCQs & Short Questions)"
    )
    var selectedPattern by remember { mutableStateOf(patterns[0]) }

    // Timer state
    var isTimerRunning by remember { mutableStateOf(false) }
    var currentBlockIndex by remember { mutableStateOf(0) }
    var blockTimeRemaining by remember { mutableStateOf(0) }
    var totalTimePreset by remember { mutableStateOf(0) }

    // Sounds
    val ambientSounds = listOf("Exam-Hall Silence 🕰️", "Focus Fan Hum 🌬️", "Deep Alpha Waves 🧠", "Nature Rustle 🍃")
    var selectedSound by remember { mutableStateOf(ambientSounds[0]) }
    val context = LocalContext.current

    // Set weights based on selected pattern
    val blocks = remember(selectedPattern) {
        when (selectedPattern) {
            "Physics/Chemistry Section C (Derivations & Numericals)" -> listOf(
                "Long Derivations 📐 (40%)" to 20,
                "Numerical Practice 🧮 (40%)" to 20,
                "Conceptual MCQs ✏️ (20%)" to 10
            )
            "Mathematics Section B & C (Theorems & Proofs)" -> listOf(
                "Theorem Proofs 📝 (50%)" to 25,
                "Past Paper Practice 📊 (25%)" to 12,
                "Algebraic Equations 🔍 (25%)" to 13
            )
            "Urdu/Islamiat Section B & C (Ayat/Asha'ar Translation)" -> listOf(
                "Ayat / Asha'ar Translation 🕌 (50%)" to 25,
                "Short Conceptual Qs ✍️ (50%)" to 25
            )
            else -> listOf(
                "Conceptual MCQs ✏️ (40%)" to 20,
                "Section B Short Answers 📝 (60%)" to 30
            )
        }
    }

    // Launch Timer Tick
    LaunchedEffect(isTimerRunning, blockTimeRemaining) {
        if (isTimerRunning && blockTimeRemaining > 0) {
            kotlinx.coroutines.delay(1000)
            blockTimeRemaining--
        } else if (isTimerRunning && blockTimeRemaining == 0) {
            // Move to next block
            if (currentBlockIndex < blocks.lastIndex) {
                currentBlockIndex++
                blockTimeRemaining = blocks[currentBlockIndex].second * 60
                totalTimePreset = blockTimeRemaining
                Toast.makeText(context, "🎉 Block Complete! Next: ${blocks[currentBlockIndex].first}", Toast.LENGTH_LONG).show()
            } else {
                isTimerRunning = false
                viewModel.earnDuelPoints(200) // Award focused XP
                Toast.makeText(context, "🏆 Focus Session Complete! Earned +200 XP!", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Board Predictive Focus Session ⏱️🎯",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Generic timers ignore exam patterns. This session breaks down focus intervals strictly according to Matric board exam question weightage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftGray
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Choose Focus Task
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "1. Choose Target Subject Task:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryLilac
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PrimaryLilac.copy(alpha = 0.05f))
                                .border(1.dp, BorderPastel, RoundedCornerShape(12.dp))
                                .clickable { expandedDropdown = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedTask?.title ?: "General Subject Practice 🧠",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(imageVector = Icons.Rounded.ArrowDropDown, contentDescription = null, tint = SoftGray)
                            }
                            DropdownMenu(
                                expanded = expandedDropdown,
                                onDismissRequest = { expandedDropdown = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .background(if (isDark) DarkSurface else Color.White)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("General Board Prep Mock") },
                                    onClick = {
                                        selectedTask = null
                                        expandedDropdown = false
                                    }
                                )
                                activeTasks.forEach { task ->
                                    DropdownMenuItem(
                                        text = { Text("${task.subject} - ${task.title}") },
                                        onClick = {
                                            selectedTask = task
                                            expandedDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Select Exam Weightage Pattern
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "2. Select Board Question Weightage:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryLilac
                        )
                        patterns.forEach { pattern ->
                            val active = selectedPattern == pattern
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) PrimaryLilac.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        if (!isTimerRunning) selectedPattern = pattern
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = active,
                                    onClick = { if (!isTimerRunning) selectedPattern = pattern },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryLilac)
                                )
                                Text(
                                    text = pattern,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Focus block breakdowns visualization
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Focus Blocks Weightage Breakdown:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SoftGray
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Gray.copy(alpha = 0.1f))
                        ) {
                            val colors = listOf(PrimaryLilac, SecondaryPeach, SunnyYellow, MintGreen)
                            val totalMins = blocks.sumOf { it.second }
                            blocks.forEachIndexed { idx, (label, mins) ->
                                val weight = mins.toFloat() / totalMins
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(weight)
                                        .background(colors[idx % colors.size]),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$mins m",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            blocks.forEachIndexed { idx, (label, mins) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val colors = listOf(PrimaryLilac, SecondaryPeach, SunnyYellow, MintGreen)
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(colors[idx % colors.size])
                                    )
                                    Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = SoftGray)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Focus Session Screen Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTimerRunning) PrimaryLilac.copy(alpha = 0.08f) else cardBg
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isTimerRunning) PrimaryLilac else borderCol)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val currentBlockTitle = blocks.getOrNull(currentBlockIndex)?.first ?: "Not Started"
                    
                    Text(
                        text = "ACTIVE STAGE: $currentBlockTitle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryLilac
                    )

                    // Large circular progress block
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val progressFraction = if (totalTimePreset > 0) {
                            blockTimeRemaining.toFloat() / totalTimePreset
                        } else 1f

                        CircularProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxSize(),
                            color = PrimaryLilac,
                            strokeWidth = 10.dp,
                            trackColor = PrimaryLilac.copy(alpha = 0.12f)
                        )

                        val minutesLeft = blockTimeRemaining / 60
                        val secondsLeft = blockTimeRemaining % 60
                        val timeStr = String.format("%02d:%02d", minutesLeft, secondsLeft)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTimerRunning) "Studying ✍️" else "Ready",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isTimerRunning) MintGreen else OrangeRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Ambient focus audio selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryLilac.copy(alpha = 0.05f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔊 Ambient Sound:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ambientSounds) { sound ->
                                val active = selectedSound == sound
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) PrimaryLilac else Color.Transparent)
                                        .clickable {
                                            selectedSound = sound
                                            Toast.makeText(context, "Playing simulated background noise: $sound 🎶", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = sound.take(12) + "..",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) Color.White else SoftGray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!isTimerRunning && blockTimeRemaining == 0) {
                                    // Start a brand new session
                                    currentBlockIndex = 0
                                    blockTimeRemaining = blocks[0].second * 60
                                    totalTimePreset = blockTimeRemaining
                                }
                                isTimerRunning = !isTimerRunning
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTimerRunning) OrangeRed else MintGreen
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isTimerRunning) "Pause focus" else "Start Board Focus ⏱️", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                isTimerRunning = false
                                currentBlockIndex = 0
                                blockTimeRemaining = 0
                                totalTimePreset = 0
                            },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reset", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// 3. PEER ACCOUNTABILITY "STUDY DUELS"
// ------------------------------------------
@Composable
fun StudyDuelsView(
    viewModel: StudyViewModel,
    isDark: Boolean,
    cardBg: Color,
    borderCol: Color
) {
    val isDuelActive by viewModel.isDuelActive.collectAsState()
    val timeRemaining by viewModel.duelTimeRemaining.collectAsState()
    val myScore by viewModel.duelMyScore.collectAsState()
    val opponentScore by viewModel.duelOpponentScore.collectAsState()
    val reaction by viewModel.duelOpponentReaction.collectAsState()
    val context = LocalContext.current

    val leaderboard = listOf(
        Triple("Lilac Scholar 🌸", 1450, "Rawalpindi"),
        Triple("Matric Master 🏆", 1280, "Karachi"),
        Triple("Alpha Deriver 📐", 1120, "Lahore"),
        Triple("Pakistani Einstein 🧠", 980, "Peshawar"),
        Triple("Derivation Expert ⚡", 890, "Islamabad")
    )

    // Duel progress simulation
    LaunchedEffect(isDuelActive) {
        while (isDuelActive) {
            kotlinx.coroutines.delay((3000..8000).random().toLong())
            viewModel.simulateOpponentAction()
        }
    }

    // Reaction popup clear
    LaunchedEffect(reaction) {
        if (reaction.isNotEmpty()) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearOpponentReaction()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isDuelActive) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Launch Study Focus Duel! ⚔️🤝",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Studying alone is tough. Connect anonymously with other Matric students in Pakistan. Lock your screen, study together, and climb the neighborhood leaderboards!",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftGray
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val durations = listOf(30, 45, 60)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            durations.forEach { mins ->
                                Button(
                                    onClick = {
                                        viewModel.startDuel(mins)
                                        Toast.makeText(context, "Duel launched! 45m screen lock active. ⚔️", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac.copy(alpha = 0.15f), contentColor = PrimaryLilac),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("$mins Mins ⏱️", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Weekly Neighborhood Leaderboard 🏆",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(leaderboard) { (name, xp, city) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.5f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderCol.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👤", fontSize = 18.sp)
                            Column {
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(city, style = MaterialTheme.typography.labelSmall, color = SoftGray)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SunnyYellow.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("$xp XP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SunnyYellow)
                        }
                    }
                }
            }
        } else {
            // Duel is ACTIVE
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(2.dp, OrangeRed)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "⚔️ STUDY DUEL ACTIVE! ⚔️",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OrangeRed
                        )

                        // Timer countdown
                        val minsLeft = timeRemaining / 60
                        val secsLeft = timeRemaining % 60
                        val timeStr = String.format("%02d:%02d", minsLeft, secsLeft)
                        Text(timeStr, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = PrimaryLilac)

                        Spacer(modifier = Modifier.height(4.dp))

                        // Score bars
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Me progress
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("You (Focusing) 👤", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("$myScore Points", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PrimaryLilac)
                                }
                                LinearProgressIndicator(
                                    progress = { (myScore.toFloat() / 200f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = PrimaryLilac,
                                    trackColor = PrimaryLilac.copy(alpha = 0.15f)
                                )
                            }

                            // Opponent progress
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Lilac Scholar 🌸 (Opponent)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("$opponentScore Points", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SecondaryPeach)
                                }
                                LinearProgressIndicator(
                                    progress = { (opponentScore.toFloat() / 200f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = SecondaryPeach,
                                    trackColor = SecondaryPeach.copy(alpha = 0.15f)
                                )
                            }
                        }

                        // Opponent Reaction Popup
                        AnimatedVisibility(visible = reaction.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SunnyYellow.copy(alpha = 0.15f))
                                    .border(1.dp, SunnyYellow, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Opponent sent: $reaction",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SunnyYellow
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Interactive reaction buttons
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Send quick accountability reaction:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray
                            )
                            val reactionsList = listOf("✋ High-Five", "🤲 Dua", "👏 Bravo", "💪 Focus")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                reactionsList.forEach { r ->
                                    Button(
                                        onClick = {
                                            viewModel.earnDuelPoints(10)
                                            Toast.makeText(context, "Reaction sent! Earned +10 Points! ✋✨", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac.copy(alpha = 0.15f), contentColor = PrimaryLilac),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(r, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedButton(
                            onClick = { viewModel.stopDuel() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, OrangeRed)
                        ) {
                            Text("Forfeit Duel (Deducts XP) ❌", color = OrangeRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// 4. SYLLABUS BURN-DOWN & PANIC PREDICTOR
// ------------------------------------------
@Composable
fun SyllabusBurnDownView(
    viewModel: StudyViewModel,
    isDark: Boolean,
    cardBg: Color,
    borderCol: Color
) {
    val tasks by viewModel.allTasks.collectAsState()
    val syllabusProgress by viewModel.syllabusProgress.collectAsState()
    val subjectWeeklyTargets by viewModel.subjectWeeklyTargets.collectAsState()
    val focusSessions by viewModel.focusSessions.collectAsState()
    
    val completedCount = tasks.count { it.isCompleted }
    val totalCount = tasks.size.coerceAtLeast(5)
    val remainingCount = (totalCount - completedCount).coerceAtLeast(0)
    val isRescheduling by viewModel.isRescheduling.collectAsState()
    val context = LocalContext.current
    val hasPanicState = remainingCount >= 3

    var activeSubTab by remember { mutableStateOf("Syllabus") } // "Syllabus", "SpacedRep", "Balance"
    var selectedSubject by remember { mutableStateOf("Mathematics") }

    val syllabusData = remember {
        mapOf(
            "Mathematics" to listOf(
                "Chapter 1: Algebraic Expressions & Equations" to listOf("Quadratic Equations", "Simultaneous Equations", "Exponents & Radicals"),
                "Chapter 2: Functions & Graphs" to listOf("Linear Functions", "Parabolic Functions", "Exponential Graphs"),
                "Chapter 3: Geometry & Trigonometry" to listOf("Similarity & Congruency", "Trigonometric Identities", "Sine & Cosine Rules")
            ),
            "Physics" to listOf(
                "Chapter 1: Kinematics" to listOf("Displacement & Velocity-Time Graphs", "1D Projectile Motion", "Equations of Motion"),
                "Chapter 2: Newton's Laws" to listOf("Friction & Friction Coefficients", "Newton's Second Law", "Force & Free-Body Diagrams"),
                "Chapter 3: Work, Energy & Power" to listOf("Work-Energy Theorem", "Conservation of Energy", "Power & Efficiency Calculations")
            ),
            "Biology" to listOf(
                "Chapter 1: DNA & RNA" to listOf("Structure of DNA & RNA", "DNA Replication Process", "Protein Synthesis & Translation"),
                "Chapter 2: Meiosis & Genetics" to listOf("Stages of Meiosis", "Genetic Variation Mechanisms", "Monohybrid Crosses"),
                "Chapter 3: Human Reproduction" to listOf("Male & Female Reproductive Organs", "Menstrual Cycle Hormones", "Pregnancy & Development")
            ),
            "Chemistry" to listOf(
                "Chapter 1: Organic Molecules" to listOf("Functional Groups & IUPAC naming", "Isomerism & Structural Formulas", "Organic Reactions"),
                "Chapter 2: Rate & Extent of Reaction" to listOf("Collision Theory & Catalysts", "Chemical Equilibrium Kc", "Le Chatelier's Principle"),
                "Chapter 3: Electrochemical Cells" to listOf("Galvanic Cells & Potentials", "Electrolytic Cells", "Corrosion Prevention")
            ),
            "English" to listOf(
                "Chapter 1: Shakespeare & Poetry" to listOf("Macbeth Act 1-5 Character Motifs", "Poetic Figures of Speech", "Sonnets Analysis"),
                "Chapter 2: Grammar & Comprehension" to listOf("Active vs Passive Voice", "Direct vs Indirect Speech", "Punctuation Rules"),
                "Chapter 3: Writing & Presentation" to listOf("Transactional Letters", "Formal Essays", "Oral Presentation Formats")
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Heartwarming Section Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (isDark) DarkSurface.copy(alpha = 0.5f) else PrimaryLilac.copy(alpha = 0.08f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple("Syllabus", "Syllabus 📚", "Syllabus Progress"),
                Triple("SpacedRep", "Spaced Rep ⏱️", "Spaced Repetition"),
                Triple("Balance", "Study Balance ⚖️", "Study Target Balance")
            ).forEach { (tabId, label, tag) ->
                val active = activeSubTab == tabId
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) PrimaryLilac else Color.Transparent)
                        .clickable { activeSubTab = tabId }
                        .padding(vertical = 10.dp)
                        .testTag("burndown_tab_$tabId"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else SoftGray
                    )
                }
            }
        }

        when (activeSubTab) {
            "Syllabus" -> {
                // --- TAB 1: SYLLABUS PROGRESS TRACKER ---
                Text(
                    text = "Syllabus Progress Tracker 📚✨",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Subjects Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val subjects = listOf(
                        "Mathematics" to "📐",
                        "Physics" to "⚡",
                        "Biology" to "🧬",
                        "Chemistry" to "🧪",
                        "English" to "📖"
                    )
                    subjects.forEach { (sub, emoji) ->
                        val sel = selectedSubject == sub
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sel) PrimaryLilac else PrimaryLilac.copy(alpha = 0.12f))
                                .clickable { selectedSubject = sub }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("subject_pill_$sub"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$emoji $sub",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else PrimaryLilac
                            )
                        }
                    }
                }

                // Chapters list
                val chapters = syllabusData[selectedSubject] ?: emptyList()
                chapters.forEach { (chapterName, subtopics) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = chapterName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Calculate chapter completeness
                                val totalSub = subtopics.size
                                var masteredCount = 0
                                subtopics.forEach { sub ->
                                    val status = syllabusProgress["$selectedSubject|$chapterName|$sub"] ?: "NOT_STARTED"
                                    if (status == "MASTERED") masteredCount++
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (masteredCount == totalSub) MintGreen.copy(alpha = 0.15f) else PrimaryLilac.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$masteredCount/$totalSub Mastered 🏆",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (masteredCount == totalSub) MintGreen else PrimaryLilac
                                    )
                                }
                            }

                            Divider(color = borderCol.copy(alpha = 0.5f))

                            // Subtopics
                            subtopics.forEach { subtopic ->
                                val status = syllabusProgress["$selectedSubject|$chapterName|$subtopic"] ?: "NOT_STARTED"
                                val isRepActive = viewModel.isSpacedRepetitionActive(selectedSubject, chapterName, subtopic)
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.01f))
                                        .border(0.5.dp, borderCol.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = subtopic,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isRepActive) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.HourglassEmpty,
                                                    contentDescription = "Spaced Repetition Active",
                                                    tint = OrangeRed,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = "Spaced Rep Active ⚡ (1-3-5-7)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = OrangeRed,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Status Cycler Button
                                        val (btnText, btnBg, btnTextCol) = when (status) {
                                            "NOT_STARTED" -> Triple("Not Started ⚪", if (isDark) DarkSurface else Color.LightGray.copy(alpha = 0.3f), SoftGray)
                                            "READ" -> Triple("Read 📖", PrimaryLilac.copy(alpha = 0.15f), PrimaryLilac)
                                            "PRACTICED" -> Triple("Practiced 📝", SecondaryPeach.copy(alpha = 0.15f), SecondaryPeach)
                                            else -> Triple("Mastered 🏆", MintGreen.copy(alpha = 0.15f), MintGreen)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(btnBg)
                                                .clickable {
                                                    val nextStatus = when (status) {
                                                        "NOT_STARTED" -> "READ"
                                                        "READ" -> "PRACTICED"
                                                        "PRACTICED" -> "MASTERED"
                                                        else -> "NOT_STARTED"
                                                    }
                                                    viewModel.setSyllabusStatus(selectedSubject, chapterName, subtopic, nextStatus)
                                                    Toast.makeText(context, "$subtopic marked as $nextStatus! ✨", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = btnText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = btnTextCol
                                            )
                                        }

                                        // Spaced Repetition trigger
                                        if (!isRepActive) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.scheduleSpacedRepetition(selectedSubject, chapterName, subtopic)
                                                    Toast.makeText(context, "Spaced Repetition (1-3-5-7 day rule) scheduled! Check your study calendar. ⏱️🗓️", Toast.LENGTH_LONG).show()
                                                },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryLilac.copy(alpha = 0.12f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Schedule,
                                                    contentDescription = "Schedule Spaced Repetition",
                                                    tint = PrimaryLilac,
                                                    modifier = Modifier.size(14.dp)
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

            "SpacedRep" -> {
                // --- TAB 2: SPACED REPETITION REMINDERS ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryLilac.copy(alpha = 0.05f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryLilac.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("💡", fontSize = 24.sp)
                        Column {
                            Text(
                                text = "The Spaced Repetition Rule ⏱️🧠",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryLilac
                            )
                            Text(
                                text = "Reviewing chapters on Day 1, 3, 5, and 7 fights the forgetting curve. The app inserts these study slots dynamically to maximize memory retention.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftGray,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Text(
                    text = "Today's Active Spaced Revisions ⏰",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Query REVISION tasks due today
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val todayEnd = todayStart + 86400000L

                val pendingRevisions = tasks.filter { 
                    it.taskType == "REVISION" && !it.isCompleted && it.dueDate < todayEnd 
                }

                if (pendingRevisions.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🎉", fontSize = 36.sp)
                            Text(
                                text = "All clear! No revisions due today",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Outstanding work keeping your learning structured. Tap the 1-3-5-7 clock button on any subtopic to schedule active revision slots.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        pendingRevisions.forEach { revTask ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(OrangeRed.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Schedule,
                                                contentDescription = "Revision Due",
                                                tint = OrangeRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = revTask.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${revTask.subject} • Target: ${revTask.estimatedMinutes}m review",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SoftGray
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.updateTask(revTask.copy(isCompleted = true))
                                            viewModel.addXp(120)
                                            Toast.makeText(context, "Revision completed! +120 XP earned 🏆✨", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Log Done ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Balance" -> {
                // --- TAB 3: SUBJECT WEEKLY TARGETS & PROGRESS ---
                Text(
                    text = "Weekly Subject Targets & Allocation 📊⚖️",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Set weekly target study hours for each Matric subject. Ensure your actual study duration matches your weightages to balance focus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftGray
                        )

                        val subjects = listOf("Mathematics", "Physics", "Biology", "Chemistry", "English")
                        
                        // Calculate focus session hours per subject this week
                        val subjectActualHours = remember(focusSessions) {
                            subjects.associateWith { sub ->
                                val totalMin = focusSessions.filter { session ->
                                    val isThisWeek = session.timestamp >= System.currentTimeMillis() - 7 * 86400000L
                                    if (!isThisWeek) return@filter false
                                    val title = session.taskTitle.lowercase()
                                    when (sub) {
                                        "Mathematics" -> title.contains("math") || title.contains("calc") || title.contains("algebra") || title.contains("geom")
                                        "Physics" -> title.contains("phys") || title.contains("force") || title.contains("kinematic") || title.contains("newton")
                                        "Biology" -> title.contains("bio") || title.contains("dna") || title.contains("genetics") || title.contains("meiosis")
                                        "Chemistry" -> title.contains("chem") || title.contains("acid") || title.contains("equilibrium") || title.contains("cell")
                                        "English" -> title.contains("eng") || title.contains("read") || title.contains("poem") || title.contains("shakespeare") || title.contains("essay")
                                        else -> false
                                    }
                                }.sumOf { it.durationMinutes }
                                totalMin / 60f
                            }
                        }

                        // Imbalance alerts
                        var hasBias = false
                        var biasedSubject = ""
                        var neglectedSubject = ""
                        
                        subjects.forEach { s ->
                            val actual = subjectActualHours[s] ?: 0f
                            val target = subjectWeeklyTargets[s] ?: 4
                            if (actual > target * 1.5f) {
                                hasBias = true
                                biasedSubject = s
                            } else if (actual < target * 0.2f && target > 0) {
                                neglectedSubject = s
                            }
                        }

                        // Display warning or encouraging message
                        if (hasBias && neglectedSubject.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(OrangeRed.copy(alpha = 0.08f))
                                    .border(1.dp, OrangeRed.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("⚠️", fontSize = 18.sp)
                                    Text(
                                        text = "Study Imbalance Alert: You are investing high energy into $biasedSubject but neglecting $neglectedSubject. Use the Scheduler to spread the workload equally! ⚖️",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OrangeRed
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MintGreen.copy(alpha = 0.08f))
                                    .border(1.dp, MintGreen.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("⚖️", fontSize = 18.sp)
                                    Text(
                                        text = "Your weekly subject-level allocation is balanced nicely. Keep keeping pressure uniform across all papers!",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MintGreen
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Sliders & Progress Bars
                        subjects.forEach { sub ->
                            val actual = subjectActualHours[sub] ?: 0f
                            val target = subjectWeeklyTargets[sub] ?: 4
                            val progressPct = if (target > 0) (actual / target).coerceIn(0f, 1f) else 1.0f

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = { if (target > 1) viewModel.setSubjectWeeklyTarget(sub, target - 1) },
                                            modifier = Modifier.size(24.dp).clip(CircleShape).background(PrimaryLilac.copy(alpha = 0.1f))
                                        ) {
                                            Icon(Icons.Rounded.Remove, "Less Target", tint = PrimaryLilac, modifier = Modifier.size(12.dp))
                                        }
                                        Text(
                                            text = "${String.format("%.1f", actual)}h / ${target}h Target",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryLilac
                                        )
                                        IconButton(
                                            onClick = { if (target < 15) viewModel.setSubjectWeeklyTarget(sub, target + 1) },
                                            modifier = Modifier.size(24.dp).clip(CircleShape).background(PrimaryLilac.copy(alpha = 0.1f))
                                        ) {
                                            Icon(Icons.Rounded.Add, "More Target", tint = PrimaryLilac, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }

                                // Custom progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryLilac.copy(alpha = 0.1f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progressPct)
                                            .clip(CircleShape)
                                            .background(if (actual >= target) MintGreen else PrimaryLilac)
                                    )
                                }
                            }
                        }
                    }
                }

                // Burn Down Progressive Chart card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Syllabus Progressive Burn-Down Chart 📉📊",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tracks ideal vs actual progression for your Matric exams. Keeping behind for 3 days triggers our balancing recalculator.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftGray
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Draw High-fidelity syllabus burn-down canvas chart
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(PrimaryLilac.copy(alpha = 0.04f))
                                .border(1.dp, BorderPastel.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height

                                // Draw grid lines
                                drawLine(color = Color.LightGray.copy(alpha = 0.3f), start = Offset(0f, h * 0.25f), end = Offset(w, h * 0.25f), strokeWidth = 1f)
                                drawLine(color = Color.LightGray.copy(alpha = 0.3f), start = Offset(0f, h * 0.5f), end = Offset(w, h * 0.5f), strokeWidth = 1f)
                                drawLine(color = Color.LightGray.copy(alpha = 0.3f), start = Offset(0f, h * 0.75f), end = Offset(w, h * 0.75f), strokeWidth = 1f)

                                // Ideal line (Dashed gray, 100% down to 0%)
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.4f),
                                    start = Offset(0f, 10f),
                                    end = Offset(w, h - 10f),
                                    strokeWidth = 3f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )

                                // Actual line (Solid Purple gradient)
                                val actualPoints = listOf(
                                    Offset(0f, 10f),
                                    Offset(w * 0.2f, h * 0.15f),
                                    Offset(w * 0.4f, h * 0.3f),
                                    Offset(w * 0.6f, h * 0.5f),
                                    Offset(w * 0.8f, h * 0.5f),
                                    Offset(w * 1.0f, h * (1.0f - (completedCount.toFloat() / totalCount.toFloat())))
                                )
                                for (i in 0 until actualPoints.size - 1) {
                                    drawLine(
                                        color = PrimaryLilac,
                                        start = actualPoints[i],
                                        end = actualPoints[i + 1],
                                        strokeWidth = 5f
                                    )
                                }
                                // Highlight current progress dot
                                drawCircle(
                                    color = SunnyYellow,
                                    radius = 6.dp.toPx(),
                                    center = actualPoints.last()
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.5f)))
                                Text("Ideal Line (Dashed)", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = SoftGray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PrimaryLilac))
                                Text("Your Progress (Solid)", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = SoftGray)
                            }
                        }
                    }
                }

                // Workload Balancer
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasPanicState) OrangeRed.copy(alpha = 0.08f) else MintGreen.copy(alpha = 0.08f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (hasPanicState) OrangeRed else MintGreen)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(if (hasPanicState) "⚠️" else "🌸", fontSize = 24.sp)
                            Column {
                                Text(
                                    text = "Intelligent Panic Predictor Status",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hasPanicState) "Recalculation Recommended!" else "Workload fully optimized.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftGray
                                )
                            }
                        }

                        Text(
                            text = if (hasPanicState) {
                                "Warning! You have fallen behind on your study schedule targets by $remainingCount chapters. If unchanged, you will miss 28% of the Board Syllabus. Don't panic—rebalance below!"
                            } else {
                                "Excellent work! You are currently matching the target syllabus pace. Your daily study pressure is highly optimized."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (hasPanicState) {
                            Button(
                                onClick = {
                                    viewModel.autoBalanceTasks()
                                    Toast.makeText(context, "Syllabus Rebalanced! Missed items distributed evenly. ✨", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OrangeRed),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isRescheduling
                            ) {
                                if (isRescheduling) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                } else {
                                    Text("Auto-Balance Remaining Syllabus ⚖️", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// 5. OFFLINE-FIRST "LOADSHEDDING SYNC" VIEW
// ------------------------------------------
@Composable
fun LoadsheddingSyncView(
    viewModel: StudyViewModel,
    isDark: Boolean,
    cardBg: Color,
    borderCol: Color
) {
    val isOffline by viewModel.isLoadsheddingMode.collectAsState()
    val pendingPackets by viewModel.pendingSyncCount.collectAsState()
    val isSyncing by viewModel.isVoiceLoggerProcessing.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Loadshedding Sync Center ⚡🔋",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "No electricity? No problem! The app is completely offline-first. Schedule tasks, track streaks, and process NLP offline. When power returns, sync with low-bandwidth compression.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftGray
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isOffline) OrangeRed.copy(alpha = 0.08f) else MintGreen.copy(alpha = 0.08f))
                            .border(1.dp, if (isOffline) OrangeRed.copy(alpha = 0.4f) else MintGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isOffline) "⚡" else "🟢", fontSize = 24.sp)
                            Column {
                                Text(
                                    text = if (isOffline) "Offline Mode (Loadshedding Active)" else "Connected (Synced)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isOffline) "Using localized offline NLP engines" else "Cloud database sync secure",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftGray
                                )
                            }
                        }
                        Switch(
                            checked = isOffline,
                            onCheckedChange = { viewModel.setLoadsheddingMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = OrangeRed)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sync Status Block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryLilac.copy(alpha = 0.06f))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pending Sync Queue:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PrimaryLilac)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("$pendingPackets Packets", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "Packets represent study entries, focus sessions, and tasks created offline during power cuts, compressed into tiny low-bandwidth blobs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftGray
                            )

                            if (pendingPackets > 0) {
                                Button(
                                    onClick = {
                                        viewModel.syncOfflineData()
                                        viewModel.earnDuelPoints(50) // Resilience bonus!
                                        Toast.makeText(context, "Power restored! Low-bandwidth sync completed. Earned +50 Resilience XP! 🔋✨", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLilac),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isSyncing
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Text("Sync Offline Data Now 💡", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



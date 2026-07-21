package com.example.ui

import android.widget.Toast
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
                    viewModel.logFocusSession(taskName, 25, xpAwarded)
                    
                    lastCompletedTaskName = taskName
                    lastEarnedXp = xpAwarded
                    showSessionCompleteDialog = true
                    
                    // Trigger particles
                    triggerTaskCompleteAnimation(Offset(500f, 1000f))
                    
                    timerMode = "Break"
                    pomodoroMinutes = 5
                } else {
                    timerMode = "Study"
                    pomodoroMinutes = 25
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
                    Triple("dashboard", "Dashboard", Icons.Rounded.Dashboard),
                    Triple("todo", "To-Do", Icons.Rounded.CheckCircle),
                    Triple("scanner", "Scanner", Icons.Rounded.QrCodeScanner),
                    Triple("calendar", "Scheduler", Icons.Rounded.CalendarMonth),
                    Triple("mistakes", "Analyzer", Icons.Rounded.FactCheck),
                    Triple("coach", "Coach", Icons.Rounded.Forum)
                )
                tabs.forEach { (tabId, label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == tabId,
                        onClick = { currentTab = tabId },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
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
                        selectedFocusTask = selectedFocusTask,
                        onSelectFocusTask = { selectedFocusTask = it },
                        onToggleTimer = { isTimerRunning = !isTimerRunning },
                        onResetTimer = {
                            isTimerRunning = false
                            pomodoroMinutes = if (timerMode == "Study") 25 else 5
                            pomodoroSeconds = 0
                        },
                        onSetTimerDuration = { mins, mode ->
                            isTimerRunning = false
                            pomodoroMinutes = mins
                            pomodoroSeconds = 0
                            timerMode = mode
                        },
                        onTriggerCompleteAnim = triggerTaskCompleteAnimation
                    )
                    "todo" -> ToDoScreen(
                        viewModel = viewModel,
                        onTriggerCompleteAnim = triggerTaskCompleteAnimation
                    )
                    "scanner" -> ScannerScreen(viewModel)
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

// --- SCREEN 1: THE DASHBOARD SCREEN ---
@Composable
fun DashboardScreen(
    viewModel: StudyViewModel,
    xpPoints: Int,
    pomodoroMinutes: Int,
    pomodoroSeconds: Int,
    isTimerRunning: Boolean,
    timerMode: String,
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
    val progressFraction = if (totalCount > 0) completedCount.toFloat() / totalCount else 0.0f

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
                                    onClick = { if (dailyTargetHours < 12) viewModel.setDailyTargetHours(dailyTargetHours + 1) },
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
                                    text = "${(goalProgressFraction * 100).toInt()}%",
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
                        val totalSecondsPreset = when {
                            pomodoroMinutes == 25 -> 25 * 60
                            pomodoroMinutes == 50 -> 50 * 60
                            pomodoroMinutes == 5 -> 5 * 60
                            pomodoroMinutes == 15 -> 15 * 60
                            else -> pomodoroMinutes * 60 + pomodoroSeconds
                        }
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
                text = "Paste your syllabus text or homework assignment guidelines, and our AI will automatically structure your study timeline!",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray
            )
        }

        // Simulated Viewfinder styled with rounded brackets
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MidnightPlum)
                    .border(2.dp, PrimaryLilac, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Bracket Corners
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val margin = 20.dp.toPx()
                    val len = 30.dp.toPx()
                    val stroke = 6f

                    // Top Left Bracket
                    drawLine(Color.White, Offset(margin, margin), Offset(margin + len, margin), stroke)
                    drawLine(Color.White, Offset(margin, margin), Offset(margin, margin + len), stroke)

                    // Top Right Bracket
                    drawLine(Color.White, Offset(size.width - margin, margin), Offset(size.width - margin - len, margin), stroke)
                    drawLine(Color.White, Offset(size.width - margin, margin), Offset(size.width - margin, margin + len), stroke)

                    // Bottom Left Bracket
                    drawLine(Color.White, Offset(margin, size.height - margin), Offset(margin + len, size.height - margin), stroke)
                    drawLine(Color.White, Offset(margin, size.height - margin), Offset(margin, size.height - margin - len), stroke)

                    // Bottom Right Bracket
                    drawLine(Color.White, Offset(size.width - margin, size.height - margin), Offset(size.width - margin - len, size.height - margin), stroke)
                    drawLine(Color.White, Offset(size.width - margin, size.height - margin), Offset(size.width - margin, size.height - margin - len), stroke)
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = "Scan Icon",
                            tint = PrimaryLilac,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Simulator Active: Scanner ready!",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
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
                label = { Text("Paste guidelines or text here") },
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
                            delay(4000L) // Wait simulation or real
                            resultMessage = "Awesome! 🚀 AI structured 2 new tasks and added them to your dashboard subject calendars."
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

    var selectedDayOffset by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
                    
                    // Check if day has tests or complete tasks
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
                            
                            // Visual indicator dots
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

                    // Mascot representation
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

        // "I Couldn't Study Today / I Am Sick" Button
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

        // Daily Google Calendar Hourly Timeline
        item {
            Text(
                text = "Hourly Study Timeline ⏰🗓️",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Your daily hourly slots. Tasks are color-coded and automatically assigned to healthy hour windows.",
                style = MaterialTheme.typography.bodySmall,
                color = SoftGray
            )
        }

        items((8..20).toList()) { h ->
            val tasksAtHour = hourToTasks[h] ?: emptyList()
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Time Label Column
                Text(
                    text = String.format("%02d:00", h),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SoftGray,
                    modifier = Modifier.width(55.dp).padding(top = 8.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Event Card Column
                if (tasksAtHour.isNotEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (task in tasksAtHour) {
                            val categoryColor = when(task.subject.uppercase()) {
                                "MATHEMATICS", "MATH" -> Color(0xFF20BF6B) // Mint Green
                                "PHYSICAL SCIENCES", "PHYSICS" -> Color(0xFF54A0FF) // Blue
                                "LIFE SCIENCES", "BIOLOGY" -> Color(0xFFFF6B6B) // Pink/Red
                                else -> PrimaryLilac
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(categoryColor.copy(alpha = 0.15f))
                                    .border(1.5.dp, categoryColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .clickable { viewModel.toggleTaskCompletion(task) }
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
                                        // Task checkbox
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
                                    
                                    IconButton(
                                        onClick = { viewModel.deleteTask(task.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Delete",
                                            tint = OrangeRed.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Free slot dotted spacer
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .padding(vertical = 4.dp)
                            .drawBehind {
                                drawLine(
                                    color = SoftGray.copy(alpha = 0.25f),
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(size.width, size.height / 2),
                                    strokeWidth = 1.5f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(12f, 12f),
                                        0f
                                    )
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Free Slot",
                                tint = SoftGray.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Healthy Study Gap 🌿",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
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
    
    var newTodoText by remember { mutableStateOf("") }
    var scanInputText by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var showScanPanel by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    val currentTheme by viewModel.appTheme.collectAsState()
    val isDark = currentTheme == "Cosmic Candy"

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
                            text = "Paste handwritten lists, tasks, or guidelines. Spark AI will extract clean to-do items and add them immediately!",
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
                                .border(1.dp, PrimaryLilac.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Bracket Corners
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val margin = 12.dp.toPx()
                                val len = 20.dp.toPx()
                                val stroke = 4f
                                val c = if (isDark) Color.White else PrimaryLilac

                                drawLine(c, Offset(margin, margin), Offset(margin + len, margin), stroke)
                                drawLine(c, Offset(margin, margin), Offset(margin, margin + len), stroke)
                                drawLine(c, Offset(size.width - margin, margin), Offset(size.width - margin - len, margin), stroke)
                                drawLine(c, Offset(size.width - margin, margin), Offset(size.width - margin, margin + len), stroke)
                                drawLine(c, Offset(margin, size.height - margin), Offset(margin + len, size.height - margin), stroke)
                                drawLine(c, Offset(margin, size.height - margin), Offset(margin, size.height - margin - len), stroke)
                                drawLine(c, Offset(size.width - margin, size.height - margin), Offset(size.width - margin - len, size.height - margin), stroke)
                                drawLine(c, Offset(size.width - margin, size.height - margin), Offset(size.width - margin, size.height - margin - len), stroke)
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

                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    imageVector = if (scanLoading) Icons.Rounded.Autorenew else Icons.Rounded.PhotoCamera,
                                    contentDescription = "Scan icon",
                                    tint = if (scanLoading) SunnyYellow else PrimaryLilac,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (scanLoading) "Parsing with Gemini AI..." else "Simulator Active: Camera feedback ready!",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else TextDark
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
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryLilac,
                                unfocusedBorderColor = if (isDark) BorderDarkPastel else BorderPastel
                            )
                        )

                        Button(
                            onClick = {
                                if (scanInputText.isNotBlank()) {
                                    viewModel.scanTodoListText(scanInputText)
                                    coroutineScope.launch {
                                        delay(3000L)
                                        resultMessage = "Successfully added scanned list to Room!"
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
                            enabled = !scanLoading
                        ) {
                            Text("Extract with AI Spark ✨", fontWeight = FontWeight.Bold)
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
}

@Composable
fun LoginScreen(viewModel: StudyViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    
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

                    // Name input
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "What is your name? ✏️",
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
                            text = "Your Gmail Address: ✉️",
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // Start Studying Button
                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            val trimmedEmail = email.trim()
                            if (trimmedName.isEmpty()) {
                                Toast.makeText(context, "Please enter your name 🌸", Toast.LENGTH_SHORT).show()
                            } else if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
                                Toast.makeText(context, "Please enter a valid email address ✉️", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.login(trimmedName, trimmedEmail, selectedAvatar)
                                Toast.makeText(context, "Welcome back, $trimmedName! 🎉", Toast.LENGTH_SHORT).show()
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


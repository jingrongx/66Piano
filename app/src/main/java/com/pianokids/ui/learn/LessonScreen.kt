package com.pianokids.ui.learn

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianokids.ui.components.PianoKeyboard
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.Primary
import com.pianokids.ui.theme.Secondary
import com.pianokids.ui.theme.Tertiary
import com.pianokids.ui.theme.Warning

/** 步骤名称 */
private val STEP_NAMES = listOf("看动画", "看示范", "跟我弹", "自己弹", "领奖励")

/**
 * 课程教学界面。
 *
 * 5 个步骤的完整教学流程。
 *
 * @param lessonId 课程 ID
 * @param onBack 完成或返回时回调
 */
@Composable
fun LessonScreen(
    onBack: () -> Unit,
    lessonId: String = "lesson_1",
    viewModel: LessonViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 加载课程内容
    LaunchedEffect(lessonId) {
        viewModel.loadLesson(lessonId)
    }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val content = uiState.content

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部返回 + 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "${content.icon} 第 ${content.level} 课：${content.title}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            // 步骤进度条
            StepProgressBar(currentStep = uiState.step, totalSteps = 5)

            // 步骤内容
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState.step) {
                    0 -> StepWatchAnimation(
                        introText = content.introText,
                        icon = content.icon,
                        onNext = viewModel::nextStep,
                    )
                    1 -> StepWatchDemo(
                        demoName = content.demoName,
                        demoDesc = content.demoDesc,
                        demoPlayed = uiState.demoPlayed,
                        highlightedNotes = uiState.highlightedNotes,
                        noteColors = uiState.noteColors,
                        onPlayDemo = viewModel::playDemoTone,
                        onNext = viewModel::nextStep,
                    )
                    2 -> StepFollowMe(
                        targetName = content.demoName,
                        highlightedNotes = uiState.highlightedNotes,
                        noteColors = uiState.noteColors,
                    )
                    3 -> StepSolo(
                        soloHint = content.soloHint,
                        soloTargets = content.soloTargets,
                        soloProgress = uiState.soloProgress,
                        highlightedNotes = uiState.highlightedNotes,
                        noteColors = uiState.noteColors,
                    )
                    4 -> StepReward(
                        title = content.title,
                        rewardStars = content.rewardStars,
                        rewardExp = content.rewardExp,
                        onBack = onBack,
                    )
                }
            }
        }
    }
}

/**
 * 步骤进度条：5 个圆点 + 连接线。
 */
@Composable
private fun StepProgressBar(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until totalSteps) {
            val isCurrent = i == currentStep
            val isDone = i < currentStep
            val color = when {
                isDone -> Correct
                isCurrent -> Primary
                else -> MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 32.dp else 26.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                if (isDone) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = (i + 1).toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
            if (i < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < currentStep) Correct else MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
    Text(
        text = "步骤 ${currentStep + 1}/5 · ${STEP_NAMES[currentStep]}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

// ============== 步骤 1：看动画 ==============

@Composable
private fun StepWatchAnimation(
    introText: String,
    icon: String,
    onNext: () -> Unit,
) {
    // 键盘脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Tertiary.copy(alpha = 0.1f)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$icon 钢琴小知识",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = introText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp,
                )
            }
        }

        // 动画钢琴键盘
        PianoKeyboard(
            modifier = Modifier
                .fillMaxWidth()
                .scale(pulseScale),
            startMidi = 60,
            endMidi = 84,
            highlightedNotes = setOf(60),
            noteColors = mapOf(60 to Primary),
            keyHeight = 140.dp,
        )

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text("下一步", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

// ============== 步骤 2：看示范 ==============

@Composable
private fun StepWatchDemo(
    demoName: String,
    demoDesc: String,
    demoPlayed: Boolean,
    highlightedNotes: Set<Int>,
    noteColors: Map<Int, Color>,
    onPlayDemo: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = demoDesc,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
        )

        PianoKeyboard(
            modifier = Modifier.fillMaxWidth(),
            startMidi = 48,
            endMidi = 84,
            highlightedNotes = highlightedNotes,
            noteColors = noteColors,
            keyHeight = 160.dp,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onPlayDemo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tertiary),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (demoPlayed) "再听一次 $demoName" else "播放示范音 $demoName",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("下一步", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}

// ============== 步骤 3：跟我弹 ==============

@Composable
private fun StepFollowMe(
    targetName: String,
    highlightedNotes: Set<Int>,
    noteColors: Map<Int, Color>,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "请弹奏 $targetName",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "弹对了会变绿色哦~",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PianoKeyboard(
            modifier = Modifier.fillMaxWidth(),
            startMidi = 48,
            endMidi = 84,
            highlightedNotes = highlightedNotes,
            noteColors = noteColors,
            keyHeight = 180.dp,
        )
    }
}

// ============== 步骤 4：自己弹 ==============

@Composable
private fun StepSolo(
    soloHint: String,
    soloTargets: List<Int>,
    soloProgress: Int,
    highlightedNotes: Set<Int>,
    noteColors: Map<Int, Color>,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = soloHint,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        // 目标音符进度
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            soloTargets.forEachIndexed { index, midi ->
                val done = index < soloProgress
                val current = index == soloProgress
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> Correct
                                current -> Warning
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = midiToName(midi),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (done || current) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        PianoKeyboard(
            modifier = Modifier.fillMaxWidth(),
            startMidi = 48,
            endMidi = 84,
            highlightedNotes = highlightedNotes,
            noteColors = noteColors,
            keyHeight = 160.dp,
        )
    }
}

// ============== 步骤 5：领奖励 ==============

@Composable
private fun StepReward(
    title: String,
    rewardStars: Int,
    rewardExp: Int,
    onBack: () -> Unit,
) {
    // 星星弹跳动画
    val starScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "star",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(rewardStars) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "星",
                    tint = Secondary,
                    modifier = Modifier
                        .size(64.dp)
                        .scale(starScale),
                )
            }
        }

        Text(
            text = "🎉",
            fontSize = 64.sp,
        )

        Text(
            text = "恭喜完成 $title！",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "获得 $rewardStars 颗星 + $rewardExp 经验",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text("返回学习", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

private val NOTE_NAMES_ARR = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private fun midiToName(midi: Int): String {
    val octave = midi / 12 - 1
    return NOTE_NAMES_ARR[midi % 12] + octave
}

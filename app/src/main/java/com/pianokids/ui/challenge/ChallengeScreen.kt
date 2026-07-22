package com.pianokids.ui.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.PinkSoft
import com.pianokids.ui.theme.Primary
import com.pianokids.ui.theme.Secondary
import com.pianokids.ui.theme.Tertiary
import com.pianokids.ui.theme.Wrong
import com.pianokids.ui.theme.YellowLight
import com.pianokids.ui.util.WindowClass
import com.pianokids.ui.util.rememberWindowClass

/**
 * 闯关大冒险页面。
 *
 * @param onBack 点击返回时回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    onBack: () -> Unit,
    viewModel: ChallengeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val highScore by viewModel.highScore.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("闯关大冒险", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(vertical = 12.dp),
        ) {
            // 顶部展示当前最高分
            HighScoreCard(highScore = highScore)

            Spacer(modifier = Modifier.height(8.dp))

            when {
                uiState.mode == ChallengeMode.IDLE -> {
                    ModePicker(onSelect = viewModel::selectMode)
                }
                uiState.finished -> {
                    GameOverContent(
                        score = uiState.score,
                        highScore = highScore,
                        onRestart = viewModel::resetToModePicker,
                    )
                }
                else -> {
                    GameRunningContent(
                        state = uiState,
                        onAnswer = viewModel::submitEarTrainingAnswer,
                        onReplay = {
                            when (uiState.mode) {
                                ChallengeMode.EAR_TRAINING -> viewModel.replayEarTraining()
                                ChallengeMode.RHYTHM -> viewModel.replayRhythm()
                                else -> {}
                            }
                        },
                        onTap = viewModel::recordRhythmTap,
                        onResetSightReading = viewModel::resetSightReading,
                    )
                }
            }
        }
    }
}

/**
 * 历史最高分卡片。
 */
@Composable
private fun HighScoreCard(highScore: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Secondary.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Secondary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "历史最高分：$highScore",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 模式选择卡片。
 */
@Composable
private fun ModePicker(onSelect: (ChallengeMode) -> Unit) {
    Text(
        text = "选择挑战模式",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(12.dp))

    ModeCard(
        title = "听音辨音",
        desc = "听一个音，选出正确音名",
        icon = Icons.Filled.Hearing,
        color = Primary,
        onClick = { onSelect(ChallengeMode.EAR_TRAINING) },
    )
    Spacer(modifier = Modifier.height(12.dp))
    ModeCard(
        title = "节奏挑战",
        desc = "敲击复读节奏",
        icon = Icons.Filled.MusicNote,
        color = Tertiary,
        onClick = { onSelect(ChallengeMode.RHYTHM) },
    )
    Spacer(modifier = Modifier.height(12.dp))
    ModeCard(
        title = "视奏挑战",
        desc = "看谱按顺序弹奏 3 个音",
        icon = Icons.Filled.Quiz,
        color = Correct,
        onClick = { onSelect(ChallengeMode.SIGHT_READING) },
    )
}

@Composable
private fun ModeCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * 游戏进行中：根据模式渲染不同 UI。
 */
@Composable
private fun GameRunningContent(
    state: ChallengeUiState,
    onAnswer: (Int) -> Unit,
    onReplay: () -> Unit,
    onTap: () -> Unit,
    onResetSightReading: () -> Unit,
) {
    // 进度行
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "回合 ${state.round} / ${state.totalRounds}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Secondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${state.score}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { state.round.toFloat() / state.totalRounds.toFloat() },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = Primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Spacer(modifier = Modifier.height(16.dp))

    when (state.mode) {
        ChallengeMode.EAR_TRAINING -> EarTrainingContent(state, onAnswer, onReplay)
        ChallengeMode.RHYTHM -> RhythmContent(state, onTap, onReplay)
        ChallengeMode.SIGHT_READING -> SightReadingContent(state, onResetSightReading)
        ChallengeMode.IDLE -> {}
    }
}

@Composable
private fun EarTrainingContent(
    state: ChallengeUiState,
    onAnswer: (Int) -> Unit,
    onReplay: () -> Unit,
) {
    val question = state.question ?: return
    val windowClass = rememberWindowClass()
    val optionHeight = if (windowClass == WindowClass.COMPACT) 80.dp else 72.dp
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(YellowLight, Secondary, PinkSoft),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Hearing,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
            Text(
                text = "听一听，是哪个音？",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(
                onClick = onReplay,
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("再听一次")
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    // 选项网格 5 个
    val noteNames = listOf("C" to 60, "D" to 62, "E" to 64, "F" to 65, "G" to 67)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        question.options.take(5).forEachIndexed { index, midi ->
            val pair = noteNames.firstOrNull { it.second == midi } ?: return@forEachIndexed
            val feedbackColor = when {
                state.feedback == "correct" && midi == question.targetMidi -> Correct
                state.feedback == "wrong" && midi == question.targetMidi -> Correct
                state.feedback == "wrong" -> Wrong.copy(alpha = 0.5f)
                else -> Primary
            }
            Button(
                onClick = { onAnswer(midi) },
                modifier = Modifier
                    .weight(1f)
                    .height(optionHeight),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = feedbackColor),
            ) {
                Text(
                    text = pair.first,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RhythmContent(
    state: ChallengeUiState,
    onTap: () -> Unit,
    onReplay: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "敲击 4 下复读节奏",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "已敲 ${state.rhythmTaps.size} / 4",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onReplay,
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("重新听节奏")
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Tertiary),
    ) {
        Text(
            text = "敲！",
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SightReadingContent(
    state: ChallengeUiState,
    onReset: () -> Unit,
) {
    val question = state.question ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "看谱按顺序弹奏",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                question.sequence.forEachIndexed { idx, midi ->
                    val name = midiToName(midi)
                    val played = idx < state.sightReadingIndex
                    val current = idx == state.sightReadingIndex
                    val bg = when {
                        played -> Correct
                        current -> Primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (played || current) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "进度：${state.sightReadingIndex} / ${question.sequence.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onReset,
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("重新开始本题")
            }
        }
    }
}

@Composable
private fun GameOverContent(
    score: Int,
    highScore: Int,
    onRestart: () -> Unit,
) {
    val correctCount = score / 10
    val rewardStars = correctCount * 2
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 大星徽章
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(YellowLight, Secondary, PinkSoft),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(80.dp),
            )
        }
        Text(
            text = "挑战完成！",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "本次得分",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$score 分",
                    style = MaterialTheme.typography.displayMedium,
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "答对 $correctCount 题",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "奖励 $rewardStars 颗星",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (score >= highScore && score > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🎉 创造新纪录！",
                        style = MaterialTheme.typography.titleMedium,
                        color = Correct,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Button(
            onClick = onRestart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text(
                text = "再玩一次",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

/**
 * MIDI -> 音名（仅 C4-G4）。
 */
private fun midiToName(midi: Int): String = when (midi) {
    60 -> "C"
    62 -> "D"
    64 -> "E"
    65 -> "F"
    67 -> "G"
    else -> "?"
}

package com.pianokids.ui.parent

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianokids.data.db.PracticeSessionEntity
import com.pianokids.ui.learn.LessonCatalog
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.Primary
import com.pianokids.ui.theme.Secondary
import com.pianokids.ui.theme.Tertiary
import com.pianokids.ui.theme.Warning
import com.pianokids.ui.util.WindowClass
import com.pianokids.ui.util.rememberWindowClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 家长区界面。
 *
 * 进入流程：输入 PIN → 解锁 → 显示统计 + 设置。
 *
 * @param onBack 返回
 * @param onNavigateToUpdate 跳转到应用更新页
 */
@Composable
fun ParentScreen(
    onBack: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    viewModel: ParentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPinDialog by remember { mutableStateOf(false) }
    val windowClass = rememberWindowClass()
    val useTwoColumns = windowClass != WindowClass.COMPACT

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (windowClass == WindowClass.COMPACT) 16.dp else 24.dp)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部
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
                    text = "家长区",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.unlocked) {
                    IconButton(onClick = { showPinDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            }

            if (!uiState.unlocked) {
                PinUnlockCard(onVerify = viewModel::verifyPin)
            } else if (uiState.loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("加载中…")
                }
            } else {
                // 解锁后：统计 + 设置
                if (useTwoColumns) {
                    // 横屏：左右两列
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OverviewCard(
                                stars = uiState.totalStars,
                                petLevel = uiState.petLevel,
                                petExp = uiState.petExp,
                                streakDays = uiState.streakDays,
                                totalPracticeMs = uiState.totalPracticeMs,
                                averageAccuracy = uiState.averageAccuracy,
                            )
                            DailyGoalCard(
                                current = uiState.dailyGoalMinutes,
                                onChange = viewModel::setDailyGoal,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TtsToggleCard(
                                enabled = uiState.ttsEnabled,
                                onToggle = viewModel::toggleTts,
                            )
                            RecentSessionsCard(sessions = uiState.recentSessions)
                        }
                    }
                } else {
                    // 竖屏：单列
                    OverviewCard(
                        stars = uiState.totalStars,
                        petLevel = uiState.petLevel,
                        petExp = uiState.petExp,
                        streakDays = uiState.streakDays,
                        totalPracticeMs = uiState.totalPracticeMs,
                        averageAccuracy = uiState.averageAccuracy,
                    )
                    DailyGoalCard(
                        current = uiState.dailyGoalMinutes,
                        onChange = viewModel::setDailyGoal,
                    )
                    TtsToggleCard(
                        enabled = uiState.ttsEnabled,
                        onToggle = viewModel::toggleTts,
                    )
                    RecentSessionsCard(sessions = uiState.recentSessions)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 应用更新入口
                OutlinedButton(
                    onClick = onNavigateToUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = Primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("检查应用更新")
                }

                OutlinedButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("修改家长锁 PIN")
                }
            }
        }
    }

    // 修改 PIN 对话框
    if (showPinDialog) {
        PinEditDialog(
            onConfirm = { newPin ->
                viewModel.setPin(newPin)
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false },
        )
    }
}

@Composable
private fun PinUnlockCard(onVerify: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "家长区已锁定",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "输入 4 位 PIN 解锁（默认 0000）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                label = { Text("PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onVerify(pin); pin = "" },
                enabled = pin.length == 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("解锁", color = Color.White)
            }
        }
    }
}

@Composable
private fun OverviewCard(
    stars: Int,
    petLevel: Int,
    petExp: Int,
    streakDays: Int,
    totalPracticeMs: Long,
    averageAccuracy: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "📊 孩子成长报告",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            // 4 个数据卡
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatItem(
                    icon = Icons.Filled.Star,
                    value = stars.toString(),
                    label = "星星",
                    color = Secondary,
                    modifier = Modifier.weight(1f),
                )
                StatItem(
                    icon = Icons.Filled.LocalFireDepartment,
                    value = "$streakDays 天",
                    label = "连续打卡",
                    color = Correct,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatItem(
                    icon = Icons.Filled.MusicNote,
                    value = "${totalPracticeMs / 60_000} 分",
                    label = "累计练琴",
                    color = Primary,
                    modifier = Modifier.weight(1f),
                )
                StatItem(
                    icon = Icons.Filled.Star,
                    value = "${(averageAccuracy * 100).toInt()}%",
                    label = "平均准确率",
                    color = Tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
            // 豆豆等级
            Text(
                text = "豆豆等级 Lv.$petLevel · 经验 $petExp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DailyGoalCard(
    current: Int,
    onChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "🎯 每日练琴目标",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "当前：$current 分钟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5, 10, 15, 20, 30).forEach { min ->
                    OutlinedButton(
                        onClick = { onChange(min) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (current == min) ButtonDefaults.outlinedButtonColors(
                            containerColor = Primary,
                        ) else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(
                            "$min",
                            color = if (current == min) Color.White else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsToggleCard(
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.VolumeUp,
                contentDescription = null,
                tint = if (enabled) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🔊 语音引导",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (enabled) "已开启 TTS 语音播报" else "已关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
private fun RecentSessionsCard(sessions: List<PracticeSessionEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "⏰ 最近练习记录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            if (sessions.isEmpty()) {
                Text(
                    text = "还没有练习记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                sessions.forEachIndexed { idx, s ->
                    SessionRow(s)
                    if (idx != sessions.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: PracticeSessionEntity) {
    val date = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(session.timestamp))
    val accuracyPercent = (session.accuracy * 100).toInt()
    val title = runCatching { LessonCatalog.get(session.lessonId).title }.getOrDefault(session.lessonId)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when {
                        accuracyPercent >= 80 -> Correct
                        accuracyPercent >= 50 -> Warning
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$accuracyPercent%",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PinEditDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newPin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改 PIN") },
        text = {
            Column {
                Text("请输入新的 4 位数字 PIN")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("新 PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newPin) },
                enabled = newPin.length == 4,
            ) {
                Text("保存", color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

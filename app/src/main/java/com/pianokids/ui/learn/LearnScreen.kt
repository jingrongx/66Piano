package com.pianokids.ui.learn

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianokids.ui.theme.OnSurface
import com.pianokids.ui.theme.Primary
import com.pianokids.ui.theme.Secondary
import com.pianokids.ui.theme.SurfaceVariant
import com.pianokids.ui.util.WindowClass
import com.pianokids.ui.util.rememberWindowClass

/**
 * 学习路径界面。
 *
 * 展示 6 个等级的学习路径列表，点击已解锁的课程进入 [LessonScreen]。
 *
 * @param onLessonClick 点击课程回调，参数为 lessonId
 */
@Composable
fun LearnScreen(
    onLessonClick: (String) -> Unit,
    viewModel: LearnViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val windowClass = rememberWindowClass()
    val useTwoColumns = windowClass != WindowClass.COMPACT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = if (windowClass == WindowClass.COMPACT) 16.dp else 24.dp)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "学习路径",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        viewModel.levels.forEach { section ->
            LevelHeader(section)
            if (useTwoColumns) {
                // 横屏：每两个 lesson 一行
                section.lessons.chunked(2).forEach { lessonPair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        lessonPair.forEach { lesson ->
                            LessonCard(
                                lesson = lesson,
                                stars = progress[lesson.id] ?: 0,
                                unlocked = viewModel.isUnlocked(lesson.id),
                                onClick = { onLessonClick(lesson.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // 奇数个时补一个空 Spacer 占位
                        if (lessonPair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                section.lessons.forEach { lesson ->
                    LessonCard(
                        lesson = lesson,
                        stars = progress[lesson.id] ?: 0,
                        unlocked = viewModel.isUnlocked(lesson.id),
                        onClick = { onLessonClick(lesson.id) },
                    )
                }
            }
        }
    }
}

/**
 * 等级标题。
 */
@Composable
private fun LevelHeader(section: LevelSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = section.emoji, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "等级 ${section.level} · ${section.title}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 单个课程卡片。
 */
@Composable
private fun LessonCard(
    lesson: LessonItem,
    stars: Int,
    unlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clickable(enabled = unlocked) { onClick() }
            .alpha(if (unlocked) 1f else 0.5f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) MaterialTheme.colorScheme.surface else SurfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (unlocked) 3.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 课程图标
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (unlocked) Secondary.copy(alpha = 0.3f)
                        else Color.Gray.copy(alpha = 0.2f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = lesson.icon, fontSize = 26.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            // 课程信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (unlocked) OnSurface else Color.Gray,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = lesson.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 锁或星星
            if (!unlocked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "未解锁",
                    tint = Color.Gray,
                    modifier = Modifier.size(28.dp),
                )
            } else if (stars > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(stars) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "星",
                            tint = Secondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
